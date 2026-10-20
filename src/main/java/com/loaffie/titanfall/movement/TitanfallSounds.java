package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class TitanfallSounds {
	public static final SoundEvent WALLRUN_START = register("wallrun_start");
	public static final SoundEvent WALLRUN_LOOP = register("wallrun_loop");
	public static final SoundEvent WALLJUMP = register("walljump");
	public static final SoundEvent SLIDE_START = register("slide_start");
	public static final SoundEvent SLIDE_LOOP = register("slide_loop");
	public static final SoundEvent JUMP = register("jump");
	public static final SoundEvent DOUBLE_JUMP = register("double_jump");
	public static final SoundEvent LAND = register("land");
	public static final SoundEvent GRAPPLE_FIRE = register("grapple_fire");
	public static final SoundEvent GRAPPLE_IMPACT = register("grapple_impact");
	public static final SoundEvent GRAPPLE_REEL = register("grapple_reel");
	public static final SoundEvent GRAPPLE_RELEASE = register("grapple_release");
	public static final SoundEvent GRAPPLE_READY = register("grapple_ready");
	public static final SoundEvent GRAPPLE_DENIED = register("grapple_denied");
	public static final SoundEvent STIM_ACTIVATE = register("stim_activate");
	public static final SoundEvent STIM_LOOP = register("stim_loop");
	public static final SoundEvent STIM_DEACTIVATE = register("stim_deactivate");
	public static final SoundEvent PHASE_ACTIVATE = register("phase_activate");
	public static final SoundEvent PHASE_LOOP = register("phase_loop");
	public static final SoundEvent PHASE_WARNING = register("phase_warning");
	public static final SoundEvent PHASE_END = register("phase_end");
	public static final SoundEvent PHASE_TELEFRAG = register("phase_telefrag");
	public static final SoundEvent PULSE_DRAW = register("pulse_draw");
	public static final SoundEvent PULSE_THROW = register("pulse_throw");
	public static final SoundEvent PULSE_IMPACT = register("pulse_impact");
	public static final SoundEvent PULSE_PING = register("pulse_ping");
	public static final SoundEvent PULSE_DETECTED = register("pulse_detected");

	private TitanfallSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.of(TitanfallMovement.MOD_ID, name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void register() {
	}
}
