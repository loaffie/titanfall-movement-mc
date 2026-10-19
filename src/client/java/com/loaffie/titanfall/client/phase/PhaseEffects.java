package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Phase Shift particles, after the game's {@code P_phase_shift_main}: shifting out, the pilot dissolves in a
 * burst of blue sparks and leaves a ghost of themselves and a short trail of light along the way they were
 * heading; shifting back in, a white-blue flash, a ring round their feet and a spray of sparks. While
 * phased the gauntlet's coil throws off sparks (seen by whoever can see the pilot). The same "thruster
 * particles" switch as the jumpkit's turns them off.
 */
public final class PhaseEffects {
	private static final int OUT_SPARKS = 22;
	private static final int IN_SPARKS = 28;
	private static final int TRAIL_WISPS = 10;

	private PhaseEffects() {
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	/** The local pilot in first person: their own shift happens on screen, not in particles in their face. */
	private static boolean ownView(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		return player == client.player && client.options.getPerspective().isFirstPerson();
	}

	static void shiftOut(PlayerEntity player) {
		if (!ownView(player)) {
			PhaseGhost.spawn(player);
		}

		if (!enabled() || ownView(player)) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		Vec3d motion = player.getVelocity();
		for (int i = 0; i < OUT_SPARKS; i++) {
			double y = player.getY() + random.nextDouble() * player.getHeight();
			double a = random.nextDouble() * MathHelper.TAU;
			double r = player.getWidth() * 0.5;
			double vx = Math.cos(a) * (0.08 + random.nextDouble() * 0.12) + motion.x * 0.5;
			double vz = Math.sin(a) * (0.08 + random.nextDouble() * 0.12) + motion.z * 0.5;
			double vy = (random.nextDouble() - 0.3) * 0.12;
			world.addParticle(TitanfallParticles.PHASE_SPARK, player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r, vx, vy, vz);
		}

		// The trail of light and smoke along the heading: a few wisps laid ahead along the motion, drifting on.
		Vec3d heading = motion.lengthSquared() > 1.0E-4 ? motion : player.getRotationVector().multiply(0.15);
		for (int i = 0; i < TRAIL_WISPS; i++) {
			double k = i / (double) TRAIL_WISPS * 3.0;
			Vec3d at = player.getPos().add(heading.multiply(k)).add((random.nextDouble() - 0.5) * 0.3,
					0.3 + random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 0.3);
			Vec3d drift = heading.multiply(0.35);
			world.addParticle(TitanfallParticles.PHASE_WISP, at.x, at.y, at.z, drift.x, drift.y, drift.z);
		}
	}

	static void shiftIn(PlayerEntity player) {
		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		boolean own = ownView(player);
		if (!own) {
			world.addParticle(TitanfallParticles.PHASE_FLASH, player.getX(), player.getBodyY(0.55), player.getZ(), 0.0, 0.0, 0.0);
		}

		world.addParticle(TitanfallParticles.PHASE_RING, player.getX(), player.getY() + 0.05, player.getZ(), 0.0, 1.0, 0.0);
		for (int i = 0; i < (own ? IN_SPARKS / 3 : IN_SPARKS); i++) {
			double a = random.nextDouble() * MathHelper.TAU;
			double speed = 0.12 + random.nextDouble() * 0.2;
			double y = player.getY() + random.nextDouble() * player.getHeight();
			world.addParticle(TitanfallParticles.PHASE_SPARK, player.getX(), y, player.getZ(),
					Math.cos(a) * speed, (random.nextDouble() - 0.2) * 0.15, Math.sin(a) * speed);
		}
	}

	/** One tick of a phased pilot: sparks off the coil, where it was drawn in the world. */
	static void phasedTick(PlayerEntity player, PhaseStates.State state) {
		if (!enabled() || !(player instanceof AbstractClientPlayerEntity client)) {
			return;
		}

		Vec3d coil = PhaseFeatureRenderer.coil(client);
		World world = player.getWorld();
		Random random = world.getRandom();
		if (coil == null || random.nextFloat() > 0.7F) {
			return;
		}

		Vec3d motion = player.getVelocity();
		for (int i = 0; i < 2; i++) {
			world.addParticle(TitanfallParticles.PHASE_SPARK, coil.x, coil.y, coil.z,
					motion.x + (random.nextDouble() - 0.5) * 0.2, motion.y + random.nextDouble() * 0.12, motion.z + (random.nextDouble() - 0.5) * 0.2);
		}
	}
}
