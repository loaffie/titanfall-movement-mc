package com.loaffie.titanfall.client.pulse;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Third-person Pulse Blade throw, left-handed: the left arm comes up and cocks back over the shoulder with
 * the kunai, holds there while the pilot aims, then whips forward and down across the body as it lets go,
 * and drops back. Applied at the end of {@code setAngles} for the player being drawn in the world (the same
 * gate as the other ability poses). Directions in model space: +X the pilot's left, +Y down, -Z the front.
 */
public final class PulsePoses {
	/** The arm cocked back: up past the head, a little out and back. */
	private static final Vector3f COCKED = new Vector3f(0.28F, -0.86F, 0.42F).normalize();
	/** Followed through: forward, a little down and across the body. */
	private static final Vector3f FOLLOWED = new Vector3f(-0.3F, 0.25F, -0.92F).normalize();

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private PulsePoses() {
	}

	public static void prepare(PlayerEntity player, float tickDelta) {
		pending = player;
		pendingTickDelta = tickDelta;
	}

	public static void apply(LivingEntity entity, PlayerEntityModel<?> model) {
		if (entity != pending || !(entity instanceof PlayerEntity player)) {
			return;
		}

		pending = null;
		PulseStates.State state = PulseStates.of(player);
		float weight = state == null ? 0.0F : state.raise(pendingTickDelta);
		if (weight < 0.01F) {
			return;
		}

		Vector3f d = new Vector3f(COCKED).lerp(FOLLOWED, state.swing(pendingTickDelta)).normalize();
		// The arm hangs along +Y; pitch then yaw (ZYX order) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);
		model.leftArm.pitch = MathHelper.lerp(weight, model.leftArm.pitch, pitch);
		model.leftArm.yaw = MathHelper.lerp(weight, model.leftArm.yaw, yaw);
		model.leftArm.roll = MathHelper.lerp(weight, model.leftArm.roll, 0.0F);
		model.leftSleeve.copyTransform(model.leftArm);
	}
}
