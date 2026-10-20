package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.holo.HoloPilot;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.movement.HoloController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;

/**
 * Draws the Holo Pilot projector on the chest of every player in view who wears it - out over a chestplate
 * if they wear one. The local pilot's lights show their charges; everyone else's show both held.
 */
public class HoloFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public HoloFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!HoloPilot.isWorn(player) || player.isInvisible()) {
			return;
		}

		boolean overArmor = !Jumpkit.isWorn(player) && player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
		int charges = 2;
		float partial = 0.0F;
		int lens = HoloModel.READY;
		if (player == MinecraftClient.getInstance().player) {
			double meter = HoloController.meter();
			charges = (int) Math.floor(meter + 1.0E-6);
			partial = (float) (meter - charges);
			if (!HoloController.ready()) {
				lens = GrappleModel.dim(HoloModel.CHARGING, 0.55F + 0.25F * (float) Math.sin(player.age * 0.35F));
			}
		}

		matrices.push();
		this.getContextModel().body.rotate(matrices);
		HoloModel.onChest(matrices, overArmor);
		HoloModel.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, lens, charges, partial);
		matrices.pop();
	}
}
