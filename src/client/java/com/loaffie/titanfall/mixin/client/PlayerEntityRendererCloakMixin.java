package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.cloak.CloakShimmer;
import com.loaffie.titanfall.client.cloak.CloakStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cloaked pilot's own first-person hand: the cloak's look, faint enough to read as cloaked but there. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererCloakMixin {
	@Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
	private void titanfall$cloakedHand(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
			ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
		float amount = CloakStates.amount(player, tickDelta);
		if (amount <= 0.0F || !TitanfallConfig.get().cloakScreenEffects) {
			return;
		}

		PlayerEntityModel<AbstractClientPlayerEntity> model = ((PlayerEntityRenderer) (Object) this).getModel();
		model.handSwingProgress = 0.0F;
		model.sneaking = false;
		model.leaningPitch = 0.0F;
		model.setAngles(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		arm.pitch = 0.0F;
		sleeve.pitch = 0.0F;
		Identifier skin = player.getSkinTextures().texture();
		int color = CloakShimmer.bodyColor(1.0F - amount * (1.0F - CloakShimmer.OWN_HANDS), amount);
		arm.render(matrices, consumers.getBuffer(CloakShimmer.bodyLayer(skin)), light, OverlayTexture.DEFAULT_UV, color);
		if (sleeve.visible) {
			sleeve.render(matrices, consumers.getBuffer(CloakShimmer.bodyLayer(skin)), light, OverlayTexture.DEFAULT_UV, color);
		}

		ci.cancel();
	}
}
