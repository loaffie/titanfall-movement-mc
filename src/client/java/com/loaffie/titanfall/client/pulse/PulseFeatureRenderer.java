package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.pulse.PulseBlade;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Pulse Blade on every player wearing it: the sheath on the front of the left hip - holding the
 * kunai when one is charged, empty while the next charges - and, while they draw, aim and throw, the kunai
 * in the throwing fist: blade pointing back while it is pulled back, swinging round to the front as the arm
 * comes over to let it go.
 */
public class PulseFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** Where the kunai's tip points (model pixels): pulled back, up and well behind the pilot; thrown, well ahead. */
	private static final Vector3f BEHIND = new Vector3f(0.0F, -24.0F, 40.0F);
	private static final Vector3f AHEAD = new Vector3f(0.0F, -10.0F, -40.0F);

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
			Vector3f aim = new Vector3f(BEHIND).lerp(AHEAD, state.swing(tickDelta));
			Vector3f target = modelRoot.transformPosition(aim.div(16.0F));
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			Arm arm = PulseBlade.throwingArm(player);
			float centre = (arm == Arm.LEFT ? 1.0F : -1.0F) * (slim ? 0.5F : 1.0F);
			matrices.push();
			(arm == Arm.LEFT ? model.leftArm : model.rightArm).rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			PulseModel.renderInHand(matrices, vertexConsumers, light, new Vector3f(centre, 9.0F, -0.4F), target, true);
			matrices.pop();
		}
	}
}
