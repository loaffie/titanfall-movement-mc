package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.awall.AWall;
import com.loaffie.titanfall.awall.AWallThrowPayload;
import com.loaffie.titanfall.client.awall.AWallAnimation;
import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The local pilot's A-Wall: the key, the toss and the charge. A press starts the toss
 * ({@link AWallAnimation}) and spends the charge; a few ticks later, as the hand lets go, the disc is thrown
 * along the view, with the pilot's motion, by the server. The charge is back twenty seconds after the press.
 */
public final class AWallController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = -1.0;
	/** Ticks until the disc leaves the hand, or -1 with no toss under way. */
	private static int releaseIn = -1;

	private AWallController() {
	}

	/** The Tactical key (or the A-Wall key) was pressed. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !AWall.isWorn(player) || player.isSpectator() || !player.isAlive() || releaseIn >= 0) {
			return;
		}

		if (!ready() || PhaseShift.isPhased(player)) {
			GrappleSounds.denied();
			return;
		}

		meter = 0.0;
		releaseIn = AWallAnimation.RELEASE_TICKS - 1;
		AWallAnimation.start(player);
	}

	/** The server refused the throw (it disagreed about the charge, or the pilot was phased): the charge comes back. */
	public static void refused() {
		meter = 1.0;
	}

	/** End of every client tick: let the disc go when the hand does, and refill the charge. */
	public static void clientTick(MinecraftClient client) {
		if (client.player != trackedPlayer || meter < 0.0) {
			trackedPlayer = client.player;
			meter = 1.0;
			releaseIn = -1;
		}

		if (trackedPlayer == null) {
			return;
		}

		if (releaseIn >= 0 && --releaseIn < 0) {
			release(trackedPlayer);
		}

		if (meter >= 1.0 || releaseIn >= 0) {
			return;
		}

		int ticks = AWall.rechargeTicks(TitanfallConfig.get());
		meter = ticks <= 0 ? 1.0 : Math.min(1.0, meter + 1.0 / ticks);
		if (meter >= 1.0) {
			GrappleSounds.ready();
		}
	}

	/** The hand lets go: the disc goes along the view - unless the pilot can't throw it any more, and keeps the charge. */
	private static void release(ClientPlayerEntity player) {
		if (!AWall.isWorn(player) || !player.isAlive() || PhaseShift.isPhased(player)) {
			meter = 1.0;
			return;
		}

		Vec3d motion = player.getVelocity();
		if (ClientPlayNetworking.canSend(AWallThrowPayload.ID)) {
			ClientPlayNetworking.send(new AWallThrowPayload(player.getYaw(), player.getPitch(), motion.x, motion.y, motion.z));
		}
	}

	/** The charge, 0..1. */
	public static double meter() {
		return Math.max(0.0, meter);
	}

	public static boolean ready() {
		return meter >= 1.0 - 1.0E-6;
	}
}
