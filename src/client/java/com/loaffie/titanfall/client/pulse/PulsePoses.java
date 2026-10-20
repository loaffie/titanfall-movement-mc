package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.pulse.PulseBlade;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Third-person Pulse Blade throw, overhand like a baseball with the main arm: it comes up from the sheath on
 * the left hip and pulls back, out to the side and up behind the head, the kunai's blade pointing back; holds
 * there while the pilot aims; then whips over the top and forward, letting go, and follows through down
 * across the body. Applied at the end of {@code setAngles} for the player being drawn in the world (the same
 * gate as the other ability poses). Directions in model space for a right arm, mirrored for a left: +X the
 * pilot's left, +Y down, -Z the front.
 */
public final class PulsePoses {
	/** Pulled back: out to the side, up, and behind the shoulder. */
	private static final Vector3f COCKED = new Vector3f(-0.55F, -0.62F, 0.56F);
	/** Over the top: straight up and a little forward, the arm passing beside the head. */
	private static final Vector3f OVER = new Vector3f(-0.2F, -0.95F, -0.22F);
	/** Followed through: forward, down and across the body. */
	private static final Vector3f FOLLOWED = new Vector3f(0.38F, 0.35F, -0.86F);

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

		Arm arm = PulseBlade.throwingArm(player);
		Vector3f d = direction(state.swing(pendingTickDelta));
		if (arm == Arm.LEFT) {
			d.x = -d.x;
		}

		// The arm hangs along +Y; pitch then yaw (ZYX order) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);
		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
		part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
		part.roll = MathHelper.lerp(weight, part.roll, 0.0F);
		(arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve).copyTransform(part);
	}

	/** The arm's direction (right arm) through the swing: pulled back, over the top, followed through. */
	private static Vector3f direction(float swing) {
		if (swing < 0.5F) {
			return new Vector3f(COCKED).lerp(OVER, swing * 2.0F).normalize();
		}

		return new Vector3f(OVER).lerp(FOLLOWED, (swing - 0.5F) * 2.0F).normalize();
	}
}
