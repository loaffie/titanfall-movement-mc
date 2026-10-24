package com.loaffie.titanfall.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.loaffie.titanfall.awall.AWallServer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An A-Wall between a blast and someone shields them from it, damage and push alike
 * ({@code SetBlocksRadiusDamage}). Both scale with how exposed they are to the blast, but vanilla adds a point
 * of damage even with no exposure, so the damage is skipped outright.
 */
@Mixin(Explosion.class)
public abstract class ExplosionAWallMixin {
	@Inject(method = "getExposure", at = @At("HEAD"), cancellable = true)
	private static void titanfall$behindAWall(Vec3d source, Entity entity, CallbackInfoReturnable<Float> cir) {
		if (AWallServer.blocksBlast(source, entity)) {
			cir.setReturnValue(0.0F);
		}
	}

	@WrapOperation(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/explosion/ExplosionBehavior;shouldDamage(Lnet/minecraft/world/explosion/Explosion;Lnet/minecraft/entity/Entity;)Z"))
	private boolean titanfall$shielded(ExplosionBehavior behavior, Explosion explosion, Entity entity, Operation<Boolean> original) {
		return original.call(behavior, explosion, entity) && !AWallServer.blocksBlast(explosion.getPosition(), entity);
	}
}
