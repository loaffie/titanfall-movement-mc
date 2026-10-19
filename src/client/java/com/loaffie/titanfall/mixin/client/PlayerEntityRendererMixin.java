package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.client.anim.PilotPoses;
import com.loaffie.titanfall.client.grapple.GrapplePoses;
import com.loaffie.titanfall.client.stim.StimPoses;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whole-body pilot poses. {@code setupTransforms} runs once per frame per rendered player, after the
 * body-yaw rotation and before the model is set up, so this is where the per-frame animation blend is
 * advanced and the lean/recline is applied to the matrix. It also marks the player for the grapple and
 * stim arm poses, which only world renders get (first-person arms skip this method).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("TAIL"))
	private void titanfall$pilotBody(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw,
			float tickDelta, float scale, CallbackInfo ci) {
		GrapplePoses.prepare(player, tickDelta);
		StimPoses.prepare(player, tickDelta);
		PilotAnimations.State state = PilotAnimations.frame(player);
		if (state != null) {
			PilotPoses.applyBody(player, state, matrices, bodyYaw);
		}
	}
}
