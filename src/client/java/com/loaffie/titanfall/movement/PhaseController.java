package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.client.phase.PhaseStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseServer;
import com.loaffie.titanfall.phase.PhaseShift;
import com.loaffie.titanfall.phase.PhaseShiftPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The local pilot's Phase Shift: the key and the charge meter. As in Titanfall 2 the shift is instant, the
 * meter empties on use and refills at once at a steady rate, over 15 seconds, and a shift can't be used
 * again - or cut short - until it is full.
 */
public final class PhaseController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = 1.0;

	private PhaseController() {
	}

	/** The Tactical key (or the Phase Shift key) was pressed. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !PhaseShift.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		if (!ready() || PhaseStates.phased(player)) {
			GrappleSounds.denied();
			return;
		}

		meter = 0.0;
		PhaseStates.start(player, PhaseServer.phaseTicks(TitanfallConfig.get()));
		if (ClientPlayNetworking.canSend(PhaseShiftPayload.ID)) {
			ClientPlayNetworking.send(new PhaseShiftPayload());
		}
	}

	/** End of every client tick: refill the meter. */
	public static void clientTick(MinecraftClient client) {
		if (client.player != trackedPlayer) {
			trackedPlayer = client.player;
			meter = 1.0;
		}

		if (trackedPlayer == null || meter >= 1.0) {
			return;
		}

		double seconds = TitanfallConfig.get().phaseCooldownSeconds;
		meter = seconds <= 0.0 ? 1.0 : Math.min(1.0, meter + 1.0 / (seconds * 20.0));
		if (meter >= 1.0) {
			GrappleSounds.ready();
		}
	}

	public static double meter() {
		return meter;
	}

	public static boolean ready() {
		return meter >= 1.0 - 1.0E-6;
	}
}
