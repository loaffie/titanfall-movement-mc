package com.loaffie.titanfall.phase;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of Phase Shift, as Titanfall 2 runs it ({@code sh_phase_shift.gnut}):
 *
 * <ul>
 *   <li>The shift starts at once ({@code SHIFTER_WARMUP_TIME} 0) and lasts {@code phaseLengthSeconds}. The
 *   pilot stays where they are, in the same world, and keeps moving; the world just can't touch them, nor
 *   they it. Mobs hunting them lose them, and a pilot riding something slips off it.</li>
 *   <li>Damage to and from a phased pilot is refused (only what bypasses invulnerability, like the void, gets
 *   through), and so is every attack and use of an item, block or entity.</li>
 *   <li>On the way out, whatever the pilot comes back inside is telefragged - killed outright, credited to the
 *   pilot - if it is human-sized; anything bigger pushes the pilot back out of it instead (the game's
 *   {@code GetEntityAtPhaseShiftExitPosition} and {@code PushPlayersApart}). What the pilot was riding when
 *   they shifted is left alone: they were sitting in it, not shifting into it.</li>
 *   <li>Other players are told, so they see the pilot vanish, the ghost it leaves and the flash it returns
 *   in, and hear it.</li>
 * </ul>
 *
 * The client runs the cooldown; the server only refuses a shift that comes back much sooner than it could.
 */
public final class PhaseServer {
	public static final RegistryKey<DamageType> TELEFRAG = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(TitanfallMovement.MOD_ID, "telefrag"));
	/** Ticks of slack on the cooldown check, for latency. */
	private static final int COOLDOWN_SLACK_TICKS = 20;
	/** Mobs this close that were after the pilot lose them when they phase out, blocks. */
	private static final double LOSE_TARGET_RANGE = 48.0;
	/** Up to this size the pilot telefrags what they come back inside (a player is 0.6 x 1.8); bigger pushes them out. */
	private static final double HUMAN_WIDTH = 1.0;
	private static final double HUMAN_HEIGHT = 3.0;
	/** The pilot's box is shrunk by this before the exit check, so only a real overlap counts, blocks. */
	private static final double EXIT_MARGIN = 0.12;
	/**
	 * How hard the pilot is pushed out of something too big to telefrag, blocks/tick: enough to clear it in a
	 * few ticks. Titanfall's 600 u/s (0.76) throws a pilot far under the jumpkit's light gravity.
	 */
	private static final double PUSH_SPEED = 0.3;
	private static final double PUSH_LIFT = 0.15;

	/** Phased players and the server tick their phase ends. */
	private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_USE = new HashMap<>();
	/** What each phased pilot was riding when they shifted. */
	private static final Map<UUID, UUID> MOUNTS = new HashMap<>();
	private static int now;

	private PhaseServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PhaseShiftPayload.ID, PhaseShiftPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PhaseSyncPayload.ID, PhaseSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PhaseShiftPayload.ID, (payload, context) -> activate(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(PhaseServer::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}

			return !PhaseShift.isPhased(entity) && !PhaseShift.isPhased(source.getAttacker());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player && ACTIVE.remove(player.getUuid()) != null) {
				broadcastEnd(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ACTIVE.remove(handler.player.getUuid());
			LAST_USE.remove(handler.player.getUuid());
			MOUNTS.remove(handler.player.getUuid());
		});

		// Out of phase, the pilot can't touch the world (both sides: the client asks first, the server checks).
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> PhaseShift.isPhased(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> PhaseShift.isPhased(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> PhaseShift.isPhased(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> PhaseShift.isPhased(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> PhaseShift.isPhased(player)
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !PhaseShift.isPhased(player));
	}

	/** Phase length in ticks. */
	public static int phaseTicks(TitanfallConfig config) {
		return Math.max(1, (int) Math.round(config.phaseLengthSeconds * 20.0));
	}

	static boolean isPhased(Entity entity) {
		return ACTIVE.containsKey(entity.getUuid());
	}

	private static void activate(ServerPlayerEntity player) {
		TitanfallConfig config = TitanfallConfig.get();
		Integer last = LAST_USE.get(player.getUuid());
		int cooldown = (int) Math.round(config.phaseCooldownSeconds * 20.0) - COOLDOWN_SLACK_TICKS;
		if (!PhaseShift.isWorn(player) || player.isSpectator() || !player.isAlive() || ACTIVE.containsKey(player.getUuid())
				|| (last != null && now - last < cooldown)) {
			// Tell the pilot's client, which already shifted, to come back.
			ServerPlayNetworking.send(player, new PhaseSyncPayload(player.getId(), 0));
			return;
		}

		int ticks = phaseTicks(config);
		LAST_USE.put(player.getUuid(), now);
		ACTIVE.put(player.getUuid(), now + ticks);
		// Out of phase nothing in the world can carry the pilot.
		Entity mount = player.getRootVehicle();
		if (mount != player) {
			MOUNTS.put(player.getUuid(), mount.getUuid());
			player.stopRiding();
		}

		// Whatever was hunting the pilot loses them.
		Box around = player.getBoundingBox().expand(LOSE_TARGET_RANGE);
		for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, around, mob -> mob.getTarget() == player)) {
			mob.setTarget(null);
		}

		PhaseSyncPayload sync = new PhaseSyncPayload(player.getId(), ticks);
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
		ACTIVE.forEach((id, end) -> {
			if (now >= end) {
				ending.add(id);
			}
		});
		for (UUID id : ending) {
			ACTIVE.remove(id);
			UUID mount = MOUNTS.remove(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null && player.isAlive()) {
				exit(player, mount);
			}
		}
	}

	/**
	 * Back in phase: telefrag whatever human-sized thing the pilot is inside, or get pushed out of anything
	 * bigger. Whatever they were riding when they shifted - and anyone else riding it - is left alone.
	 */
	private static void exit(ServerPlayerEntity player, UUID mount) {
		if (!TitanfallConfig.get().phaseTelefrag) {
			return;
		}

		Box box = player.getBoundingBox().contract(EXIT_MARGIN, 0.05, EXIT_MARGIN);
		List<LivingEntity> inside = player.getServerWorld().getEntitiesByClass(LivingEntity.class, box, target -> target != player
				&& target.isAlive() && !target.isSpectator() && !PhaseShift.isPhased(target)
				&& !(target instanceof PlayerEntity other && other.isCreative())
				&& (mount == null || !target.getRootVehicle().getUuid().equals(mount)));
		for (LivingEntity target : inside) {
			if (target.getWidth() <= HUMAN_WIDTH && target.getHeight() <= HUMAN_HEIGHT) {
				target.damage(player.getDamageSources().create(TELEFRAG, player), target.getMaxHealth() * 100.0F + 1000.0F);
				player.getServerWorld().playSound(null, target.getX(), target.getBodyY(0.5), target.getZ(), TitanfallSounds.PHASE_TELEFRAG,
						SoundCategory.PLAYERS, 1.0F, 1.0F);
			} else {
				Vec3d away = player.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSquared() < 1.0E-4 ? player.getRotationVector().multiply(-1.0, 0.0, -1.0) : away;
				away = away.normalize().multiply(PUSH_SPEED);
				player.setVelocity(away.x, PUSH_LIFT, away.z);
				player.velocityModified = true;
			}
		}
	}

	private static void broadcastEnd(ServerPlayerEntity player) {
		PhaseSyncPayload end = new PhaseSyncPayload(player.getId(), 0);
		ServerPlayNetworking.send(player, end);
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, end);
			}
		}
	}
}
