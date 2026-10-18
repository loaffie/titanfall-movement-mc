package com.loaffie.titanfall.client.camera;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallController;
import com.loaffie.titanfall.movement.physics.HVec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Util;

/**
 * First-person camera dynamics for the pilot: roll toward the wall while wall running, a roll kick on
 * wall jumps, and a lower eye height, wider FOV and slight drift tilt while sliding.
 *
 * <h2>Timing</h2>
 * {@link #frame} runs once per rendered frame from the tail of {@code Camera.update}, which is the
 * first thing {@code GameRenderer.renderWorld} does, so every effect read later in the same frame (FOV,
 * view roll) uses values computed for that frame. Targets come straight from the controller's current
 * state ({@link TitanfallController#view}) and the camera's own interpolated yaw, so nothing waits for
 * a game tick.
 *
 * <h2>Smoothing</h2>
 * Each value is moved toward its target with a per-frame linear interpolation
 * {@code value = lerp(value, target, alpha)} where {@code alpha = 1 - exp(-rate * frameSeconds)}.
 * That is a lerp whose weight is derived from the real frame time, so the easing curve is identical at
 * 30, 144 or 360 fps: no stutter from frame-rate-dependent lerp factors and no hard snaps.
 */
public final class PilotCamera {
	/** Above this, a frame is a hitch or a pause; smoothing just jumps the rest of the way. */
	private static final float MAX_FRAME_SECONDS = 0.1F;
	/** The wall-jump roll kick springs back at this rate, 1/s. */
	private static final float KICK_DECAY = 5.0F;

	private static long lastFrameNanos;
	private static float roll;
	private static float kick;
	private static float eyeDrop;
	private static float fovBoost;
	private static int seenWallJumpSerial;
	private static int seenSlideHopSerial;
	private static float hopFov;

	private PilotCamera() {
	}

	/** Called once per frame with the camera's interpolated yaw. */
	public static void frame(float cameraYaw, boolean ownView) {
		long now = Util.getMeasuringTimeNano();
		float dt = lastFrameNanos == 0L ? 0.0F : Math.min((now - lastFrameNanos) / 1.0E9F, MAX_FRAME_SECONDS);
		lastFrameNanos = now;

		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		TitanfallConfig config = TitanfallConfig.get();
		float targetRoll = 0.0F;
		float targetDrop = 0.0F;
		float targetFov = 0.0F;

		if (player != null) {
			// Keep the local player's one-shot animations (wall jump, hop) on schedule even in first person.
			PilotAnimations.frame(player);
		}

		if (player != null && ownView) {
			TitanfallController.View view = TitanfallController.view(player);
			HVec right = HVec.rightFromYaw(cameraYaw);

			if (view.mode() == TitanfallController.Mode.WALL) {
				// The wall normal points from the wall to the pilot, so a wall on the right has a normal
				// pointing left: -dot(normal, right) is +1 for a wall dead on the right, -1 on the left, and
				// falls to 0 as the pilot turns to face the wall or put it behind them.
				// The view banks outward, away from the wall (head tilted off the surface, like Titanfall's
				// wall-run camera); a negative config value banks toward the wall instead.
				float side = (float) -view.wallNormal().dot(right);
				targetRoll = -(float) config.wallRunCameraRoll * side;
			} else if (view.mode() == TitanfallController.Mode.SLIDE) {
				targetDrop = (float) config.slideCameraDrop;
				targetFov = (float) config.slideFovBoost;
				HVec direction = view.momentum().normalize();
				targetRoll = (float) (config.slideCameraTilt * direction.dot(right));
			}

			if (view.wallJumpSerial() != seenWallJumpSerial) {
				seenWallJumpSerial = view.wallJumpSerial();
				// Kick away from the wall that was just left.
				kick += (float) (config.wallJumpCameraKick * view.lastWallJumpNormal().dot(right));
			}

			if (view.slideHopSerial() != seenSlideHopSerial) {
				seenSlideHopSerial = view.slideHopSerial();
				hopFov = (float) config.slideFovBoost;
			}
		}

		float alpha = smoothing((float) config.cameraSmoothing, dt);
		roll = lerp(alpha, roll, targetRoll);
		eyeDrop = lerp(alpha, eyeDrop, targetDrop);
		// A slide hop keeps the slide's FOV for a moment and lets it ease out through the air.
		fovBoost = lerp(alpha, fovBoost, Math.max(targetFov, hopFov));
		float decay = (float) Math.exp(-KICK_DECAY * dt);
		kick *= decay;
		hopFov *= decay;
	}

	/** View roll in degrees, positive banks the view to the right. Applied to the projection each frame. */
	public static float roll() {
		return roll + kick;
	}

	/** How far below the vanilla eye position to place the first-person camera, blocks. */
	public static float eyeDrop() {
		return eyeDrop;
	}

	/** Fractional FOV widening, before the player's FOV Effects scale is applied. */
	public static float fovBoost() {
		return fovBoost;
	}

	private static float smoothing(float rate, float dt) {
		return dt <= 0.0F ? 0.0F : 1.0F - (float) Math.exp(-rate * dt);
	}

	private static float lerp(float alpha, float from, float to) {
		return from + (to - from) * alpha;
	}
}
