package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.StimController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The injection's timeline, shared by the first- and third-person animations. Over {@link StimStates#INJECT_SECONDS}
 * the right hand reaches the holster on the hip (to 0.16) and draws the injector (0.18), brings it up in
 * front of the right side of the chest (0.2 - 0.4), stabs it in and holds while the plunger empties the
 * vial (0.42 - 0.6), pulls it out (to 0.68), carries it back down to the holster (to 0.84) and lets go of
 * it there (0.86), and the arm returns to rest.
 */
public final class StimAnimation {
	private static final float DRAW = 0.18F;
	private static final float HOLSTER = 0.86F;

	private StimAnimation() {
	}

	/** Injection progress 0..1 for the player, or 1 when no injection is under way. */
	public static float progress(PlayerEntity player, float tickDelta) {
		StimStates.State state = StimStates.of(player);
		return state == null ? 1.0F : state.inject(tickDelta);
	}

	/** Whether the injector is in the hand (otherwise it is in the holster). */
	public static boolean inHand(float t) {
		return t > DRAW && t < HOLSTER;
	}

	/** How far the arm is raised out of its rest pose, 0..1..0 across the animation. */
	public static float raise(float t) {
		if (t >= 1.0F) {
			return 0.0F;
		}

		return smooth(0.0F, 0.16F, t) * (1.0F - smooth(0.88F, 1.0F, t));
	}

	/** How far whatever the right hand holds is put away for the injection, 0..1..0 (quickly away, back at the end). */
	public static float handAway(float t) {
		if (t >= 1.0F) {
			return 0.0F;
		}

		return smooth(0.0F, 0.15F, t) * (1.0F - smooth(0.85F, 1.0F, t));
	}

	/** The arm's reach for the holster on the hip (drawing, and putting back) as opposed to the chest, 0..1. */
	public static float toHip(float t) {
		return 1.0F - smooth(0.2F, 0.4F, t) + smooth(0.68F, 0.84F, t);
	}

	/** The jab: how far the needle is driven home, 0..1..0. */
	public static float jab(float t) {
		return smooth(0.42F, 0.5F, t) * (1.0F - smooth(0.6F, 0.68F, t));
	}

	/**
	 * How full the vial is. During the injection it empties as the plunger goes down; after it, the local
	 * pilot's vial shows the stim's charge, and another pilot's refills over the stim's cooldown.
	 */
	public static float fill(PlayerEntity player, float tickDelta) {
		StimStates.State state = StimStates.of(player);
		if (player == MinecraftClient.getInstance().player) {
			if (state != null && state.inject(tickDelta) < 1.0F) {
				return 1.0F - smooth(0.5F, 0.6F, state.inject(tickDelta));
			}

			return (float) StimController.meter();
		}

		if (state == null) {
			return 1.0F;
		}

		float t = state.inject(tickDelta);
		float drained = 1.0F - smooth(0.5F, 0.6F, t);
		float refill = (state.age + tickDelta) / (float) (TitanfallConfig.get().stimCooldownSeconds * 20.0);
		return t < 1.0F ? drained : MathHelper.clamp(refill, 0.0F, 1.0F);
	}

	static float smooth(float from, float to, float t) {
		float x = MathHelper.clamp((t - from) / (to - from), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
