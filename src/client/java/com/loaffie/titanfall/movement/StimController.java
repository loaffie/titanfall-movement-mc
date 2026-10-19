package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.client.stim.StimStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.stim.Stim;
import com.loaffie.titanfall.stim.StimPayload;
import com.loaffie.titanfall.stim.StimServer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The local pilot's Stim: the key, the charge meter, and the speed scale the movement controller applies
 * to the pilot moves it runs itself (the attribute modifier the server sets covers walking and sprinting).
 *
 * <p>As in Titanfall 2 the meter empties on use and refills at once, at a steady rate, over 25 seconds; a
 * stim can't be used again until it is full.
 */
public final class StimController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = 1.0;

	private StimController() {
	}

	/** The Stim key (or the Tactical key, without a Grapple) was pressed. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !Stim.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		if (meter < 1.0 - 1.0E-6 || StimStates.of(player) != null) {
			GrappleSounds.denied();
			return;
		}

		meter = 0.0;
		StimStates.start(player, StimServer.boostTicks(TitanfallConfig.get()));
		if (ClientPlayNetworking.canSend(StimPayload.ID)) {
			ClientPlayNetworking.send(new StimPayload());
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

		double seconds = TitanfallConfig.get().stimCooldownSeconds;
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

	/**
	 * Multiplier for the speeds the movement controller sets itself (take-off, wall run): 1 + the boost,
	 * easing out with it.
	 */
	public static double speedScale(ClientPlayerEntity player) {
		StimStates.State state = StimStates.of(player);
		return state == null ? 1.0 : 1.0 + TitanfallConfig.get().stimSpeedBoost * state.boost(0.0F);
	}
}
