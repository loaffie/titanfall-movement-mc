package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.grapple.Grapple;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Grapple in first person. As in Titanfall 2, the launcher arm comes up into view when the hook is
 * fired, stays raised toward the hook while the line pulls, and drops away once the hook is home. It is
 * drawn like vanilla's empty hand (the same arm transform), on the arm wearing the Grapple, with the
 * gauntlet on it; the muzzle's position is kept so the line in the world starts at the launcher on screen.
 */
public final class GrappleFirstPerson {
	/** How fast the arm rises into view and drops away, 1/s. */
	private static final float RAISE_RATE = 16.0F;
	private static final float LOWER_RATE = 7.0F;
	/** Most the arm turns toward the hook beyond its resting aim, degrees. */
	private static final float MAX_AIM = 28.0F;

	private static float raise;
	private static float aimPitch;
	private static float aimYaw;
	private static long lastNanos;
	/** The muzzle as last drawn by the hand pass (camera-relative, world-aligned), or null. */
	private static Vector3f muzzleView;
	private static float handTanHalfFov = (float) Math.tan(Math.toRadians(35.0));

	private GrappleFirstPerson() {
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		MinecraftClient client = MinecraftClient.getInstance();
		Arm arm = Grapple.wornArm(player);
		GrappleLine line = arm == null ? null : GrappleLines.active(player);
		advance(player, line, tickDelta);
		if (arm == null || raise < 0.01F || player.isInvisible() || player.isUsingSpyglass()) {
			muzzleView = null;
			return;
		}

		handTanHalfFov = (float) Math.tan(Math.toRadians(client.options.getFov().getValue()) / 2.0);
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) client.getEntityRenderDispatcher().getRenderer(player);
		float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
		float equip = 1.0F - raise;

		matrices.push();
		// Turn toward the hook about a point near the shoulder.
		matrices.translate(side * 0.4F, -0.4F, -0.2F);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(aimPitch * raise));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(aimYaw * raise));
		matrices.translate(-side * 0.4F, 0.4F, 0.2F);
		// Vanilla's empty-hand transform (HeldItemRenderer#renderArmHoldingItem with no swing), raised a little
		// higher and further out so the launcher on top of the forearm is in view.
		matrices.translate(side * 0.64F, -0.52F + equip * -0.7F, -0.72F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
		matrices.translate(side * -1.0F, 3.6F, 3.5F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 120.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200.0F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -135.0F));
		matrices.translate(side * 5.6F, 0.0F, 0.0F);
		if (arm == Arm.RIGHT) {
			renderer.renderRightArm(matrices, consumers, light, player);
		} else {
			renderer.renderLeftArm(matrices, consumers, light, player);
		}

		PlayerEntityModel<?> model = renderer.getModel();
		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		part.rotate(matrices);
		GrappleModel.onArm(matrices, arm, player.getSkinTextures().model() == SkinTextures.Model.SLIM);
		GrappleModel.renderGauntlet(matrices, consumers, light, OverlayTexture.DEFAULT_UV, line == null,
				GrappleFeatureRenderer.statusLight(player, line));
		muzzleView = matrices.peek().getPositionMatrix().transformPosition(new Vector3f(GrappleModel.MUZZLE));
		matrices.pop();
	}

	/**
	 * True while the raised Grapple arm is the player's main arm (a Grapple in Trinkets' main-hand glove slot).
	 * Vanilla would draw its own empty main hand there as well, so it skips it.
	 */
	public static boolean replacesMainArm(ClientPlayerEntity player) {
		return raise >= 0.01F && Grapple.wornArm(player) == player.getMainArm();
	}

	/** Eases the raise and the aim toward their targets, frame-rate independently. */
	private static void advance(ClientPlayerEntity player, GrappleLine line, float tickDelta) {
		long now = Util.getMeasuringTimeNano();
		float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
		lastNanos = now;
		boolean up = line != null && line.phase != GrappleLine.Phase.RETRACTING;
		float rate = up ? RAISE_RATE : LOWER_RATE;
		raise += ((up ? 1.0F : 0.0F) - raise) * (1.0F - (float) Math.exp(-rate * dt));

		float targetPitch = 0.0F;
		float targetYaw = 0.0F;
		if (line != null && line.phase == GrappleLine.Phase.ATTACHED) {
			// The hook's direction relative to the view: raise / swing the arm part of the way toward it.
			Vec3d toHook = line.position(player.getWorld(), tickDelta).subtract(player.getCameraPosVec(tickDelta)).normalize();
			float yaw = (float) Math.toDegrees(Math.atan2(-toHook.x, toHook.z));
			float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(toHook.y, -1.0, 1.0)));
			targetYaw = MathHelper.clamp(-MathHelper.wrapDegrees(yaw - player.getYaw(tickDelta)), -MAX_AIM, MAX_AIM);
			targetPitch = MathHelper.clamp(-(pitch - player.getPitch(tickDelta)), -MAX_AIM, MAX_AIM);
		}

		float follow = 1.0F - (float) Math.exp(-10.0F * dt);
		aimPitch += (targetPitch - aimPitch) * follow;
		aimYaw += (targetYaw - aimYaw) * follow;
	}

	/**
	 * The first-person muzzle in the world. The hand pass starts its matrix stack with the inverse of the
	 * view rotation (and puts the rotation back in the model-view matrix), so the recorded point is
	 * camera-relative but world-aligned. It is turned into view space, rescaled across the view - the hand
	 * is drawn with a fixed field of view and the world with the dynamic one (sprint, slide) - so it lands
	 * on the same spot of the screen, and turned back.
	 */
	static Vec3d muzzleInWorld(Camera camera, float worldTanHalfFov) {
		if (muzzleView == null) {
			return null;
		}

		Quaternionf toWorld = camera.getRotation();
		Vector3f view = new Quaternionf(toWorld).conjugate().transform(new Vector3f(muzzleView));
		float k = worldTanHalfFov / handTanHalfFov;
		view.set(view.x * k, view.y * k, view.z);
		Vector3f world = toWorld.transform(view);
		return camera.getPos().add(world.x, world.y, world.z);
	}
}
