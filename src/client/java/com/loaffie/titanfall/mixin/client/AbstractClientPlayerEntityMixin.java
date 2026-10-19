package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.stim.Stim;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla widens the FOV with the movement-speed attribute, so the Stim's +40% speed modifier would swing
 * the view out by a fifth - far more than Titanfall's subtle widening. Its share is taken back out here;
 * the camera adds the Stim's own, gentler FOV boost instead.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getFovMultiplier()F", at = @At("RETURN"), cancellable = true)
	private void titanfall$withoutStimSpeed(CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		EntityAttributeModifier stim = speed == null ? null : speed.getModifier(Stim.SPEED_MODIFIER);
		float walk = player.getAbilities().getWalkSpeed();
		if (stim == null || walk <= 0.0F) {
			return;
		}

		// The modifier multiplies the total, so the speed without it is the value divided back down.
		double with = speed.getValue() / walk;
		double without = with / (1.0 + stim.value());
		cir.setReturnValue((float) (cir.getReturnValueF() * (without + 1.0) / (with + 1.0)));
	}
}
