package com.loaffie.titanfall.client.awall;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The toss's timeline, shared by the first- and third-person animations: the off hand reaches back over the
 * shoulder to the pack and pulls the disc from its cradle (to {@link #GRAB}), swings it across the chest (to
 * {@link #WIND}), flicks it out forward like a frisbee and lets go ({@link #RELEASE}), follows through, and
 * comes back to rest. The local pilot's disc leaves the hand at the release; another pilot's toss is seen
 * from the moment their disc appears, already let go.
 */
public final class AWallAnimation {
	/** The whole toss, seconds. */
	public static final float SECONDS = 0.6F;
	/** Phase ends, 0..1 of the toss. */
	public static final float GRAB = 0.2F;
	public static final float WIND = 0.33F;
	public static final float RELEASE = 0.42F;
	public static final float FOLLOW = 0.55F;
	/** Ticks from the key press to the disc leaving the hand. */
	public static final int RELEASE_TICKS = Math.round(RELEASE * SECONDS * 20.0F);

	private static final Map<PlayerEntity, Float> STARTED = new WeakHashMap<>();

	private AWallAnimation() {
	}

	/** The pilot started a toss now (their key press). */
	public static void start(PlayerEntity player) {
		STARTED.put(player, (float) player.getWorld().getTime());
	}

	/** Another pilot's disc has just appeared: their toss, from the release. */
	public static void released(PlayerEntity player) {
		Float started = STARTED.get(player);
		float now = player.getWorld().getTime();
		if (started == null || now - started > SECONDS * 20.0F) {
			STARTED.put(player, now - RELEASE_TICKS);
		}
	}

	/** Toss progress 0..1, or 1 when no toss is under way. */
	public static float progress(PlayerEntity player, float tickDelta) {
		Float started = STARTED.get(player);
		if (started == null) {
			return 1.0F;
		}

		float t = (player.getWorld().getTime() - started + tickDelta) / (SECONDS * 20.0F);
		if (t >= 1.0F || t < 0.0F) {
			STARTED.remove(player);
			return 1.0F;
		}

		return t;
	}

	/** The disc is in the hand: pulled from the cradle and not yet let go. */
	public static boolean inHand(float t) {
		return t > GRAB * 0.7F && t < RELEASE;
	}

	/** The cradle is empty: from the grab until the pack has a disc again (decided by the caller after the toss). */
	public static boolean cradleEmptied(float t) {
		return t < 1.0F && t > GRAB * 0.7F;
	}

	/** How far the arm is out of its rest pose, 0..1..0. */
	public static float weight(float t) {
		return t >= 1.0F ? 0.0F : smooth(0.0F, 0.12F, t) * (1.0F - smooth(FOLLOW, 1.0F, t));
	}

	public static float smooth(float from, float to, float t) {
		float x = MathHelper.clamp((t - from) / (to - from), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
