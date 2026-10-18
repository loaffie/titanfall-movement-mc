package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.anim.PilotPoses;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Limb poses for pilots, layered on top of vanilla's animation at the end of {@code setAngles}. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
	/**
	 * Vanilla never resets the head's roll or the legs' sideways pivot, and one model instance is shared by
	 * every player the renderer draws. Resetting them first keeps a pilot's pose from sticking or leaking.
	 */
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void titanfall$resetHeadRoll(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
		model.head.roll = 0.0F;
		// The slide-hop coil moves the leg joints sideways; vanilla never resets pivotX (only Y and Z), so
		// restore the default hip positions before vanilla animates the shared model for the next player.
		model.rightLeg.pivotX = -1.9F;
		model.leftLeg.pivotX = 1.9F;
		// The wall run slides the wall-side shoulder along the wall normal; restore the arm joints too.
		model.rightArm.pivotX = -5.0F;
		model.rightArm.pivotZ = 0.0F;
		model.leftArm.pivotX = 5.0F;
		model.leftArm.pivotZ = 0.0F;
	}

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void titanfall$pilotLimbs(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		PilotPoses.applyLimbs(entity, (PlayerEntityModel<?>) (Object) this);
	}
}
