package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A phased pilot can't be targeted - mobs' target checks ask {@code canTakeDamage} - and doesn't touch what
 * they walk through: no picking up items or experience.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPhaseMixin {
	@Inject(method = "canTakeDamage", at = @At("HEAD"), cancellable = true)
	private void titanfall$phasedUntargetable(CallbackInfoReturnable<Boolean> cir) {
		if (PhaseShift.isPhased((PlayerEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "collideWithEntity", at = @At("HEAD"), cancellable = true)
	private void titanfall$phasedTouchesNothing(Entity entity, CallbackInfo ci) {
		if (PhaseShift.isPhased((PlayerEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
