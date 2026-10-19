package com.loaffie.titanfall.stim;

import com.loaffie.titanfall.config.TitanfallConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Stim, as Titanfall 2 runs it ({@code sh_stim.gnut}, {@code _health_regen.gnut}):
 *
 * <ul>
 *   <li>The speed boost is a status effect lasting the stim's duration plus half a second, easing out over
 *   its last quarter second. Here it is a movement-speed attribute modifier, so walking and sprinting are
 *   faster on the server and the client alike (the client also scales the pilot moves it runs itself).</li>
 *   <li>While the stim runs, health regenerates at double rate, starting two seconds after the last hit
 *   instead of the usual five ({@code ABILITY_STIM_REGEN_MOD}, {@code ABILITY_STIM_REGEN_DELAY}). Here the
 *   healing also lingers for {@code stimHealLingerSeconds} after the stim ends.</li>
 *   <li>Other players are told, so they see the trail and hear the stim.</li>
 * </ul>
 *
 * The client runs the cooldown; the server only refuses a stim that comes back much sooner than it could.
 */
public final class StimServer {
	/** The boost eases out over its last quarter second (the status effect's fade). */
	private static final int FADE_TICKS = 5;
	/** Ticks of slack on the cooldown check, for latency. */
	private static final int COOLDOWN_SLACK_TICKS = 20;

	private record Active(int start, int boostTicks, int healTicks) {
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_USE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_DAMAGE = new HashMap<>();
	private static int now;

	private StimServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(StimPayload.ID, StimPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StimSyncPayload.ID, StimSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StimPayload.ID, (payload, context) -> activate(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(StimServer::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player && damageTaken > 0.0F) {
				LAST_DAMAGE.put(player.getUuid(), now);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			ACTIVE.remove(id);
			LAST_USE.remove(id);
			LAST_DAMAGE.remove(id);
		});
	}

	/** Boost length in ticks: the stim plus the half second the speed effect outlasts it. */
	public static int boostTicks(TitanfallConfig config) {
		return (int) Math.round((config.stimLengthSeconds + 0.5) * 20.0);
	}

	private static void activate(ServerPlayerEntity player) {
		TitanfallConfig config = TitanfallConfig.get();
		if (!Stim.isWorn(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		Integer last = LAST_USE.get(player.getUuid());
		int cooldown = (int) Math.round(config.stimCooldownSeconds * 20.0) - COOLDOWN_SLACK_TICKS;
		if (last != null && now - last < cooldown) {
			return;
		}

		int boost = boostTicks(config);
		LAST_USE.put(player.getUuid(), now);
		int heal = (int) Math.round((config.stimLengthSeconds + config.stimHealLingerSeconds) * 20.0);
		ACTIVE.put(player.getUuid(), new Active(now, boost, heal));
		StimSyncPayload sync = new StimSyncPayload(player.getId(), boost);
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

		TitanfallConfig config = TitanfallConfig.get();
		Iterator<Map.Entry<UUID, Active>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Active> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			Active active = entry.getValue();
			int elapsed = now - active.start();
			if (player == null) {
				iterator.remove();
				continue;
			}

			boolean boosting = elapsed < active.boostTicks() && player.isAlive();
			float level = boosting ? Math.min(1.0F, (active.boostTicks() - elapsed) / (float) FADE_TICKS) : 0.0F;
			setBoost(player, config.stimSpeedBoost * level);
			if (!player.isAlive() || (!boosting && elapsed >= active.healTicks())) {
				iterator.remove();
				continue;
			}

			// Double-rate healing while the stim runs and for a while after, once two seconds have passed since
			// the last hit.
			int lastDamage = LAST_DAMAGE.getOrDefault(entry.getKey(), Integer.MIN_VALUE / 2);
			if (elapsed < active.healTicks() && player.getHealth() < player.getMaxHealth()
					&& now - lastDamage >= config.stimRegenDelaySeconds * 20.0) {
				player.heal((float) (player.getMaxHealth() * config.stimHealPerSecond / 20.0));
			}
		}
	}

	/** Sets the speed modifier to {@code amount} (a multiplier on the total, 0.4 = +40%), or removes it at 0. */
	private static void setBoost(ServerPlayerEntity player, double amount) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		EntityAttributeModifier current = speed.getModifier(Stim.SPEED_MODIFIER);
		if (current != null && Math.abs(current.value() - amount) < 1.0E-4) {
			return;
		}

		speed.removeModifier(Stim.SPEED_MODIFIER);
		if (amount > 1.0E-4) {
			speed.addTemporaryModifier(new EntityAttributeModifier(Stim.SPEED_MODIFIER, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
