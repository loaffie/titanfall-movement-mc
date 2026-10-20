package com.loaffie.titanfall.client.anim;

import com.loaffie.titanfall.client.particle.PilotEffects;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.movement.TitanfallController;
import com.loaffie.titanfall.movement.WallProbe;
import com.loaffie.titanfall.movement.physics.HVec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Animation state for every pilot the client can see.
 *
 * <p>Per game tick ({@link #tick}) each player's <em>targets</em> are refreshed. The local player's come
 * straight from the movement controller. Other players are not networked by this mod, so their moves
 * are recognised from what the client already knows: position deltas, the synced on-ground flag and
 * sneaking pose, and the same wall raycasts the controller uses. Per rendered frame ({@link #frame})
 * every weight is eased toward its target with a frame-time-derived lerp, so poses blend in and out
 * smoothly at any frame rate.
 *
 * <p>Slide and slide-hop are both <em>held</em> states with their own targets: "on the ground sliding"
 * and "in the air after leaving a slide". A hop chain is therefore just the two targets swapping on every
 * take-off and landing - each swap starts blending from wherever the pose currently is, so a chain of any
 * length loops seamlessly and never passes through the standing pose.
 *
 * <p>Every hop also restarts a short take-off sequence, timed from the hop itself: a push-off (legs
 * driving down, arms swinging up) peaks at ~80 ms and is gone by 200 ms, and the coil only starts drawing
 * in once the push-off is under way. Held-jump chains hop on every landing, so the sequence replays on
 * every jump: push-off, coil, touch down, push-off, coil... The wall jump is a one-shot
 * envelope {@code sin(pi * phase)}; a retrigger resumes from the current height of the envelope instead
 * of restarting it, so it never snaps.
 */
public final class PilotAnimations {
	private static final float BLEND_RATE = 12.0F;
	/** Into the air tuck quickly, out of it a little slower, so a quick hop still reads as a tuck. */
	private static final float HOP_IN_RATE = 18.0F;
	private static final float HOP_OUT_RATE = 10.0F;
	private static final float SLIDE_RATE = 14.0F;
	private static final float LAUNCH_SECONDS = 0.45F;
	/** Push-off length, seconds, and the point in it where the coil starts drawing in. */
	private static final float PUSH_SECONDS = 0.2F;
	private static final float COIL_START = 0.09F;
	/** The coil releases fast when a new hop starts, so the next push-off reads clearly. */
	private static final float COIL_RELEASE_RATE = 22.0F;
	private static final double REMOTE_SLIDE_SPEED = 4.0;
	private static final double REMOTE_WALL_SPEED = 2.5;
	private static final double REMOTE_WALL_REACH = 0.45;
	private static final int REMOTE_HOP_GROUND_TICKS = 2;
	/** Upward speed gained in one airborne tick (b/s) that can only be a double jump. */
	private static final double REMOTE_DOUBLE_JUMP_KICK = 5.0;

	private static final int AIRBORNE_FLAME_TICKS = 2;

	private static final Map<PlayerEntity, State> STATES = new WeakHashMap<>();

	/** Smoothed pose weights for one player, read by the renderer and model mixins. */
	public static final class State {
		// targets, set per tick
		boolean wallTarget;
		float wallSideTarget;
		/** Wall geometry targets (world space): the wall's outward normal, the run direction along it, and the
		 * horizontal distance from the player's centre to the surface. */
		HVec wallNormalTarget = HVec.ZERO;
		HVec wallAlongTarget = HVec.ZERO;
		float wallDistanceTarget = 0.3F;
		float wallForwardTarget;
		boolean slideTarget;
		boolean hopTarget;
		/** Consecutive on-ground ticks of a remote player, so a one-tick touchdown in a hop chain keeps the tuck. */
		int groundTicks;
		// smoothed, advanced per frame
		public float wall;
		/** +1 wall on the body's right, -1 on its left. */
		public float wallSide;
		/** Smoothed wall outward normal (world x/z, not renormalised), run direction along it, and distance. */
		public float wallNormalX;
		public float wallNormalZ;
		public float wallAlongX;
		public float wallAlongZ;
		public float wallDistance = 0.3F;
		/**
		 * The wall's surface as a fixed plane in the world: outward normal (x, z) and offset, so that
		 * {@code x * nx + z * nz - wallPlane} is any point's distance out from the wall. Unlike
		 * {@link #wallDistance} it stays where the wall is when the pilot leaves it (a wall jump), so the
		 * jumpkit can relax away from the wall as it actually recedes. Zero normal = no wall.
		 */
		public float wallPlaneNx;
		public float wallPlaneNz;
		public double wallPlane;
		/** +1 running the way the body faces, -1 running backward along the wall. */
		public float wallForward = 1.0F;
		public float slide;
		/** Wall-jump envelope 0..1..0 and the side of the wall that was left (+1 right). */
		public float launch;
		public float launchSide;
		float launchPhase = 1.0F;
		/** Slide-hop coil weight, 0..1. */
		public float hop;
		/** Slide-hop push-off weight, 0..1..0, at the start of every hop. */
		public float push;
		/** Seconds since the last hop started (advanced per frame). */
		float hopTime = 10.0F;
		boolean hopEvent;
		boolean wasGround;

		long lastFrameNanos;
		Vec3d lastPosition;
		boolean wasWall;
		boolean wasSlide;
		HVec lastWallNormal = HVec.ZERO;
		int seenWallJumpSerial = -1;
		int seenSlideHopSerial = -1;
		int seenDoubleJumpSerial = -1;
		/** Ticks this state has been updated, used to pace continuous effects. */
		int ticks;
		double lastVerticalSpeed;
		/** Consecutive ticks in free air, for the idle thruster flame. */
		int freeAirTicks;

		public boolean active() {
			return wall > 0.01F || slide > 0.01F || launch > 0.01F || hop > 0.01F || push > 0.01F;
		}
	}

	private PilotAnimations() {
	}

	/** End of every client tick: refresh pose targets for all players in the world. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			STATES.clear();
			return;
		}

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			tickPlayer(client, player);
		}
	}

	/**
	 * A stand-in player that isn't in the world's player list (a Holo Pilot decoy's look-alike), animated like
	 * any other pilot from how it moves. Called once a game tick by whoever moves it, after moving it.
	 */
	public static void tickStandIn(AbstractClientPlayerEntity player) {
		tickPlayer(MinecraftClient.getInstance(), player);
	}

	private static void tickPlayer(MinecraftClient client, AbstractClientPlayerEntity player) {
		State state = STATES.computeIfAbsent(player, ignored -> new State());
		if (!Jumpkit.isWorn(player)) {
			state.wallTarget = false;
			state.slideTarget = false;
			state.hopTarget = false;
			state.lastPosition = player.getPos();
			return;
		}

		if (player instanceof ClientPlayerEntity local && local == client.player) {
			tickLocal(local, state);
		} else {
			tickRemote(player, state);
		}
	}

	private static void tickLocal(ClientPlayerEntity player, State state) {
		TitanfallController.View view = TitanfallController.view(player);
		state.ticks++;
		if (state.seenDoubleJumpSerial >= 0 && view.doubleJumpSerial() != state.seenDoubleJumpSerial) {
			PilotEffects.doubleJump(player);
		}

		if (state.seenWallJumpSerial >= 0 && view.wallJumpSerial() != state.seenWallJumpSerial) {
			PilotEffects.wallJump(player, view.lastWallJumpNormal());
		}

		if (state.seenSlideHopSerial >= 0 && view.slideHopSerial() != state.seenSlideHopSerial) {
			PilotEffects.slideHop(player);
		}

		if (view.mode() == TitanfallController.Mode.WALL) {
			PilotEffects.wallRunTick(player, view.wallNormal(), view.wallVelocity(), state.ticks);
		} else if (view.mode() == TitanfallController.Mode.SLIDE) {
			PilotEffects.slideTick(player, state.ticks);
		} else if (thrusterAirborne(player, state)) {
			PilotEffects.airborneTick(player);
		}

		state.seenDoubleJumpSerial = view.doubleJumpSerial();
		state.wallTarget = view.mode() == TitanfallController.Mode.WALL;
		state.slideTarget = view.mode() == TitanfallController.Mode.SLIDE;
		state.hopTarget = view.slideHopAir();
		if (state.wallTarget) {
			setWallTargets(player, state, view.wallNormal(), view.wallVelocity());
		}

		if (state.seenWallJumpSerial >= 0 && view.wallJumpSerial() != state.seenWallJumpSerial) {
			startLaunch(player, state, view.lastWallJumpNormal());
		}

		if (state.seenSlideHopSerial >= 0 && view.slideHopSerial() != state.seenSlideHopSerial) {
			state.hopEvent = true;
		}

		state.seenWallJumpSerial = view.wallJumpSerial();
		state.seenSlideHopSerial = view.slideHopSerial();
		state.lastPosition = player.getPos();
	}

	/** Recognises another pilot's moves from the client-side view of their motion. */
	private static void tickRemote(AbstractClientPlayerEntity player, State state) {
		Vec3d position = player.getPos();
		Vec3d velocity = state.lastPosition == null ? Vec3d.ZERO : position.subtract(state.lastPosition).multiply(20.0);
		state.lastPosition = position;
		state.ticks++;
		HVec horizontal = new HVec(velocity.x, velocity.z);
		double speed = horizontal.length();

		// The server syncs the SLIDING pose, which is exact; the speed heuristic covers servers that have
		// not yet applied it.
		boolean slide = player.getPose() == EntityPose.SLIDING
				|| (player.isOnGround() && player.isInSneakingPose() && speed > REMOTE_SLIDE_SPEED);
		WallProbe.Wall wall = null;
		if (!player.isOnGround() && speed > REMOTE_WALL_SPEED && velocity.y > -8.0) {
			wall = WallProbe.find(player, horizontal, REMOTE_WALL_REACH, null);
		}

		boolean onWall = wall != null && Math.abs(horizontal.normalize().dot(wall.normal())) < 0.7;
		state.wallTarget = onWall;
		state.slideTarget = slide;
		if (onWall) {
			setWallTargets(player, state, wall.normal(), horizontal.rejectFrom(wall.normal()));
			state.lastWallNormal = wall.normal();
			PilotEffects.wallRunTick(player, wall.normal(), horizontal.rejectFrom(wall.normal()), state.ticks);
		} else if (state.wasWall && velocity.y > 2.0) {
			startLaunch(player, state, state.lastWallNormal);
			PilotEffects.wallJump(player, state.lastWallNormal);
		} else if (!player.isOnGround() && !state.wasGround && velocity.y - state.lastVerticalSpeed > REMOTE_DOUBLE_JUMP_KICK) {
			// A sudden upward kick in mid-air, away from any wall: another pilot's double jump.
			PilotEffects.doubleJump(player);
		}

		if (slide) {
			PilotEffects.slideTick(player, state.ticks);
		} else if (!onWall && thrusterAirborne(player, state)) {
			PilotEffects.airborneTick(player);
		}

		state.lastVerticalSpeed = velocity.y;

		// Air after a slide (hop or slid off an edge) holds the tuck until the pilot lands or grabs a wall.
		// A held-jump chain touches down for a tick or two between hops; only a real stop ends the tuck.
		state.groundTicks = player.isOnGround() ? state.groundTicks + 1 : 0;
		boolean leftGroundRising = !player.isOnGround() && velocity.y > 0.0 && (state.wasGround || state.wasSlide);
		if (leftGroundRising && (state.wasSlide || state.hopTarget)) {
			// A hop out of a slide, or the next hop of a running chain.
			state.hopTarget = true;
			state.hopEvent = true;
			PilotEffects.slideHop(player);
		} else if (slide || onWall || state.groundTicks > REMOTE_HOP_GROUND_TICKS) {
			state.hopTarget = false;
		} else if (state.wasSlide && !player.isOnGround()) {
			state.hopTarget = true;
		}

		state.wasGround = player.isOnGround();

		state.wasWall = onWall;
		state.wasSlide = slide;
	}

	/**
	 * In free air the way a jumpkit is: off the ground and not swimming, in lava, flying, gliding, climbing,
	 * riding or spectating. The thrusters idle with a small flame then.
	 */
	private static boolean thrusterAirborne(PlayerEntity player, State state) {
		boolean free = !player.isOnGround() && !player.isTouchingWater() && !player.isInLava() && !player.getAbilities().flying
				&& !player.isFallFlying() && !player.isSpectator() && !player.hasVehicle() && !player.isClimbing();
		state.freeAirTicks = free ? state.freeAirTicks + 1 : 0;
		// Two ticks, so stepping down a block edge does not blip the flame on and off.
		return state.freeAirTicks >= AIRBORNE_FLAME_TICKS;
	}

	private static void setWallTargets(PlayerEntity player, State state, HVec normal, HVec wallVelocity) {
		HVec right = HVec.rightFromYaw(player.bodyYaw);
		HVec forward = HVec.forwardFromYaw(player.bodyYaw);
		state.wallSideTarget = -normal.dot(right) >= 0.0 ? 1.0F : -1.0F;
		if (wallVelocity.length() > 0.5) {
			state.wallForwardTarget = wallVelocity.dot(forward) >= 0.0 ? 1.0F : -1.0F;
			state.wallAlongTarget = wallVelocity.normalize();
		} else if (state.wallAlongTarget.length() < 0.5) {
			state.wallAlongTarget = forward.rejectFrom(normal).normalize();
		}

		state.wallNormalTarget = normal;
		state.wallDistanceTarget = (float) wallDistance(player, normal);
		state.wallPlaneNx = (float) normal.x();
		state.wallPlaneNz = (float) normal.z();
		state.wallPlane = player.getX() * normal.x() + player.getZ() * normal.z() - state.wallDistanceTarget;
	}

	/**
	 * Horizontal distance from the player's centre line to the wall line, from the same wall fit the run
	 * uses: on a diagonal row of blocks that is the line through the outermost corners, so the wall-side
	 * arm lies along the wall instead of reaching into the notches. Falls back to the hitbox half-width.
	 */
	private static double wallDistance(PlayerEntity player, HVec normal) {
		WallProbe.Wall wall = WallProbe.scan(player, 1.0).at(Math.atan2(normal.z(), normal.x()));
		if (wall == null) {
			return player.getWidth() / 2.0;
		}

		return Math.max(player.getX() * wall.normal().x() + player.getZ() * wall.normal().z() - wall.plane(), 0.05);
	}


	private static void startLaunch(PlayerEntity player, State state, HVec wallNormal) {
		HVec right = HVec.rightFromYaw(player.bodyYaw);
		state.launchSide = -wallNormal.dot(right) >= 0.0 ? 1.0F : -1.0F;
		// Resume on the rising half at the envelope's current height, so a retrigger never snaps to zero.
		state.launchPhase = (float) (Math.asin(Math.min(1.0F, Math.max(0.0F, state.launch))) / Math.PI);
	}

	/**
	 * Advances the smoothing for one player and returns its state, or null when the player has no pilot
	 * pose to show. Called once per rendered frame per player, from the renderer.
	 */
	public static State frame(PlayerEntity player) {
		State state = STATES.get(player);
		if (state == null || !TitanfallConfig.get().animationsEnabled) {
			return null;
		}

		long now = Util.getMeasuringTimeNano();
		float dt = state.lastFrameNanos == 0L ? 0.0F : Math.min((now - state.lastFrameNanos) / 1.0E9F, 0.1F);
		state.lastFrameNanos = now;
		float alpha = dt <= 0.0F ? 0.0F : 1.0F - (float) Math.exp(-BLEND_RATE * dt);

		state.wall = lerp(alpha, state.wall, state.wallTarget ? 1.0F : 0.0F);
		state.slide = lerp(rate(SLIDE_RATE, dt), state.slide, state.slideTarget ? 1.0F : 0.0F);
		if (state.hopEvent) {
			state.hopEvent = false;
			state.hopTime = 0.0F;
		}

		state.hopTime = Math.min(state.hopTime + dt, 10.0F);
		// Push-off: a smooth 0 -> 1 -> 0 bump over PUSH_SECONDS, peaking early.
		float p = state.hopTime / PUSH_SECONDS;
		state.push = p >= 1.0F ? 0.0F : (float) Math.sin(Math.PI * Math.pow(p, 0.75));
		// Coil: held while in the air after a slide, but only drawn in once the push-off is under way; at the
		// start of each new hop it lets go quickly so the legs can drive down again.
		boolean coilWanted = state.hopTarget && state.hopTime >= COIL_START;
		float coilRate = coilWanted ? HOP_IN_RATE : (state.hopTime < COIL_START ? COIL_RELEASE_RATE : HOP_OUT_RATE);
		state.hop = lerp(rate(coilRate, dt), state.hop, coilWanted ? 1.0F : 0.0F);
		if (state.wallTarget) {
			state.wallSide = lerp(alpha, state.wallSide, state.wallSideTarget);
			// The first frame of a run snaps the geometry; afterwards it glides (corner wraps, reversals).
			float geometry = state.wallNormalX == 0.0F && state.wallNormalZ == 0.0F ? 1.0F : alpha;
			state.wallNormalX = lerp(geometry, state.wallNormalX, (float) state.wallNormalTarget.x());
			state.wallNormalZ = lerp(geometry, state.wallNormalZ, (float) state.wallNormalTarget.z());
			state.wallAlongX = lerp(geometry, state.wallAlongX, (float) state.wallAlongTarget.x());
			state.wallAlongZ = lerp(geometry, state.wallAlongZ, (float) state.wallAlongTarget.z());
			state.wallDistance = lerp(geometry, state.wallDistance, state.wallDistanceTarget);
		} else if (state.wall < 0.01F) {
			state.wallNormalX = 0.0F;
			state.wallNormalZ = 0.0F;
			state.wallPlaneNx = 0.0F;
			state.wallPlaneNz = 0.0F;
		}

		// Reversing along the wall swings the pose from leaning into the run to leaning back over the same
		// blend rate, so a direction change mid-run is one continuous motion.
		state.wallForward = lerp(alpha, state.wallForward, state.wallForwardTarget == 0.0F ? 1.0F : state.wallForwardTarget);

		state.launchPhase = Math.min(1.0F, state.launchPhase + dt / LAUNCH_SECONDS);
		state.launch = (float) Math.sin(Math.PI * state.launchPhase);
		return state.active() ? state : null;
	}

	/**
	 * The player's animation state as last advanced by {@link #frame} (without advancing it), or null when
	 * animations are off or the player has none.
	 */
	public static State current(PlayerEntity player) {
		return TitanfallConfig.get().animationsEnabled ? STATES.get(player) : null;
	}

	private static float rate(float perSecond, float dt) {
		return dt <= 0.0F ? 0.0F : 1.0F - (float) Math.exp(-perSecond * dt);
	}

	private static float lerp(float alpha, float from, float to) {
		return from + (to - from) * alpha;
	}
}
