package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.cloak.Cloak;
import com.loaffie.titanfall.cloak.CloakServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cloaked pilot stays invisible through potion changes (vanilla recomputes the flag from the effects
 * whenever they change), and mobs barely notice them whatever armour they wear (vanilla's invisibility
 * notices armour: the cloak covers it).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCloakMixin {
	@Inject(method = "updatePotionVisibility", at = @At("TAIL"))
	private void titanfall$stayCloaked(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (Cloak.isCloaked(self)) {
			self.setInvisible(true);
		}
	}

	@Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
	private void titanfall$cloakedHardToSee(Entity entity, CallbackInfoReturnable<Double> cir) {
		if (Cloak.isCloaked((LivingEntity) (Object) this)) {
			cir.setReturnValue(Math.min(cir.getReturnValueD(), CloakServer.DETECTION));
		}
	}
}
