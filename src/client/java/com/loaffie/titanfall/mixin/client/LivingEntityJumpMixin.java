package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.movement.TitanfallController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets vanilla's ground jump for the local pilot. The controller keeps vanilla's vertical impulse
 * (jump boost, honey and the jump strength attribute all still apply) but strips the horizontal
 * sprint-jump kick and learns that a ground jump happened this tick, which is how a slide hop or a
 * bunny hop keeps its momentum. {@code PlayerEntity.jump} calls {@code super.jump}, so these run for it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
	@Inject(method = "jump()V", at = @At("HEAD"))
	private void titanfall$beforeJump(CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity player) {
			TitanfallController.beforeVanillaJump(player);
		}
	}

	@Inject(method = "jump()V", at = @At("TAIL"))
	private void titanfall$afterJump(CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity player) {
			TitanfallController.afterVanillaJump(player);
		}
	}
}
