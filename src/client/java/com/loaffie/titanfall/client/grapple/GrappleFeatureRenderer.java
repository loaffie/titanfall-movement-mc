package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.movement.GrappleController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Draws the Grapple on the forearm that wears it, on every player in view (and the local player in third
 * person). It rides the arm part, so it follows every arm pose - including the grapple's own aim pose -
 * and records where its muzzle is this frame, so the line leaves exactly from the launcher.
 */
public class GrappleFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** Muzzles further than this from the player are not from the world render (the inventory doll). */
	private static final double MUZZLE_SANITY_DISTANCE = 4.0;

	public GrappleFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		Arm arm = Grapple.wornArm(player);
		if (arm == null || player.isInvisible()) {
			return;
		}

		PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		GrappleLine line = GrappleLines.active(player);

		matrices.push();
		part.rotate(matrices);
		boolean mirrored = GrappleModel.onArm(matrices, arm, slim);
		GrappleModel.renderGauntlet(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, line == null, statusLight(player, line), mirrored);
		recordMuzzle(player, line, matrices.peek().getPositionMatrix().transformPosition(new Vector3f(GrappleModel.MUZZLE)));
		matrices.pop();
	}

	/**
	 * Status light colour: hot while the line is out; for the local pilot, cyan when a charge is ready and
	 * amber while it refills; other pilots' meters are not known, so theirs shows ready.
	 */
	static int statusLight(AbstractClientPlayerEntity player, GrappleLine line) {
		if (line != null && line.phase != GrappleLine.Phase.RETRACTING) {
			return GrappleModel.LIGHT_ACTIVE;
		}

		if (player == MinecraftClient.getInstance().player && !GrappleController.ready()) {
			float pulse = 0.55F + 0.25F * (float) Math.sin(player.age * 0.35F);
			return GrappleModel.dim(GrappleModel.LIGHT_CHARGING, pulse);
		}

		return GrappleModel.LIGHT_READY;
	}

	/** Entity rendering is camera-relative: the camera position turns the muzzle into world space. */
	private static void recordMuzzle(AbstractClientPlayerEntity player, GrappleLine line, Vector3f cameraRelative) {
		if (line == null) {
			return;
		}

		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		Vec3d muzzle = camera.add(cameraRelative.x, cameraRelative.y, cameraRelative.z);
		if (muzzle.squaredDistanceTo(player.getPos()) > MUZZLE_SANITY_DISTANCE * MUZZLE_SANITY_DISTANCE) {
			return;
		}

		line.tip = muzzle;
		line.tipFrame = GrappleRenderer.frame();
	}
}
