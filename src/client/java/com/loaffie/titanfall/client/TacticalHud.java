package com.loaffie.titanfall.client;

import com.loaffie.titanfall.client.phase.PhaseStates;
import com.loaffie.titanfall.client.cloak.CloakStates;
import com.loaffie.titanfall.cloak.Cloak;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.holo.HoloPilot;
import com.loaffie.titanfall.movement.CloakController;
import com.loaffie.titanfall.movement.GrappleController;
import com.loaffie.titanfall.movement.HoloController;
import com.loaffie.titanfall.movement.PhaseController;
import com.loaffie.titanfall.movement.PulseController;
import com.loaffie.titanfall.movement.StimController;
import com.loaffie.titanfall.phase.PhaseShift;
import com.loaffie.titanfall.pulse.PulseBlade;
import com.loaffie.titanfall.stim.Stim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/**
 * The tactical abilities' crosshair readout, after Titanfall 2's tactical ring: a short arc round the
 * crosshair per ability that fills as its charge comes back (amber while refilling, cyan when ready) - the
 * first ability worn (Grapple, Phase Shift, Cloak, Holo Pilot, Pulse Blade, Stim) under the crosshair, a second over
 * it - the Holo Pilot's split in two, one half per charge - and for
 * the Grapple two brackets either side of the crosshair that light up when something the hook could bite is
 * under it and in range. While phased, the Phase Shift's arc counts the phase down in white.
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
	private static final int ACTIVE = 0xF0E8F6FF;
	private static final int BRACKET_ON = 0xF0BDF3FF;
	private static final int BRACKET_OFF = 0x50BDF3FF;

	private TacticalHud() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || !TitanfallConfig.get().grappleHud
				|| !client.options.getPerspective().isFirstPerson() || client.player.isSpectator() || PulseController.aiming(client.player)) {
			return;
		}

		boolean grapple = Grapple.isWorn(client.player);
		boolean phase = PhaseShift.isWorn(client.player);
		boolean cloak = Cloak.isWorn(client.player);
		boolean holo = HoloPilot.isWorn(client.player);
		boolean pulse = PulseBlade.isWorn(client.player);
		boolean stim = Stim.isWorn(client.player);
		if (!grapple && !phase && !cloak && !holo && !pulse && !stim) {
			return;
		}

		// The centre pixel of vanilla's 15 x 15 crosshair, which is drawn at ((w - 15) / 2, (h - 15) / 2). Everything
		// below is drawn in single pixels mirrored about it, so the readout sits exactly round the crosshair.
		int cx = (context.getScaledWindowWidth() - 15) / 2 + 7;
		int cy = (context.getScaledWindowHeight() - 15) / 2 + 7;
		int side = 1;
		if (grapple) {
			arc(context, cx, cy, GrappleController.meter(), GrappleController.ready() ? READY : CHARGING, side);
			side = -1;
			int bracket = GrappleController.ready() && GrappleController.targetInRange() ? BRACKET_ON : BRACKET_OFF;
			for (int s = -1; s <= 1; s += 2) {
				int x = cx + s * BRACKET_GAP;
				context.fill(x, cy - 3, x + 1, cy + 4, bracket);
				// Serifs point inward, toward the crosshair.
				context.fill(x - s, cy - 3, x - s + 1, cy - 2, bracket);
				context.fill(x - s, cy + 3, x - s + 1, cy + 4, bracket);
			}
		}

		if (phase) {
			PhaseStates.State state = PhaseStates.of(client.player);
			if (state != null && state.phased()) {
				float left = 1.0F - (state.age + tickCounter.getTickDelta(false)) / state.ticks;
				arc(context, cx, cy, MathHelper.clamp(left, 0.0F, 1.0F), ACTIVE, side);
			} else {
				arc(context, cx, cy, PhaseController.meter(), PhaseController.ready() ? READY : CHARGING, side);
			}

			side = -side;
		}

		if (cloak) {
			CloakStates.State state = CloakStates.of(client.player);
			boolean holding = state != null && state.holding();
			arc(context, cx, cy, CloakController.meter(), holding ? ACTIVE : CloakController.ready() ? READY : CHARGING, side);
			side = -side;
		}

		if (holo) {
			int charges = HoloPilot.charges(TitanfallConfig.get());
			arc(context, cx, cy, HoloController.meter() / charges, HoloController.ready() ? READY : CHARGING, side, charges);
			side = -side;
		}

		if (pulse) {
			arc(context, cx, cy, PulseController.meter(), PulseController.ready() ? READY : CHARGING, side);
			side = -side;
		}

		if (stim) {
			arc(context, cx, cy, StimController.meter(), StimController.ready() ? READY : CHARGING, side);
		}
	}

	/** One charge arc: under the crosshair ({@code side} 1) or over it (-1), filled to {@code meter} in {@code fill}. */
	private static void arc(DrawContext context, int cx, int cy, double meter, int fill, int side) {
		arc(context, cx, cy, meter, fill, side, 1);
	}

	/** A charge arc split into {@code segments} with a one-dot gap between them, one per charge. */
	private static void arc(DrawContext context, int cx, int cy, double meter, int fill, int side, int segments) {
		int filled = (int) Math.floor(meter * DOTS + 1.0E-6);
		for (int i = 0; i < DOTS; i++) {
			// The dot at (or just past) each boundary between charges is left out.
			if (segments > 1 && i > 0 && i < DOTS - 1 && (i * segments) % (DOTS - 1) < segments) {
				continue;
			}

			float a = MathHelper.lerp(i / (float) (DOTS - 1), ARC_START, ARC_END);
			int x = cx + Math.round(MathHelper.cos(a) * RADIUS);
			int y = cy + side * Math.round(MathHelper.sin(a) * RADIUS);
			int color = i < filled ? fill : EMPTY;
			context.fill(x, y, x + 1, y + 1, color);
		}
	}
}
