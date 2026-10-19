package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.movement.physics.HVec;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Third-person grapple pose: the launcher arm points straight at the hook - thrown out as it fires, held
 * on the line while it pulls - and eases back down once the hook is home.
 *
 * <p>Applied at the end of the player model's {@code setAngles} for the player whose transforms were just
 * set up for a world render (see {@code PlayerEntityRendererMixin}), so first-person arms, which run the
 * same {@code setAngles}, are never touched.
 */
public final class GrapplePoses {
	private static final float RATE = 14.0F;
	/** Shoulder joint height above the feet, blocks: 22 px of the 15/16-scaled model. */
	private static final double SHOULDER_HEIGHT = 22.0 / 16.0 * 0.9375;
	private static final double SHOULDER_SIDE = 5.0 / 16.0 * 0.9375;

	/** Smoothed aim weight per player, and when it was last advanced. */
	private static final class Aim {
		float weight;
		long lastNanos;
		/** Last aim at the hook, kept so the arm can ease down after the hook is home. */
		float pitch;
		float yaw;
	}

	private static final Map<PlayerEntity, Aim> AIMS = new WeakHashMap<>();
	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private GrapplePoses() {
	}

	/** From the renderer's transform setup: this player's model is about to be posed for the world. */
	public static void prepare(PlayerEntity player, float tickDelta) {
		pending = player;
		pendingTickDelta = tickDelta;
	}

	public static void apply(LivingEntity entity, PlayerEntityModel<?> model) {
		if (entity != pending || !(entity instanceof PlayerEntity player)) {
			return;
		}

		pending = null;
		Arm arm = Grapple.wornArm(player);
		GrappleLine line = arm == null ? null : GrappleLines.active(player);
		Aim aim = AIMS.computeIfAbsent(player, ignored -> new Aim());
		long now = Util.getMeasuringTimeNano();
		float dt = aim.lastNanos == 0L ? 0.0F : Math.min((now - aim.lastNanos) / 1.0E9F, 0.1F);
		aim.lastNanos = now;
		boolean up = line != null && line.phase != GrappleLine.Phase.RETRACTING;
		aim.weight += ((up ? 1.0F : 0.0F) - aim.weight) * (1.0F - (float) Math.exp(-RATE * dt));
		float weight = aim.weight;
		if (arm == null || weight < 0.01F) {
			return;
		}

		Vec3d hook = line != null ? line.position(player.getWorld(), pendingTickDelta) : null;
		if (hook != null) {
			aimAt(player, arm, hook, aim);
		}

		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.pitch = lerpAngle(weight, part.pitch, aim.pitch);
		part.yaw = lerpAngle(weight, part.yaw, aim.yaw);
		part.roll = lerpAngle(weight, part.roll, 0.0F);
		(arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve).copyTransform(part);
	}

	/** Arm angles that point the arm from its shoulder at {@code hook}. */
	private static void aimAt(PlayerEntity player, Arm arm, Vec3d hook, Aim aim) {
		float bodyYaw = MathHelper.lerp(pendingTickDelta, player.prevBodyYaw, player.bodyYaw);
		HVec right = HVec.rightFromYaw(bodyYaw);
		HVec forward = HVec.forwardFromYaw(bodyYaw);
		double side = arm == Arm.LEFT ? -1.0 : 1.0;
		Vec3d shoulder = player.getLerpedPos(pendingTickDelta).add(right.x() * SHOULDER_SIDE * side, SHOULDER_HEIGHT, right.z() * SHOULDER_SIDE * side);
		Vec3d d = hook.subtract(shoulder);
		if (d.lengthSquared() < 1.0E-4) {
			return;
		}

		d = d.normalize();
		// Into model space: +X the player's left, +Y down, -Z forward.
		double mx = -(d.x * right.x() + d.z * right.z());
		double my = -d.y;
		double mz = -(d.x * forward.x() + d.z * forward.z());
		// The arm hangs along +Y; pitch then yaw (ZYX order, no roll) turns it to (sin p sin y, cos p, sin p cos y).
		aim.pitch = (float) -Math.acos(MathHelper.clamp(my, -1.0, 1.0));
		float sinPitch = MathHelper.sin(aim.pitch);
		aim.yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(mx / sinPitch, mz / sinPitch);
	}

	private static float lerpAngle(float t, float from, float to) {
		return from + MathHelper.wrapDegrees((to - from) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE * t;
	}
}
