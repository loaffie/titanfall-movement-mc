package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.movement.GrappleController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/**
 * The Grapple's crosshair readout, after Titanfall 2's tactical ring: a short arc under the crosshair that
 * fills as the charge comes back (amber while refilling, cyan when ready), and two brackets either side of
 * the crosshair that light up when something the hook could bite is under it and in range.
 */
public final class GrappleHud {
	private static final int RADIUS = 11;
	private static final int DOTS = 15;
	/** The arc spans the bottom of the ring, from 205 to 335 degrees (screen y points down). */
	private static final float ARC_START = (float) Math.toRadians(25.0);
	private static final float ARC_END = (float) Math.toRadians(155.0);
	private static final int READY = 0xE05FD8FF;
	private static final int CHARGING = 0xD0FF9A2E;
	private static final int EMPTY = 0x60202830;
	private static final int BRACKET_ON = 0xF0BDF3FF;
	private static final int BRACKET_OFF = 0x50BDF3FF;

	private GrappleHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || !TitanfallConfig.get().grappleHud || !Grapple.isWorn(client.player)
				|| !client.options.getPerspective().isFirstPerson() || client.player.isSpectator()) {
			return;
		}

		int cx = context.getScaledWindowWidth() / 2;
		int cy = context.getScaledWindowHeight() / 2;
		double meter = GrappleController.meter();
		boolean ready = GrappleController.ready();
		int filled = (int) Math.floor(meter * DOTS + 1.0E-6);
		for (int i = 0; i < DOTS; i++) {
			float a = MathHelper.lerp(i / (float) (DOTS - 1), ARC_START, ARC_END);
			int x = cx + Math.round(MathHelper.cos(a) * RADIUS);
			int y = cy + Math.round(MathHelper.sin(a) * RADIUS);
			int color = i < filled ? (ready ? READY : CHARGING) : EMPTY;
			context.fill(x - 1, y, x + 1, y + 1, color);
		}

		int bracket = ready && GrappleController.targetInRange() ? BRACKET_ON : BRACKET_OFF;
		for (int s = -1; s <= 1; s += 2) {
			int x = cx + s * 7;
			context.fill(x, cy - 3, x + 1, cy + 4, bracket);
			context.fill(s < 0 ? x : x - 1, cy - 3, s < 0 ? x + 2 : x + 1, cy - 2, bracket);
			context.fill(s < 0 ? x : x - 1, cy + 3, s < 0 ? x + 2 : x + 1, cy + 4, bracket);
		}
	}
}
