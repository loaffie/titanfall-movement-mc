package com.loaffie.titanfall.client.awall;

import com.loaffie.titanfall.awall.AWall;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.movement.AWallController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the A-Wall pack on the upper back of every player in view who wears it - out over a chestplate if they
 * wear one - and, through a toss, the disc in their off hand. The local pilot's pack shows their charge; for
 * everyone else the cradle is full except while their disc is out.
 */
public class AWallFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** The disc in the hand: its centre this far down the arm from the shoulder joint, pixels (the hand is at 10), and its size. */
	private static final float GRIP = 12.0F;
	private static final float IN_HAND_SCALE = 0.85F;

	public AWallFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!AWall.isWorn(player) || player.isInvisible()) {
			return;
		}

		float t = AWallAnimation.progress(player, tickDelta);
		float charge;
		boolean disc;
		if (player == MinecraftClient.getInstance().player) {
			charge = (float) AWallController.meter();
			disc = AWallController.ready() && !AWallAnimation.cradleEmptied(t);
		} else {
			disc = !AWallAnimation.cradleEmptied(t) && !AWallStates.hasWallOut(player);
			charge = disc ? 1.0F : 0.0F;
		}

		boolean overArmor = !Jumpkit.isWorn(player) && player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
		matrices.push();
		this.getContextModel().body.rotate(matrices);
		AWallModel.onBack(matrices, overArmor);
		AWallModel.renderPack(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, charge, disc);
		matrices.pop();

		if (AWallAnimation.inHand(t)) {
			Arm arm = player.getMainArm().getOpposite();
			ModelPart part = arm == Arm.LEFT ? this.getContextModel().leftArm : this.getContextModel().rightArm;
			matrices.push();
			part.rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			matrices.translate(arm == Arm.LEFT ? 1.0F : -1.0F, GRIP, 0.0F);
			// Held flat, like a frisbee: the disc's axis across the arm, up when the arm points ahead.
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
			matrices.scale(IN_HAND_SCALE, IN_HAND_SCALE, IN_HAND_SCALE);
			matrices.translate(0.0F, -0.7F, 0.0F);
			AWallModel.renderDisc(matrices, vertexConsumers, light, 0.0F, AWallModel.AMBER);
			matrices.pop();
		}
	}
}
