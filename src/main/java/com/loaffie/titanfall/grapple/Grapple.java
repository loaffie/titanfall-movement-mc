package com.loaffie.titanfall.grapple;

import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;

/**
 * Where a pilot wears the Grapple, if anywhere. The single source of truth for "can this entity grapple"
 * and for which forearm the launcher is drawn on.
 *
 * <p>With Trinkets installed the Grapple goes in a glove slot: the {@code offhand} group's glove is the
 * off arm, the {@code hand} group's the main arm. Without Trinkets (or as well as it) the Grapple can be
 * held in the off hand, or the main hand, and is then worn on that arm. In Titanfall 2 the launcher sits
 * on the off-hand forearm - the weapon hand stays free - so that is the slot the item suggests.
 */
public final class Grapple {
	private Grapple() {
	}

	/** The arm the Grapple is worn on, or null when the entity has none equipped. */
	public static Arm wornArm(LivingEntity entity) {
		Arm main = entity.getMainArm();
		String group = PilotGear.trinketGroup(entity, TitanfallItems.PILOT_GRAPPLE);
		if (group != null) {
			return "hand".equals(group) ? main : main.getOpposite();
		}

		if (entity.getOffHandStack().isOf(TitanfallItems.PILOT_GRAPPLE)) {
			return main.getOpposite();
		}

		return entity.getMainHandStack().isOf(TitanfallItems.PILOT_GRAPPLE) ? main : null;
	}

	public static boolean isWorn(LivingEntity entity) {
		return wornArm(entity) != null;
	}
}
