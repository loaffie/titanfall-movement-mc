package com.loaffie.titanfall.client.cloak;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The cloaked pilot's own view: a cool, faint shimmer round the edges of the screen, breathing slowly, that
 * flares as the cloak closes and as it falls away - and stutters in its last seconds, with the warning sound.
 */
public final class CloakHud {
	private static final Identifier VIGNETTE = Identifier.of(TitanfallMovement.MOD_ID, "textures/gui/stim_vignette.png");
	/** The stutter of the last seconds, ticks before the end. */
	private static final int STUTTER_TICKS = 60;

	private CloakHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden || !TitanfallConfig.get().cloakScreenEffects) {
			return;
		}

		CloakStates.State state = CloakStates.of(player);
		float tickDelta = tickCounter.getTickDelta(false);
		float amount = state == null ? 0.0F : state.amount(tickDelta);
		if (amount <= 0.0F) {
			return;
		}

		float time = player.age + tickDelta;
		float edge = 1.0F - Math.abs(amount * 2.0F - 1.0F);
		float alpha = amount * (0.22F + 0.06F * MathHelper.sin(time * 0.12F)) + 0.4F * edge;
		if (state.holding() && state.ticks - state.age < STUTTER_TICKS && MathHelper.sin(time * 1.9F) > 0.2F) {
			alpha *= 0.35F;
		}

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderColor(0.55F, 0.75F, 1.0F, MathHelper.clamp(alpha, 0.0F, 1.0F));
		context.drawTexture(VIGNETTE, 0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0.0F, 0.0F, 256, 256, 256, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}
