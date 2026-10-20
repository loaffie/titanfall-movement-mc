package com.loaffie.titanfall.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * A thrown Pulse Blade. It flies fast and nearly flat ({@link PulseBlade#launch}, {@link PulseBlade#gravity})
 * and sticks into the first block it meets. A living thing in its way takes the blade - Titanfall's direct
 * hit is 100 damage, a pilot's whole health - and the kunai drops off it to stick where it lands.
 *
 * <p>Once stuck it sends a sonar pulse at once and every {@link PulseBlade#pulseInterval} after, for
 * {@link PulseBlade#activeTicks}, then is gone. The pulses' effects - the wave, the reveal, the warning to
 * those revealed - are all on the client, which knows the kunai's place and who threw it; the server only
 * flies it, deals the hit and times its life. Both sides run the same flight, so the kunai lands in the
 * same place on every screen.
 */
public class PulseBladeEntity extends ProjectileEntity {
	/** Client hooks for the kunai's effects (set by the client entrypoint). */
	public interface Listener {
		void flying(PulseBladeEntity blade);

		void landed(PulseBladeEntity blade);

		void pulse(PulseBladeEntity blade, int index);
	}

	private static final TrackedData<Boolean> STUCK = DataTracker.registerData(PulseBladeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Longest the kunai may fly before it is given up on, ticks. */
	private static final int MAX_FLIGHT_TICKS = 200;
	/** How much of its speed the kunai keeps glancing off someone it hit, and the little hop it takes. */
	private static final double GLANCE = 0.04;
	private static final double GLANCE_HOP = 0.15;
	/** The tip sinks this far into what it sticks in, blocks. */
	public static final double EMBED = 0.06;
	private static Listener listener = new Listener() {
		@Override
		public void flying(PulseBladeEntity blade) {
		}

		@Override
		public void landed(PulseBladeEntity blade) {
		}

		@Override
		public void pulse(PulseBladeEntity blade, int index) {
		}
	};

	private int stuckTicks = -1;
	private boolean hitSomeone;
	/** The client saw the kunai stick in its own run of the flight, ahead of the server saying so. */
	private boolean stuckLocally;
	/** Where the kunai points once stuck (its flight direction as it hit), for the renderer. */
	private Vec3d stuckDirection = new Vec3d(0.0, -1.0, 0.0);
	/** The face of the block it stuck in (where the pulse rings spread), null until known. */
	private Vec3d stuckNormal;

	public PulseBladeEntity(EntityType<? extends PulseBladeEntity> type, World world) {
		super(type, world);
	}

	public static void setListener(Listener clientListener) {
		listener = clientListener;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(STUCK, false);
	}

	public boolean isStuck() {
		return stuckLocally || this.dataTracker.get(STUCK);
	}

	/** Ticks since the kunai stuck, or -1 in flight. */
	public int stuckTicks() {
		return stuckTicks;
	}

	public Vec3d stuckDirection() {
		return stuckDirection;
	}

	/** The face of the block the kunai is stuck in, pointing out of it. */
	public Vec3d stuckNormal() {
		return stuckNormal == null ? new Vec3d(0.0, 1.0, 0.0) : stuckNormal;
	}

	@Override
	public void tick() {
		super.tick();
		TitanfallConfig config = TitanfallConfig.get();
		if (isStuck()) {
			if (stuckTicks < 0) {
				land();
			}

			stuckTicks++;
			this.setVelocity(Vec3d.ZERO);
			int interval = PulseBlade.pulseInterval(config);
			if (stuckTicks % interval == 1 && this.getWorld().isClient()) {
				listener.pulse(this, stuckTicks / interval);
			}

			if (!this.getWorld().isClient() && stuckTicks > PulseBlade.activeTicks(config)) {
				this.discard();
			}

			return;
		}

		if (!this.getWorld().isClient() && this.age > MAX_FLIGHT_TICKS) {
			this.discard();
			return;
		}

		fly();
		if (this.getWorld().isClient() && !isStuck()) {
			listener.flying(this);
		}
	}

	/** One tick of flight: move, hitting what is in the way, then fall. */
	private void fly() {
		World world = this.getWorld();
		Vec3d from = this.getPos();
		Vec3d velocity = this.getVelocity();
		Vec3d to = from.add(velocity);
		BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		if (block.getType() != HitResult.Type.MISS) {
			to = block.getPos();
		}

		EntityHitResult hit = hitSomeone ? null : ProjectileUtil.getEntityCollision(world, this, from, to,
				this.getBoundingBox().stretch(to.subtract(from)).expand(1.0), this::canHit);
		if (hit != null) {
			hitSomeone = true;
			if (!world.isClient() && hit.getEntity() instanceof LivingEntity target) {
				Entity owner = this.getOwner();
				target.damage(this.getDamageSources().create(PulseBlade.DAMAGE, this, owner), (float) TitanfallConfig.get().pulseImpactDamage);
			}

			// Back off the target to just in front of it, where the blade glanced off.
			Entity target = hit.getEntity();
			Vec3d direction = velocity.normalize();
			double back = target.getWidth() / 2.0 + 0.2;
			Vec3d at = new Vec3d(target.getX(), MathHelper.clamp(from.y, target.getY(), target.getY() + target.getHeight()), target.getZ());
			this.setPosition(at.subtract(direction.multiply(back)));
			this.setVelocity(velocity.multiply(-GLANCE).add(0.0, GLANCE_HOP, 0.0));
			return;
		}

		if (block.getType() != HitResult.Type.MISS) {
			Vec3d direction = velocity.normalize();
			this.setPosition(block.getPos().add(direction.multiply(EMBED)));
			stuckDirection = direction;
			stuckNormal = Vec3d.of(block.getSide().getVector());
			if (world.isClient()) {
				stuckLocally = true;
			} else {
				this.dataTracker.set(STUCK, true);
			}

			this.setVelocity(Vec3d.ZERO);
			return;
		}

		this.setPosition(to);
		this.setVelocity(velocity.add(0.0, -PulseBlade.gravity(), 0.0));
		Vec3d now = this.getVelocity();
		this.setYaw((float) (MathHelper.atan2(now.x, now.z) * MathHelper.DEGREES_PER_RADIAN));
		this.setPitch((float) (MathHelper.atan2(now.y, now.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN));
	}

	/**
	 * First tick stuck. A client that did not see the hit itself (the server's word came first) takes the
	 * direction the kunai stuck in from its last heading.
	 */
	private void land() {
		stuckTicks = 0;
		if (this.getWorld().isClient() && !stuckLocally) {
			float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			float pitch = this.getPitch() * MathHelper.RADIANS_PER_DEGREE;
			stuckDirection = new Vec3d(MathHelper.sin(yaw) * MathHelper.cos(pitch), MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
			// Find the face it went into by looking back along its heading.
			Vec3d at = this.getPos();
			BlockHitResult face = this.getWorld().raycast(new RaycastContext(at.subtract(stuckDirection.multiply(0.5)), at.add(stuckDirection.multiply(0.5)),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
			if (face.getType() != HitResult.Type.MISS) {
				stuckNormal = Vec3d.of(face.getSide().getVector());
			}
		}

		if (this.getWorld().isClient()) {
			listener.landed(this);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && entity instanceof LivingEntity && !PhaseShift.isPhased(entity);
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}
}
