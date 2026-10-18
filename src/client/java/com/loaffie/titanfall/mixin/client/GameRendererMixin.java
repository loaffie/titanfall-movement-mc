package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.camera.PilotCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * View roll and slide FOV.
 *
 * <p>{@code renderWorld} builds its projection as {@code projection * viewBob}, where the view-bob stack
 * first receives {@code tiltViewWhenHurt}. A rotation about Z added there rolls the image in view space,
 * exactly like vanilla's hurt tilt, for both the world and the held item (which uses the same call).
 * A positive angle turns the scene counter-clockwise on screen, which reads as the camera banking right.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	// HEAD, not TAIL: tiltViewWhenHurt returns early whenever the player is not hurt.
	@Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
	private void titanfall$pilotRoll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		float roll = PilotCamera.roll();
		if (Math.abs(roll) > 1.0E-3F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
		}
	}

	/** Widens the world FOV while sliding; respects the FOV Effects accessibility slider like sprinting does. */
	@Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
	private void titanfall$slideFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		float boost = PilotCamera.fovBoost();
		if (changingFov && boost > 1.0E-4F) {
			double scale = MinecraftClient.getInstance().options.getFovEffectScale().getValue();
			cir.setReturnValue(cir.getReturnValueD() * (1.0 + boost * scale));
		}
	}
}
