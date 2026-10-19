package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleEffects;
import com.loaffie.titanfall.client.grapple.GrappleLine;
import com.loaffie.titanfall.client.grapple.GrappleLines;
import com.loaffie.titanfall.client.grapple.GrappleSounds;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.grapple.GrapplePayload;
import com.loaffie.titanfall.TitanfallMovement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The local pilot's Grapple: the key, the charge meter, the hook's flight, and the rules for when the line
 * holds and when it lets go. The pull itself is a movement mode of {@link TitanfallController}, which asks
 * this class where the hook is.
 *
 * <h2>As in Titanfall 2</h2>
 * The hook flies out along the crosshair at 4400 u/s and reaches its full 1100 u (28 blocks) in a quarter
 * of a second; a shot that finds nothing reels back in and costs nothing. A hook that bites spends a charge
 * and reels the pilot in. The line lets go when the pilot arrives, jumps, presses the key again, loses
 * sight of the hook, is flung away from it, or hangs about too slowly for too long; after it lets go the
 * meter waits three seconds and then refills. Values are in {@link TitanfallConfig}.
 */
public final class GrappleController {
	/** Blocks the hook will not bite (the barrier, by default). Data pack tag. */
	public static final TagKey<net.minecraft.block.Block> DISALLOWED = TagKey.of(RegistryKeys.BLOCK,
			Identifier.of(TitanfallMovement.MOD_ID, "grapple_disallowed"));
	/** Ticks the hook may be out of sight before the line lets go: a frame of grace for corners swept past. */
	private static final int SIGHT_GRACE_TICKS = 2;
	/** Lines longer than this past the range (an entity running off) snap. */
	private static final double LEASH_SLACK = 8.0;

	private static ClientPlayerEntity trackedPlayer;
	/** Meter, 0..1. One charge is {@code 1 / grappleCharges} of it. */
	private static double meter = 1.0;
	private static int regenDelayTicks;
	private static boolean targetInRange;
	private static boolean fireQueued;
	private static boolean releaseQueued;
	private static Vec3d fireOrigin = Vec3d.ZERO;
	/** Distance from the pilot to the hook when it bit, for the range-dependent pull speed. */
	private static double attachDistance;
	private static boolean justAttached;
	private static int outOfSightTicks;
	private static double lowSpeedSeconds;

	private GrappleController() {
	}

	// ------------------------------------------------------------------------------------------------
	// Input and meter (client tick)
	// ------------------------------------------------------------------------------------------------

	/** The Grapple key was pressed: fire, or let go if the line is already out. */
	public static void onKeyPressed(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}

		GrappleLine line = GrappleLines.of(player);
		if (line.phase == GrappleLine.Phase.ATTACHED || line.phase == GrappleLine.Phase.FLYING) {
			releaseQueued = true;
		} else if (line.phase == GrappleLine.Phase.NONE) {
			fireQueued = true;
		}
	}

	/** End of every client tick: refill the meter, drop state between worlds. */
	public static void clientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player != trackedPlayer) {
			reset();
			trackedPlayer = player;
		}

		if (player == null) {
			return;
		}

		TitanfallConfig config = TitanfallConfig.get();
		GrappleLine line = GrappleLines.of(player);
		targetInRange = line.phase == GrappleLine.Phase.NONE && config.grappleHud && Grapple.isWorn(player) && aimedInRange(player, config);
		if (line.phase == GrappleLine.Phase.ATTACHED || line.phase == GrappleLine.Phase.FLYING) {
			return;
		}

		if (regenDelayTicks > 0) {
			regenDelayTicks--;
			return;
		}

		double charge = charge(config);
		if (meter < 1.0) {
			double before = meter;
			double rate = config.grappleCooldownSeconds <= 0.0 ? 1.0 : charge / (config.grappleCooldownSeconds * 20.0);
			meter = Math.min(1.0, meter + rate);
			// A charge just became available.
			if (Math.floor(before / charge + 1.0E-6) < Math.floor(meter / charge + 1.0E-6)) {
				GrappleSounds.ready();
			}
		}

		if (!Grapple.isWorn(player)) {
			fireQueued = false;
		}
	}

	/**
	 * Start of every client tick. The line is normally checked from the movement tick, but that does not run
	 * while riding (or in a few other states), so a line that can no longer be held is dropped here too.
	 */
	public static void preClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || player != trackedPlayer) {
			return;
		}

		GrappleLine line = GrappleLines.of(player);
		if ((line.phase == GrappleLine.Phase.ATTACHED || line.phase == GrappleLine.Phase.FLYING) && !canGrapple(player)) {
			letGo(player, line);
		}
	}

	/** Whether something the hook could bite is under the crosshair within range (for the HUD marker). */
	private static boolean aimedInRange(ClientPlayerEntity player, TitanfallConfig config) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(config.grappleRange));
		BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (block.getType() == HitResult.Type.BLOCK && !player.getWorld().getBlockState(block.getBlockPos()).isIn(DISALLOWED)) {
			return true;
		}

		Vec3d reach = block.getType() == HitResult.Type.BLOCK ? block.getPos() : end;
		return ProjectileUtil.raycast(player, eye, reach, new Box(eye, reach).expand(1.0),
				target -> target instanceof LivingEntity && target.isAlive() && !target.isSpectator() && target.canHit(),
				eye.squaredDistanceTo(reach)) != null;
	}

	/** Something grapple-able is under the crosshair, in range (refreshed every tick while the line is in). */
	public static boolean targetInRange() {
		return targetInRange;
	}

	private static double charge(TitanfallConfig config) {
		return 1.0 / Math.max(1, config.grappleCharges);
	}

	/** Meter for the HUD, 0..1. */
	public static double meter() {
		return meter;
	}

	public static boolean ready() {
		return meter >= charge(TitanfallConfig.get()) - 1.0E-6;
	}

	// ------------------------------------------------------------------------------------------------
	// Movement tick (head of travel)
	// ------------------------------------------------------------------------------------------------

	/**
	 * Head of the local player's {@code travel}, before the movement controller runs: fires a queued shot,
	 * flies the hook, and checks the line. Returns true when the hook is in and the pilot is being reeled.
	 */
	static boolean tick(ClientPlayerEntity player) {
		if (player != trackedPlayer) {
			reset();
			trackedPlayer = player;
		}

		GrappleLine line = GrappleLines.of(player);
		TitanfallConfig config = TitanfallConfig.get();
		boolean able = canGrapple(player);
		boolean out = line.phase == GrappleLine.Phase.ATTACHED || line.phase == GrappleLine.Phase.FLYING;
		if (out && (releaseQueued || !able)) {
			if (line.phase == GrappleLine.Phase.FLYING) {
				letGo(player, line);
			} else if (pendingRelease == Release.NONE) {
				// Hooked: the movement controller lets go this tick - with the release kick for a key press.
				pendingRelease = able ? Release.LET_GO : Release.SNAP;
			}
		}

		releaseQueued = false;
		if (fireQueued) {
			fireQueued = false;
			if (able && line.phase == GrappleLine.Phase.NONE) {
				if (ready()) {
					fire(player, line);
				} else {
					GrappleSounds.denied();
				}
			}
		}

		if (line.phase == GrappleLine.Phase.FLYING) {
			fly(player, line, config);
		}

		if (line.phase != GrappleLine.Phase.ATTACHED) {
			return false;
		}

		if (pendingRelease == Release.NONE) {
			checkLine(player, line, config);
		}

		return true;
	}

	/** Why the line is about to let go, decided here and carried out by the movement controller this tick. */
	enum Release {
		NONE,
		/** Let go on purpose or arrived: the Titanfall release kick (upward boost, speed trimmed). */
		LET_GO,
		/** Lost: out of sight, snapped, too slow, target gone. The pilot just keeps their motion. */
		SNAP
	}

	static Release pendingRelease = Release.NONE;

	private static boolean canGrapple(ClientPlayerEntity player) {
		return Grapple.isWorn(player)
				&& !player.isSpectator()
				&& !player.isDead()
				&& !player.hasVehicle()
				&& !player.isSleeping()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isUsingRiptide()
				&& !player.noClip;
	}

	private static void fire(ClientPlayerEntity player, GrappleLine line) {
		fireOrigin = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0F);
		line.fly(fireOrigin, direction);
		outOfSightTicks = 0;
		lowSpeedSeconds = 0.0;
		GrappleSounds.fire(player);
		GrappleEffects.fire(player, line);
		send(new GrapplePayload(GrapplePayload.FIRE, fireOrigin, direction, -1));
	}

	/** One tick of hook flight: sweep the segment it covers for blocks and entities, and bite the nearest. */
	private static void fly(ClientPlayerEntity player, GrappleLine line, TitanfallConfig config) {
		World world = player.getWorld();
		Vec3d from = line.hook;
		double travelled = from.distanceTo(fireOrigin);
		double step = Math.min(config.grappleHookSpeed / 20.0, config.grappleRange - travelled);
		Vec3d to = from.add(line.dir.multiply(Math.max(step, 0.0)));
		line.prevHook = from;

		BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.BLOCK ? block.getPos() : to;
		EntityHitResult entity = ProjectileUtil.raycast(player, from, end, new Box(from, end).expand(1.0),
				target -> target instanceof LivingEntity && target.isAlive() && !target.isSpectator() && target.canHit() && target != player,
				from.squaredDistanceTo(end));

		if (entity != null) {
			line.hook = entity.getPos();
			attach(player, line, entity.getPos(), null, entity.getEntity());
		} else if (block.getType() == HitResult.Type.BLOCK) {
			BlockState state = world.getBlockState(block.getBlockPos());
			line.hook = block.getPos();
			if (state.isIn(DISALLOWED)) {
				letGo(player, line);
			} else {
				Vec3d normal = Vec3d.of(block.getSide().getVector());
				attach(player, line, block.getPos(), normal, null);
			}
		} else {
			line.hook = to;
			if (step <= 1.0E-3 || travelled + step >= config.grappleRange - 1.0E-3) {
				// Nothing in range: the hook reels back in and the charge is kept.
				letGo(player, line);
			}
		}
	}

	private static void attach(ClientPlayerEntity player, GrappleLine line, Vec3d at, Vec3d normal, Entity entity) {
		meter = Math.max(0.0, meter - charge(TitanfallConfig.get()));
		attachDistance = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0).distanceTo(at);
		justAttached = true;
		outOfSightTicks = 0;
		lowSpeedSeconds = 0.0;
		pendingRelease = Release.NONE;
		GrappleLines.bite(player, line, at, normal, entity);
		send(new GrapplePayload(GrapplePayload.ATTACH, at, normal == null ? Vec3d.ZERO : normal, entity == null ? -1 : entity.getId()));
	}

	/**
	 * Per-tick checks on a hooked line that do not depend on the pull: the hook must stay in sight and its
	 * entity must still exist.
	 */
	private static void checkLine(ClientPlayerEntity player, GrappleLine line, TitanfallConfig config) {
		Entity entity = line.entityId >= 0 ? line.entity(player.getWorld()) : null;
		if (line.entityId >= 0 && (entity == null || !entity.isAlive())) {
			pendingRelease = Release.SNAP;
			return;
		}

		Vec3d anchor = anchor(player);
		Vec3d eye = player.getEyePos();
		if (eye.distanceTo(anchor) > config.grappleRange + LEASH_SLACK) {
			pendingRelease = Release.SNAP;
			return;
		}

		// Line of sight: aim a hair off the surface the hook is in, so the block it bit does not count.
		Vec3d target = line.normal != null ? anchor.add(line.normal.multiply(0.05)) : anchor;
		BlockHitResult sight = player.getWorld().raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		boolean blocked = sight.getType() == HitResult.Type.BLOCK && sight.getPos().squaredDistanceTo(eye) < eye.squaredDistanceTo(target) - 0.04;
		outOfSightTicks = blocked ? outOfSightTicks + 1 : 0;
		if (outOfSightTicks > SIGHT_GRACE_TICKS) {
			pendingRelease = Release.SNAP;
		}
	}

	/**
	 * The slow-line rule, from the movement controller with this tick's speed: slower than
	 * {@code grappleLowSpeed} for too long - 0.7 s on the ground, 1.2 s against a wall, 1.5 s in the air -
	 * and the line lets go.
	 */
	static boolean tooSlow(ClientPlayerEntity player, double speed, TitanfallConfig config) {
		if (speed >= config.grappleLowSpeed) {
			lowSpeedSeconds = 0.0;
			return false;
		}

		lowSpeedSeconds += 0.05;
		double limit = player.isOnGround() ? config.grappleLowSpeedGroundTime
				: player.horizontalCollision ? config.grappleLowSpeedWallTime : config.grappleLowSpeedAirTime;
		return lowSpeedSeconds >= limit;
	}

	/** Where the hook is (its entity's current position for an entity), for the pull. */
	static Vec3d anchor(ClientPlayerEntity player) {
		return GrappleLines.of(player).position(player.getWorld(), 1.0F);
	}

	static double attachDistance() {
		return attachDistance;
	}

	/** True once, on the first pull tick after the hook bit: the impact kick is applied then. */
	static boolean consumeJustAttached() {
		boolean value = justAttached;
		justAttached = false;
		return value;
	}

	/** The line lets go (or a miss reels back in). Starts the meter's regen delay. */
	static void letGo(ClientPlayerEntity player, GrappleLine line) {
		if (!line.active() || line.phase == GrappleLine.Phase.RETRACTING) {
			return;
		}

		line.retract(player.getWorld());
		pendingRelease = Release.NONE;
		justAttached = false;
		regenDelayTicks = (int) Math.round(TitanfallConfig.get().grappleRegenDelaySeconds * 20.0);
		GrappleSounds.release(player);
		send(GrapplePayload.release());
	}

	public static boolean isAttached(ClientPlayerEntity player) {
		GrappleLine line = GrappleLines.active(player);
		return line != null && line.phase == GrappleLine.Phase.ATTACHED;
	}

	private static void send(GrapplePayload payload) {
		if (ClientPlayNetworking.canSend(GrapplePayload.ID)) {
			ClientPlayNetworking.send(payload);
		}
	}

	private static void reset() {
		meter = 1.0;
		regenDelayTicks = 0;
		fireQueued = false;
		releaseQueued = false;
		justAttached = false;
		outOfSightTicks = 0;
		lowSpeedSeconds = 0.0;
		pendingRelease = Release.NONE;
		GrappleSounds.stopReel();
	}
}
