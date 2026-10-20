package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.holo.HoloPilot;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Third-person activation: the off hand comes across to the projector on the chest, taps it, and drops
 * again. Applied at the end of {@code setAngles} for the player being drawn in the world (the same gate as
 * the other tactical poses). Positions in model pixels: +X the pilot's left, +Y down, -Z the front.
 */
public final class HoloPoses {
	/** The tap's length, seconds: across in a tenth, a beat on the projector, and down. */
	private static final float SECONDS = 0.45F;
	private static final float RAISE = 0.1F;
	private static final float HOLD_UNTIL = 0.2F;
	/**
	 * Where the hand goes, for a left arm (mirrored for the right): just in front of the projector, the
	 * reach of a straight 12-pixel arm from the shoulder.
	 */
	private static final Vector3f HAND = new Vector3f(0.8F, 5.0F, -10.6F);
	private static final Vector3f SHOULDER = new Vector3f(5.0F, 2.0F, 0.0F);
	/** The tap itself: the arm pushes in this much further, radians of pitch, in the beat it holds. */
	private static final float PRESS = 0.12F;

	private static final Map<PlayerEntity, Long> TAPPED = new WeakHashMap<>();
	private static PlayerEntity pending;
	private static float pendingTickDelta;

	private HoloPoses() {
	}

	public static void tapped(PlayerEntity player) {
		TAPPED.put(player, player.getWorld().getTime());
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
		Long at = TAPPED.get(player);
		if (at == null || !HoloPilot.isWorn(player)) {
			return;
		}

		float t = (player.getWorld().getTime() - at + pendingTickDelta) / 20.0F / SECONDS;
		if (t < 0.0F || t >= 1.0F) {
			if (t >= 1.0F) {
				TAPPED.remove(player);
			}

			return;
		}

		float weight = t < RAISE / SECONDS ? smooth(t * SECONDS / RAISE)
				: t < HOLD_UNTIL / SECONDS ? 1.0F : 1.0F - smooth((t * SECONDS - HOLD_UNTIL) / (SECONDS - HOLD_UNTIL));
		float press = t * SECONDS > RAISE && t * SECONDS < HOLD_UNTIL ? MathHelper.sin((t * SECONDS - RAISE) / (HOLD_UNTIL - RAISE) * MathHelper.PI) : 0.0F;

		Arm arm = player.getMainArm().getOpposite();
		float side = arm == Arm.LEFT ? 1.0F : -1.0F;
		Vector3f d = new Vector3f(HAND.x * side, HAND.y, HAND.z).sub(SHOULDER.x * side, SHOULDER.y, SHOULDER.z).normalize();
		// The arm hangs along +Y; pitch then yaw (ZYX order) turn it to (sin p sin y, cos p, sin p cos y).
		float pitch = (float) -Math.acos(MathHelper.clamp(d.y, -1.0F, 1.0F)) - PRESS * press;
		float sinPitch = MathHelper.sin(pitch);
		float yaw = Math.abs(sinPitch) < 1.0E-4F ? 0.0F : (float) Math.atan2(d.x / sinPitch, d.z / sinPitch);

		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
		part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
		part.roll = MathHelper.lerp(weight, part.roll, 0.0F);
		(arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve).copyTransform(part);
	}

	private static float smooth(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
