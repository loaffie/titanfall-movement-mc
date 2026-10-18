package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.movement.TitanfallController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code setVelocityClient} is what the client's entity-velocity packet handler calls. For the local
 * player that packet replaces the velocity outright (melee knockback, launch pads), so the controller
 * replaces its momentum instead of treating the change as an additive push.
 */
@Mixin(Entity.class)
public abstract class EntityServerVelocityMixin {
	@Inject(method = "setVelocityClient(DDD)V", at = @At("TAIL"))
	private void titanfall$serverSetVelocity(double x, double y, double z, CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity player) {
			TitanfallController.onServerVelocity(player);
		}
	}
}
