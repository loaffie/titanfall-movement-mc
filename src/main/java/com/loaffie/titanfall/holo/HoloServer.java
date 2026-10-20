package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Holo Pilot, as Titanfall 2 runs it ({@code mp_ability_holopilot}):
 *
 * <ul>
 *   <li>Two charges, one spent per decoy, each back in 12.5 seconds ({@code ammo_per_shot} 100 of 200 at
 *   {@code regen_ammo_refill_rate} 8 a second); the server keeps its own count and refuses a decoy it doesn't
 *   think is charged.</li>
 *   <li>The decoy carries on with the move the pilot's client describes ({@link HoloDecoyEntity#of}).</li>
 *   <li>It doesn't break a cloak ({@code breaks_cloak} 0), and isn't made while phased (it couldn't be in
 *   the world the pilot has left).</li>
 *   <li>Monsters attack decoys as they would the pilot ({@code EnableAttackableByAI}): those chasing the pilot
 *   that can see the new decoy go after it instead.</li>
 * </ul>
 */
public final class HoloServer {
	/** Slack on the charge check, for latency: half a second of a 12.5 s charge. */
	private static final double CHARGE_SLACK = 0.04;
	/** Monsters this close to the pilot that can see the decoy are drawn to it, blocks. */
	private static final double DISTRACT_RANGE = 32.0;

	/** A pilot's charge as of a tick. */
	private record Charge(double value, int tick) {
	}

	private static final Map<UUID, Charge> CHARGE = new HashMap<>();
	private static int now;

	private HoloServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(HoloPayload.ID, HoloPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HoloRefusedPayload.ID, HoloRefusedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HoloPayload.ID, (payload, context) -> deploy(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> now++);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHARGE.remove(handler.player.getUuid()));
	}

	/** The pilot's charges now, 0..{@link HoloPilot#charges}. */
	private static double charge(ServerPlayerEntity player, TitanfallConfig config) {
		Charge charge = CHARGE.get(player.getUuid());
		int max = HoloPilot.charges(config);
		return charge == null ? max : Math.min(max, charge.value() + (now - charge.tick()) * HoloPilot.refillPerTick(config));
	}

	private static void deploy(ServerPlayerEntity player, HoloPayload payload) {
		TitanfallConfig config = TitanfallConfig.get();
		double charge = charge(player, config);
		if (!HoloPilot.isWorn(player) || player.isSpectator() || !player.isAlive() || PhaseShift.isPhased(player)
				|| charge < 1.0 - CHARGE_SLACK) {
			ServerPlayNetworking.send(player, new HoloRefusedPayload());
			return;
		}

		CHARGE.put(player.getUuid(), new Charge(Math.max(0.0, charge - 1.0), now));
		HoloDecoyEntity decoy = HoloDecoyEntity.of(player, payload, config);
		player.getServerWorld().spawnEntity(decoy);
		if (config.holoDistractsMobs) {
			distract(player, decoy);
		}
	}

	private static void distract(ServerPlayerEntity player, HoloDecoyEntity decoy) {
		Box around = player.getBoundingBox().expand(DISTRACT_RANGE);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, around,
				mob -> mob instanceof Monster && mob.getTarget() == player && mob.canSee(decoy))) {
			mob.setTarget(decoy);
		}
	}
}
