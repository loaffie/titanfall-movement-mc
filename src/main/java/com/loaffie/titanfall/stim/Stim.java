package com.loaffie.titanfall.stim;

import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * The Stim: Titanfall 2's Stim Pilot tactical. One jab of the autoinjector gives a burst of speed and fast
 * healing for a few seconds. Worn on the right hip (Trinkets' belt slot), or held in either hand.
 */
public final class Stim {
	/** The movement-speed modifier the stim adds while it runs (server side, synced to the client). */
	public static final Identifier SPEED_MODIFIER = Identifier.of("titanfall_movement", "stim_speed");

	private Stim() {
	}

	public static boolean isWorn(LivingEntity entity) {
		return PilotGear.worn(entity, TitanfallItems.PILOT_STIM);
	}
}
