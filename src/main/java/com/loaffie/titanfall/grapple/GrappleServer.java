package com.loaffie.titanfall.grapple;

import com.loaffie.titanfall.config.TitanfallConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Grapple: checks that the sender really wears one, relays each grapple event to the
 * players tracking the sender, keeps a pilot on the line from building up fall damage, and - as in
 * Titanfall 2, where a hooked pilot caught in mid-air is reeled in toward the grappler while the grappler
 * is pulled to them - tugs an airborne hooked entity toward the pilot holding the line.
 */
public final class GrappleServer {
	/** Tug on a hooked airborne entity, blocks/tick added per tick, and the closing speed it stops at (blocks/tick). */
	private static final double TUG_ACCEL = 0.06;
	private static final double TUG_MAX_SPEED = 0.45;
	/** Slack allowed on the client's reported range (latency, the target moving). */
	private static final double RANGE_SLACK = 6.0;
	/** A hold no client release ever ended (lost packet) is dropped after this many ticks. */
	private static final int MAX_HOLD_TICKS = 20 * 15;

	/** A pilot on the line: the entity hooked (-1 for a block) and ticks held. */
	private record Hold(int entityId, int ticks) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private GrappleServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(GrapplePayload.ID, GrapplePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GrappleSyncPayload.ID, GrappleSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GrapplePayload.ID, (payload, context) -> receive(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(GrappleServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HOLDS.remove(handler.player.getUuid()));
	}

	private static void receive(ServerPlayerEntity player, GrapplePayload payload) {
		if (payload.phase() == GrapplePayload.RELEASE) {
			HOLDS.remove(player.getUuid());
		} else if (!Grapple.isWorn(player) || player.isSpectator()) {
			return;
		} else if (payload.phase() == GrapplePayload.ATTACH) {
			int entityId = -1;
			if (payload.entity() >= 0) {
				Entity target = player.getWorld().getEntityById(payload.entity());
				double range = TitanfallConfig.get().grappleRange + RANGE_SLACK;
				if (target instanceof LivingEntity && target != player && target.squaredDistanceTo(player) < range * range) {
					entityId = target.getId();
				}
			}

			HOLDS.put(player.getUuid(), new Hold(entityId, 0));
		} else if (payload.phase() != GrapplePayload.FIRE) {
			return;
		}

		GrappleSyncPayload sync = new GrappleSyncPayload(player.getId(), payload);
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, sync);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Hold>> iterator = HOLDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Hold> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Hold hold = entry.getValue();
			if (player == null || hold.ticks() > MAX_HOLD_TICKS || !Grapple.isWorn(player)) {
				iterator.remove();
				continue;
			}

			entry.setValue(new Hold(hold.entityId(), hold.ticks() + 1));
			// The line holds the pilot's weight: a fall caught by the hook is not a fall. (Fall damage is decided
			// here on the server, from the fall the server saw.)
			player.fallDistance = 0.0F;
			if (hold.entityId() < 0) {
				continue;
			}

			Entity target = player.getWorld().getEntityById(hold.entityId());
			if (target == null || !target.isAlive()) {
				entry.setValue(new Hold(-1, hold.ticks() + 1));
				continue;
			}

			if (target.isOnGround() || target.isTouchingWater()) {
				continue;
			}

			Vec3d toPilot = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0).subtract(target.getPos().add(0.0, target.getHeight() * 0.5, 0.0));
			double distance = toPilot.length();
			if (distance < 1.5) {
				continue;
			}

			Vec3d direction = toPilot.multiply(1.0 / distance);
			double closing = target.getVelocity().dotProduct(direction);
			if (closing < TUG_MAX_SPEED) {
				target.addVelocity(direction.multiply(Math.min(TUG_ACCEL, TUG_MAX_SPEED - closing)));
				target.velocityModified = true;
			}
		}
	}
}
