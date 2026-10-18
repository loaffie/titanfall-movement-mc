package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.Jumpkit;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pilots do not take fall damage. Fall damage is decided on the server from the distance the server saw
 * the player fall, so a client-side reset cannot prevent it: chained wall runs and double jumps would
 * otherwise accumulate fall distance and kill the pilot on the first landing. This mixin lives in the
 * common mixin config, so it applies on dedicated servers, integrated servers and the client alike.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityFallDamageMixin {
	@Inject(method = "handleFallDamage(FFLnet/minecraft/entity/damage/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
	private void titanfall$jumpkitAbsorbsFalls(float fallDistance, float damageMultiplier, DamageSource damageSource,
			CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (TitanfallConfig.get().jumpkitNegatesFallDamage && Jumpkit.abilitiesActive(self)) {
			cir.setReturnValue(false);
		}
	}
}
