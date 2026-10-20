package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.pulse.PulseBlade;
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
 * Draws the Pulse Blade on every player wearing it: the sheath on the front of the left hip - holding the
 * kunai when one is charged, empty while the next charges - and, while they draw, aim and throw, the kunai
 * in the left fist, held overhand with its tip forward.
 */
public class PulseFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** Where the kunai's tip points from the raised fist (model pixels): well ahead and above the pilot. */
	private static final Vector3f AIM_AHEAD = new Vector3f(3.0F, -14.0F, -40.0F);

	public PulseFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!PulseBlade.isWorn(player) || player.isInvisible()) {
			return;
		}

		PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
		Matrix4f modelRoot = new Matrix4f(matrices.peek().getPositionMatrix());
		matrices.push();
		model.body.rotate(matrices);
		PulseModel.onHip(matrices);
		PulseModel.renderSheathed(matrices, vertexConsumers, light, PulseStates.sheathed(player));
		matrices.pop();

		PulseStates.State state = PulseStates.of(player);
		if (state != null && state.inHand()) {
			Vector3f target = modelRoot.transformPosition(new Vector3f(AIM_AHEAD).div(16.0F));
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			matrices.push();
			model.leftArm.rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			PulseModel.renderInHand(matrices, vertexConsumers, light, new Vector3f(slim ? 0.5F : 1.0F, 9.0F, -0.4F), target, true);
			matrices.pop();
		}
	}
}
