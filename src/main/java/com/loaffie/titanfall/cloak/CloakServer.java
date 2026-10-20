package com.loaffie.titanfall.cloak;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Cloak, as Titanfall 2 runs it ({@code mp_ability_cloak}):
 *
 * <ul>
 *   <li>The cloak starts at once and lasts {@code cloakDurationSeconds} (15). The charge drains over it and
 *   refills from wherever it ended at 10 a second (20 s from empty); a cloak needs a full charge.</li>
 *   <li>The cloaked pilot is invisible - Minecraft's own invisibility, so no name tag, no shadow, and mobs
 *   barely notice them (only right up close, whatever they wear); mobs hunting them from further off lose
 *   them. Other players see the client's shimmer.</li>
 *   <li>Firing a weapon - loosing an arrow, a crossbow bolt or rocket, a trident - drops the cloak, as in the
 *   game since its first patch. Melee and thrown things (snowballs, pearls, the Pulse Blade) don't.</li>
 *   <li>Other players are told, so they see the pilot fade and hear it.</li>
 * </ul>
 *
 * The client runs its meter itself; the server keeps its own and refuses a cloak it doesn't think is charged.
 */
public final class CloakServer {
	/** Slack on the charge check, for latency: a second's worth of a 20 s refill. */
	private static final double CHARGE_SLACK = 0.05;
	/** Mobs hunting the pilot from further off than this lose them as they cloak, blocks. */
	private static final double LOSE_TARGET_BEYOND = 4.0;
	private static final double LOSE_TARGET_RANGE = 48.0;
	/** How well mobs notice a cloaked pilot, against 1 for a visible one (vanilla invisibility: 0.07 to 0.7 by armour). */
	public static final double DETECTION = 0.1;

	private record Active(int start, int end) {
	}

	/** The charge as a cloak ended, and when. */
	private record Charge(double value, int tick) {
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static final Map<UUID, Charge> CHARGE = new HashMap<>();
	private static int now;

	private CloakServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CloakPayload.ID, CloakPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CloakSyncPayload.ID, CloakSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CloakPayload.ID, (payload, context) -> activate(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(CloakServer::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player && ACTIVE.containsKey(player.getUuid())) {
				end(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ACTIVE.remove(handler.player.getUuid());
			CHARGE.remove(handler.player.getUuid());
		});
		// Firing a weapon gives the pilot away.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if ((entity instanceof PersistentProjectileEntity || entity instanceof FireworkRocketEntity)
					&& ((ProjectileEntity) entity).getOwner() instanceof ServerPlayerEntity player && ACTIVE.containsKey(player.getUuid())) {
				end(player);
			}
		});
	}

	static boolean isCloaked(Entity entity) {
		return ACTIVE.containsKey(entity.getUuid());
	}

	/** The pilot's charge now, 0..1. */
	private static double charge(ServerPlayerEntity player, TitanfallConfig config) {
		Active active = ACTIVE.get(player.getUuid());
		if (active != null) {
			return Cloak.chargeLeft(config, now - active.start());
		}

		Charge charge = CHARGE.get(player.getUuid());
		return charge == null ? 1.0 : Math.min(1.0, charge.value() + (now - charge.tick()) * Cloak.refillPerTick(config));
	}

	private static void activate(ServerPlayerEntity player) {
		TitanfallConfig config = TitanfallConfig.get();
		if (!Cloak.isWorn(player) || player.isSpectator() || !player.isAlive() || ACTIVE.containsKey(player.getUuid())
				|| PhaseShift.isPhased(player) || charge(player, config) < 1.0 - CHARGE_SLACK) {
			// Tell the pilot's client, which already cloaked, to drop it.
			ServerPlayNetworking.send(player, new CloakSyncPayload(player.getId(), 0));
			return;
		}

		int ticks = Cloak.durationTicks(config);
		ACTIVE.put(player.getUuid(), new Active(now, now + ticks));
		player.setInvisible(true);
		Box around = player.getBoundingBox().expand(LOSE_TARGET_RANGE);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, around,
				mob -> mob.getTarget() == player && mob.squaredDistanceTo(player) > LOSE_TARGET_BEYOND * LOSE_TARGET_BEYOND)) {
			mob.setTarget(null);
		}

		CloakSyncPayload sync = new CloakSyncPayload(player.getId(), ticks);
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, sync);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		now++;
		if (ACTIVE.isEmpty()) {
			return;
		}

		List<UUID> ending = new ArrayList<>();
		ACTIVE.forEach((id, active) -> {
			if (now >= active.end()) {
				ending.add(id);
			}
		});
		for (UUID id : ending) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				end(player);
			} else {
				ACTIVE.remove(id);
			}
		}
	}

	/** The cloak drops: visible again (unless a potion says otherwise), the charge left behind, everyone told. */
	private static void end(ServerPlayerEntity player) {
		TitanfallConfig config = TitanfallConfig.get();
		Active active = ACTIVE.remove(player.getUuid());
		if (active == null) {
			return;
		}

		CHARGE.put(player.getUuid(), new Charge(Cloak.chargeLeft(config, now - active.start()), now));
		player.setInvisible(player.hasStatusEffect(StatusEffects.INVISIBILITY));
		CloakSyncPayload sync = new CloakSyncPayload(player.getId(), 0);
		ServerPlayNetworking.send(player, sync);
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, sync);
			}
		}
	}
}
