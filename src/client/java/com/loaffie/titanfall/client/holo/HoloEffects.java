package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.holo.HoloDecoyEntity;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The Holo Pilot's particles: scan rings sweeping up a decoy as it is projected, the faint trail of
 * hologram flecks its own side sees coming off its chest ({@code HOLO_PILOT_TRAIL_FX}, friendly only), and
 * the pieces it breaks into as it dissolves.
 */
public final class HoloEffects {
	private static final int RINGS = 3;
	private static final int PROJECT_MOTES = 14;
	private static final int DISSOLVE_MOTES = 34;
	/** The chest, where the trail comes from, blocks above the feet. */
	private static final double CHEST = 1.15;

	private HoloEffects() {
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	public static void projected(HoloDecoyEntity decoy) {
		if (!enabled()) {
			return;
		}

		World world = decoy.getWorld();
		for (int i = 0; i < RINGS; i++) {
			double y = decoy.getY() + 0.1 + i * 0.8;
			world.addParticle(TitanfallParticles.HOLO_RING, decoy.getX(), y, decoy.getZ(), 0.0, 1.0, 0.0);
		}

		Random random = decoy.getRandom();
		for (int i = 0; i < PROJECT_MOTES; i++) {
			double a = random.nextDouble() * MathHelper.TAU;
			double r = 0.25 + random.nextDouble() * 0.2;
			world.addParticle(TitanfallParticles.HOLO_MOTE, decoy.getX() + Math.cos(a) * r, decoy.getY() + random.nextDouble() * 1.8,
					decoy.getZ() + Math.sin(a) * r, -Math.cos(a) * 0.02, 0.01, -Math.sin(a) * 0.02);
		}
	}

	/** A fleck or two off the chest every other tick, left hanging behind it. */
	public static void trail(HoloDecoyEntity decoy, int age) {
		if (!enabled() || (age & 1) != 0) {
			return;
		}

		Random random = decoy.getRandom();
		double x = decoy.getX() + (random.nextDouble() - 0.5) * 0.3;
		double z = decoy.getZ() + (random.nextDouble() - 0.5) * 0.3;
		decoy.getWorld().addParticle(TitanfallParticles.HOLO_MOTE, x, decoy.getY() + CHEST + (random.nextDouble() - 0.5) * 0.4, z,
				0.0, 0.006, 0.0);
	}

	/** Breaking up: flecks from all over the body, drifting up and out. */
	public static void dissolve(HoloDecoyEntity decoy) {
		if (!enabled()) {
			return;
		}

		Random random = decoy.getRandom();
		for (int i = 0; i < DISSOLVE_MOTES; i++) {
			double a = random.nextDouble() * MathHelper.TAU;
			double r = random.nextDouble() * 0.3;
			double y = random.nextDouble() * 1.8;
			decoy.getWorld().addParticle(TitanfallParticles.HOLO_MOTE, decoy.getX() + Math.cos(a) * r, decoy.getY() + y,
					decoy.getZ() + Math.sin(a) * r, Math.cos(a) * 0.03, 0.015 + random.nextDouble() * 0.03, Math.sin(a) * 0.03);
		}
	}
}
