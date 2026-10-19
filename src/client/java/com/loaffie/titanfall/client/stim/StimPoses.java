package com.loaffie.titanfall.client.stim;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Third-person injection: the right hand drops to the holster on the hip, swings up across the body and
 * jabs the injector into the chest, then puts it back. Applied at the end of {@code setAngles} for the
 * player being drawn in the world (the same gate as the grapple pose), after the other pilot poses.
 */
public final class StimPoses {
	/** Hand at the front of the right hip. */
	private static final float HIP_PITCH = -0.3F;
	private static final float HIP_ROLL = 0.1F;
	/** Hand across in front of the chest: raised and swung in. */
	private static final float CHEST_PITCH = -1.3F;
	private static final float CHEST_YAW = -0.6F;
	private static final float CHEST_ROLL = -0.1F;

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private StimPoses() {
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
		float t = StimAnimation.progress(player, pendingTickDelta);
		float weight = StimAnimation.raise(t);
		if (weight < 0.01F) {
			return;
		}

		float hip = StimAnimation.toHip(t);
		// The jab pushes the fist a touch further in.
		float jab = StimAnimation.jab(t) * 0.12F;
		float pitch = MathHelper.lerp(hip, CHEST_PITCH - jab, HIP_PITCH);
		float yaw = MathHelper.lerp(hip, CHEST_YAW, 0.0F);
		float roll = MathHelper.lerp(hip, CHEST_ROLL, HIP_ROLL);
		model.rightArm.pitch = MathHelper.lerp(weight, model.rightArm.pitch, pitch);
		model.rightArm.yaw = MathHelper.lerp(weight, model.rightArm.yaw, yaw);
		model.rightArm.roll = MathHelper.lerp(weight, model.rightArm.roll, roll);
		model.rightSleeve.copyTransform(model.rightArm);
	}
}
