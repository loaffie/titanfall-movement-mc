package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.awall.AWallServer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A shot that went out through an A-Wall hits harder ({@code pass_through_amps_weapon}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAWallMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float titanfall$amped(float amount, DamageSource source) {
		return amount * AWallServer.amp(source.getSource());
	}
}
