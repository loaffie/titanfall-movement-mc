package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.LivingEntity;

/**
 * Holo Pilot: Titanfall 2's Holo Pilot tactical ({@code mp_ability_holopilot}). A projector worn on the
 * chest throws out a hologram of the pilot that carries on with their last move - running on the way they
 * were heading, sliding, wall running - for ten seconds, to draw fire and fool whoever is watching. It holds
 * two charges, each back in 12.5 seconds.
 *
 * <p>The decoy itself is a {@link HoloDecoyEntity}, spawned and moved by the server ({@link HoloServer}).
 */
public final class HoloPilot {
	/**
	 * How far the movement keys must be pushed for the decoy to run, 0..1 ({@code CreatePlayerDecoy}'s
	 * {@code stickPercentToRun} 0.65); with less it stands where it was made.
	 */
	public static final float STICK_TO_RUN = 0.65F;

	private HoloPilot() {
	}

	/** Worn on the chest (Trinkets' necklace slot, in the chest group) or held in either hand. */
	public static boolean isWorn(LivingEntity entity) {
		return PilotGear.worn(entity, TitanfallItems.PILOT_HOLO);
	}

	public static int charges(TitanfallConfig config) {
		return Math.max(1, config.holoCharges);
	}

	/** Charge regained a tick, in charges: one every {@code holoChargeSeconds}. */
	public static double refillPerTick(TitanfallConfig config) {
		return config.holoChargeSeconds <= 0.0 ? 1.0 : 1.0 / (config.holoChargeSeconds * 20.0);
	}

	/** A decoy's life, ticks ({@code DECOY_DURATION} 10 s). */
	public static int durationTicks(TitanfallConfig config) {
		return Math.max(1, (int) Math.round(config.holoDurationSeconds * 20.0));
	}
}
