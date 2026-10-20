package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.phase.PhaseScreen;
import com.loaffie.titanfall.movement.PulseController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pared-down HUD while phased: out of phase there is nothing to aim at or use, so the crosshair, the
 * hotbar, experience and status effects go; health (and the tactical readout) stay. The crosshair also goes
 * while a Pulse Blade is up: its arc and landing ring do the aiming.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void titanfall$phaseCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (PhaseScreen.limitedHud() || (player != null && PulseController.aiming(player))) {
			ci.cancel();
		}
	}

	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
	private void titanfall$phaseHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (PhaseScreen.limitedHud()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void titanfall$phaseExperienceBar(DrawContext context, int x, CallbackInfo ci) {
		if (PhaseScreen.limitedHud()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void titanfall$phaseExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (PhaseScreen.limitedHud()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void titanfall$phaseStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (PhaseScreen.limitedHud()) {
			ci.cancel();
		}
	}
}
