package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.slide.SlideState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 0.8-block slide hitbox, on client and server alike.
 *
 * <ul>
 *   <li>{@code getBaseDimensions(SLIDING)} returns {@link SlideState#DIMENSIONS}. Vanilla players never
 *   use that pose, so nothing else is affected.</li>
 *   <li>{@code updatePose} picks {@code SLIDING} while the slide flag is set. It runs every tick on the
 *   local client and on the server ({@code OtherClientPlayerEntity} skips it and shows the synced
 *   pose). If even 0.8 blocks does not fit, it falls back to vanilla's 0.6-block crawl, so a slide can
 *   never put the head inside a block.</li>
 *   <li>When the flag clears under a low ceiling, vanilla's own logic takes over: it only stands or
 *   crouches the player if that fits, and otherwise keeps them crawling.</li>
 * </ul>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySlidePoseMixin implements SlideState {
	@Unique
	private boolean titanfall$sliding;

	@Shadow
	protected abstract boolean canChangeIntoPose(EntityPose pose);

	@Override
	public boolean titanfall$isSliding() {
		return titanfall$sliding;
	}

	@Override
	public void titanfall$setSliding(boolean sliding) {
		titanfall$sliding = sliding;
	}

	@Inject(method = "getBaseDimensions(Lnet/minecraft/entity/EntityPose;)Lnet/minecraft/entity/EntityDimensions;", at = @At("HEAD"), cancellable = true)
	private void titanfall$slideDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (pose == EntityPose.SLIDING) {
			cir.setReturnValue(SlideState.DIMENSIONS);
		}
	}

	@Inject(method = "updatePose()V", at = @At("HEAD"), cancellable = true)
	private void titanfall$slidePose(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!titanfall$sliding || !Jumpkit.abilitiesActive(self) || self.getAbilities().flying || self.isFallFlying()
				|| self.isSwimming() || self.isSleeping() || self.hasVehicle() || self.isSpectator()) {
			return;
		}

		if (this.canChangeIntoPose(EntityPose.SLIDING)) {
			self.setPose(EntityPose.SLIDING);
			ci.cancel();
		} else if (this.canChangeIntoPose(EntityPose.SWIMMING)) {
			self.setPose(EntityPose.SWIMMING);
			ci.cancel();
		}
	}
}
