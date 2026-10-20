package com.loaffie.titanfall.pulse;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The Pulse Blade: Titanfall 2's Pulse Blade Pilot tactical ({@code mp_weapon_grenade_sonar}). A throwing
 * kunai with a sonar emitter: held, it shows its arc; thrown, it sticks where it lands and sends out sonar
 * pulses that reveal everything around it, through walls, to the pilot who threw it and their team. Worn
 * in a sheath on the left hip (Trinkets' belt slot), or held.
 *
 * <p>The flight is the same on both sides and in the aiming arc: {@link #origin} and {@link #launch} give the
 * start, then every tick the kunai moves by its velocity and falls by {@link #gravity}.
 */
public final class PulseBlade {
	public static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(TitanfallMovement.MOD_ID, "pulse_blade"));
	/** Units per block: Titanfall's 1 u = 1 inch. */
	private static final double UNITS_TO_BLOCKS = 0.0254;
	/** Titanfall's {@code sv_gravity} (750 u/s^2) times the kunai's {@code projectile_gravity_scale} (2). */
	private static final double GRAVITY_UNITS = 750.0 * 2.0;
	/** The pilot's own motion carried into the throw ({@code projectile_inherit_owner_velocity_scale} 1), capped, blocks/tick. */
	private static final double MAX_INHERITED = 2.0;
	/** Where the kunai leaves the hand, from the eye: ahead, out to the throwing arm's side, and a little up (an overhand throw). */
	private static final double ORIGIN_AHEAD = 0.35;
	private static final double ORIGIN_SIDE = 0.22;
	private static final double ORIGIN_UP = 0.05;

	private PulseBlade() {
	}

	public static boolean isWorn(LivingEntity entity) {
		return PilotGear.worn(entity, TitanfallItems.PILOT_PULSE_BLADE);
	}

	/** Gravity on the kunai in flight, blocks/tick^2. */
	public static double gravity() {
		return GRAVITY_UNITS * UNITS_TO_BLOCKS / 400.0;
	}

	/** Throw speed, blocks/tick (Titanfall's {@code projectile_launch_speed} is 3500 u/s = 88.9 b/s). */
	public static double speed(TitanfallConfig config) {
		return config.pulseThrowSpeed / 20.0;
	}

	/** The arm that throws: the pilot's main arm (it draws across the body from the sheath on the left hip). */
	public static Arm throwingArm(LivingEntity entity) {
		return entity.getMainArm();
	}

	/** Where the kunai starts for a throw from {@code eye} with this view, by {@code arm}. */
	public static Vec3d origin(Vec3d eye, float yaw, float pitch, Arm arm) {
		Vec3d look = Vec3d.fromPolar(pitch, yaw);
		Vec3d right = Vec3d.fromPolar(0.0F, yaw + 90.0F);
		double side = arm == Arm.RIGHT ? ORIGIN_SIDE : -ORIGIN_SIDE;
		return eye.add(look.multiply(ORIGIN_AHEAD)).add(right.multiply(side)).add(0.0, ORIGIN_UP, 0.0);
	}

	/** The kunai's starting velocity for a throw along this direction, carrying the pilot's own motion. */
	public static Vec3d launch(TitanfallConfig config, float yaw, float pitch, Vec3d ownerVelocity) {
		return Vec3d.fromPolar(pitch, yaw).multiply(speed(config)).add(inherited(ownerVelocity));
	}

	/** The share of the pilot's motion the kunai carries: all of it, capped. */
	public static Vec3d inherited(Vec3d ownerVelocity) {
		return ownerVelocity.lengthSquared() > MAX_INHERITED * MAX_INHERITED ? ownerVelocity.normalize().multiply(MAX_INHERITED) : ownerVelocity;
	}

	/**
	 * The throw direction that lands the kunai on {@code target} - what's under the crosshair - from {@code
	 * origin} (the hand, off to the side of the eye), as a unit vector, or null if it can't reach. Shooters
	 * converge projectiles on the crosshair the same way; thrown straight along the view, the kunai would land
	 * off to the throwing hand's side and low.
	 *
	 * <p>The flight steps once a tick - move by the velocity, then fall by {@link #gravity} - so after {@code n}
	 * ticks it has moved {@code n v - g n(n-1)/2} (up). With {@code v = s d + u} (speed {@code s} along unit
	 * {@code d}, plus the inherited motion {@code u}) landing on the target needs
	 * {@code s d n = D - u n + g n(n-1)/2 up}, where {@code D} is the target from the origin: solved for the
	 * flight time {@code n} at which the right side's length is {@code s n} (the flatter arc), then for
	 * {@code d}.
	 */
	public static Vec3d aimAt(TitanfallConfig config, Vec3d origin, Vec3d target, Vec3d ownerVelocity) {
		double s = speed(config);
		double g = gravity();
		Vec3d u = inherited(ownerVelocity);
		Vec3d d = target.subtract(origin);
		double n = d.length() / s;
		if (n < 1.0E-3) {
			return null;
		}

		// Newton's method on f(n) = |R(n)| - s n, from the straight-line time; it settles in a few steps.
		for (int i = 0; i < 12; i++) {
			Vec3d r = required(d, u, g, n);
			double f = r.length() - s * n;
			Vec3d r2 = required(d, u, g, n + 1.0E-3);
			double slope = (r2.length() - s * (n + 1.0E-3) - f) / 1.0E-3;
			if (Math.abs(slope) < 1.0E-9) {
				break;
			}

			double next = n - f / slope;
			if (!Double.isFinite(next) || next <= 0.0) {
				return null;
			}

			n = next;
			if (Math.abs(f) < 1.0E-5) {
				break;
			}
		}

		Vec3d r = required(d, u, g, n);
		if (Math.abs(r.length() - s * n) > 1.0E-2) {
			return null;
		}

		return r.normalize();
	}

	private static Vec3d required(Vec3d d, Vec3d u, double g, double n) {
		return d.subtract(u.multiply(n)).add(0.0, g * n * (n - 1.0) / 2.0, 0.0);
	}

	/** The yaw (degrees, Minecraft's) of a direction. */
	public static float yawOf(Vec3d direction) {
		return (float) (MathHelper.atan2(-direction.x, direction.z) * MathHelper.DEGREES_PER_RADIAN);
	}

	/** The pitch (degrees, Minecraft's: positive down) of a unit direction. */
	public static float pitchOf(Vec3d direction) {
		return (float) (-Math.asin(MathHelper.clamp(direction.y, -1.0, 1.0)) * MathHelper.DEGREES_PER_RADIAN);
	}

	/** Pulse radius, blocks (Titanfall's {@code SONAR_GRENADE_RADIUS} 1250 u). */
	public static double radius(TitanfallConfig config) {
		return config.pulseRadius;
	}

	/** Ticks between pulses (1.3333 s), the first as the kunai lands. */
	public static int pulseInterval(TitanfallConfig config) {
		return Math.max(1, MathHelper.ceil(config.pulseIntervalSeconds * 20.0));
	}

	/** Ticks the kunai pulses for once it has landed, before it is gone ({@code grenade_ignition_time} 4.5 s). */
	public static int activeTicks(TitanfallConfig config) {
		return Math.max(1, MathHelper.ceil(config.pulseActiveSeconds * 20.0));
	}

	/**
	 * Whether a pulse thrown by {@code owner} reveals {@code target}: anything living but the thrower, their
	 * team, armor stands and pilots out of phase (the game's sonar trigger ignores phased entities).
	 */
	public static boolean reveals(Entity owner, LivingEntity target) {
		if (target == owner || !target.isAlive() || target.isSpectator() || target instanceof ArmorStandEntity || PhaseShift.isPhased(target)) {
			return false;
		}

		return owner == null || !owner.isTeammate(target);
	}
}
