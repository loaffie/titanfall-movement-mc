package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.awall.AWallAmped;
import com.loaffie.titanfall.awall.AWallServer;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every projectile, at the start of its tick (each kind calls this first, before it moves): an A-Wall in the
 * way stops it or amps it ({@link AWallServer#beforeMove}), and an amped one trails sparks.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityAWallMixin implements AWallAmped {
	@Unique
	private boolean titanfall$amped;

	@Override
	public boolean titanfall$amped() {
		return titanfall$amped;
	}

	@Override
	public void titanfall$amp() {
		titanfall$amped = true;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void titanfall$aWall(CallbackInfo ci) {
		ProjectileEntity self = (ProjectileEntity) (Object) this;
		if (self.getWorld().isClient()) {
			return;
		}

		if (titanfall$amped) {
			AWallServer.ampedTrail(self);
		}

		AWallServer.beforeMove(self);
	}
}
