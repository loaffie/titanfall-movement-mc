package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.entity.TitanfallEntities;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * A Holo Pilot decoy: a hologram of its pilot that carries on with the pilot's last move. Titanfall 2 makes
 * one with {@code CreatePlayerDecoy}: it copies the pilot's movement and runs on the way they were heading if
 * the stick was pushed past 65%, or stands where it appeared; it slides on out of a slide and runs along a wall
 * it was running on. It lives ten seconds ({@code DECOY_DURATION}), has 50 health - half a pilot's - and
 * dissolves when shot down or when its time is up. It can be meleed and shot, but its own team can't hurt it.
 *
 * <p>Here it is a living entity the server moves with the pilot's own physics - the run at the pilot's run
 * speed under Source ground friction, the slide's decay, the wall run's gravity, and the pilot's gravity and
 * jump with or without a jumpkit - hopping onto anything a block high in its way. It wears copies of the
 * pilot's armour and holds copies of what they held, purely for looks: the copies carry no enchantments or
 * attribute modifiers, and never drop. The client draws it as the pilot (see {@code HoloDecoyRenderer}).
 */
public class HoloDecoyEntity extends LivingEntity {
	/** What the pilot was doing as the decoy was made, and what the decoy is doing now. */
	public enum Move {
		/** On the ground: running in its direction, or standing. */
		GROUND,
		/** In the air, on whatever course it had. */
		AIR,
		/** Sliding on, until the slide runs down. */
		SLIDE,
		/** Running along a wall, until the run gives out or the wall ends. */
		WALL;

		static Move of(int ordinal) {
			Move[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AIR;
		}
	}

	private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(HoloDecoyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Boolean> LEFT_HANDED = DataTracker.registerData(HoloDecoyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** The age the decoy started to dissolve, or -1 while it holds. */
	private static final TrackedData<Integer> DISSOLVE_START = DataTracker.registerData(HoloDecoyEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** The decoy breaks up over this long once shot down or out of time, ticks. */
	public static final int DISSOLVE_TICKS = 12;

	private static final double DT = 1.0 / 20.0;
	/** The pilot's run speed, blocks/second: the speed a pilot's skid hands back to vanilla's sprint. */
	private static final double RUN_SPEED = 5.8;
	/** Vanilla's ground drag for a decoy without a jumpkit, and the air drag every pilot has none of. */
	private static final double VANILLA_AIR_DRAG = 0.91;
	private static final double VERTICAL_DRAG = 0.98;
	/** Ticks spent pressed against something it can't hop before it gives up running and stands. */
	private static final int BLOCKED_TICKS_TO_STAND = 8;
	/**
	 * Ticks a slide survives off the ground: a new entity isn't on the ground until its first move, and a
	 * slide over a small dip shouldn't end it.
	 */
	private static final int SLIDE_AIR_GRACE = 3;
	/** How far from its side the wall must still be for the run to go on, blocks. */
	private static final double WALL_REACH = 0.4;
	/** Its head turns this far either way while it stands, degrees, so it doesn't stand like a statue. */
	private static final float LOOK_AROUND = 35.0F;

	private final DefaultedList<ItemStack> handItems = DefaultedList.ofSize(2, ItemStack.EMPTY);
	private final DefaultedList<ItemStack> armorItems = DefaultedList.ofSize(4, ItemStack.EMPTY);

	private Move move = Move.AIR;
	private boolean running;
	/** Its heading, a unit vector on the ground. */
	private Vec3d runDirection = new Vec3d(0.0, 0.0, 1.0);
	private Vec3d wallNormal = Vec3d.ZERO;
	private boolean pilotPhysics;
	private int life;
	private int moveTicks;
	private int airTicks;
	private int blockedTicks;
	private boolean wasBlocked;

	public HoloDecoyEntity(EntityType<? extends HoloDecoyEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
				.add(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 0.0);
	}

	/**
	 * A decoy of {@code pilot}, carrying on with the move the pilot's client described. Its look - armour,
	 * hands, which hand leads - and its physics - gravity, jump, whether it has a jumpkit's - are the pilot's.
	 */
	public static HoloDecoyEntity of(ServerPlayerEntity pilot, HoloPayload payload, TitanfallConfig config) {
		HoloDecoyEntity decoy = new HoloDecoyEntity(TitanfallEntities.HOLO_DECOY, pilot.getWorld());
		decoy.dataTracker.set(OWNER, Optional.of(pilot.getUuid()));
		decoy.dataTracker.set(LEFT_HANDED, pilot.getMainArm() == Arm.LEFT);
		decoy.life = HoloPilot.durationTicks(config);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				decoy.equipStack(slot, lookAlike(pilot.getEquippedStack(slot)));
			}
		}

		double health = Math.max(1.0, config.holoHealth);
		decoy.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		decoy.setHealth((float) health);
		decoy.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(pilot.getAttributeValue(EntityAttributes.GENERIC_GRAVITY));
		decoy.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(pilot.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
		decoy.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(pilot.getAttributeValue(EntityAttributes.GENERIC_STEP_HEIGHT));
		decoy.pilotPhysics = Jumpkit.isWorn(pilot);

		Vec3d velocity = new Vec3d(payload.vx(), payload.vy(), payload.vz());
		double max = config.maxVelocity * DT;
		if (!Double.isFinite(velocity.lengthSquared())) {
			velocity = Vec3d.ZERO;
		} else if (velocity.length() > max) {
			velocity = velocity.normalize().multiply(max);
		}

		float yaw = Float.isFinite(payload.runYaw()) ? payload.runYaw() : pilot.getYaw();
		decoy.refreshPositionAndAngles(pilot.getX(), pilot.getY(), pilot.getZ(), yaw, 0.0F);
		decoy.setHeadYaw(yaw);
		decoy.setBodyYaw(yaw);
		decoy.setVelocity(velocity);
		decoy.runDirection = Vec3d.fromPolar(0.0F, yaw);
		decoy.running = payload.run();
		decoy.move = Move.of(payload.move());
		Vec3d normal = new Vec3d(payload.wallX(), 0.0, payload.wallZ());
		if (decoy.move == Move.WALL) {
			if (normal.lengthSquared() < 0.25 || !Double.isFinite(normal.lengthSquared())) {
				decoy.move = Move.AIR;
			} else {
				decoy.wallNormal = normal.normalize();
			}
		}

		if (decoy.move == Move.SLIDE) {
			decoy.setPose(EntityPose.SLIDING);
		}

		return decoy;
	}

	/** A copy of a stack for the decoy to show: no enchantments (a shimmer instead), no attribute modifiers. */
	private static ItemStack lookAlike(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack copy = stack.copy();
		if (copy.hasEnchantments()) {
			copy.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		}

		copy.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		return copy;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(OWNER, Optional.empty());
		builder.add(LEFT_HANDED, false);
		builder.add(DISSOLVE_START, -1);
	}

	public UUID ownerUuid() {
		return this.dataTracker.get(OWNER).orElse(null);
	}

	/** The pilot it is a hologram of, if they are in this world. */
	public PlayerEntity owner() {
		UUID owner = ownerUuid();
		return owner == null ? null : this.getWorld().getPlayerByUuid(owner);
	}

	public boolean dissolving() {
		return this.dataTracker.get(DISSOLVE_START) >= 0;
	}

	/** How far it has dissolved, 0..1. */
	public float dissolve(float tickDelta) {
		int start = this.dataTracker.get(DISSOLVE_START);
		return start < 0 ? 0.0F : MathHelper.clamp((this.age - start + tickDelta) / DISSOLVE_TICKS, 0.0F, 1.0F);
	}

	private void startDissolving() {
		if (!dissolving()) {
			this.dataTracker.set(DISSOLVE_START, this.age);
			this.playSound(TitanfallSounds.HOLO_END, 0.9F, 0.95F + this.random.nextFloat() * 0.1F);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient()) {
			return;
		}

		if (!dissolving() && --life <= 0) {
			startDissolving();
		}

		if (dissolving() && this.dissolve(0.0F) >= 1.0F && !this.isRemoved()) {
			this.discard();
		}
	}

	/** Shot down: it dissolves instead of falling over, and leaves nothing behind. */
	@Override
	protected void updatePostDeath() {
		this.deathTime++;
		startDissolving();
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (this.isLogicalSideForUpdatingMovement()) {
			physics();
		}

		this.updateLimbs(false);
	}

	/** One tick of the pilot's movement with nobody at the controls: the move it was given, carried on. */
	private void physics() {
		moveTicks++;
		TitanfallConfig config = TitanfallConfig.get();
		Vec3d velocity = this.getVelocity();
		double gravity = this.getFinalGravity();
		boolean ground = this.isOnGround();
		airTicks = ground ? 0 : airTicks + 1;
		Vec3d horizontal = new Vec3d(velocity.x, 0.0, velocity.z);
		double vy = velocity.y;
		boolean frozen = dissolving() || this.isDead();

		if (move == Move.WALL) {
			if (!wallBeside() || moveTicks > config.wallRunMaxTicks || ground || frozen) {
				// The run gives out: off the wall with its speed along it, and on the way it was running.
				move = ground ? Move.GROUND : Move.AIR;
				if (horizontal.lengthSquared() > 1.0E-6) {
					runDirection = horizontal.normalize();
				}
			} else {
				Vec3d along = horizontal.subtract(wallNormal.multiply(horizontal.dotProduct(wallNormal)));
				double speed = along.length() / DT;
				if (along.lengthSquared() > 1.0E-8) {
					runDirection = along.normalize();
				}

				speed = running ? approach(speed, Math.max(speed, config.wallRunMaxSpeed), config.wallRunAccel * DT)
						: approach(speed, 0.0, config.wallRunFriction * DT);
				double wallGravity = config.wallRunGravity * Math.min(1.0, moveTicks / (double) config.wallRunGravityRampTicks);
				if (moveTicks > config.wallRunSlipTicks) {
					wallGravity += config.wallRunSlipGravity;
				}

				double verticalSpeed = approach(vy / DT, 0.0, config.wallRunVerticalDamping * DT) - wallGravity * DT;
				verticalSpeed = MathHelper.clamp(verticalSpeed, -config.wallRunMaxVerticalSpeed, config.wallRunMaxVerticalSpeed);
				// Pressed lightly into the wall, so it stays on it round gentle bends.
				horizontal = runDirection.multiply(speed * DT).subtract(wallNormal.multiply(0.02));
				vy = verticalSpeed * DT;
				moveAndTurn(horizontal, vy);
				return;
			}
		}

		if (move == Move.SLIDE) {
			if (airTicks > SLIDE_AIR_GRACE || frozen) {
				move = ground ? Move.GROUND : Move.AIR;
				this.setPose(EntityPose.STANDING);
			} else {
				double speed = horizontal.length() / DT;
				double decayed = speed * Math.pow(config.slideDecay, DT) - config.slideDecel * DT;
				if (decayed < config.slideEndSpeed) {
					// The slide is spent: up and running on, as a pilot coming out of one would.
					move = Move.GROUND;
					this.setPose(EntityPose.STANDING);
				} else {
					horizontal = horizontal.normalize().multiply(decayed * DT);
					runDirection = horizontal.normalize();
					moveAndTurn(horizontal, ground ? -gravity : (vy - gravity) * VERTICAL_DRAG);
					return;
				}
			}
		}

		if (ground) {
			move = Move.GROUND;
			boolean run = running && !frozen;
			// Source ground friction, then acceleration toward the run.
			double speed = horizontal.length() / DT;
			if (speed > 0.0) {
				double drop = Math.max(speed, config.stopSpeed) * config.groundFriction * DT;
				horizontal = horizontal.multiply(Math.max(speed - drop, 0.0) / speed);
			}

			if (run) {
				double wish = RUN_SPEED;
				double add = wish - horizontal.dotProduct(runDirection) / DT;
				if (add > 0.0) {
					horizontal = horizontal.add(runDirection.multiply(Math.min(add, config.groundAccelerate * wish * DT) * DT));
				}

				if (wasBlocked) {
					// Pressed against something: hop it if a jump clears it, else give up after a moment and stand.
					if (canHop()) {
						vy = this.getJumpVelocity();
						blockedTicks = 0;
					} else if (++blockedTicks >= BLOCKED_TICKS_TO_STAND) {
						running = false;
					}
				} else {
					blockedTicks = 0;
				}
			}

			if (vy <= 0.0) {
				vy = -gravity;
			}
		} else {
			move = Move.AIR;
			if (!pilotPhysics) {
				horizontal = horizontal.multiply(VANILLA_AIR_DRAG);
			}

			vy = (vy - gravity) * VERTICAL_DRAG;
		}

		if (this.isTouchingWater() || this.isInLava()) {
			// Wading: slowed, and bobbing at the surface rather than sinking.
			horizontal = horizontal.multiply(0.8);
			vy = vy * 0.8 + (this.isSubmergedInWater() ? 0.03 : 0.0);
		}

		moveAndTurn(horizontal, vy);
	}

	private void moveAndTurn(Vec3d horizontal, double vy) {
		Vec3d velocity = new Vec3d(horizontal.x, vy, horizontal.z);
		this.setVelocity(velocity);
		this.move(MovementType.SELF, velocity);
		wasBlocked = this.horizontalCollision && running;
		if (move == Move.GROUND && wasBlocked && !this.isOnGround()) {
			wasBlocked = false;
		}

		float heading = (float) (MathHelper.atan2(-runDirection.x, runDirection.z) * MathHelper.DEGREES_PER_RADIAN);
		float yaw = MathHelper.stepUnwrappedAngleTowards(this.getYaw(), heading, 20.0F);
		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		float look = running || move != Move.GROUND ? 0.0F : LOOK_AROUND * MathHelper.sin(this.age * 0.035F) * MathHelper.sin(this.age * 0.011F + 1.0F);
		this.setHeadYaw(yaw + look);
	}

	/** Whether a jump clears what it ran into: the space a block up, a step ahead, is open. */
	private boolean canHop() {
		Vec3d ahead = runDirection.multiply(0.3);
		double rise = 1.0 + this.getStepHeight();
		return this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(0.0, rise, 0.0).offset(ahead.x, 0.0, ahead.z))
				&& this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(0.0, rise, 0.0));
	}

	/** Whether the wall it was running on is still at its side. */
	private boolean wallBeside() {
		Vec3d in = wallNormal.multiply(-WALL_REACH);
		return !this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(in.x, 0.0, in.z));
	}

	private static double approach(double value, double target, double step) {
		return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (dissolving()) {
			return false;
		}

		// Its own side can't hurt it ({@code SetFriendlyFire( false )}).
		Entity attacker = source.getAttacker();
		PlayerEntity owner = owner();
		if (attacker != null && owner != null && (attacker == owner || attacker.isTeammate(owner))) {
			return false;
		}

		return super.damage(source, amount);
	}

	@Override
	public boolean isTeammate(Entity other) {
		PlayerEntity owner = owner();
		return owner != null && (other == owner || owner.isTeammate(other)) || super.isTeammate(other);
	}

	/**
	 * A hit decoy sounds like a hit player, so the shooter can't tell by ear; the crackle of its projection is
	 * played to its own side by the client ({@code HoloDecoys}).
	 */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void tickCramming() {
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	protected void dropInventory() {
	}

	@Override
	public boolean canTakeDamage() {
		return !dissolving() && super.canTakeDamage();
	}

	@Override
	public Iterable<ItemStack> getArmorItems() {
		return armorItems;
	}

	@Override
	public Iterable<ItemStack> getHandItems() {
		return handItems;
	}

	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return switch (slot.getType()) {
			case HAND -> handItems.get(slot.getEntitySlotId());
			case HUMANOID_ARMOR -> armorItems.get(slot.getEntitySlotId());
			default -> ItemStack.EMPTY;
		};
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
		switch (slot.getType()) {
			case HAND -> handItems.set(slot.getEntitySlotId(), stack);
			case HUMANOID_ARMOR -> armorItems.set(slot.getEntitySlotId(), stack);
			default -> {
			}
		}
	}

	@Override
	public Arm getMainArm() {
		return this.dataTracker.get(LEFT_HANDED) ? Arm.LEFT : Arm.RIGHT;
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
