package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.camera.PilotCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Camera.update} is the first step of every rendered frame. Its tail advances the pilot camera's
 * per-frame smoothing and, in first person, lowers the eye by the slide drop. Vanilla has already eased
 * the eye height into the crouch at this point, so the two lerps stack into one smooth descent.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	public abstract Vec3d getPos();

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Shadow
	public abstract float getYaw();

	@Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void titanfall$pilotCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
			float tickDelta, CallbackInfo ci) {
		boolean ownView = focusedEntity == MinecraftClient.getInstance().player && !inverseView;
		PilotCamera.frame(this.getYaw(), ownView);
		float drop = PilotCamera.eyeDrop();
		if (!thirdPerson && drop > 1.0E-4F && focusedEntity == MinecraftClient.getInstance().player) {
			Vec3d pos = this.getPos();
			this.setPos(pos.x, pos.y - drop, pos.z);
		}
	}
}
