package com.loaffie.titanfall.client.stim;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Third-person injection. The right hand reaches down to the holster on the front of the hip - the fist
 * closing round the injector's grip rings exactly where they sit - and draws it; then the arm becomes a
 * forearm: its joint - normally the shoulder - moves down to where the elbow would be, tucked
 * in at the side just above the waist, so the forearm angles up and forward with the fist before the
 * right side of the chest, and stabs the injector straight back into it. Applied at the end of
 * {@code setAngles} for the player being drawn in the world (the same gate as the grapple pose).
 *
 * <p>All positions are in model pixels: +X the pilot's left, +Y down, -Z the front. The body's front is at
 * z = -2 and the right side of the chest is around x = -2.4.
 */
public final class StimPoses {
	/** Where the needle goes in: the right side of the chest. */
	public static final Vector3f CHEST = new Vector3f(-2.4F, 4.6F, -2.0F);
	/**
	 * The fist before the stab, and how far the stab drives it in: held by its grip rings, the injector's
	 * needle tip is 4.1 px from the fist, so at full depth it goes 2 px in.
	 */
	private static final Vector3f FIST = new Vector3f(-2.4F, 4.8F, -5.9F);
	private static final float STAB = 1.9F;
	/**
	 * The elbow, from the fist: tucked in at the side of the body just above the waist (y 9 of the 12 px torso),
	 * so the forearm angles up and forward to the fist in front of the chest. 8.9 px: the fist's distance from
	 * the joint along the arm.
	 */
	private static final Vector3f ELBOW_FROM_FIST = new Vector3f(-2.2F, 4.4F, 7.4F);
	/** The shoulder joint. */
	private static final Vector3f SHOULDER = new Vector3f(-5.0F, 2.0F, 0.0F);
	/** The fist in the right arm's own space (pixels): the middle of the hand, a little toward the front. */
	private static final Vector3f FIST_ON_ARM = new Vector3f(-1.0F, 9.0F, -0.4F);
	private static final Vector3f FIST_ON_SLIM_ARM = new Vector3f(-0.5F, 9.0F, -0.4F);
	/**
	 * Reaching for the holster: the arm points from the shoulder at the injector's grip rings, and its joint
	 * settles (by a pixel or so) so the fist lands on them exactly.
	 */
	private static final Vector3f HOLSTER_GRIP = StimModel.onHipPoint(new Vector3f(0.0F, StimModel.RING_GRIP_Y, 0.0F));
	private static final float HIP_PITCH;
	private static final float HIP_YAW;
	private static final Vector3f HIP_JOINT;

	static {
		Vector3f d = new Vector3f(HOLSTER_GRIP).sub(SHOULDER).normalize();
		HIP_PITCH = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F));
		float sinPitch = MathHelper.sin(HIP_PITCH);
		HIP_YAW = (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);
		HIP_JOINT = new Vector3f(HOLSTER_GRIP).sub(new Quaternionf().rotationZYX(0.0F, HIP_YAW, HIP_PITCH).transform(new Vector3f(FIST_ON_ARM)));
	}

	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private StimPoses() {
	}

	public static void prepare(PlayerEntity player, float tickDelta) {
		pending = player;
		pendingTickDelta = tickDelta;
	}

	/** Where the fist holds the injector, in the right arm's own space (pixels). */
	public static Vector3f fistOnArm(boolean slim) {
		return new Vector3f(slim ? FIST_ON_SLIM_ARM : FIST_ON_ARM);
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

		// Blend from the hand on the holster (shoulder joint) to the forearm (elbow joint) as the injector comes up.
		float hip = StimAnimation.toHip(t);
		Vector3f joint = new Vector3f(elbow).lerp(HIP_JOINT, hip);
		float targetPitch = MathHelper.lerp(hip, pitch, HIP_PITCH);
		float targetYaw = MathHelper.lerp(hip, yaw, HIP_YAW);
		float targetRoll = 0.0F;

		model.rightArm.pivotX = MathHelper.lerp(weight, model.rightArm.pivotX, joint.x);
		model.rightArm.pivotY = MathHelper.lerp(weight, model.rightArm.pivotY, joint.y);
		model.rightArm.pivotZ = MathHelper.lerp(weight, model.rightArm.pivotZ, joint.z);
		model.rightArm.pitch = MathHelper.lerp(weight, model.rightArm.pitch, targetPitch);
		model.rightArm.yaw = MathHelper.lerp(weight, model.rightArm.yaw, targetYaw);
		model.rightArm.roll = MathHelper.lerp(weight, model.rightArm.roll, targetRoll);
		model.rightSleeve.copyTransform(model.rightArm);
	}
}
