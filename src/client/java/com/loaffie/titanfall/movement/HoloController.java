package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.holo.HoloDecoyEntity;
import com.loaffie.titanfall.holo.HoloPayload;
import com.loaffie.titanfall.holo.HoloPilot;
import com.loaffie.titanfall.movement.physics.HVec;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The local pilot's Holo Pilot: the key and the two charges. A press spends a charge and tells the server to
 * send out a decoy, with what the pilot is doing right now for it to carry on: the movement controller's
 * mode (a slide, a wall run, the air), the velocity, and the way the keys point - pushed past 65% and the
 * decoy runs that way, otherwise it stands where it appears. Each charge comes back in 12.5 seconds.
 */
public final class HoloController {
	private static ClientPlayerEntity trackedPlayer;
	private static double meter = -1.0;

	private HoloController() {
	}

	/** The Tactical key (or the Holo Pilot key) was pressed. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || !HoloPilot.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		if (!ready() || PhaseShift.isPhased(player)) {
			GrappleSounds.denied();
			return;
		}

		meter -= 1.0;
		HoloPayload payload = describe(player);
		if (ClientPlayNetworking.canSend(HoloPayload.ID)) {
			ClientPlayNetworking.send(payload);
		}
	}

	/** The pilot's move, as the decoy should carry it on. */
	static HoloPayload describe(ClientPlayerEntity player) {
		TitanfallController.View view = TitanfallController.view(player);
		Vec3d velocity = player.getVelocity();
		HoloDecoyEntity.Move move = switch (view.mode()) {
			case WALL -> HoloDecoyEntity.Move.WALL;
			case SLIDE -> HoloDecoyEntity.Move.SLIDE;
			case AIR, GRAPPLE -> HoloDecoyEntity.Move.AIR;
			default -> player.isOnGround() ? HoloDecoyEntity.Move.GROUND : HoloDecoyEntity.Move.AIR;
		};

		float forward = player.input.movementForward;
		float sideways = player.input.movementSideways;
		float push = Math.min(1.0F, MathHelper.sqrt(forward * forward + sideways * sideways));
		boolean run = push >= HoloPilot.STICK_TO_RUN;
		float yaw = player.getYaw();
		float runYaw = yaw;
		HVec normal = HVec.ZERO;
		if (move == HoloDecoyEntity.Move.WALL) {
			normal = view.wallNormal();
			HVec along = view.wallVelocity();
			if (along.length() > 0.1) {
				runYaw = yawOf(along.x(), along.z());
			}
		} else if (move == HoloDecoyEntity.Move.SLIDE && velocity.horizontalLengthSquared() > 1.0E-6) {
			runYaw = yawOf(velocity.x, velocity.z);
		} else if (push > 0.01F) {
			// The keys' direction in the world, as vanilla turns movement input by the yaw.
			float sin = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
			float cos = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
			runYaw = yawOf(sideways * cos - forward * sin, forward * cos + sideways * sin);
		}

		return new HoloPayload(velocity.x, velocity.y, velocity.z, move.ordinal(), run || move == HoloDecoyEntity.Move.SLIDE
				|| move == HoloDecoyEntity.Move.WALL, runYaw, (float) normal.x(), (float) normal.z());
	}

	private static float yawOf(double x, double z) {
		return (float) (MathHelper.atan2(-x, z) * MathHelper.DEGREES_PER_RADIAN);
	}

	/** The server refused the decoy (it disagreed about the charge, or the pilot was phased): the charge comes back. */
	public static void refused() {
		meter = Math.min(HoloPilot.charges(TitanfallConfig.get()), meter + 1.0);
	}

	/** End of every client tick: refill the charges, chiming as each one comes back. */
	public static void clientTick(MinecraftClient client) {
		TitanfallConfig config = TitanfallConfig.get();
		int max = HoloPilot.charges(config);
		if (client.player != trackedPlayer || meter < 0.0) {
			trackedPlayer = client.player;
			meter = max;
		}

		if (trackedPlayer == null || meter >= max) {
			return;
		}

		int before = (int) Math.floor(meter + 1.0E-6);
		meter = Math.min(max, meter + HoloPilot.refillPerTick(config));
		if ((int) Math.floor(meter + 1.0E-6) > before) {
			GrappleSounds.ready();
		}
	}

	/** Charges held, 0..{@link HoloPilot#charges}, fractional while one comes back. */
	public static double meter() {
		return Math.max(0.0, meter);
	}

	public static boolean ready() {
		return meter >= 1.0 - 1.0E-6;
	}
}
