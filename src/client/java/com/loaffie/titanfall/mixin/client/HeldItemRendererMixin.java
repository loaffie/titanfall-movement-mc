package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.grapple.GrappleFirstPerson;
import com.loaffie.titanfall.client.phase.PhaseFirstPerson;
import com.loaffie.titanfall.client.stim.StimFirstPerson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person pilot gear. Draws the Grapple arm, the Stim injection and the Phase Shift hand with the hands,
 * before the hand buffers are flushed; keeps vanilla from drawing a second arm where the raised Grapple arm
 * or Phase Shift hand already is; and lowers vanilla's right-hand item out of the way while the Stim is
 * injected.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	private static final String RENDER_FIRST_PERSON_ITEM = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

	@Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void titanfall$yieldMainArm(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!(player instanceof ClientPlayerEntity local)) {
			return;
		}

		Arm arm = hand == Hand.MAIN_HAND ? local.getMainArm() : local.getMainArm().getOpposite();
		if ((hand == Hand.MAIN_HAND && item.isEmpty() && GrappleFirstPerson.replacesMainArm(local)) || PhaseFirstPerson.replacesArm(local, arm)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
	private void titanfall$grappleArm(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		GrappleFirstPerson.render(matrices, vertexConsumers, player, tickDelta, light);
		StimFirstPerson.render(matrices, vertexConsumers, player, tickDelta, light);
		PhaseFirstPerson.render(matrices, vertexConsumers, player, tickDelta, light);
	}

	/** Vanilla's main-hand draw: lowered out of view while the right hand is busy injecting a Stim. */
	@ModifyArg(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = RENDER_FIRST_PERSON_ITEM, ordinal = 0), index = 6)
	private float titanfall$lowerMainHand(float equipProgress) {
		return lowered(equipProgress, MinecraftClient.getInstance().player, true);
	}

	/** Vanilla's off-hand draw, likewise. */
	@ModifyArg(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = RENDER_FIRST_PERSON_ITEM, ordinal = 1), index = 6)
	private float titanfall$lowerOffHand(float equipProgress) {
		return lowered(equipProgress, MinecraftClient.getInstance().player, false);
	}

	private static float lowered(float equipProgress, ClientPlayerEntity player, boolean mainHand) {
		if (player == null) {
			return equipProgress;
		}

		Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		return arm == Arm.RIGHT ? Math.max(equipProgress, StimFirstPerson.lowerRightHand(player, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false))) : equipProgress;
	}
}
