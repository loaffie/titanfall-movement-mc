package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.client.grapple.GrappleLines;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.movement.physics.HVec;
import com.loaffie.titanfall.movement.physics.SourceMovement;
import com.loaffie.titanfall.slide.SlidePayload;
import com.loaffie.titanfall.slide.SlideState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Titanfall 2 pilot movement on top of Minecraft's player physics.
 *
 * <h2>Where it runs</h2>
 * The controller is driven from inside {@code LivingEntity.travel} for the local player only:
 * {@link #preTravel} runs at the head of {@code travel} - after this tick's input has been read and
 * after vanilla has (or has not) jumped, but <em>before</em> the player is moved - and {@link #postTravel}
 * runs at its tail, after the move and vanilla's drag. Writing the velocity before the move means the
 * value computed from this tick's keys and camera is exactly the displacement applied this tick: no
 * tick of input latency, and no vanilla friction ever touches the modelled speed.
 *
 * <h2>Ownership</h2>
 * In {@link Mode#NONE} the player walks with untouched vanilla physics. In every other mode the
 * controller owns the horizontal velocity: {@link #momentum} (blocks/s) is the authority, vanilla's
 * WASD input is zeroed for the tick, and the velocity vanilla is left holding after its drag is
 * ignored - except for differences nobody here caused (knockback, explosions, entity pushing), which
 * are adopted as external impulses. Collisions are applied Source-style in {@link #postTravel}: a
 * blocked axis loses its momentum ({@code ClipVelocity}), so hitting a wall cannot store speed.
 *
 * <h2>Jumpkit</h2>
 * Every tick starts with {@link Jumpkit#abilitiesActive}. Without the kit the controller releases the
 * player to vanilla on the spot and does nothing else, so taking the kit off mid-air hands control
 * back cleanly.
 */
public final class TitanfallController {
	private static final double TICKS_PER_SECOND = 20.0;
	private static final double DT = 1.0 / TICKS_PER_SECOND;
	/** Horizontal velocity differences below this (blocks/tick) are rounding, not an outside push. */
	private static final double IMPULSE_THRESHOLD = 0.01;
	/** A change of vertical speed this large (blocks/tick) during a wall run means something hit the pilot. */
	private static final double VERTICAL_KNOCK_THRESHOLD = 0.1;
	/** Displacements above this (blocks/s) are teleports, respawns or server corrections. */
	private static final double TELEPORT_SPEED = 100.0;
	/** Vanilla's horizontal air drag, applied after every airborne move. */
	private static final double AIR_DRAG = 0.91;
	/** Default block slipperiness: slides and skids on ordinary blocks use the configured friction unscaled. */
	private static final double NORMAL_SLIPPERINESS = 0.6;
	private static final double LAND_SOUND_HEIGHT = 1.0;
	/** Height of a crouching player; a slide cannot end into anything lower. */
	private static final double CROUCH_HEIGHT = 1.5;
	/** Airborne ticks before any wall can be latched. */
	private static final int MIN_AIR_TICKS_TO_LATCH = 3;
	/** Clearance kept between the hitbox and the wall line while running, blocks. */
	private static final double WALL_STANDOFF = 0.03;
	/** Most the run moves toward or away from the wall per tick to hold that clearance, blocks. */
	private static final double WALL_PULL = 0.1;
	/** Fastest the run turns to follow a wall that changes direction, radians per tick (20 degrees). */
	private static final double WALL_MAX_TURN = Math.toRadians(20.0);

	public enum Mode {
		/** Vanilla walking. The controller only watches. */
		NONE,
		/** Airborne: Source air acceleration, no drag, double jump, lurch. */
		AIR,
		/** Wall running. */
		WALL,
		/** Sliding: momentum kept, low slide friction. */
		SLIDE,
		/** On the ground faster than vanilla walks, without a slide: Source ground friction until normal speed. */
		SKID,
		/**
		 * Reeled in by the Grapple. The controller owns the full 3D velocity: the line pulls toward the hook,
		 * the pilot steers with strong air control, and gravity is partly carried by the line. Works with or
		 * without the jumpkit - the Grapple is its own piece of kit.
		 */
		GRAPPLE
	}

	private static ClientPlayerEntity trackedPlayer;
	private static Mode mode = Mode.NONE;
	private static HVec momentum = HVec.ZERO;

	private static Vec3d lastPosition;
	private static float lastYaw;
	private static boolean hasLastYaw;
	private static boolean jumpWasDown;
	private static int lastKeyMask;
	private static boolean vanillaJumped;
	private static Vec3d jumpVelocityBefore;

	/** Horizontal velocity (blocks/tick) vanilla was left with after the last owned tick. */
	private static HVec lastPostVelocity = HVec.ZERO;
	private static double lastPostVerticalVelocity;
	/** What preTravel wrote this tick, blocks/tick, for collision clipping. */
	private static double writtenX;
	private static double writtenZ;
	private static boolean ownedThisTick;

	/**
	 * The Stim's speed boost for this tick (1 without one). Walking and sprinting get it from the server's
	 * attribute modifier; the speeds this controller sets itself - take-off, the skid hand-back, the wall run's
	 * cap and entry speed - are scaled by it here.
	 */
	private static double stimScale = 1.0;

	private static boolean doubleJumpAvailable;
	private static double peakY;

	private static int lurchTicksLeft;
	private static boolean lurchUsed;

	private static int slideBoostCooldown;

	private static WallProbe.Wall wall;
	private static WallProbe.Wall lastWall;
	private static WallProbe.Wall lastBumpWall;
	/** Unit vector along the current wall; the run's velocity is {@code wallTangent * wallSpeed}. */
	private static HVec wallTangent = HVec.ZERO;
	/** Signed speed along {@link #wallTangent}, b/s: the sign is the run direction, so reversing is continuous. */
	private static double wallSpeed;
	private static int wallAwayTicks;
	/** Last slide flag sent to the server. */
	private static boolean slideFlagSent;
	/** Airborne after leaving a slide (slide hop or sliding off an edge): drives the tuck animation. */
	private static boolean slideHopAir;
	/** Ticks since the pilot last stood on the ground. */
	private static int airTicks;
	/** Walls run on since the last landing: re-runs give no double jump back and a weaker wall jump. */
	private static final java.util.List<WallProbe.Wall> wallsSinceLanding = new java.util.ArrayList<>();
	/** Bumped on every wall jump / slide hop so the camera and animations can react to the event. */
	private static int wallJumpSerial;
	private static HVec lastWallJumpNormal = HVec.ZERO;
	private static int slideHopSerial;
	private static int doubleJumpSerial;
	private static double wallVerticalSpeed;
	private static int wallTicks;
	private static int wallGraceTicks;
	private static int wallCooldown;
	private static int sameWallCooldown;

	private TitanfallController() {
	}

	// ------------------------------------------------------------------------------------------------
	// Hooks
	// ------------------------------------------------------------------------------------------------

	/**
	 * Head of {@code travel}. Returns the movement input vanilla should use this tick: unchanged while
	 * walking normally, zero while the controller owns the horizontal velocity.
	 */
	public static Vec3d preTravel(ClientPlayerEntity player, Vec3d movementInput) {
		Vec3d result = tickMovement(player, movementInput);
		syncSlide(player);
		return result;
	}

	/**
	 * Keeps the player's slide flag (and so the 0.8-block {@code SLIDING} pose and hitbox) in step with the
	 * controller, locally and on the server. The flag is set during {@code travel}; vanilla re-evaluates
	 * the pose at the end of the same player tick, and the packet is sent before this tick's movement
	 * packet, so the server applies the smaller box to the same move the client made with it.
	 */
	private static void syncSlide(ClientPlayerEntity player) {
		boolean sliding = mode == Mode.SLIDE && ClientPlayNetworking.canSend(SlidePayload.ID);
		if (sliding != slideFlagSent || SlideState.isSliding(player) != sliding) {
			SlideState.setSliding(player, sliding);
			if (ClientPlayNetworking.canSend(SlidePayload.ID)) {
				ClientPlayNetworking.send(new SlidePayload(sliding));
			}

			slideFlagSent = sliding;
		}
	}

	private static Vec3d tickMovement(ClientPlayerEntity player, Vec3d movementInput) {
		if (player != trackedPlayer) {
			reset();
			trackedPlayer = player;
		}

		TitanfallConfig config = TitanfallConfig.get();
		ownedThisTick = false;
		stimScale = StimController.speedScale(player);

		Vec3d position = player.getPos();
		HVec displacement = lastPosition == null
				? HVec.ZERO
				: new HVec(position.x - lastPosition.x, position.z - lastPosition.z).scale(TICKS_PER_SECOND);
		boolean teleported = displacement.length() > TELEPORT_SPEED
				|| (lastPosition != null && Math.abs(position.y - lastPosition.y) * TICKS_PER_SECOND > TELEPORT_SPEED * 1.5);
		lastPosition = position;

		Input input = player.input;
		boolean jumpDown = input.jumping;
		boolean jumpPressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		int keyMask = keyMask(input);
		int newKeys = keyMask & ~lastKeyMask;
		lastKeyMask = keyMask;

		float yaw = player.getYaw();
		// Wrapped, so a server rotation update (or an accumulated yaw of thousands of degrees) never
		// makes the interpolation sweep a full circle.
		float previousYaw = hasLastYaw ? yaw - MathHelper.wrapDegrees(yaw - lastYaw) : yaw;
		lastYaw = yaw;
		hasLastYaw = true;

		boolean jumpedFromGround = vanillaJumped;
		vanillaJumped = false;

		tickCooldowns();

		if (GrappleController.tick(player) && !teleported) {
			if (mode == Mode.NONE) {
				momentum = displacement;
			} else {
				adoptExternalImpulse(player);
			}

			grappleTick(player, config, input, jumpPressed, previousYaw, yaw);
			if (mode == Mode.NONE) {
				return movementInput;
			}

			ownedThisTick = true;
			Vec3d written = player.getVelocity();
			writtenX = written.x;
			writtenZ = written.z;
			return Vec3d.ZERO;
		} else if (mode == Mode.GRAPPLE) {
			// The line went without the controller letting go (a teleport, or it was dropped elsewhere).
			mode = Mode.AIR;
			if (teleported) {
				GrappleController.letGo(player, GrappleLines.of(player));
			}
		}

		if (!pilotActive(player) || teleported) {
			release(player);
			return movementInput;
		}

		if (mode != Mode.NONE) {
			adoptExternalImpulse(player);
		}

		if (mode == Mode.WALL && Math.abs(player.getVelocity().y - lastPostVerticalVelocity) > VERTICAL_KNOCK_THRESHOLD) {
			// Knocked off the wall by a hit or an explosion: keep the push, drop the run.
			endWallRun();
		}

		if (player.isOnGround()) {
			groundTick(player, config, input, displacement, jumpedFromGround, yaw);
		} else {
			airTick(player, config, input, displacement, jumpPressed, newKeys, previousYaw, yaw);
		}

		if (mode == Mode.NONE) {
			return movementInput;
		}

		ownedThisTick = true;
		Vec3d written = player.getVelocity();
		writtenX = written.x;
		writtenZ = written.z;
		return Vec3d.ZERO;
	}

	/** Tail of {@code travel}: clip momentum against what the move collided with, remember vanilla's result. */
	public static void postTravel(ClientPlayerEntity player) {
		if (player != trackedPlayer) {
			return;
		}

		Vec3d velocity = player.getVelocity();
		if (ownedThisTick && mode != Mode.NONE) {
			boolean blockedX = velocity.x == 0.0 && Math.abs(writtenX) > 1.0E-7;
			boolean blockedZ = velocity.z == 0.0 && Math.abs(writtenZ) > 1.0E-7;
			if (player.horizontalCollision) {
				// Source ClipVelocity on axis-aligned blocks: the blocked axis loses its momentum.
				momentum = new HVec(blockedX ? 0.0 : momentum.x(), blockedZ ? 0.0 : momentum.z());
				if (mode == Mode.WALL) {
					// Ran into a corner or an obstacle on the wall: the blocked part of the run is gone, but the
					// run itself survives - the pilot can turn round with the opposite key.
					wallSpeed = momentum.dot(wallTangent);
				}
			} else if (blockedX && blockedZ) {
				// Velocity zeroed without a collision: cobwebs, berry bushes and powder snow reset it inside
				// the move. Let vanilla handle those blocks instead of carrying speed through them.
				momentum = HVec.ZERO;
			}
		}

		lastPostVelocity = new HVec(velocity.x, velocity.z);
		lastPostVerticalVelocity = velocity.y;
	}

	/** Head of {@code LivingEntity.jump}: remember the velocity so vanilla's sprint-jump kick can be undone. */
	public static void beforeVanillaJump(ClientPlayerEntity player) {
		jumpVelocityBefore = pilotActive(player) ? player.getVelocity() : null;
	}

	/**
	 * Tail of {@code LivingEntity.jump}. Vanilla adds 0.2 blocks/tick toward the facing direction when
	 * sprinting. In a model without drag that kick would compound on every hop, so it is removed and the
	 * controller decides the take-off speed itself. The vertical jump impulse (potions, honey and all)
	 * is left exactly as vanilla computed it.
	 */
	public static void afterVanillaJump(ClientPlayerEntity player) {
		if (jumpVelocityBefore == null) {
			return;
		}

		Vec3d velocity = player.getVelocity();
		// jump() returns early without touching the velocity when the jump power is ~0 (jump strength
		// attribute at 0, for example); only a real jump changes the vertical speed.
		boolean jumped = velocity.y != jumpVelocityBefore.y;
		player.setVelocity(jumpVelocityBefore.x, velocity.y, jumpVelocityBefore.z);
		jumpVelocityBefore = null;
		vanillaJumped = jumped;
	}

	/**
	 * The server set the local player's velocity (an entity velocity packet: melee knockback, a
	 * plugin's launch pad, a /tp with motion...). That packet <em>replaces</em> the velocity rather than
	 * adding to it, so the modelled momentum is replaced too instead of being diffed as an impulse.
	 * Additive pushes - explosions, wind charges, entity pushing - still go through the impulse diff.
	 */
	public static void onServerVelocity(ClientPlayerEntity player) {
		if (player != trackedPlayer || mode == Mode.NONE) {
			return;
		}

		Vec3d velocity = player.getVelocity();
		momentum = new HVec(velocity.x, velocity.z).scale(TICKS_PER_SECOND);
		lastPostVelocity = new HVec(velocity.x, velocity.z);
		lastPostVerticalVelocity = velocity.y;
		if (mode == Mode.WALL) {
			endWallRun();
		}
	}

	/**
	 * Read-only snapshot of the controller for the camera and animation code. It is the state the movement
	 * of the current tick was computed from, so camera effects never lag the physics by a tick.
	 */
	public record View(Mode mode, HVec wallNormal, HVec wallVelocity, HVec momentum, int wallJumpSerial,
			HVec lastWallJumpNormal, int slideHopSerial, boolean slideHopAir, int doubleJumpSerial) {
	}

	public static View view(ClientPlayerEntity player) {
		if (player != trackedPlayer) {
			return new View(Mode.NONE, HVec.ZERO, HVec.ZERO, HVec.ZERO, wallJumpSerial, lastWallJumpNormal, slideHopSerial, false, doubleJumpSerial);
		}

		HVec normal = mode == Mode.WALL && wall != null ? wall.normal() : HVec.ZERO;
		HVec wallVelocity = mode == Mode.WALL ? wallTangent.scale(wallSpeed) : HVec.ZERO;
		return new View(mode, normal, wallVelocity, momentum, wallJumpSerial, lastWallJumpNormal, slideHopSerial,
				mode == Mode.AIR && slideHopAir, doubleJumpSerial);
	}

	/** End of every client tick: drop all state when there is no player (menus, disconnects). */
	public static void clientTick(MinecraftClient client) {
		if (client.player == null && trackedPlayer != null) {
			reset();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Ground
	// ------------------------------------------------------------------------------------------------

	private static void groundTick(ClientPlayerEntity player, TitanfallConfig config, Input input, HVec displacement,
			boolean jumpedFromGround, float yaw) {
		if (mode == Mode.WALL) {
			endWallRun();
		}

		doubleJumpAvailable = true;
		lastWall = null;
		lastBumpWall = null;
		sameWallCooldown = 0;
		airTicks = 0;
		wallsSinceLanding.clear();
		lurchTicksLeft = 0;
		boolean inSlideChain = slideHopAir || mode == Mode.SLIDE;
		slideHopAir = false;

		// Held jump in a slide-hop chain: hop on the very tick of landing. Vanilla only re-jumps a held key
		// once its 10-tick jump cooldown has run out, so short hops would otherwise sit on the ground for a
		// few ticks (losing speed to friction and dropping the hop animation). Calling jump() keeps
		// vanilla's own impulse (jump boost, honey...), and the jump mixin still strips the sprint kick.
		if (!jumpedFromGround && input.jumping && mode != Mode.NONE && (inSlideChain || input.sneaking)) {
			player.jump();
			jumpedFromGround = vanillaJumped;
			vanillaJumped = false;
		}

		if (jumpedFromGround) {
			// Crouch-jumping off a landing, or any hop while a chain is running, is a slide hop.
			boolean slideHop = mode == Mode.SLIDE || (mode != Mode.NONE && (inSlideChain || input.sneaking));
			boolean fromSlide = mode == Mode.SLIDE;
			takeOff(player, config, input, displacement, yaw);
			slideHopAir = slideHop;
			if (slideHop && !fromSlide) {
				// takeOff counts hops out of a ground slide; count held-jump chain hops too, so every hop
				// in the chain replays the push-off and coil animation.
				slideHopSerial++;
			}
			return;
		}

		boolean crouching = input.sneaking;
		if (mode == Mode.AIR) {
			// First tick on the ground. This is where a chain is kept or lost: crouch slides with all of the
			// air momentum, otherwise the speed above normal bleeds off through ground friction.
			if (peakY - player.getY() >= LAND_SOUND_HEIGHT) {
				MovementSounds.land();
			}

			if (crouching && momentum.length() >= config.slideMinSpeed) {
				startSlide(config);
			} else if (momentum.length() > config.skidReleaseSpeed * stimScale) {
				mode = Mode.SKID;
			} else {
				release(player);
				return;
			}
		} else if (mode == Mode.NONE) {
			// Crouch while running at sprint speed: a slide. The displacement of the tick that just ended is
			// the honest speed - vanilla's stored velocity has already been through ground friction.
			if (crouching && displacement.length() >= config.slideMinSpeed && !player.isUsingItem()) {
				momentum = displacement;
				startSlide(config);
			} else {
				return;
			}
		} else if (mode == Mode.SKID && crouching && momentum.length() >= config.slideMinSpeed) {
			startSlide(config);
		}

		HVec wish = wishDirection(yaw, input);
		double surface = surfaceFrictionScale(player);
		momentum = momentum.scale(velocityMultiplier(player));

		if (mode == Mode.SLIDE) {
			// Un-crouch check: under anything lower than a crouching player (1.5 blocks) the slide can neither
			// be stood up out of nor allowed to fizzle out; it keeps a minimum speed to carry the pilot through
			// the gap. Only if something stops it dead does it end, and then vanilla puts the player into its
			// 0.6-block crawl rather than standing them up into the ceiling.
			boolean ceiling = !fitsHeight(player, CROUCH_HEIGHT);
			if (!crouching && !ceiling) {
				MovementSounds.stopSlide();
				mode = Mode.SKID;
			} else {
				// Titanfall slide: constant deceleration plus exponential decay, both scaled down on slippery blocks.
				double speed = momentum.length();
				double decayed = speed * Math.pow(config.slideDecay, DT * surface) - config.slideDecel * DT * surface;
				momentum = momentum.withLength(Math.max(decayed, 0.0));
				momentum = SourceMovement.steer(momentum, wish, config.slideSteer * DT);
				if (ceiling && momentum.length() > 0.05) {
					momentum = momentum.withLength(Math.max(momentum.length(), config.slideCeilingSpeed));
				}

				boolean spent = ceiling ? momentum.length() <= 0.05 : momentum.length() < config.slideEndSpeed;
				if (spent) {
					MovementSounds.stopSlide();
					release(player);
					return;
				}
			}
		}

		if (mode == Mode.SKID) {
			momentum = SourceMovement.friction(momentum, config.groundFriction * surface, config.stopSpeed, DT);
			momentum = SourceMovement.groundAccelerate(momentum, wish, config.skidReleaseSpeed * stimScale, config.groundAccelerate, DT);
			if (momentum.length() <= config.skidReleaseSpeed * stimScale) {
				release(player);
				return;
			}
		}

		momentum = SourceMovement.clampLength(momentum, config.maxVelocity);
		player.setVelocity(momentum.x() * DT, player.getVelocity().y, momentum.z() * DT);
	}

	/**
	 * Vanilla jumped this tick (the pilot was on the ground holding jump). From normal walking the take-off
	 * speed is the measured run speed, raised to the pilot sprint speed when sprinting. From a slide, a skid
	 * or the landing tick of a jump, the momentum is kept exactly: the hop happens before any ground
	 * friction, which is the Source bunny hop and the Titanfall slide hop.
	 */
	private static void takeOff(ClientPlayerEntity player, TitanfallConfig config, Input input, HVec displacement, float yaw) {
		if (mode == Mode.NONE) {
			HVec takeoff = displacement;
			if (player.isSprinting() && input.pressingForward && takeoff.length() < config.sprintTakeoffSpeed * stimScale) {
				HVec direction = takeoff.length() > 1.0 ? takeoff.normalize() : HVec.forwardFromYaw(yaw);
				takeoff = direction.scale(config.sprintTakeoffSpeed * stimScale);
			}

			momentum = takeoff;
		} else if (mode == Mode.SLIDE) {
			MovementSounds.stopSlide();
			slideHopSerial++;
		}

		MovementSounds.jump();
		mode = Mode.AIR;
		peakY = player.getY();
		startLurch();
		airAccelerate(config, input, yaw, yaw);
		// The vertical speed is vanilla's fresh jump impulse: no gravity compensation on this tick.
		writeAirVelocity(player, config);
	}

	private static void startSlide(TitanfallConfig config) {
		double speed = momentum.length();
		if (slideBoostCooldown == 0 && speed < config.slideBoostCap) {
			// The boost only fills up to the cap, so slide hopping keeps speed but does not farm it.
			momentum = momentum.withLength(Math.min(speed + config.slideBoost, config.slideBoostCap));
			slideBoostCooldown = config.slideBoostCooldownTicks;
		}

		mode = Mode.SLIDE;
		MovementSounds.startSlide();
	}

	// ------------------------------------------------------------------------------------------------
	// Air and walls
	// ------------------------------------------------------------------------------------------------

	private static void airTick(ClientPlayerEntity player, TitanfallConfig config, Input input, HVec displacement,
			boolean jumpPressed, int newKeys, float previousYaw, float yaw) {
		if (mode == Mode.NONE) {
			// Walked off a ledge or was knocked into the air: start from the speed actually travelled.
			momentum = displacement;
			mode = Mode.AIR;
			peakY = player.getY();
		} else if (mode == Mode.SLIDE || mode == Mode.SKID) {
			// Slid off an edge: the slide's momentum carries straight into the air.
			if (mode == Mode.SLIDE) {
				MovementSounds.stopSlide();
				slideHopAir = true;
			}

			mode = Mode.AIR;
			peakY = player.getY();
		}

		peakY = Math.max(peakY, player.getY());
		airTicks++;
		boolean crouching = input.sneaking;
		HVec wish = wishDirection(yaw, input);

		if (mode == Mode.WALL) {
			if (jumpPressed) {
				wallJump(player, config, wish);
				return;
			}

			// Crouch drops off the wall. Every movement key keeps the run alive: keys along the wall drive it
			// either way, and only holding straight away from the wall for a moment peels the pilot off.
			boolean pushingAway = !wish.isZero() && wish.dot(wall.normal()) > 0.7 && Math.abs(wish.dot(wallTangent)) < 0.3;
			wallAwayTicks = pushingAway ? wallAwayTicks + 1 : 0;
			if (crouching || wallAwayTicks >= config.wallPeelTicks) {
				endWallRun();
			} else if (continueWallRun(player, config, wish)) {
				return;
			}
		}

		if (wallCooldown == 0 && !crouching) {
			WallProbe.Wall found = WallProbe.find(player, momentum.add(wish.scale(config.wallRunMinSpeed)), config.wallRunReach, null);
			if (found != null && !(found.samePlaneAs(lastWall) && sameWallCooldown > 0)
					&& canLatch(config, found, wish) && startWallRun(player, config, found, wish)) {
				return;
			}
		}

		// A wall bump - crouch held against a wall mid-air - hands the air jump back without landing. Once per
		// wall plane until the pilot lands, otherwise crouch + jump against one wall would climb it forever.
		if (config.airJumpOnWallBump && !doubleJumpAvailable && crouching) {
			WallProbe.Wall bumped = WallProbe.touching(player, config.wallRunReach);
			if (bumped != null && !bumped.samePlaneAs(lastBumpWall)) {
				lastBumpWall = bumped;
				doubleJumpAvailable = true;
			}
		}

		if (jumpPressed && doubleJumpAvailable) {
			doubleJump(player, config, wish);
		}

		applyLurch(config, newKeys, wish);
		airAccelerate(config, input, previousYaw, yaw);
		writeAirVelocity(player, config);
	}

	/**
	 * The Source air accelerator, sub-stepped. The camera yaw is interpolated from last tick's to this
	 * tick's across the sub-steps, so a smooth mouse turn while holding A or D is integrated as a curve,
	 * the way Source's 60+ Hz movement sees it, instead of as one 50 ms jump in direction.
	 */
	private static void airAccelerate(TitanfallConfig config, Input input, float previousYaw, float yaw) {
		int steps = Math.max(1, config.airSubsteps);
		double stepDt = DT / steps;
		for (int step = 1; step <= steps; step++) {
			double stepYaw = previousYaw + (yaw - previousYaw) * step / (double) steps;
			HVec wish = wishDirection(stepYaw, input);
			momentum = SourceMovement.airAccelerate(momentum, wish, config.airSpeedCap, config.airAcceleration, stepDt);
		}
	}

	/**
	 * Writes the owned horizontal velocity and keeps vanilla's vertical speed. The floatier Titanfall arc
	 * comes from the jumpkit's gravity attribute modifier, which vanilla's own gravity step already uses -
	 * on the server too - so nothing is compensated here.
	 */
	private static void writeAirVelocity(ClientPlayerEntity player, TitanfallConfig config) {
		momentum = SourceMovement.clampLength(momentum, config.maxVelocity);
		player.setVelocity(momentum.x() * DT, player.getVelocity().y, momentum.z() * DT);
	}

	/**
	 * The air jump. Like a ground jump it <em>replaces</em> the vertical speed, so jumping while falling
	 * still gives a full second jump. Existing momentum is turned part of the way toward the held
	 * direction (which follows the camera) and given a small push that way; its speed is kept.
	 */
	private static void doubleJump(ClientPlayerEntity player, TitanfallConfig config, HVec wish) {
		doubleJumpAvailable = false;
		if (!wish.isZero()) {
			momentum = SourceMovement.redirect(momentum, wish, config.doubleJumpRedirect).add(wish.scale(config.doubleJumpBoost));
		}

		player.setVelocity(player.getVelocity().x, config.doubleJumpVelocity, player.getVelocity().z);
		player.fallDistance = 0.0F;
		peakY = player.getY();
		startLurch();
		MovementSounds.doubleJump();
		doubleJumpSerial++;
	}

	/**
	 * Whether a wall in reach should be latched. Facing is irrelevant - the pilot can look along the wall,
	 * into it, or have it at their back. What matters is motion or intent along the surface: either enough
	 * momentum already runs along it, or any held key (W, A, S or D, in camera space) has a component
	 * along it while the pilot carries some speed or is hugging the wall.
	 */
	private static boolean canLatch(TitanfallConfig config, WallProbe.Wall found, HVec wish) {
		// Never on the first airborne ticks: stepping off stairs or a slab, or a standing jump beside a wall,
		// must not turn into a wall run.
		if (airTicks < MIN_AIR_TICKS_TO_LATCH) {
			return false;
		}

		HVec tangent = found.tangent();
		double momentumAlong = Math.abs(momentum.dot(tangent));
		double wishAlong = wish.isZero() ? 0.0 : Math.abs(wish.dot(tangent));
		if (momentumAlong >= config.wallRunMinAlongSpeed) {
			return momentum.length() >= config.wallRunMinSpeed || wishAlong >= 0.3;
		}

		// Input-only latch (hugging the wall): a key along the wall, and either some speed or a key that
		// actually presses into the wall.
		boolean pressingIn = !wish.isZero() && wish.dot(found.normal()) < -0.3;
		return wishAlong >= 0.3 && (momentum.length() >= config.wallRunMinAlongSpeed || pressingIn);
	}

	/** Latches onto {@code found}. Returns false if the very first tick of the run already ended it. */
	private static boolean startWallRun(ClientPlayerEntity player, TitanfallConfig config, WallProbe.Wall found, HVec wish) {
		wall = found;
		mode = Mode.WALL;
		wallTicks = 0;
		slideHopAir = false;
		wallAwayTicks = 0;
		wallGraceTicks = config.wallDetachGraceTicks;
		wallTangent = found.tangent();

		// Turn the momentum onto the wall plane. The run keeps most of the incoming speed instead of only
		// the part that already ran along the wall, which is what makes chaining walls keep speed. The run
		// direction comes from the momentum along the wall, or - when the pilot latched by hugging the wall -
		// from the held keys, then the camera.
		double momentumAlong = momentum.dot(wallTangent);
		double direction;
		if (Math.abs(momentumAlong) >= 0.5) {
			direction = Math.signum(momentumAlong);
		} else if (!wish.isZero() && Math.abs(wish.dot(wallTangent)) >= 0.1) {
			direction = Math.signum(wish.dot(wallTangent));
		} else {
			double look = HVec.forwardFromYaw(player.getYaw()).dot(wallTangent);
			direction = look < 0.0 ? -1.0 : 1.0;
		}

		double speed = Math.max(Math.abs(momentumAlong), momentum.length() * config.wallRunSpeedRetain);
		if (!wish.isZero() && Math.abs(wish.dot(wallTangent)) >= 0.3) {
			speed = Math.max(speed, config.wallRunEntrySpeed * stimScale);
		}

		wallSpeed = direction * speed;
		wallVerticalSpeed = clamp(player.getVelocity().y * TICKS_PER_SECOND, -config.wallRunMaxVerticalSpeed, config.wallRunMaxVerticalSpeed);
		momentum = wallTangent.scale(wallSpeed);

		// The double jump comes back only from a wall not yet run on since landing, so bouncing between two
		// walls cannot refill it forever.
		boolean fresh = wallsSinceLanding.stream().noneMatch(found::samePlaneAs);
		if (config.airJumpOnWallRun && fresh) {
			doubleJumpAvailable = true;
		}

		wallsSinceLanding.add(found);
		lurchTicksLeft = 0;
		MovementSounds.startWallRun();
		return continueWallRun(player, config, wish);
	}

	/**
	 * One tick of running along {@link #wall}. Returns false (and ends the run) when the wall is gone or
	 * time is up. The run follows the wall as it turns, and round a corner too sharp to follow it transfers to the
	 * next wall without losing speed.
	 */
	private static boolean continueWallRun(ClientPlayerEntity player, TitanfallConfig config, HVec wish) {
		WallProbe.Scan scan = WallProbe.scan(player, config.wallRunReach + 0.1);
		WallProbe.Wall tracked = WallProbe.track(scan, wall, config.wallRunReach);
		boolean touching = tracked != null;
		if (touching) {
			followWall(scan, tracked);
			wallGraceTicks = config.wallDetachGraceTicks;
		} else {
			// The wall ran out: carry on round an outside corner onto whatever wall continues the run.
			WallProbe.Wall next = WallProbe.find(scan, wallTangent.scale(wallSpeed), config.wallRunReach, wall);
			if (next != null) {
				transferWall(next);
				touching = true;
			} else if (wallGraceTicks > 0) {
				wallGraceTicks--;
			} else {
				endWallRun();
				return false;
			}
		}

		if (++wallTicks > config.wallRunMaxTicks) {
			endWallRun();
			return false;
		}

		// Any key with a component along the wall drives the run toward that direction at that key's share
		// of the cap; an opposite key decelerates through zero and builds speed the other way, so the
		// run reverses continuously instead of dropping off. A pilot faster than the cap is not braked by
		// keys that agree with the motion. No key along the wall: light wall friction only.
		double along = wish.isZero() ? 0.0 : wish.dot(wallTangent);
		if (Math.abs(along) > 0.1) {
			double sign = Math.signum(along);
			boolean agrees = Math.signum(wallSpeed) == sign || wallSpeed == 0.0;
			double target = sign * (agrees ? Math.max(Math.abs(wallSpeed), config.wallRunMaxSpeed * stimScale) : config.wallRunMaxSpeed * stimScale);
			double rate = (agrees ? config.wallRunAccel : config.wallRunReverseAccel) * DT;
			wallSpeed = approach(wallSpeed, target, rate);
		} else {
			wallSpeed = approach(wallSpeed, 0.0, config.wallRunFriction * DT);
		}

		momentum = wallTangent.scale(wallSpeed);

		// Vertical: pulled toward zero (an upward entry arcs over, a falling entry is caught), while wall
		// gravity ramps in over the first 0.7 s and slipping adds more after 1.5 s.
		double gravity = config.wallRunGravity * Math.min(1.0, wallTicks / (double) config.wallRunGravityRampTicks);
		if (wallTicks > config.wallRunSlipTicks) {
			gravity += config.wallRunSlipGravity;
		}

		wallVerticalSpeed = approachZero(wallVerticalSpeed, config.wallRunVerticalDamping * DT) - gravity * DT;
		wallVerticalSpeed = clamp(wallVerticalSpeed, -config.wallRunMaxVerticalSpeed, config.wallRunMaxVerticalSpeed);

		// Hold the hitbox a hair off the wall line: pull in while there is a gap, push out if a corner of the
		// wall sticks into the line ahead. No pull during grace ticks - the wall is not there, and pulling
		// would drag the pilot round a corner.
		HVec normal = wall.normal();
		double stick = touching ? clamp(wall.gap() - WALL_STANDOFF, -WALL_PULL, WALL_PULL) : 0.0;
		double vx = momentum.x() * DT - normal.x() * stick;
		double vz = momentum.z() * DT - normal.z() * stick;

		// Minecraft resolves a move one axis at a time, larger axis first. Along a diagonal wall that can
		// test the hitbox half a step into the wall (inside a block of the staircase) and stop the run dead.
		// Moving the axis that heads away from the wall first, ourselves, keeps every intermediate position
		// in the open; vanilla then moves the other axis.
		boolean xOutward = vx * normal.x() > 0.0;
		boolean zOutward = vz * normal.z() > 0.0;
		if (xOutward != zOutward) {
			if (xOutward) {
				player.move(MovementType.SELF, new Vec3d(vx, 0.0, 0.0));
				vx = 0.0;
			} else {
				player.move(MovementType.SELF, new Vec3d(0.0, 0.0, vz));
				vz = 0.0;
			}
		}

		player.setVelocity(vx, wallVerticalSpeed * DT, vz);
		player.fallDistance = 0.0F;
		peakY = player.getY();
		return true;
	}

	/**
	 * Follows the wall as its direction changes - a face turning into a diagonal row, a staircase changing
	 * slope, an outside corner being rounded - turning at most {@link #WALL_MAX_TURN} per tick so the run
	 * curves instead of snapping. The run keeps its speed and direction along the wall.
	 */
	private static void followWall(WallProbe.Scan scan, WallProbe.Wall tracked) {
		double turn = Math.atan2(Math.sin(tracked.angle() - wall.angle()), Math.cos(tracked.angle() - wall.angle()));
		WallProbe.Wall next = tracked;
		if (Math.abs(turn) > WALL_MAX_TURN) {
			WallProbe.Wall eased = scan.at(wall.angle() + Math.signum(turn) * WALL_MAX_TURN);
			next = eased != null ? eased : tracked;
		}

		HVec tangent = next.tangent();
		wallTangent = tangent.dot(wallTangent) < 0.0 ? tangent.scale(-1.0) : tangent;
		wall = next;
	}

	/**
	 * Moves the run onto another wall (round an outside corner too sharp to follow) keeping its speed. The
	 * velocity is projected onto the new wall; going round a 90 degree outside corner that projection is
	 * zero, so the run continues toward the old wall's side ({@code -oldNormal}), which is the way round.
	 */
	private static void transferWall(WallProbe.Wall next) {
		HVec velocity = wallTangent.scale(wallSpeed);
		HVec oldNormal = wall.normal();
		HVec tangent = next.tangent();
		double projected = velocity.dot(tangent);
		double sign = Math.abs(projected) > 1.0E-3 ? Math.signum(projected) : Math.signum(oldNormal.scale(-1.0).dot(tangent));
		if (sign == 0.0) {
			sign = 1.0;
		}

		wall = next;
		wallTangent = tangent;
		wallSpeed = sign * Math.abs(wallSpeed);
		wallGraceTicks = TitanfallConfig.get().wallDetachGraceTicks;
	}

	/**
	 * The wall kick: the run's momentum is carried through, the pilot is launched outward along the wall
	 * normal with an upward boost, and the held direction adds a little more - Titanfall's
	 * {@code wallrunJumpOutwardSpeed}, {@code wallrunJumpUpSpeed} and {@code wallrunJumpInputDirSpeed}.
	 */
	private static void wallJump(ClientPlayerEntity player, TitanfallConfig config, HVec wish) {
		HVec normal = wall.normal();
		double carried = wallSpeed * config.wallJumpMomentum;
		HVec launch = wallTangent.scale(carried).add(normal.scale(config.wallJumpPush));
		if (!wish.isZero()) {
			launch = launch.add(wish.scale(config.wallJumpInputSpeed));
			// The input push steers the kick; it must not raise the along-wall speed past what the run had
			// (or the run cap), or hopping between two parallel walls would gain speed without limit.
			double along = launch.dot(wallTangent);
			double alongCap = Math.max(Math.abs(carried), config.wallRunMaxSpeed * stimScale);
			if (Math.abs(along) > alongCap) {
				launch = launch.subtract(wallTangent.scale(along - Math.signum(along) * alongCap));
			}
		}

		lastWallJumpNormal = normal;
		wallJumpSerial++;
		endWallRun();
		momentum = SourceMovement.clampLength(launch, config.maxVelocity);
		// Each further wall since the last landing gives a weaker upward kick, so a shaft cannot be climbed
		// indefinitely by bouncing between its walls.
		double upScale = Math.max(0.4, 1.0 - 0.15 * Math.max(0, wallsSinceLanding.size() - 1));
		player.setVelocity(momentum.x() * DT, config.wallJumpUp * upScale * DT, momentum.z() * DT);
		player.fallDistance = 0.0F;
		peakY = player.getY();
		startLurch();
		MovementSounds.wallJump();
	}

	private static void endWallRun() {
		if (mode != Mode.WALL) {
			return;
		}

		MovementSounds.stopWallRun();
		mode = Mode.AIR;
		lastWall = wall;
		wall = null;
		wallTicks = 0;
		wallCooldown = TitanfallConfig.get().wallCooldownTicks;
		sameWallCooldown = TitanfallConfig.get().sameWallCooldownTicks;
	}

	// ------------------------------------------------------------------------------------------------
	// Grapple
	// ------------------------------------------------------------------------------------------------

	/**
	 * One tick on the Grapple line, in blocks per second throughout.
	 *
	 * <ol>
	 *   <li>The tick the hook bites, the pilot is kicked upward ({@code grapple_impactVerticalBoost}), which
	 *   pops them off the ground.</li>
	 *   <li>The line reels in: velocity toward the hook is built up at {@code grapplePullAccel} until the
	 *   closing speed reaches the pull speed, which is a little higher for a hook fired from further away.
	 *   Speed the pilot already has beyond that is never taken away, so swinging and air strafing keep
	 *   working, and the line never lets the pilot drift further from the hook than it already is.</li>
	 *   <li>Air control is much stronger than in free flight ({@code grapple_airSpeedMax} /
	 *   {@code grapple_airAccel}), so the pilot can swing round the hook.</li>
	 *   <li>The line carries part of the pilot's weight: gravity is scaled from 0.7 with the hook level or
	 *   below to 0.25 with it straight overhead ({@code grapple_gravityFracMax} / {@code Min}).</li>
	 * </ol>
	 *
	 * The line lets go on arrival, a jump or a second key press - all with the release kick - or, with no
	 * kick, when it is flung away from the hook or stays too slow ({@link GrappleController} handles losing
	 * sight of the hook).
	 */
	private static void grappleTick(ClientPlayerEntity player, TitanfallConfig config, Input input, boolean jumpPressed,
			float previousYaw, float yaw) {
		if (mode == Mode.WALL) {
			endWallRun();
		} else if (mode == Mode.SLIDE) {
			MovementSounds.stopSlide();
		}

		mode = Mode.GRAPPLE;
		slideHopAir = false;
		lurchTicksLeft = 0;

		Vec3d anchor = GrappleController.anchor(player);
		Vec3d centre = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
		Vec3d toAnchor = anchor.subtract(centre);
		double distance = toAnchor.length();
		Vec3d toward = distance > 1.0E-4 ? toAnchor.multiply(1.0 / distance) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d velocity = new Vec3d(momentum.x(), player.getVelocity().y * TICKS_PER_SECOND, momentum.z());

		if (GrappleController.consumeJustAttached() && anchor.y > player.getY()) {
			velocity = new Vec3d(velocity.x, verticalKick(velocity.y, config.grappleImpactBoost), velocity.z);
		}

		double speed = velocity.length();
		double arrive = clamp(speed / config.grapplePullSpeedFar, 0.0, 1.0);
		double detachLength = config.grappleDetachLengthMin + (config.grappleDetachLengthMax - config.grappleDetachLengthMin) * arrive;
		GrappleController.Release release = GrappleController.pendingRelease;
		if (release == GrappleController.Release.NONE) {
			if (jumpPressed || distance <= detachLength) {
				release = GrappleController.Release.LET_GO;
			} else if (-velocity.dotProduct(toward) > config.grappleDetachAwaySpeed || GrappleController.tooSlow(player, speed, config)) {
				release = GrappleController.Release.SNAP;
			}
		}

		if (release != GrappleController.Release.NONE) {
			if (release == GrappleController.Release.LET_GO) {
				// Titanfall's release: the speed is trimmed (never below the floor) and the pilot is kicked upward,
				// which is what pops a grappling pilot up and over the ledge they were reeled to.
				double trimmed = Math.max(speed - config.grappleDetachSpeedLoss, Math.min(speed, config.grappleDetachSpeedLossMin));
				velocity = speed > 1.0E-6 ? velocity.multiply(trimmed / speed) : velocity;
				velocity = new Vec3d(velocity.x, verticalKick(velocity.y, config.grappleDetachBoost), velocity.z);
			}

			GrappleController.letGo(player, GrappleLines.of(player));
			momentum = SourceMovement.clampLength(new HVec(velocity.x, velocity.z), config.maxVelocity);
			player.setVelocity(momentum.x() * DT, velocity.y * DT, momentum.z() * DT);
			peakY = player.getY();
			airTicks = MIN_AIR_TICKS_TO_LATCH;
			mode = Mode.AIR;
			if (!pilotActive(player)) {
				release(player);
			}

			return;
		}

		// Reel in toward the hook.
		double reach = clamp(GrappleController.attachDistance() / config.grappleRange, 0.0, 1.0);
		double pullSpeed = config.grapplePullSpeed + (config.grapplePullSpeedFar - config.grapplePullSpeed) * reach;
		double closing = velocity.dotProduct(toward);
		if (closing < pullSpeed) {
			velocity = velocity.add(toward.multiply(Math.min(config.grapplePullAccel * DT, pullSpeed - closing)));
		}

		// Strong air control round the hook (horizontal, sub-stepped like free flight).
		HVec horizontal = new HVec(velocity.x, velocity.z);
		int steps = Math.max(1, config.airSubsteps);
		for (int step = 1; step <= steps; step++) {
			double stepYaw = previousYaw + (yaw - previousYaw) * step / (double) steps;
			horizontal = SourceMovement.airAccelerate(horizontal, wishDirection(stepYaw, input), config.grappleAirSpeed, config.grappleAirAccel, DT / steps);
		}

		velocity = new Vec3d(horizontal.x(), velocity.y, horizontal.z());
		// The line never pays out: no motion away from the hook survives.
		double away = velocity.dotProduct(toward);
		if (away < 0.0) {
			velocity = velocity.subtract(toward.multiply(away));
		}

		momentum = SourceMovement.clampLength(new HVec(velocity.x, velocity.z), config.maxVelocity);
		// Vanilla subtracts the full gravity after the move; the line holds up the rest of it.
		double overhead = clamp(toward.y, 0.0, 1.0);
		double gravityFrac = config.grappleGravityFracMax + (config.grappleGravityFracMin - config.grappleGravityFracMax) * overhead;
		double held = (1.0 - gravityFrac) * player.getFinalGravity();
		player.setVelocity(momentum.x() * DT, velocity.y * DT + held, momentum.z() * DT);
		player.fallDistance = 0.0F;
		peakY = player.getY();
		GrappleLines.of(player).pull = (float) velocity.dotProduct(toward);
	}

	/** Titanfall's capped vertical kick: add {@code boost} upward, but never lift past {@code boost} by it. */
	private static double verticalKick(double vertical, double boost) {
		return Math.max(vertical, Math.min(vertical + boost, boost));
	}

	// ------------------------------------------------------------------------------------------------
	// Lurch
	// ------------------------------------------------------------------------------------------------

	private static void startLurch() {
		lurchTicksLeft = TitanfallConfig.get().lurchTicks;
		lurchUsed = false;
	}

	/**
	 * Titanfall's lurch ("keyboard grace"): pressing a new movement key shortly after a jump swings the
	 * velocity toward it. The new velocity is a blend of the old one and the wish direction at the same
	 * speed; blending two directions shortens the vector, so a sharp lurch costs speed and a lurch along
	 * the current direction costs nothing - the documented trade-off - with the loss capped at
	 * {@code lurchMaxSpeedLoss}. Keys already held at the jump do not lurch, and holding left and right
	 * together cancels it. One lurch per jump; its strength fades to zero across the window.
	 */
	private static void applyLurch(TitanfallConfig config, int newKeys, HVec wish) {
		if (lurchTicksLeft <= 0) {
			return;
		}

		int window = Math.max(1, config.lurchTicks);
		double strength = config.lurchStrength * lurchTicksLeft / (double) window;
		lurchTicksLeft--;
		boolean cancelled = (lastKeyMask & (KEY_LEFT | KEY_RIGHT)) == (KEY_LEFT | KEY_RIGHT);
		if (lurchUsed || newKeys == 0 || cancelled || wish.isZero() || strength <= 0.0) {
			return;
		}

		double speed = momentum.length();
		HVec blended = momentum.scale(1.0 - strength).add(wish.scale(speed * strength));
		double kept = Math.max(blended.length(), speed * (1.0 - config.lurchMaxSpeedLoss));
		momentum = blended.withLength(kept);
		lurchUsed = true;
	}

	// ------------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------------

	private static final int KEY_FORWARD = 1;
	private static final int KEY_BACK = 2;
	private static final int KEY_LEFT = 4;
	private static final int KEY_RIGHT = 8;

	private static int keyMask(Input input) {
		return (input.pressingForward ? KEY_FORWARD : 0) | (input.pressingBack ? KEY_BACK : 0)
				| (input.pressingLeft ? KEY_LEFT : 0) | (input.pressingRight ? KEY_RIGHT : 0);
	}

	/**
	 * Unit wish direction from the raw WASD state and a camera yaw, or zero with no input. Raw key state
	 * is used rather than {@code movementForward/Sideways}, which vanilla scales down while crouching or
	 * using an item - those slow-downs belong to walking, not to air control.
	 */
	static HVec wishDirection(double yaw, Input input) {
		double forward = (input.pressingForward ? 1.0 : 0.0) - (input.pressingBack ? 1.0 : 0.0);
		double right = (input.pressingRight ? 1.0 : 0.0) - (input.pressingLeft ? 1.0 : 0.0);
		if (forward == 0.0 && right == 0.0) {
			return HVec.ZERO;
		}

		return HVec.forwardFromYaw(yaw).scale(forward).add(HVec.rightFromYaw(yaw).scale(right)).normalize();
	}

	/** Pilot movement applies: kit worn, and nothing else (water, ladders, flight, vehicles...) has the player. */
	private static boolean pilotActive(ClientPlayerEntity player) {
		return Jumpkit.abilitiesActive(player)
				&& !player.isSpectator()
				&& !player.isDead()
				&& !player.hasVehicle()
				&& !player.isSleeping()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isTouchingWater()
				&& !player.isInLava()
				&& !player.isClimbing()
				&& !player.isUsingRiptide()
				&& !player.noClip;
	}

	/** Anything that changed the horizontal velocity since our last tick that we did not cause is adopted. */
	private static void adoptExternalImpulse(ClientPlayerEntity player) {
		Vec3d velocity = player.getVelocity();
		HVec impulse = new HVec(velocity.x, velocity.z).subtract(lastPostVelocity);
		if (impulse.length() > IMPULSE_THRESHOLD) {
			momentum = momentum.add(impulse.scale(TICKS_PER_SECOND));
		}
	}

	/**
	 * Hands the player back to vanilla. Vanilla's stored velocity is "after friction": a player moving at
	 * speed v on a block of slipperiness s holds {@code v * s * 0.91} between ticks and adds its input
	 * acceleration on top. Writing the momentum in that form makes the hand-off continuous instead of a
	 * one-tick lurch forward.
	 */
	private static void release(ClientPlayerEntity player) {
		if (mode == Mode.NONE) {
			momentum = HVec.ZERO;
			return;
		}

		if (mode == Mode.WALL) {
			endWallRun();
		}

		MovementSounds.clear();
		double factor = player.isOnGround() ? blockBelow(player).getSlipperiness() * AIR_DRAG : AIR_DRAG;
		HVec handoff = momentum.scale(DT * factor);
		player.setVelocity(handoff.x(), player.getVelocity().y, handoff.z());
		mode = Mode.NONE;
		momentum = HVec.ZERO;
		lurchTicksLeft = 0;
	}

	/** Whether a box of the player's width and the given height fits at the player's feet. */
	private static boolean fitsHeight(ClientPlayerEntity player, double height) {
		Box box = player.getBoundingBox();
		Box test = new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + height, box.maxZ).contract(1.0E-7);
		return player.getWorld().isSpaceEmpty(player, test);
	}

	private static Block blockBelow(ClientPlayerEntity player) {
		World world = player.getWorld();
		return world.getBlockState(player.getVelocityAffectingPos()).getBlock();
	}

	/** Slide and skid friction scale: 1 on ordinary blocks, less on ice and slime (never more than 1). */
	private static double surfaceFrictionScale(ClientPlayerEntity player) {
		double slipperiness = blockBelow(player).getSlipperiness();
		return clamp((1.0 - slipperiness) / (1.0 - NORMAL_SLIPPERINESS), 0.02, 1.0);
	}

	/** Soul sand, honey and similar blocks slow owned ground movement the way they slow walking. */
	private static double velocityMultiplier(ClientPlayerEntity player) {
		return Math.min(1.0, blockBelow(player).getVelocityMultiplier());
	}


	private static void tickCooldowns() {
		if (slideBoostCooldown > 0) {
			slideBoostCooldown--;
		}

		if (wallCooldown > 0) {
			wallCooldown--;
		}

		if (sameWallCooldown > 0) {
			sameWallCooldown--;
		}
	}

	private static double approach(double value, double target, double step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	private static double approachZero(double value, double step) {
		return value > 0.0 ? Math.max(0.0, value - step) : Math.min(0.0, value + step);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void reset() {
		MovementSounds.clear();
		if (trackedPlayer != null) {
			SlideState.setSliding(trackedPlayer, false);
		}

		slideFlagSent = false;
		slideHopAir = false;
		trackedPlayer = null;
		mode = Mode.NONE;
		momentum = HVec.ZERO;
		lastPosition = null;
		hasLastYaw = false;
		jumpWasDown = false;
		lastKeyMask = 0;
		vanillaJumped = false;
		jumpVelocityBefore = null;
		lastPostVelocity = HVec.ZERO;
		lastPostVerticalVelocity = 0.0;
		ownedThisTick = false;
		doubleJumpAvailable = false;
		lurchTicksLeft = 0;
		lurchUsed = false;
		slideBoostCooldown = 0;
		wall = null;
		lastWall = null;
		lastBumpWall = null;
		wallTangent = HVec.ZERO;
		wallAwayTicks = 0;
		airTicks = 0;
		wallsSinceLanding.clear();
		wallSpeed = 0.0;
		wallVerticalSpeed = 0.0;
		wallTicks = 0;
		wallGraceTicks = 0;
		wallCooldown = 0;
		sameWallCooldown = 0;
	}
}
