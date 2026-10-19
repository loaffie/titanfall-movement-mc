package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A phased pilot is out of the world's reach: nothing can hit them - no projectile, no melee swing, no
 * crosshair - and nothing pushes them, nor do they push anything they walk through.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPhaseMixin {
	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
	private void titanfall$phasedCantBeHit(CallbackInfoReturnable<Boolean> cir) {
		if (PhaseShift.isPhased((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void titanfall$phasedNotPushable(CallbackInfoReturnable<Boolean> cir) {
		if (PhaseShift.isPhased((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
	private void titanfall$phasedPushesNothing(CallbackInfo ci) {
		if (PhaseShift.isPhased((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
