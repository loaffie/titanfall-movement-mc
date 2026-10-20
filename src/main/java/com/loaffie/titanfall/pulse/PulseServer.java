package com.loaffie.titanfall.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.entity.TitanfallEntities;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Pulse Blade: throws it for the pilot who let it go - from their hand, along the view
 * and with the motion their client reports, as the client's aiming arc showed - and relays the wind-up and
 * the throw so other players see the arm. The client runs the cooldown; the server only refuses a throw
 * that comes back much sooner than it could, or one from a phased pilot.
 */
public final class PulseServer {
	/** Ticks of slack on the cooldown check, for latency. */
	private static final int COOLDOWN_SLACK_TICKS = 20;

	private static final Map<UUID, Long> LAST_THROW = new HashMap<>();

	private PulseServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PulseThrowPayload.ID, PulseThrowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PulseSyncPayload.ID, PulseSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PulseThrowPayload.ID, (payload, context) -> receive(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_THROW.remove(handler.player.getUuid()));
	}

	private static void receive(ServerPlayerEntity player, PulseThrowPayload payload) {
		if (!PulseBlade.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		if (payload.stage() == PulseThrowPayload.THROW && !throwBlade(player, payload)) {
			return;
		}

		PulseSyncPayload sync = new PulseSyncPayload(player.getId(), payload.stage());
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, sync);
			}
		}
	}

	private static boolean throwBlade(ServerPlayerEntity player, PulseThrowPayload payload) {
		TitanfallConfig config = TitanfallConfig.get();
		long now = player.getServer().getTicks();
		Long last = LAST_THROW.get(player.getUuid());
		long cooldown = Math.round(config.pulseCooldownSeconds * 20.0) - COOLDOWN_SLACK_TICKS;
		if (PhaseShift.isPhased(player) || (last != null && now - last < cooldown)
				|| !Float.isFinite(payload.yaw()) || !Float.isFinite(payload.pitch())) {
			return false;
		}

		LAST_THROW.put(player.getUuid(), now);
		float pitch = MathHelper.clamp(payload.pitch(), -90.0F, 90.0F);
		Vec3d motion = new Vec3d(payload.vx(), payload.vy(), payload.vz());
		if (!Double.isFinite(motion.lengthSquared())) {
			motion = Vec3d.ZERO;
		}

		PulseBladeEntity blade = new PulseBladeEntity(TitanfallEntities.PULSE_BLADE, player.getServerWorld());
		blade.setOwner(player);
		Vec3d origin = PulseBlade.origin(player.getEyePos(), payload.yaw(), pitch, PulseBlade.throwingArm(player));
		blade.setPosition(origin);
		Vec3d velocity = PulseBlade.launch(config, payload.yaw(), pitch, motion);
		blade.setVelocity(velocity);
		blade.setYaw((float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN));
		blade.setPitch((float) (MathHelper.atan2(velocity.y, velocity.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN));
		player.getServerWorld().spawnEntity(blade);
		return true;
	}
}
