package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.stim.Stim;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Stim on every player wearing it: the holster on the front of the right hip, with the injector
 * in it - or, during an injection, in the right fist with the needle aimed into the right side of the
 * chest (the pose moves the fist; the needle goes in with it).
 */
public class StimFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public StimFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!Stim.isWorn(player) || player.isInvisible()) {
			return;
		}

		PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
		float t = StimAnimation.progress(player, tickDelta);
		float fill = StimAnimation.fill(player, tickDelta);
		boolean inHand = StimAnimation.inHand(t);
		Matrix4f modelRoot = new Matrix4f(matrices.peek().getPositionMatrix());

		matrices.push();
		model.body.rotate(matrices);
		StimModel.onHip(matrices);
		StimModel.renderHolster(matrices, vertexConsumers, light);
		if (!inHand) {
			StimModel.renderInjector(matrices, vertexConsumers, light, fill);
		}

		matrices.pop();

		if (inHand) {
			// The chest point in the same camera-relative space the matrices end in.
			Vector3f chest = modelRoot.transformPosition(new Vector3f(StimPoses.CHEST).div(16.0F));
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			matrices.push();
			model.rightArm.rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			Vector3f fist = new Vector3f(slim ? -0.5F : -1.0F, 9.0F, -0.4F);
			StimModel.renderInFist(matrices, vertexConsumers, light, fist, chest, 0.0F, fill);
			matrices.pop();
		}
	}
}
