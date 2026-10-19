package com.loaffie.titanfall.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loaffie.titanfall.TitanfallMovement;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Tunables for the pilot movement model.
 *
 * <p>Speeds are in blocks per second and accelerations in blocks per second squared. Titanfall 2's
 * scripts treat one world unit as one inch and its pilot hull is 72 units (1.83 m) tall, the same as a
 * Minecraft player, so Respawn's values convert to blocks at 1 unit = 0.0254 blocks. The defaults below
 * are taken from the game's own player settings files ({@code pilot_base.set}, {@code pilot_mp.set})
 * where those values exist; values marked "estimated" were not datamined and are reasoned defaults.
 *
 * <p>For scale: vanilla walking is 4.32 b/s and sprinting 5.61 b/s; a Titanfall pilot walks at 4.41
 * and sprints at 6.60.
 */
public class TitanfallConfig {
	/**
	 * Bumped whenever field meanings change, so an old file is backed up instead of misread. Fields that are
	 * only added (like the grapple's) keep their defaults when an older file is read, so they need no bump.
	 */
	public static final int CURRENT_VERSION = 7;

	public int configVersion = CURRENT_VERSION;

	// --- jumpkit ---
	/** Pilot movement only works while the Pilot Jumpkit is worn. Turn off only for testing. */
	public boolean requireJumpkit = true;
	/** Pilots take no fall damage while wearing the jumpkit (server side). */
	public boolean jumpkitNegatesFallDamage = true;
	/** Thruster puffs at the hips on double jumps and wall jumps. */
	public boolean thrusterParticles = true;

	// --- air movement ---
	/**
	 * Air wish-speed cap (Titanfall {@code airSpeed}, ~60 u/s = 1.52 b/s). It caps the velocity
	 * <em>projection</em> onto the wish direction, never the total speed: that is what lets air
	 * strafing build speed.
	 */
	public double airSpeedCap = 1.52;
	/** Air acceleration, b/s^2 (Titanfall {@code airAcceleration}, ~500 u/s^2 = 12.7 b/s^2). */
	public double airAcceleration = 12.7;
	/**
	 * Air acceleration sub-steps per 20 Hz game tick. The camera yaw is interpolated across the
	 * sub-steps, so a smooth mouse turn is integrated like Source's 60+ Hz movement instead of in
	 * coarse 50 ms jumps. 3 gives 60 Hz.
	 */
	public int airSubsteps = 3;
	/** Hard safety limit on horizontal speed, b/s (Source sv_maxvelocity analogue). Not a gameplay cap. At most 90. */
	public double maxVelocity = 60.0;

	// --- jumping ---
	/**
	 * Minimum horizontal take-off speed when sprint-jumping from normal ground movement, b/s. Vanilla's
	 * own +0.2 b/t sprint-jump kick is removed (it would compound on every hop in a frictionless model),
	 * and the take-off is raised to the pilot sprint speed of 260 u/s = 6.6 b/s instead.
	 */
	public double sprintTakeoffSpeed = 6.6;
	/**
	 * Double jump vertical velocity, blocks/tick. It replaces (not adds to) the vertical speed. 0.337 is the
	 * kit's own ground jump: a full 1.52-block Titanfall jump under the kit's gravity. Gravity itself comes
	 * from the jumpkit item (see {@code TitanfallItems.GRAVITY_SCALE}), so it is the same on every client
	 * and the server.
	 */
	public double doubleJumpVelocity = 0.337;
	/** Horizontal push toward the held direction on a double jump, b/s (follows the camera yaw). */
	public double doubleJumpBoost = 1.0;
	/** How far a double jump turns existing momentum toward the held direction, 0..1 (speed is kept). */
	public double doubleJumpRedirect = 0.35;
	/** Ticks after any jump during which a new strafe input "lurches" the velocity. ~0.4 s. */
	public int lurchTicks = 8;
	/**
	 * Strongest lurch (right after the jump), 0..1; fades to 0 across the window. The velocity is blended
	 * toward the new input at the same speed, so a sharp lurch costs speed by itself (a 45 degree lurch
	 * at 0.6 keeps ~93%), capped by {@link #lurchMaxSpeedLoss}. Estimated: not datamined.
	 */
	public double lurchStrength = 0.6;
	/** Most speed one lurch can cost, 0..1. Community figure for Titanfall 2: 12.5%. */
	public double lurchMaxSpeedLoss = 0.125;
	/** Whether attaching to a wall gives the double jump back. */
	public boolean airJumpOnWallRun = true;
	/** Whether crouching against a wall mid-air (a wall bump) gives the double jump back. */
	public boolean airJumpOnWallBump = true;

	// --- ground (momentum carried past normal speed) ---
	/** Source sv_friction for skidding on landings without a slide. Estimated (Source default 4). */
	public double groundFriction = 4.0;
	/** Source sv_stopspeed, b/s (100 u = 2.54 b/s). Estimated (Source default). */
	public double stopSpeed = 2.54;
	/** Source sv_accelerate, used for steering while skidding. Estimated (Source default 10). */
	public double groundAccelerate = 10.0;
	/** Below this speed a skid hands the player back to vanilla walking, b/s. Just above vanilla sprint. */
	public double skidReleaseSpeed = 5.8;

	// --- sliding ---
	/** Speed needed to start a slide, b/s ({@code slideRequiredStartSpeed} 200 u = 5.08). Sprinting qualifies, walking does not. */
	public double slideMinSpeed = 5.08;
	/** Slide start boost, b/s ({@code slideSpeedBoost} 150 u = 3.81). */
	public double slideBoost = 3.81;
	/** The boost never lifts the speed past this, b/s ({@code slideSpeedBoostCap} 400 u = 10.16). */
	public double slideBoostCap = 10.16;
	/** Ticks before another slide boost; 40 = the ~2 s timer Titanfall uses. */
	public int slideBoostCooldownTicks = 40;
	/** Constant slide deceleration, b/s^2 ({@code slidedecel} 50 u = 1.27). */
	public double slideDecel = 1.27;
	/** Exponential slide decay: fraction of speed kept per second ({@code slidevelocitydecay} 0.7). */
	public double slideDecay = 0.7;
	/** Steering while sliding, b/s of lateral change per second. A slide turns reluctantly. */
	public double slideSteer = 3.0;
	/** A slide that slows below this ends, b/s. Vanilla walking speed, so the hand-back is seamless. */
	public double slideEndSpeed = 4.3;
	/** Minimum slide speed while there is no headroom to stand or crouch (sliding through a 1-block gap), b/s. */
	public double slideCeilingSpeed = 3.0;

	// --- wall running ---
	/** Minimum horizontal speed to attach to a wall, b/s. A sprint jump qualifies, a walk jump does not. */
	public double wallRunMinSpeed = 5.0;
	/** Wall run time limit in ticks ({@code wallrun_timeLimit} 1.75 s = 35 ticks). */
	public int wallRunMaxTicks = 35;
	/** Speed the run accelerates up to while forward is held, b/s ({@code wallrunMaxSpeedHorizontal} 420 u = 10.67). */
	public double wallRunMaxSpeed = 10.67;
	/** Horizontal wall run acceleration, b/s^2 ({@code wallrunAccelerateHorizontal} 1500 u = 38.1). */
	public double wallRunAccel = 38.1;
	/** Fraction of the incoming speed kept when the velocity is turned onto the wall, 0..1. */
	public double wallRunSpeedRetain = 0.9;
	/** Vertical speed limit on the wall, b/s ({@code wallrunMaxSpeedVertical} 225 u = 5.72). */
	public double wallRunMaxVerticalSpeed = 5.72;
	/** Rate vertical speed is pulled toward zero on the wall, b/s^2 ({@code wallrunAccelerateVertical} 360 u = 9.14). */
	public double wallRunVerticalDamping = 9.14;
	/** Gravity on the wall once fully ramped in, b/s^2. Slightly under the damping, so a run sinks gently. Estimated. */
	public double wallRunGravity = 8.0;
	/** Ticks for wall gravity to ramp from zero to full ({@code wallrun_gravityRampUpTime} 0.7 s). */
	public int wallRunGravityRampTicks = 14;
	/** Tick after which the pilot starts slipping down the wall ({@code wallrun_slipstarttime} 1.5 s). */
	public int wallRunSlipTicks = 30;
	/** Extra gravity while slipping, b/s^2. Estimated. */
	public double wallRunSlipGravity = 14.0;
	/** Gap between hitbox and wall at which a run can latch or continue, blocks. Generous, so hugging a wall latches. */
	public double wallRunReach = 0.4;
	/**
	 * Speed along the wall (b/s) that by itself is enough to latch. Facing does not matter: the run starts
	 * whether the pilot looks along the wall, at it, or away from it with the wall at their back.
	 */
	public double wallRunMinAlongSpeed = 1.5;
	/**
	 * Speed a run starts at when it was started by input (any of W/A/S/D with a component along the wall)
	 * rather than by momentum - e.g. a pilot hugging a wall from a standstill jump. b/s.
	 */
	public double wallRunEntrySpeed = 5.0;
	/** Acceleration used to swing a run round when the input points back along the wall, b/s^2. */
	public double wallRunReverseAccel = 24.0;
	/** Speed lost per second on the wall with no input along it, b/s^2. */
	public double wallRunFriction = 1.5;
	/** Ticks of holding straight away from the wall (with nothing along it) before the pilot peels off. */
	public int wallPeelTicks = 6;
	/** Ticks a run survives with no wall in reach (doorways, single missing blocks). */
	public int wallDetachGraceTicks = 2;
	/** Push away from the wall on a wall jump, b/s ({@code wallrunJumpOutwardSpeed} 205 u = 5.21). */
	public double wallJumpPush = 5.21;
	/** Wall jump vertical velocity, b/s ({@code wallrunJumpUpSpeed} 230 u = 5.84). */
	public double wallJumpUp = 5.84;
	/** Extra push toward the held direction on a wall jump, b/s ({@code wallrunJumpInputDirSpeed} 80 u = 2.03). */
	public double wallJumpInputSpeed = 2.03;
	/** Fraction of the run's speed carried through the kick; 1.0 keeps all of it. */
	public double wallJumpMomentum = 1.0;
	/** Ticks after leaving a wall before any wall can be latched again. */
	public int wallCooldownTicks = 5;
	/** Ticks before the same wall plane can be latched again (landing resets it). */
	public int sameWallCooldownTicks = 20;

	// --- grapple ---
	// Titanfall 2's Grapple, from the game's own files: the ability itself ({@code mp_ability_grapple.txt}) and
	// the pilot's grapple settings ({@code pilot_base.set}, {@code pilot_mp.set}). The pull strength is set by
	// the engine, not by those files, and is matched to measured in-game speeds instead.
	/** Hook range, blocks ({@code grapple_maxLength} 1100 u = 27.94). */
	public double grappleRange = 27.94;
	/** Hook flight speed, b/s. 4400 u/s reaches full range in 0.25 s, the measured flight time. */
	public double grappleHookSpeed = 111.76;
	/**
	 * Closing speed the line reels the pilot in at, b/s, at point-blank range; it rises to
	 * {@link #grapplePullSpeedFar} for a hook at full range. Measured in game: about 40 km/h on a short pull
	 * and 45 km/h or more from full range (11.1 and 12.5 b/s). Estimated: the pull is engine code.
	 */
	public double grapplePullSpeed = 11.1;
	public double grapplePullSpeedFar = 12.5;
	/** How hard the line accelerates the pilot toward the hook until that speed, b/s^2. Estimated. */
	public double grapplePullAccel = 30.0;
	/** Air control while hooked: wish-speed cap, b/s ({@code grapple_airSpeedMax} 420 u = 10.67). */
	public double grappleAirSpeed = 10.67;
	/** Air control while hooked: acceleration, b/s^2 ({@code grapple_airAccel} 650 u = 16.51). */
	public double grappleAirAccel = 16.51;
	/**
	 * Fraction of gravity felt while hooked ({@code grapple_gravityFracMin} 0.25, {@code grapple_gravityFracMax}
	 * 0.7): the least with the hook straight overhead, the most with it level or below.
	 */
	public double grappleGravityFracMin = 0.25;
	public double grappleGravityFracMax = 0.7;
	/** Upward kick when the hook bites, b/s, never lifting past the same speed ({@code grapple_impactVerticalBoost} 300 u = 7.62). */
	public double grappleImpactBoost = 7.62;
	/** Upward kick on letting go, b/s, capped the same way ({@code grapple_detachVerticalBoost} 200 u = 5.08). */
	public double grappleDetachBoost = 5.08;
	/** Speed shed on letting go, b/s ({@code grapple_detachSpeedLoss} 300 u = 7.62)... */
	public double grappleDetachSpeedLoss = 7.62;
	/** ...but never below this, b/s ({@code grapple_detachSpeedLossMin} 460 u = 11.68 in multiplayer). */
	public double grappleDetachSpeedLossMin = 11.68;
	/** The line lets go this close to the hook, blocks: slow arrivals at the near value, fast ones at the far
	 * ({@code grapple_detachLengthMin} / {@code Max} 33 / 50 u = 0.84 / 1.27). */
	public double grappleDetachLengthMin = 0.84;
	public double grappleDetachLengthMax = 1.27;
	/** Moving away from the hook faster than this snaps the line, b/s ({@code grapple_detachAwaySpeed} 500 u = 12.7). */
	public double grappleDetachAwaySpeed = 12.7;
	/** Slower than this for too long and the line lets go, b/s ({@code grapple_detachLowSpeedThreshold} 250 u = 6.35). */
	public double grappleLowSpeed = 6.35;
	/** How long the pilot may be that slow in the air, on a wall and on the ground, seconds
	 * ({@code grapple_detachLowSpeedTime} 1.5, {@code WallTime} 1.2, {@code GroundTime} 0.7). */
	public double grappleLowSpeedAirTime = 1.5;
	public double grappleLowSpeedWallTime = 1.2;
	public double grappleLowSpeedGroundTime = 0.7;
	/** Grapple charges. Titanfall 2 launched with two; its October 2017 update settled on one. */
	public int grappleCharges = 1;
	/** Seconds to recharge one charge (10 s since the 2017 update). */
	public double grappleCooldownSeconds = 10.0;
	/** Seconds after the line lets go before the meter starts refilling ({@code grapple_power_regen_delay} 3). */
	public double grappleRegenDelaySeconds = 3.0;
	/** Pilots cannot fire weapons while being pulled (melee still works): item use is blocked while hooked. */
	public boolean grappleBlocksItemUse = true;
	/** Tactical ability meters (Grapple, Stim) and the Grapple's range marker round the crosshair. */
	public boolean grappleHud = true;

	// --- stim ---
	// Titanfall 2's Stim, from the game's files: mp_ability_heal.txt, sh_stim.gnut, _health_regen.gnut and
	// sh_consts.gnut.
	/**
	 * How long a stim lasts, seconds. Titanfall 2's is 3.0 ({@code fire_duration}); this mod's default is a longer
	 * 5. The speed boost outlasts it by 0.5 s, as in the game.
	 */
	public double stimLengthSeconds = 5.0;
	/** Speed boost, fraction ({@code STIM_EFFECT_SEVERITY} 0.4: +40%). */
	public double stimSpeedBoost = 0.4;
	/** Seconds to recharge ({@code ammo_per_shot} 200 / {@code regen_ammo_refill_rate} 8 = 25 s, from the moment of use). */
	public double stimCooldownSeconds = 25.0;
	/** While stimmed, healing starts this long after the last hit, seconds ({@code ABILITY_STIM_REGEN_DELAY} 2.0). */
	public double stimRegenDelaySeconds = 2.0;
	/**
	 * While stimmed, health healed per second as a fraction of max health: a pilot's 6 health per 0.1 s
	 * regen tick out of 100, doubled ({@code ABILITY_STIM_REGEN_MOD} 2.0).
	 */
	public double stimHealPerSecond = 1.2;
	/** The stim's fast healing keeps going this long after the stim itself ends, seconds. */
	public double stimHealLingerSeconds = 2.0;
	/** Extra FOV while stimmed, fraction (scaled by the FOV Effects option). */
	public double stimFovBoost = 0.06;
	/** Screen-edge pulse and speed lines while stimmed. */
	public boolean stimScreenEffects = true;

	// --- phase shift ---
	// Titanfall 2's Phase Shift, from the game's files: mp_ability_shifter.txt and sh_phase_shift.gnut.
	/** How long a phase lasts, seconds ({@code fire_duration} 2.0 in multiplayer). */
	public double phaseLengthSeconds = 2.0;
	/** Time for the charge to come back, seconds: 200 ammo at {@code regen_ammo_refill_rate} 13.333 a second = 15. */
	public double phaseCooldownSeconds = 15.0;
	/**
	 * Coming out of phase inside someone kills them (Titanfall's telefrag): anything up to human size. Anything
	 * bigger pushes the pilot out instead.
	 */
	public boolean phaseTelefrag = true;
	/** The black-and-white phase world, the shift's screen flashes and the pared-down HUD while phased. */
	public boolean phaseScreenEffects = true;

	// --- camera ---
	/**
	 * Camera roll while wall running, degrees, scaled by how side-on the wall is to the view. Positive banks the
	 * view outward, away from the wall (default); negative banks toward it.
	 */
	public double wallRunCameraRoll = 12.0;
	/** Roll kick away from the wall on a wall jump, degrees; springs back over ~0.4 s. */
	public double wallJumpCameraKick = 5.0;
	/**
	 * Extra camera drop while sliding, blocks. The 0.8-block slide pose already lowers the eyes to 0.6 blocks
	 * (vanilla eases the eye height smoothly), so this defaults to 0.
	 */
	public double slideCameraDrop = 0.0;
	/** FOV widening while sliding and slide hopping, fraction (0.08 = +8%), scaled by the FOV Effects option. */
	public double slideFovBoost = 0.08;
	/** Camera roll into the slide's sideways drift, degrees. */
	public double slideCameraTilt = 3.0;
	/** Smoothing rate of all camera effects, 1/s. Higher reaches the target faster; frame-rate independent. */
	public double cameraSmoothing = 10.0;

	// --- animation ---
	public boolean animationsEnabled = true;
	/** Body lean away from the wall (feet on the wall) while wall running, degrees. */
	public double wallRunLean = 22.0;
	/** Body recline while sliding, degrees. */
	public double slideRecline = 55.0;

	// --- sound ---
	public boolean soundsEnabled = true;
	public double movementVolume = 1.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("titanfall_movement.json");
	private static TitanfallConfig instance;

	public static TitanfallConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static TitanfallConfig load() {
		if (Files.exists(PATH)) {
			try {
				TitanfallConfig config;
				try (Reader reader = Files.newBufferedReader(PATH)) {
					config = GSON.fromJson(reader, TitanfallConfig.class);
				}

				if (config != null && config.configVersion == CURRENT_VERSION) {
					config.sanitize();
					// Rewritten so settings added since the file was saved show up in it with their defaults.
					config.save();
					return config;
				}

				String version = config == null ? "unknown" : Integer.toString(config.configVersion);
				TitanfallMovement.LOGGER.warn("Movement config version {} does not match {}, backing it up and writing defaults", version, CURRENT_VERSION);
				Files.move(PATH, PATH.resolveSibling(PATH.getFileName() + ".v" + version + ".bak"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException exception) {
				TitanfallMovement.LOGGER.error("Failed to read config, using defaults", exception);
			}
		}

		TitanfallConfig config = new TitanfallConfig();
		config.save();
		return config;
	}

	/** Clamps values a hand-edited file could set to something that breaks the model. */
	private void sanitize() {
		airSubsteps = Math.max(1, Math.min(airSubsteps, 10));
		maxVelocity = Math.max(1.0, Math.min(maxVelocity, 90.0));
		lurchTicks = Math.max(0, lurchTicks);
		wallPeelTicks = Math.max(1, wallPeelTicks);
		cameraSmoothing = Math.max(0.5, cameraSmoothing);
		lurchMaxSpeedLoss = Math.max(0.0, Math.min(lurchMaxSpeedLoss, 1.0));
		wallRunMaxTicks = Math.max(1, wallRunMaxTicks);
		wallRunGravityRampTicks = Math.max(1, wallRunGravityRampTicks);
		slideDecay = Math.max(0.0, Math.min(slideDecay, 1.0));
		movementVolume = Math.max(0.0, Math.min(movementVolume, 1.0));
		grappleRange = Math.max(2.0, Math.min(grappleRange, 128.0));
		grappleHookSpeed = Math.max(5.0, grappleHookSpeed);
		grappleCharges = Math.max(1, Math.min(grappleCharges, 5));
		grappleCooldownSeconds = Math.max(0.0, grappleCooldownSeconds);
		grappleRegenDelaySeconds = Math.max(0.0, grappleRegenDelaySeconds);
		grappleDetachLengthMax = Math.max(grappleDetachLengthMin, grappleDetachLengthMax);
		stimLengthSeconds = Math.max(0.25, Math.min(stimLengthSeconds, 30.0));
		stimHealLingerSeconds = Math.max(0.0, Math.min(stimHealLingerSeconds, 30.0));
		stimSpeedBoost = Math.max(0.0, Math.min(stimSpeedBoost, 2.0));
		stimCooldownSeconds = Math.max(0.0, stimCooldownSeconds);
		stimHealPerSecond = Math.max(0.0, stimHealPerSecond);
		phaseLengthSeconds = Math.max(0.25, Math.min(phaseLengthSeconds, 10.0));
		phaseCooldownSeconds = Math.max(0.0, phaseCooldownSeconds);
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(PATH)) {
			GSON.toJson(this, writer);
		} catch (IOException exception) {
			TitanfallMovement.LOGGER.error("Failed to write config", exception);
		}
	}
}
