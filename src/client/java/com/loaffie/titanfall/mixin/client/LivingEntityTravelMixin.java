package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.movement.TitanfallController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the pilot movement controller inside the local player's {@code travel}.
 *
 * <p>{@code LivingEntity.tickMovement} reads this tick's input, performs vanilla's ground jump and then
 * calls {@code travel}, which applies input acceleration, moves the player and finally applies gravity
 * and drag. Hooking the head of {@code travel} lets the controller set the velocity for this very tick
 * from this tick's keys and camera before the move happens (no tick of latency), and the tail hook sees
 * what the move collided with. The movement input is replaced with zero while the controller owns the
 * player, so vanilla's WASD acceleration never stacks on top of the modelled velocity. Gravity, collision,
 * stepping, fall distance and everything else in {@code travel} still run in vanilla's order.
 *
 * <p>{@code PlayerEntity.travel} overrides this method and calls {@code super.travel}, so these
 * injections run inside that call. Other entities (including other players, which are
 * {@code OtherClientPlayerEntity}) are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {
	@ModifyVariable(method = "travel(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), argsOnly = true)
	private Vec3d titanfall$preTravel(Vec3d movementInput) {
		if ((Object) this instanceof ClientPlayerEntity player) {
			return TitanfallController.preTravel(player, movementInput);
		}

		return movementInput;
	}

	@Inject(method = "travel(Lnet/minecraft/util/math/Vec3d;)V", at = @At("TAIL"))
	private void titanfall$postTravel(Vec3d movementInput, CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity player) {
			TitanfallController.postTravel(player);
		}
	}
}
