package com.loaffie.titanfall.awall;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The A-Wall's emitter disc and, once it has landed, the wall it holds up.
 *
 * <p>In flight it is a heavy spinning disc ({@link AWall#GRAVITY}) that glances off walls and ceilings and
 * plants itself on the first floor it meets ({@code OnDeployableCoverPlanted} keeps a wall upright the same
 * way). Planted, it stands a wall ({@link AWall#cross}) facing the way it was thrown, for
 * {@link AWall#durationTicks} or until the shots it has stopped use up its health, then shuts it down over
 * {@link AWall#COLLAPSE_TICKS} and is gone.
 *
 * <p>The wall's work - stopping and amping shots, blocking blasts - is the server's ({@link AWallServer}); the
 * client draws it from the tracked state.
 */
public class AWallEntity extends ProjectileEntity {
	public static final byte FLYING = 0;
	public static final byte STANDING = 1;
	public static final byte COLLAPSING = 2;

	private static final TrackedData<Byte> STATE = DataTracker.registerData(AWallEntity.class, TrackedDataHandlerRegistry.BYTE);
	/** The way the wall faces, as a yaw (fixed at the throw). */
	private static final TrackedData<Float> WALL_YAW = DataTracker.registerData(AWallEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Health left, 0..1 of the full. */
	private static final TrackedData<Float> HEALTH = DataTracker.registerData(AWallEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Longest the disc may fly before it is given up on, ticks. */
	private static final int MAX_FLIGHT_TICKS = 100;
	/** What a glance off a wall or ceiling keeps of the speed into it and along it. */
	private static final double BOUNCE_NORMAL = 0.25;
	private static final double BOUNCE_ALONG = 0.6;

	/** Ticks since the wall went up (server), or -1 in flight. */
	private int standingTicks = -1;
	private int collapseTicks;
	private double health;

	public AWallEntity(EntityType<? extends AWallEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(STATE, FLYING);
		builder.add(WALL_YAW, 0.0F);
		builder.add(HEALTH, 1.0F);
	}

	/** Called by the server as the disc leaves the hand. */
	public void thrown(float yaw) {
		this.dataTracker.set(WALL_YAW, MathHelper.wrapDegrees(yaw));
		this.health = TitanfallConfig.get().awallHealth;
	}

	public byte state() {
		return this.dataTracker.get(STATE);
	}

	public boolean standing() {
		return state() == STANDING;
	}

	/** Planted: the wall is up or coming down. */
	public boolean planted() {
		return state() != FLYING;
	}

	public float wallYaw() {
		return this.dataTracker.get(WALL_YAW);
	}

	public Vec3d facing() {
		return AWall.facing(wallYaw());
	}

	/** Health left, 0..1. */
	public float healthFraction() {
		return this.dataTracker.get(HEALTH);
	}

	/** The space the wall stands in. */
	public Box wallBox() {
		Vec3d base = this.getPos();
		return new Box(base.x - AWall.RADIUS * 2.0, base.y - AWall.SKIRT, base.z - AWall.RADIUS * 2.0,
				base.x + AWall.RADIUS * 2.0, base.y + AWall.HEIGHT, base.z + AWall.RADIUS * 2.0);
	}

	@Override
	public Box getVisibilityBoundingBox() {
		return planted() ? wallBox() : super.getVisibilityBoundingBox();
	}

	@Override
	public void tick() {
		super.tick();
		if (planted()) {
			this.setVelocity(Vec3d.ZERO);
			if (!this.getWorld().isClient()) {
				tickStanding();
			}

			return;
		}

		if (!this.getWorld().isClient() && this.age > MAX_FLIGHT_TICKS) {
			this.discard();
			return;
		}

		fly();
	}

	/** One tick of flight: move, glancing off what isn't a floor, and fall. */
	private void fly() {
		World world = this.getWorld();
		Vec3d from = this.getPos();
		Vec3d velocity = this.getVelocity();
		Vec3d to = from.add(velocity);
		BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		if (hit.getType() == HitResult.Type.MISS) {
			this.setPosition(to);
			this.setVelocity(velocity.multiply(0.99).add(0.0, -AWall.GRAVITY, 0.0));
		} else if (hit.getSide() == Direction.UP) {
			this.setPosition(hit.getPos());
			if (!world.isClient()) {
				plant();
			} else {
				this.setVelocity(Vec3d.ZERO);
			}

			return;
		} else {
			Vec3d normal = Vec3d.of(hit.getSide().getVector());
			Vec3d into = normal.multiply(velocity.dotProduct(normal));
			Vec3d along = velocity.subtract(into);
			this.setPosition(hit.getPos().add(normal.multiply(0.05)));
			this.setVelocity(along.multiply(BOUNCE_ALONG).subtract(into.multiply(BOUNCE_NORMAL)).add(0.0, -AWall.GRAVITY, 0.0));
		}

		// Spin about the disc's own axis as it flies (the renderer reads the yaw).
		this.setYaw(this.getYaw() + 40.0F);
	}

	private void plant() {
		this.setVelocity(Vec3d.ZERO);
		this.dataTracker.set(STATE, STANDING);
		standingTicks = 0;
		AWallServer.planted(this);
		this.getWorld().playSound(null, this.getX(), this.getY() + 0.5, this.getZ(), TitanfallSounds.AWALL_DEPLOY, SoundCategory.PLAYERS, 1.0F,
				0.95F + this.random.nextFloat() * 0.1F);
	}

	private void tickStanding() {
		if (state() == COLLAPSING) {
			if (++collapseTicks >= AWall.COLLAPSE_TICKS) {
				this.discard();
			}

			return;
		}

		if (++standingTicks >= AWall.durationTicks(TitanfallConfig.get())) {
			collapse();
		}
	}

	/** Shuts the wall down: out of time, or shot down. */
	private void collapse() {
		this.dataTracker.set(STATE, COLLAPSING);
		collapseTicks = 0;
		AWallServer.removed(this);
		this.getWorld().playSound(null, this.getX(), this.getY() + 0.5, this.getZ(), TitanfallSounds.AWALL_END, SoundCategory.PLAYERS, 1.0F,
				0.95F + this.random.nextFloat() * 0.1F);
	}

	/** A shot it stopped: takes it off the wall's health, and brings the wall down when that is gone. */
	void absorb(double damage) {
		if (!standing()) {
			return;
		}

		double full = Math.max(1.0, TitanfallConfig.get().awallHealth);
		health -= damage;
		this.dataTracker.set(HEALTH, (float) MathHelper.clamp(health / full, 0.0, 1.0));
		if (health <= 0.0) {
			collapse();
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.getWorld().isClient()) {
			AWallServer.removed(this);
		}

		super.remove(reason);
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	protected boolean canHit(Entity entity) {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}
}
