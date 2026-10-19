package com.loaffie.titanfall.client;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.movement.GrappleController;
import com.loaffie.titanfall.movement.StimController;
import com.loaffie.titanfall.stim.Stim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/**
 * The tactical abilities' crosshair readout, after Titanfall 2's tactical ring: a short arc round the
 * crosshair per ability that fills as its charge comes back (amber while refilling, cyan when ready) - the
 * Grapple's under the crosshair, the Stim's under it too or, when both are worn, over it - and for the
 * Grapple two brackets either side of the crosshair that light up when something the hook could bite is
 * under it and in range.
 */
public final class TacticalHud {
	private static final int RADIUS = 11;
	private static final int DOTS = 25;
	/** Brackets' distance either side of the crosshair's centre pixel. */
	private static final int BRACKET_GAP = 7;
	/** An arc spans the bottom (or top) of the ring, 25 to 155 degrees from the right (screen y points down). */
	private static final float ARC_START = (float) Math.toRadians(25.0);
	private static final float ARC_END = (float) Math.toRadians(155.0);
	private static final int READY = 0xE05FD8FF;
	private static final int CHARGING = 0xD0FF9A2E;
	private static final int EMPTY = 0x60202830;
	private static final int BRACKET_ON = 0xF0BDF3FF;
	private static final int BRACKET_OFF = 0x50BDF3FF;

	private TacticalHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || !TitanfallConfig.get().grappleHud
				|| !client.options.getPerspective().isFirstPerson() || client.player.isSpectator()) {
			return;
		}

		boolean grapple = Grapple.isWorn(client.player);
		boolean stim = Stim.isWorn(client.player);
		if (!grapple && !stim) {
			return;
		}

		// The centre pixel of vanilla's 15 x 15 crosshair, which is drawn at ((w - 15) / 2, (h - 15) / 2). Everything
		// below is drawn in single pixels mirrored about it, so the readout sits exactly round the crosshair.
		int cx = (context.getScaledWindowWidth() - 15) / 2 + 7;
		int cy = (context.getScaledWindowHeight() - 15) / 2 + 7;
		if (grapple) {
			arc(context, cx, cy, GrappleController.meter(), GrappleController.ready(), 1);
			int bracket = GrappleController.ready() && GrappleController.targetInRange() ? BRACKET_ON : BRACKET_OFF;
			for (int s = -1; s <= 1; s += 2) {
				int x = cx + s * BRACKET_GAP;
				context.fill(x, cy - 3, x + 1, cy + 4, bracket);
				// Serifs point inward, toward the crosshair.
				context.fill(x - s, cy - 3, x - s + 1, cy - 2, bracket);
				context.fill(x - s, cy + 3, x - s + 1, cy + 4, bracket);
			}
		}

		if (stim) {
			arc(context, cx, cy, StimController.meter(), StimController.ready(), grapple ? -1 : 1);
		}
	}

	/** One charge arc: under the crosshair ({@code side} 1) or over it (-1), filled to {@code meter}. */
	private static void arc(DrawContext context, int cx, int cy, double meter, boolean ready, int side) {
		int filled = (int) Math.floor(meter * DOTS + 1.0E-6);
		for (int i = 0; i < DOTS; i++) {
			float a = MathHelper.lerp(i / (float) (DOTS - 1), ARC_START, ARC_END);
			int x = cx + Math.round(MathHelper.cos(a) * RADIUS);
			int y = cy + side * Math.round(MathHelper.sin(a) * RADIUS);
			int color = i < filled ? (ready ? READY : CHARGING) : EMPTY;
			context.fill(x, y, x + 1, y + 1, color);
		}
	}
}
