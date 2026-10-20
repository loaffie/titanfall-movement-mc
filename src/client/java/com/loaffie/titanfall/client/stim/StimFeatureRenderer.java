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
 * in it - or, during an injection, in the right fist. Drawn, it comes out lying as it did in the holster
 * and turns as the hand rises until the needle aims into the right side of the chest (the pose moves the
 * fist; the needle goes in with it), and turns back the same way as it is put away.
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
		// Where the holstered needle points, for the injector's aim as it leaves and re-enters the holster.
		Vector3f holsteredTip = matrices.peek().getPositionMatrix().transformPosition(new Vector3f(0.0F, StimModel.NEEDLE_Y, 0.0F));
		if (!inHand) {
			StimModel.renderInjector(matrices, vertexConsumers, light, fill);
		}

		matrices.pop();

		if (inHand) {
			// The chest point in the same camera-relative space the matrices end in.
			Vector3f chest = modelRoot.transformPosition(new Vector3f(StimPoses.CHEST).div(16.0F));
			Vector3f target = holsteredTip.lerp(chest, 1.0F - StimAnimation.toHip(t));
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			matrices.push();
			model.rightArm.rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			StimModel.renderInFist(matrices, vertexConsumers, light, StimPoses.fistOnArm(slim), target, 0.0F, fill, StimModel.RING_GRIP_Y);
			matrices.pop();
		}
	}
}
