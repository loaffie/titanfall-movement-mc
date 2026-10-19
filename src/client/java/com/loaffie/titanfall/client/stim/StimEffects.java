package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.client.phase.PhaseStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.physics.HVec;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Stim particles. A stimmed pilot streams a glowing blue trail from the body - shoulders and hips - that
 * hangs in the air behind them and fades, so their speed reads from any distance; the injection itself
 * gives a soft blue flare at the chest. Uses the jumpkit's additive glow particles, and the same "thruster
 * particles" switch turns them off.
 */
public final class StimEffects {
	/** Trail anchors on the body: side offset (+ = the pilot's right) and height, blocks. */
	private static final double[][] ANCHORS = {{0.3, 1.25}, {-0.3, 1.25}, {0.14, 0.75}, {-0.14, 0.75}};
	/** Streaks per anchor per tick, spread between the last position and this one so the trail is unbroken. */
	private static final int STEPS = 4;

	private StimEffects() {
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	static void inject(PlayerEntity player) {
		if (!enabled()) {
			return;
		}

		if (player == MinecraftClient.getInstance().player && MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
			// Not in the camera's face: the screen effects carry the injection in first person.
			return;
		}

		HVec forward = HVec.forwardFromYaw(player.bodyYaw);
		Vec3d chest = player.getPos().add(forward.x() * 0.3, 1.1, forward.z() * 0.3);
		player.getWorld().addParticle(TitanfallParticles.STIM_FLASH, chest.x, chest.y, chest.z, 0.0, 0.0, 0.0);
	}

	/** One tick of trail behind a stimmed pilot. */
	static void trail(PlayerEntity player, StimStates.State state) {
		if (!enabled() || PhaseStates.hidden(player)) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		boolean ownView = player == MinecraftClient.getInstance().player && MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
		Vec3d from = new Vec3d(player.prevX, player.prevY, player.prevZ);
		Vec3d to = player.getPos();
		Vec3d motion = to.subtract(from);
		HVec right = HVec.rightFromYaw(player.bodyYaw);
		float strength = state.intensity(0.0F);
		for (int a = ownView ? 2 : 0; a < ANCHORS.length; a++) {
			double side = ANCHORS[a][0];
			double up = ANCHORS[a][1];
			for (int step = 0; step < STEPS; step++) {
				if (random.nextFloat() > strength) {
					continue;
				}

				Vec3d base = from.lerp(to, (step + random.nextDouble()) / STEPS);
				Vec3d at = base.add(right.x() * side, up + (random.nextDouble() - 0.5) * 0.1, right.z() * side);
				// A drift against the motion, which dies away, stretches each streak along the path behind the pilot.
				Vec3d drift = motion.multiply(-0.45);
				world.addParticle(TitanfallParticles.STIM_TRAIL, at.x, at.y, at.z, drift.x, drift.y, drift.z);
			}
		}
	}
}
