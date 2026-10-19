package com.loaffie.titanfall.client.phase;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The activation's timeline, shared by the first- and third-person poses (seconds since the shift): the
 * off hand snaps up with the back of the hand to the eyes, the fingers flick and the coil fires, the hand
 * holds a moment while the coil burns, then drops away.
 */
public final class PhaseAnimation {
	private static final float UP_END = 0.14F;
	private static final float FLICK_START = 0.1F;
	private static final float FLICK_END = 0.3F;
	private static final float DOWN_START = 0.6F;

	private PhaseAnimation() {
	}

	/** Seconds since the player's shift, or a large number when they are not shifting. */
	public static float progress(PlayerEntity player, float tickDelta) {
		PhaseStates.State state = PhaseStates.of(player);
		return state == null ? Float.MAX_VALUE : state.seconds(tickDelta);
	}

	/** How far up the hand is, 0..1. */
	public static float raise(float t) {
		if (t >= PhaseStates.ACTIVATE_SECONDS) {
			return 0.0F;
		}

		return smooth(t / UP_END) * (1.0F - smooth((t - DOWN_START) / (PhaseStates.ACTIVATE_SECONDS - DOWN_START)));
	}

	/** The flick that fires the coil: 0 -> 1 -> 0. */
	public static float flick(float t) {
		float k = MathHelper.clamp((t - FLICK_START) / (FLICK_END - FLICK_START), 0.0F, 1.0F);
		return MathHelper.sin(k * MathHelper.PI);
	}

	/** The coil lights as the fingers flick. */
	public static float ignition(float t) {
		return smooth((t - FLICK_START) / 0.06F);
	}

	private static float smooth(float x) {
		float k = MathHelper.clamp(x, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}
}
