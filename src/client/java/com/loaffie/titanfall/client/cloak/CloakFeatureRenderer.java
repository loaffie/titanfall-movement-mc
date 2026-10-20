package com.loaffie.titanfall.client.cloak;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.cloak.Cloak;
import com.loaffie.titanfall.movement.CloakController;
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

/**
 * Draws the Cloak bracer on the forearm that wears it, on every player in view. While a pilot is cloaked
 * nothing of them is drawn but the shimmer, the bracer included (see {@code LivingEntityRendererMixin}).
 */
public class CloakFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public CloakFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		Arm arm = Cloak.wornArm(player);
		if (arm == null || player.isInvisible()) {
			return;
		}

		ModelPart part = arm == Arm.LEFT ? this.getContextModel().leftArm : this.getContextModel().rightArm;
		matrices.push();
		part.rotate(matrices);
		GrappleModel.onArm(matrices, arm, player.getSkinTextures().model() == SkinTextures.Model.SLIM);
		CloakModel.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, lightColor(player));
		matrices.pop();
	}

	/** Pale cyan when ready; for the local pilot, amber pulsing while the charge comes back. */
	private static int lightColor(AbstractClientPlayerEntity player) {
		if (player == MinecraftClient.getInstance().player && !CloakController.ready()) {
			float pulse = 0.55F + 0.25F * (float) Math.sin(player.age * 0.35F);
			return GrappleModel.dim(CloakModel.CHARGING, pulse);
		}

		return CloakModel.READY;
	}
}
