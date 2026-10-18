package com.loaffie.titanfall.item;

import com.loaffie.titanfall.config.TitanfallConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

/**
 * The single source of truth for "is this entity a pilot right now".
 *
 * <p>Every ability hook - the client movement controller, the travel/jump/sneak mixins and the server
 * fall-damage mixin - asks this one method, so the jumpkit is the only switch for pilot movement.
 */
public final class Jumpkit {
	private Jumpkit() {
	}

	/** True when the entity is wearing the Pilot Jumpkit in its chest equipment slot. */
	public static boolean isWorn(LivingEntity entity) {
		return entity.getEquippedStack(EquipmentSlot.CHEST).isOf(TitanfallItems.PILOT_JUMPKIT);
	}

	/**
	 * True when pilot abilities are active for this entity: it wears the kit, or the config explicitly
	 * turned the requirement off (a testing switch; it defaults to requiring the kit).
	 */
	public static boolean abilitiesActive(LivingEntity entity) {
		return !TitanfallConfig.get().requireJumpkit || isWorn(entity);
	}
}
