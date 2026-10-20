package com.loaffie.titanfall.particle;

import com.loaffie.titanfall.TitanfallMovement;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Pilot jumpkit particle types. They are registered on both sides (particle types are a synced registry)
 * but only ever spawned client-side; the look of each one lives in {@code client.particle.PilotParticle}.
 */
public final class TitanfallParticles {
	/** Plasma jet from a thruster nozzle: stretched along its velocity, white-hot core fading to electric blue. */
	public static final SimpleParticleType THRUSTER_JET = register("thruster_jet");
	/** Hot spark: stretched streak, white to orange to red, falls and bounces. */
	public static final SimpleParticleType THRUSTER_SPARK = register("thruster_spark");
	/** Expanding ring, drawn flat; the spawn velocity is the ring's facing normal, not a motion. */
	public static final SimpleParticleType SHOCKWAVE = register("shockwave");
	/** Slow, fading cyan wisp left behind by the thrusters. */
	public static final SimpleParticleType AFTERGLOW = register("afterglow");
	/** Double-jump exhaust ring: faces and travels along its spawn velocity, i.e. down a nozzle's axis. */
	public static final SimpleParticleType EXHAUST_RING = register("exhaust_ring");
	/**
	 * Steady wall-run flame attached to a nozzle. The spawn velocity carries the player's entity id (x) and
	 * the port index (y); the particle redraws the flame on that nozzle every frame while it is fuelled.
	 */
	public static final SimpleParticleType THRUSTER_FLAME = register("thruster_flame");
	/** Short four-point flash at a nozzle as it fires. */
	public static final SimpleParticleType IGNITION = register("ignition");
	/** Grapple muzzle and hook-bite flash: a hot, short-lived four-point star. */
	public static final SimpleParticleType GRAPPLE_FLASH = register("grapple_flash");
	/** Small ring punched out round the hook where it bites; the spawn velocity is the surface normal. */
	public static final SimpleParticleType GRAPPLE_RING = register("grapple_ring");
	/** Faint cyan tracer left along the hook's flight. */
	public static final SimpleParticleType GRAPPLE_TRAIL = register("grapple_trail");
	/** Stim trail: a glowing blue streak left hanging in the air behind a stimmed pilot. */
	public static final SimpleParticleType STIM_TRAIL = register("stim_trail");
	/** Stim injection: a soft blue flare where the needle goes in. */
	public static final SimpleParticleType STIM_FLASH = register("stim_flash");
	/** Phase Shift: electric sparks thrown off the gauntlet's coil and off a pilot shifting in or out. */
	public static final SimpleParticleType PHASE_SPARK = register("phase_spark");
	/** Phase Shift: the flash a pilot comes back in. */
	public static final SimpleParticleType PHASE_FLASH = register("phase_flash");
	/** Phase Shift: the ring punched out round a pilot coming back. */
	public static final SimpleParticleType PHASE_RING = register("phase_ring");
	/** Phase Shift: soft light and smoke left along the way a pilot was heading as they vanish. */
	public static final SimpleParticleType PHASE_WISP = register("phase_wisp");
	/** Pulse Blade: the faint streak a thrown kunai leaves. */
	public static final SimpleParticleType PULSE_TRAIL = register("pulse_trail");
	/** Pulse Blade: sparks as the kunai bites, and off it with each pulse. */
	public static final SimpleParticleType PULSE_SPARK = register("pulse_spark");
	/** Pulse Blade: the ring each pulse sends out round the kunai, on the surface it is stuck in. */
	public static final SimpleParticleType PULSE_RING = register("pulse_ring");

	private TitanfallParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, Identifier.of(TitanfallMovement.MOD_ID, name), FabricParticleTypes.simple());
	}

	public static void register() {
	}
}
