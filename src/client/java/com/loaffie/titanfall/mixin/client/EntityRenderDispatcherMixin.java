package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.phase.PhaseStates;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase Shift visibility: a phased pilot vanishes from the world - body, gear, name tag and shadow - and a
 * phased local pilot sees the world emptied of everything but other phased pilots.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void titanfall$phaseHidden(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (PhaseStates.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
