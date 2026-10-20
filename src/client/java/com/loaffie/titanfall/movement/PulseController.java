package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.client.pulse.PulseAim;
import com.loaffie.titanfall.client.pulse.PulseSounds;
import com.loaffie.titanfall.client.pulse.PulseStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import com.loaffie.titanfall.pulse.PulseBlade;
import com.loaffie.titanfall.pulse.PulseThrowPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The local pilot's Pulse Blade: hold the key to draw the kunai and aim (the arc shows where it will land),
 * let go to throw. A quick tap still draws and throws it in one motion. As in Titanfall 2 the meter empties
 * on the throw and refills at once, at a steady rate, over 25 seconds.
 */
public final class PulseController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = 1.0;
	/** The key was let go while the kunai was still being drawn: throw as soon as it is up. */
	private static boolean releaseQueued;

	private PulseController() {
	}

	/** The Tactical key (or the Pulse Blade key) went down. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !PulseBlade.isWorn(player) || player.isSpectator() || !player.isAlive() || PulseStates.of(player) != null) {
			return;
		}

		if (!ready() || PhaseShift.isPhased(player)) {
			GrappleSounds.denied();
			return;
		}

		releaseQueued = false;
		PulseStates.draw(player);
		send(new PulseThrowPayload(PulseThrowPayload.AIM, 0.0F, 0.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0));
	}

	/** Start of every client tick, with whether the key is still held: throws on release, lets go at the release point. */
	public static void tick(MinecraftClient client, boolean held) {
		ClientPlayerEntity player = client.player;
		PulseStates.State state = player == null ? null : PulseStates.of(player);
		if (state == null) {
			return;
		}

		if (!PulseBlade.isWorn(player) || PhaseShift.isPhased(player)) {
			if (state.stage != PulseStates.Stage.THROW) {
				PulseStates.cancel(player);
				send(new PulseThrowPayload(PulseThrowPayload.CANCEL, 0.0F, 0.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0));
			}

			return;
		}

		if (state.stage != PulseStates.Stage.THROW && !held) {
			releaseQueued = true;
		}

		if (releaseQueued && state.stage == PulseStates.Stage.AIM) {
			releaseQueued = false;
			PulseStates.swing(player, 0);
		} else if (state.stage == PulseStates.Stage.THROW && state.ticks == PulseStates.RELEASE_TICK) {
			release(player);
		}
	}

	private static void release(ClientPlayerEntity player) {
		meter = 0.0;
		PulseSounds.toss(player);
		// The same throw the arc showed: from the hand, converging on the crosshair.
		PulseAim.Aim aim = PulseAim.of(player, 1.0F);
		Vec3d motion = PulseAim.motion(player);
		send(new PulseThrowPayload(PulseThrowPayload.THROW, player.getYaw(), player.getPitch(), aim.aimYaw(), aim.aimPitch(),
				motion.x, motion.y, motion.z));
	}

	private static void send(PulseThrowPayload payload) {
		if (ClientPlayNetworking.canSend(PulseThrowPayload.ID)) {
			ClientPlayNetworking.send(payload);
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

		double seconds = TitanfallConfig.get().pulseCooldownSeconds;
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

	/** True while the local pilot holds the kunai up, aiming: the arc is shown. */
	public static boolean aiming(ClientPlayerEntity player) {
		PulseStates.State state = PulseStates.of(player);
		return state != null && state.inHand();
	}
}
