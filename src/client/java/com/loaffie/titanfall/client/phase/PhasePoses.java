package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Third-person activation: the gauntlet arm swings up in front of the face, the back of the hand up, and
 * drops again. Applied at the end of {@code setAngles} for the player being drawn in the world (the same
 * gate as the grapple and stim poses). Positions in model pixels: +X the pilot's left, +Y down, -Z the front.
 */
public final class PhasePoses {
	/** Where the hand goes, for a left arm (mirrored for the right): up in front of the face, in toward the middle. */
	private static final Vector3f HAND = new Vector3f(1.6F, -2.0F, -9.0F);
	private static final Vector3f SHOULDER = new Vector3f(5.0F, 2.0F, 0.0F);
	/** The flick: the forearm rolls a little as the fingers snap. */
	private static final float FLICK_ROLL = 0.35F;

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private PhasePoses() {
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
		Arm arm = PhaseShift.wornArm(player);
		float t = PhaseAnimation.progress(player, pendingTickDelta);
		float weight = arm == null ? 0.0F : PhaseAnimation.raise(t);
		if (weight < 0.01F) {
			return;
		}

		float side = arm == Arm.LEFT ? 1.0F : -1.0F;
		Vector3f d = new Vector3f(HAND.x * side, HAND.y, HAND.z).sub(SHOULDER.x * side, SHOULDER.y, SHOULDER.z).normalize();
		// The arm hangs along +Y; pitch then yaw (ZYX order) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);
		float roll = -side * FLICK_ROLL * PhaseAnimation.flick(t);

		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
		part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
		part.roll = MathHelper.lerp(weight, part.roll, roll);
		(arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve).copyTransform(part);
	}
}
