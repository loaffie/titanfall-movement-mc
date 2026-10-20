package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.pulse.PulseStates;
import com.loaffie.titanfall.client.stim.StimAnimation;
import com.loaffie.titanfall.pulse.PulseBlade;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pilot's hand puts down what it holds while it has the Stim injector or the Pulse Blade in it. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void titanfall$handBusy(LivingEntity entity, ItemStack stack, ModelTransformationMode transformationMode, Arm arm,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}

		float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
		boolean injecting = arm == Arm.RIGHT && StimAnimation.raise(StimAnimation.progress(player, tickDelta)) > 0.3F;
		boolean throwing = arm == PulseBlade.throwingArm(player) && PulseStates.of(player) != null;
		if (injecting || throwing) {
			ci.cancel();
		}
	}
}
