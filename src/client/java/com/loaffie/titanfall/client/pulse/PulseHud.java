package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.TitanfallMovement;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * The warning a pilot gets when someone else's Pulse Blade reveals them: an alarm, and the edges of the
 * screen flash red and fade, once per pulse that finds them.
 */
public final class PulseHud {
	private static final Identifier VIGNETTE = Identifier.of(TitanfallMovement.MOD_ID, "textures/gui/stim_vignette.png");
	private static final float FLASH_SECONDS = 1.0F;

	private static long revealedNanos;

	private PulseHud() {
	}

	static void revealed() {
		revealedNanos = Util.getMeasuringTimeNano();
		PulseSounds.detected();
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (revealedNanos == 0L || MinecraftClient.getInstance().options.hudHidden) {
			return;
		}

		float t = (Util.getMeasuringTimeNano() - revealedNanos) / 1.0E9F / FLASH_SECONDS;
		if (t >= 1.0F) {
			return;
		}

		float alpha = (1.0F - t) * (1.0F - t) * 0.85F;
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderColor(1.0F, 0.18F, 0.12F, alpha);
		context.drawTexture(VIGNETTE, 0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0.0F, 0.0F, 256, 256, 256, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}
