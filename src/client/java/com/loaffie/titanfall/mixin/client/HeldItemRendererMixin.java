package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.grapple.GrappleFirstPerson;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the first-person Grapple arm with the hands, before the hand buffers are flushed, and keeps vanilla
 * from drawing a second empty arm where the raised Grapple arm already is.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void titanfall$yieldMainArm(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (hand == Hand.MAIN_HAND && item.isEmpty() && player instanceof ClientPlayerEntity local && GrappleFirstPerson.replacesMainArm(local)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
	private void titanfall$grappleArm(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		GrappleFirstPerson.render(matrices, vertexConsumers, player, tickDelta, light);
	}
}
