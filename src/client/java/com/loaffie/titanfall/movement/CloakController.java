package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.cloak.CloakStates;
import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.cloak.Cloak;
import com.loaffie.titanfall.cloak.CloakPayload;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The local pilot's Cloak: the key and the charge meter. As in Titanfall 2 a cloak needs a full charge; the
 * charge drains over the cloak and refills from wherever the cloak ended - empty after a full one, 20
 * seconds to full - so a cloak dropped early by firing comes back sooner. It can't be switched off early.
 */
public final class CloakController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = 1.0;

	private CloakController() {
	}

	/** The Tactical key (or the Cloak key) was pressed. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !Cloak.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		if (!ready() || Cloak.isCloaked(player) || PhaseShift.isPhased(player)) {
			GrappleSounds.denied();
			return;
		}

		CloakStates.start(player, Cloak.durationTicks(TitanfallConfig.get()));
		if (ClientPlayNetworking.canSend(CloakPayload.ID)) {
			ClientPlayNetworking.send(new CloakPayload());
		}
	}

	/** End of every client tick: drain the meter while cloaked, refill it after. */
	public static void clientTick(MinecraftClient client) {
		if (client.player != trackedPlayer) {
			trackedPlayer = client.player;
			meter = 1.0;
		}

		if (trackedPlayer == null) {
			return;
		}

		TitanfallConfig config = TitanfallConfig.get();
		CloakStates.State state = CloakStates.of(trackedPlayer);
		if (state != null && state.holding()) {
			meter = Cloak.chargeLeft(config, state.age);
		} else if (meter < 1.0) {
			meter = Math.min(1.0, meter + Cloak.refillPerTick(config));
			if (meter >= 1.0) {
				GrappleSounds.ready();
			}
		}
	}

	public static double meter() {
		return meter;
	}

	public static boolean ready() {
		return meter >= 1.0 - 1.0E-6;
	}
}
