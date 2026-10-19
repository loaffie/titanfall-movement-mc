package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The local pilot's view while phased, after Titanfall 2's phase colour correction and screen effects
 * ({@code mp_ability_shifter.raw}, {@code P_phase_shift_screen}): the world drained to a dark, cold black
 * and white with a blue-black vignette ({@code shaders/post/phase_shift.json}), torn by a ripple and a
 * flash on the way out and back in. The hands are drawn after it, in colour, so the gauntlet's blue burns
 * on top of the grey. The HUD is pared down to health while phased (see {@code InGameHudMixin}).
 */
public final class PhaseScreen {
	private static final Identifier POST = Identifier.of(TitanfallMovement.MOD_ID, "shaders/post/phase_shift.json");
	/** The tear's ripple lasts this long at each end of the phase, ticks. */
	private static final float WARP_TICKS = 5.0F;

	private static PostEffectProcessor processor;
	private static boolean failed;
	private static int width;
	private static int height;

	private PhaseScreen() {
	}

	private static PhaseStates.State localState() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		return player == null || !TitanfallConfig.get().phaseScreenEffects ? null : PhaseStates.of(player);
	}

	/** True while the local pilot is phased and the phase view is on: the HUD keeps only health. */
	public static boolean limitedHud() {
		PhaseStates.State state = localState();
		return state != null && state.phased();
	}

	/**
	 * Called just before the hands are drawn: runs the phase view over the world in the main framebuffer and
	 * hands it back for the hands.
	 */
	public static void renderWorldEffect(float tickDelta, float frameDuration) {
		PhaseStates.State state = localState();
		float depth = state == null ? 0.0F : state.depth(tickDelta);
		if (depth <= 0.0F || failed) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Framebuffer main = client.getFramebuffer();
		if (processor == null) {
			try {
				processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), main, POST);
			} catch (Exception exception) {
				failed = true;
				TitanfallMovement.LOGGER.error("Could not load the Phase Shift screen effect", exception);
				return;
			}

			width = -1;
		}

		if (main.textureWidth != width || main.textureHeight != height) {
			width = main.textureWidth;
			height = main.textureHeight;
			processor.setupDimensions(width, height);
		}

		processor.setUniforms("Intensity", depth);
		processor.setUniforms("Warp", warp(state, tickDelta));
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		processor.render(frameDuration);
		main.beginWrite(true);
		RenderSystem.enableDepthTest();
	}

	/** The tear: sharp as the pilot shifts out or back, gone a few ticks later. */
	private static float warp(PhaseStates.State state, float tickDelta) {
		float since = state.phased() ? state.age + tickDelta : state.cooling + tickDelta;
		return MathHelper.clamp(1.0F - since / WARP_TICKS, 0.0F, 1.0F);
	}

	/** HUD layer: the white-blue flash of the shift, on the way out and on the way back. */
	public static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		PhaseStates.State state = localState();
		if (state == null || MinecraftClient.getInstance().options.hudHidden) {
			return;
		}

		float flash = warp(state, tickCounter.getTickDelta(false));
		flash *= flash;
		if (flash <= 0.01F) {
			return;
		}

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		int alpha = Math.round(170 * flash);
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), (alpha << 24) | 0x9CD4FF);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/** On resource reload the effect is dropped, to be rebuilt from the new resources when next needed. */
	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "phase_screen");
			}

			@Override
			public void reload(ResourceManager manager) {
				if (processor != null) {
					processor.close();
					processor = null;
				}

				failed = false;
			}
		};
	}
}
