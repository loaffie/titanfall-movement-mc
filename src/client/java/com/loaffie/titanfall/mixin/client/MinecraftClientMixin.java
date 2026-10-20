package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.pulse.PulseSonar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entities a Pulse Blade has revealed get the glowing outline, seen through walls, for this client only. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
	private void titanfall$pulseReveal(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && PulseSonar.revealed(entity)) {
			cir.setReturnValue(true);
		}
	}
}
