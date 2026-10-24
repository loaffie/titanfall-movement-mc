package com.loaffie.titanfall.client.awall;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * The toss in third person ({@link AWallAnimation}): the off arm reaches up over its shoulder to the pack,
 * pulls the disc down across the chest, and flicks it out ahead. Applied at the end of {@code setAngles} for
 * the player being drawn in the world (the same gate as the other tactical poses). Hand positions in model
 * pixels, for a left arm (mirrored for the right): +X the pilot's left, +Y down, -Z the front.
 */
public final class AWallPoses {
	private static final Vector3f SHOULDER = new Vector3f(5.0F, 2.0F, 0.0F);
	/** Up behind the shoulder, at the pack's cradle. */
	private static final Vector3f AT_PACK = new Vector3f(3.0F, -6.0F, 5.0F);
	/** Wound up across the chest, the disc held flat at the far side. */
	private static final Vector3f WOUND = new Vector3f(-2.5F, 6.0F, -6.0F);
	/** Let go: the arm out straight ahead, a little to its own side and up. */
	private static final Vector3f FLUNG = new Vector3f(7.0F, 1.0F, -11.0F);

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private AWallPoses() {
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
		float t = AWallAnimation.progress(player, pendingTickDelta);
		float weight = AWallAnimation.weight(t);
		if (weight <= 0.0F) {
			return;
		}

		Arm arm = player.getMainArm().getOpposite();
		float side = arm == Arm.LEFT ? 1.0F : -1.0F;
		Vector3f hand;
		if (t < AWallAnimation.GRAB) {
			hand = new Vector3f(AT_PACK);
		} else if (t < AWallAnimation.WIND) {
			hand = new Vector3f(AT_PACK).lerp(WOUND, AWallAnimation.smooth(AWallAnimation.GRAB, AWallAnimation.WIND, t));
		} else {
			hand = new Vector3f(WOUND).lerp(FLUNG, AWallAnimation.smooth(AWallAnimation.WIND, AWallAnimation.RELEASE, t));
		}

		Vector3f d = new Vector3f(hand.x * side, hand.y, hand.z).sub(SHOULDER.x * side, SHOULDER.y, SHOULDER.z).normalize();
		// The arm hangs along +Y; pitch then yaw (ZYX order) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);

		// The body lags the head when the pilot turns: the throw follows the view, so the arm swings round by
		// the head's turn as it winds up and lets go.
		yaw += model.head.yaw * AWallAnimation.smooth(AWallAnimation.GRAB, AWallAnimation.RELEASE, t);

		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
		part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
		part.roll = MathHelper.lerp(weight, part.roll, 0.0F);
		(arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve).copyTransform(part);
	}
}
