package com.loaffie.titanfall.awall;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A-Wall: Titanfall 2's Amped Wall tactical ({@code mp_weapon_deployable_cover}). The pilot tosses an emitter
 * disc from the pack on their back; where it lands it throws up a curved wall of energy, for fifteen seconds,
 * that stops every shot coming at it from the front and amps the shots going out through it from behind.
 * People walk through it; only shots - and blasts - are stopped. The charge is back twenty seconds after a throw.
 *
 * <p>The wall's shape is the game's: part of a cylinder ({@code CreateShieldWithSettings}, radius 84 and height
 * 89 units, a 150 degree arc) whose axis stands a radius behind the disc, so the wall bows out away from the
 * pilot with the disc at its middle. Titanfall's units are scaled as the movement's are, 72 units of pilot to
 * 1.8 blocks: 1 unit = 0.025 blocks.
 */
public final class AWall {
	/** The wall's radius, blocks ({@code DEPLOYABLE_SHIELD_RADIUS} 84). */
	public static final double RADIUS = 84 * 0.025;
	/** Its height, blocks ({@code DEPLOYABLE_SHIELD_HEIGHT} 89). */
	public static final double HEIGHT = 89 * 0.025;
	/** Half its arc, radians ({@code DEPLOYABLE_SHIELD_FOV} 150). */
	public static final double HALF_ARC = Math.toRadians(150.0 / 2.0);
	/** The wall reaches this far down past the disc, blocks, so a shot along uneven ground can't slip under it. */
	public static final double SKIRT = 0.15;
	/** Throw speed, blocks/tick: a short, underhand toss that lands a few blocks out. */
	public static final double THROW_SPEED = 0.6;
	/** The disc's fall, blocks/tick^2: a heavy disc ({@code projectile_gravity_scale} 3), three times the game's gravity. */
	public static final double GRAVITY = 0.12;
	/** The toss leaves a little above the view ({@code projectile_launch_pitch_offset} 2), degrees. */
	public static final float LAUNCH_PITCH_OFFSET = 2.0F;
	/** The wall is shut down over this many ticks when it runs out or is shot down. */
	public static final int COLLAPSE_TICKS = 8;

	private AWall() {
	}

	/** Worn on the back (Trinkets' back slot, in the chest group) or held in either hand. */
	public static boolean isWorn(LivingEntity entity) {
		return PilotGear.worn(entity, TitanfallItems.PILOT_AWALL);
	}

	public static int durationTicks(TitanfallConfig config) {
		return Math.max(1, (int) Math.round(config.awallDurationSeconds * 20.0));
	}

	public static int rechargeTicks(TitanfallConfig config) {
		return Math.max(0, (int) Math.round(config.awallRechargeSeconds * 20.0));
	}

	/** The way a wall planted by a pilot looking along {@code yaw} faces: out, away from them. */
	public static Vec3d facing(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	/** The disc's launch velocity for a toss along the view, the pilot's own motion carried into it ({@code grenade_inherit_owner_velocity}). */
	public static Vec3d launch(float yaw, float pitch, Vec3d motion) {
		return Vec3d.fromPolar(pitch - LAUNCH_PITCH_OFFSET, yaw).multiply(THROW_SPEED).add(motion);
	}

	/**
	 * Where a straight path {@code from} to {@code to} first meets a wall standing on {@code base} facing
	 * {@code facing}, or null if it misses. {@link Crossing#outward} tells which way it went through: out from
	 * the pilot's side (amped) or in from the front (stopped).
	 */
	public static Crossing cross(Vec3d base, Vec3d facing, Vec3d from, Vec3d to) {
		// Work in the cylinder's frame, its axis through the centre a radius behind the disc.
		double cx = base.x - facing.x * RADIUS;
		double cz = base.z - facing.z * RADIUS;
		double ax = from.x - cx;
		double az = from.z - cz;
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double a = dx * dx + dz * dz;
		if (a < 1.0E-12) {
			return null;
		}

		double b = 2.0 * (ax * dx + az * dz);
		double c = ax * ax + az * az - RADIUS * RADIUS;
		double disc = b * b - 4.0 * a * c;
		if (disc < 0.0) {
			return null;
		}

		double root = Math.sqrt(disc);
		double minDot = Math.cos(HALF_ARC);
		for (double t : new double[] {(-b - root) / (2.0 * a), (-b + root) / (2.0 * a)}) {
			if (t < 0.0 || t > 1.0) {
				continue;
			}

			double hx = ax + dx * t;
			double hz = az + dz * t;
			double nx = hx / RADIUS;
			double nz = hz / RADIUS;
			double y = from.y + (to.y - from.y) * t;
			if (nx * facing.x + nz * facing.z < minDot || y < base.y - SKIRT || y > base.y + HEIGHT) {
				continue;
			}

			boolean outward = dx * nx + dz * nz > 0.0;
			return new Crossing(new Vec3d(cx + hx, y, cz + hz), new Vec3d(nx, 0.0, nz), outward, t);
		}

		return null;
	}

	/** A path through a wall: where ({@code point}), the wall's outward normal there, which way, and how far along the path (0..1). */
	public record Crossing(Vec3d point, Vec3d normal, boolean outward, double t) {
	}
}
