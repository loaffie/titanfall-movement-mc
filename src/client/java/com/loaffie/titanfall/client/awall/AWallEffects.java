package com.loaffie.titanfall.client.awall;

import com.loaffie.titanfall.awall.AWall;
import com.loaffie.titanfall.awall.AWallEntity;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The A-Wall's own particles (the ones for shots stopped and amped come from the server, where that is
 * decided): sparks and a ring as the disc plants, a slow scatter of embers drifting off the wall while it
 * stands, and the wall breaking up into flecks when it comes down - more, and flung harder, when it was shot
 * down.
 */
public final class AWallEffects {
	private static final int PLANT_SPARKS = 12;
	private static final int COLLAPSE_SHARDS = 46;
	/** Embers a tick off a standing wall, on average. */
	private static final float IDLE_EMBERS = 0.6F;

	private AWallEffects() {
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	/** A point on the wall: {@code across} -1..1 along its arc, {@code up} 0..1 up it. */
	public static Vec3d onWall(AWallEntity wall, double across, double up) {
		Vec3d facing = wall.facing();
		double cx = wall.getX() - facing.x * AWall.RADIUS;
		double cz = wall.getZ() - facing.z * AWall.RADIUS;
		double base = Math.atan2(facing.z, facing.x);
		double a = base + across * AWall.HALF_ARC;
		return new Vec3d(cx + Math.cos(a) * AWall.RADIUS, wall.getY() + up * AWall.HEIGHT, cz + Math.sin(a) * AWall.RADIUS);
	}

	public static void planted(AWallEntity wall) {
		if (!enabled()) {
			return;
		}

		World world = wall.getWorld();
		Random random = wall.getRandom();
		world.addParticle(TitanfallParticles.AWALL_RIPPLE, wall.getX(), wall.getY() + 0.03, wall.getZ(), 0.0, 1.0, 0.0);
		world.addParticle(TitanfallParticles.AWALL_FLASH, wall.getX(), wall.getY() + 0.2, wall.getZ(), 0.0, 0.0, 0.0);
		for (int i = 0; i < PLANT_SPARKS; i++) {
			double a = random.nextDouble() * MathHelper.TAU;
			double s = 0.08 + random.nextDouble() * 0.12;
			world.addParticle(TitanfallParticles.AWALL_SPARK, wall.getX(), wall.getY() + 0.12, wall.getZ(),
					Math.cos(a) * s, 0.12 + random.nextDouble() * 0.15, Math.sin(a) * s);
		}

		// The wall's two edges flare as it spreads out to them.
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < 3; i++) {
				Vec3d p = onWall(wall, side, random.nextDouble());
				world.addParticle(TitanfallParticles.AWALL_SHARD, p.x, p.y, p.z, 0.0, 0.01, 0.0);
			}
		}
	}

	/** Now and then an ember drifts up off the face, more of them, and redder, as the wall weakens. */
	static void idle(AWallEntity wall, AWallStates.State state) {
		if (!enabled()) {
			return;
		}

		Random random = wall.getRandom();
		float rate = IDLE_EMBERS * (1.0F + 1.5F * (1.0F - wall.healthFraction()) + state.warning());
		if (random.nextFloat() >= rate) {
			return;
		}

		Vec3d p = onWall(wall, random.nextDouble() * 2.0 - 1.0, random.nextDouble());
		Vec3d out = p.subtract(wall.getX() - wall.facing().x * AWall.RADIUS, p.y, wall.getZ() - wall.facing().z * AWall.RADIUS).normalize();
		double side = random.nextBoolean() ? 0.01 : -0.01;
		wall.getWorld().addParticle(TitanfallParticles.AWALL_SHARD, p.x, p.y, p.z, out.x * side, 0.008 + random.nextDouble() * 0.01, out.z * side);
	}

	/** Coming down: flecks all over the wall, falling away; flung out hard if it was shot down. */
	static void collapsed(AWallEntity wall, boolean destroyed) {
		if (!enabled()) {
			return;
		}

		World world = wall.getWorld();
		Random random = wall.getRandom();
		double push = destroyed ? 0.08 : 0.02;
		Vec3d centre = new Vec3d(wall.getX() - wall.facing().x * AWall.RADIUS, 0.0, wall.getZ() - wall.facing().z * AWall.RADIUS);
		for (int i = 0; i < COLLAPSE_SHARDS; i++) {
			Vec3d p = onWall(wall, random.nextDouble() * 2.0 - 1.0, random.nextDouble());
			Vec3d out = new Vec3d(p.x - centre.x, 0.0, p.z - centre.z).normalize().multiply((random.nextDouble() - 0.3) * push);
			world.addParticle(TitanfallParticles.AWALL_SHARD, p.x, p.y, p.z, out.x, (random.nextDouble() - 0.5) * 0.03, out.z);
		}

		if (destroyed) {
			for (int i = 0; i < PLANT_SPARKS; i++) {
				double a = random.nextDouble() * MathHelper.TAU;
				world.addParticle(TitanfallParticles.AWALL_SPARK, wall.getX(), wall.getY() + 0.15, wall.getZ(),
						Math.cos(a) * 0.12, 0.1 + random.nextDouble() * 0.1, Math.sin(a) * 0.12);
			}
		}
	}
}
