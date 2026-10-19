package com.loaffie.titanfall.client.stim;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Third-person injection. The right hand drops to the holster on the hip and draws the injector; then the
 * arm becomes a forearm: its joint - normally the shoulder - moves down and forward to where the elbow
 * would be, in front of the waist, so the arm stands up in front of the body with the fist before the
 * right side of the chest, and stabs the injector straight back into it. Applied at the end of
 * {@code setAngles} for the player being drawn in the world (the same gate as the grapple pose).
 *
 * <p>All positions are in model pixels: +X the pilot's left, +Y down, -Z the front. The body's front is at
 * z = -2 and the right side of the chest is around x = -2.4.
 */
public final class StimPoses {
	/** Where the needle goes in: the right side of the chest. */
	public static final Vector3f CHEST = new Vector3f(-2.4F, 4.6F, -2.0F);
	/** The fist before the stab, and how far the stab drives it in (the injector is about 6.6 px from fist to needle tip). */
	private static final Vector3f FIST = new Vector3f(-2.4F, 4.8F, -8.4F);
	private static final float STAB = 1.9F;
	/** The elbow, from the fist: below it, a little out and a little back, so the forearm leans in. */
	private static final Vector3f ELBOW_FROM_FIST = new Vector3f(-1.3F, 8.6F, 1.6F);
	/** The shoulder joint and the hand-at-the-hip pose of the first part. */
	private static final Vector3f SHOULDER = new Vector3f(-5.0F, 2.0F, 0.0F);
	private static final float HIP_PITCH = -0.3F;
	private static final float HIP_ROLL = 0.1F;

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private StimPoses() {
	}

	public static void prepare(PlayerEntity player, float tickDelta) {
		pending = player;
		pendingTickDelta = tickDelta;
	}

	/** The fist's position for a given stab depth 0..1 (model pixels). */
	public static Vector3f fist(float jab) {
		return new Vector3f(FIST).add(0.0F, 0.0F, STAB * jab);
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

		// The forearm pose: joint at the elbow, arm aimed at the fist.
		Vector3f fist = fist(StimAnimation.jab(t));
		Vector3f elbow = new Vector3f(fist).add(ELBOW_FROM_FIST);
		Vector3f d = new Vector3f(fist).sub(elbow).normalize();
		// The arm hangs along +Y; pitch then yaw (ZYX order, no roll) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);

		// Blend from the hand at the hip (shoulder joint) to the forearm (elbow joint) as the injector comes up.
		float hip = StimAnimation.toHip(t);
		Vector3f joint = new Vector3f(elbow).lerp(SHOULDER, hip);
		float targetPitch = MathHelper.lerp(hip, pitch, HIP_PITCH);
		float targetYaw = MathHelper.lerp(hip, yaw, 0.0F);
		float targetRoll = MathHelper.lerp(hip, 0.0F, HIP_ROLL);

		model.rightArm.pivotX = MathHelper.lerp(weight, model.rightArm.pivotX, joint.x);
		model.rightArm.pivotY = MathHelper.lerp(weight, model.rightArm.pivotY, joint.y);
		model.rightArm.pivotZ = MathHelper.lerp(weight, model.rightArm.pivotZ, joint.z);
		model.rightArm.pitch = MathHelper.lerp(weight, model.rightArm.pitch, targetPitch);
		model.rightArm.yaw = MathHelper.lerp(weight, model.rightArm.yaw, targetYaw);
		model.rightArm.roll = MathHelper.lerp(weight, model.rightArm.roll, targetRoll);
		model.rightSleeve.copyTransform(model.rightArm);
	}
}
