package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.cloak.CloakStates;
import com.loaffie.titanfall.client.grapple.GrappleFirstPerson;
import com.loaffie.titanfall.client.phase.PhaseFirstPerson;
import com.loaffie.titanfall.client.pulse.PulseFirstPerson;
import com.loaffie.titanfall.client.stim.StimFirstPerson;
import com.loaffie.titanfall.pulse.PulseBlade;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person pilot gear. Draws the Grapple arm, the Stim injection, the Phase Shift hand and the Pulse Blade
 * throw with the hands,
 * before the hand buffers are flushed; keeps vanilla from drawing a second arm where the raised Grapple arm
 * or a Phase Shift or Pulse Blade hand already is; and lowers vanilla's right-hand item out of the way while the Stim is
 * injected or the Pulse Blade thrown.
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
		if ((hand == Hand.MAIN_HAND && item.isEmpty() && GrappleFirstPerson.replacesMainArm(local)) || PhaseFirstPerson.replacesArm(local, arm)
				|| (item.isEmpty() && PulseFirstPerson.replacesArm(local, arm))) {
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
		PulseFirstPerson.render(matrices, vertexConsumers, player, tickDelta, light);
	}

	/** Vanilla's main-hand draw: lowered out of view while the hand is busy injecting a Stim or throwing a Pulse Blade. */
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
		float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
		float lowered = equipProgress;
		if (arm == Arm.RIGHT) {
			lowered = Math.max(lowered, StimFirstPerson.lowerRightHand(player, tickDelta));
		}

		if (arm == PulseBlade.throwingArm(player)) {
			lowered = Math.max(lowered, PulseFirstPerson.lowerMainHand(player, tickDelta));
		}

		return lowered;
	}

	/** Vanilla draws no empty hand for an invisible player; a cloaked pilot sees theirs, shimmering. */
	@Redirect(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isInvisible()Z"))
	private boolean titanfall$cloakedHandShows(AbstractClientPlayerEntity player) {
		return player.isInvisible() && CloakStates.amount(player, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)) <= 0.0F;
	}
}
