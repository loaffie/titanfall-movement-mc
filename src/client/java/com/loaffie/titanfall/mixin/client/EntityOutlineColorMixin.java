package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.pulse.PulseSonar;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Pulse Blade's reveal outlines in Titanfall's enemy red (the outline takes the entity's team colour). */
@Mixin(Entity.class)
public abstract class EntityOutlineColorMixin {
	private static final int REVEAL_RED = 0xFF3A2E;

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void titanfall$pulseRevealColor(CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		if (entity.getWorld().isClient() && PulseSonar.revealed(entity)) {
			cir.setReturnValue(REVEAL_RED);
		}
	}
}
