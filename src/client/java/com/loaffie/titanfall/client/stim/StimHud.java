package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

/**
 * The Stim's screen effects, after Titanfall 2's cockpit heal effect: the edges of the screen glow blue and
 * pulse like a racing heartbeat for as long as the stim runs, and speed lines stream out from the middle of
 * the view - more of them, and faster, the faster the pilot moves (the game's effect reads the pilot's
 * speed the same way, 0 to 360 u/s).
 */
public final class StimHud {
	private static final Identifier VIGNETTE = Identifier.of(TitanfallMovement.MOD_ID, "textures/gui/stim_vignette.png");
	/** Heartbeat of the edge pulse, beats per second. */
	private static final float PULSE_RATE = 2.2F;
	private static final int LINES = 56;
	/** Speed (blocks/s) at which the speed lines are at their thickest: Titanfall's 360 u/s. */
	private static final float FULL_SPEED = 9.14F;

	/** One speed line: its angle round the centre, how far out it is (0..1 of the way to the corner) and its pace. */
	private static final float[] ANGLE = new float[LINES];
	private static final float[] RADIUS = new float[LINES];
	private static final float[] PACE = new float[LINES];
	private static final Random RANDOM = Random.create();
	private static long lastNanos;
	private static float pulsePhase;

	static {
		for (int i = 0; i < LINES; i++) {
			respawn(i, RANDOM.nextFloat());
		}
	}

	private StimHud() {
	}

	private static void respawn(int i, float radius) {
		ANGLE[i] = RANDOM.nextFloat() * MathHelper.TAU;
		RADIUS[i] = radius;
		PACE[i] = 0.6F + RANDOM.nextFloat() * 0.8F;
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		long now = Util.getMeasuringTimeNano();
		float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
		lastNanos = now;
		if (player == null || client.options.hudHidden || !TitanfallConfig.get().stimScreenEffects
				|| !client.options.getPerspective().isFirstPerson()) {
			return;
		}

		StimStates.State state = StimStates.of(player);
		float tickDelta = tickCounter.getTickDelta(false);
		float intensity = state == null ? 0.0F : state.intensity(tickDelta);
		if (intensity < 0.01F) {
			return;
		}

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		pulsePhase = (pulsePhase + dt * PULSE_RATE) % 1.0F;
		// A heartbeat: a sharp swell and a slower fall each beat, never fully dark while the stim runs.
		float beat = (float) Math.pow(1.0 - pulsePhase, 3.0);
		float alpha = intensity * (0.4F + 0.35F * beat);

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderColor(0.35F, 0.75F, 1.0F, alpha);
		context.drawTexture(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		speedLines(context, player, width, height, dt, intensity, tickDelta);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	private static void speedLines(DrawContext context, ClientPlayerEntity player, int width, int height, float dt, float intensity, float tickDelta) {
		double dx = player.getX() - player.prevX;
		double dz = player.getZ() - player.prevZ;
		float speed = (float) Math.sqrt(dx * dx + dz * dz) * 20.0F;
		float amount = MathHelper.clamp(speed / FULL_SPEED, 0.0F, 1.0F) * intensity;
		int shown = Math.round(LINES * (0.08F + 0.92F * amount));
		float reach = (float) Math.sqrt(width * width + height * height) / 2.0F;
		float cx = width / 2.0F;
		float cy = height / 2.0F;
		for (int i = 0; i < LINES; i++) {
			RADIUS[i] += dt * PACE[i] * (0.4F + 1.6F * amount);
			if (RADIUS[i] > 1.0F) {
				respawn(i, 0.35F);
			}

			if (i >= shown) {
				continue;
			}

			float r = RADIUS[i];
			// Lines are born a third of the way out (the middle of the view stays clear) and fade at both ends.
			float fade = MathHelper.clamp((r - 0.35F) / 0.15F, 0.0F, 1.0F) * MathHelper.clamp((1.0F - r) / 0.25F, 0.0F, 1.0F);
			int a = Math.round(255 * fade * (0.25F + 0.45F * amount));
			if (a <= 2) {
				continue;
			}

			int length = Math.max(2, Math.round(reach * (0.05F + 0.12F * amount)));
			context.getMatrices().push();
			context.getMatrices().translate(cx, cy, 0.0F);
			context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(ANGLE[i]));
			int start = Math.round(r * reach);
			context.fill(start, 0, start + length, 1, (a << 24) | 0xA8E8FF);
			context.getMatrices().pop();
		}
	}
}
