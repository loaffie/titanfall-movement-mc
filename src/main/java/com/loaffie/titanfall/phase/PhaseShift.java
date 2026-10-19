package com.loaffie.titanfall.phase;

import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;

import java.util.function.Predicate;

/**
 * Phase Shift: Titanfall 2's Phase Shift Pilot tactical. The gauntlet on the back of the off hand shifts the
 * pilot out of the world for two seconds: still in the same place - in the overworld, under the same sky -
 * but out of phase with it. A phased pilot can move freely but can't be seen, hit, targeted or pushed by
 * anything in the world, and can't touch it either: no attacking, no using items or blocks. Phased pilots
 * see each other. Coming back inside someone telefrags them.
 *
 * <p>Who is phased is known on both sides: the server's own record ({@link PhaseServer}), and on the client
 * the local pilot's shift and the ones the server relays, through {@link #setClientLookup}.
 */
public final class PhaseShift {
	private static Predicate<Entity> clientLookup = entity -> false;

	private PhaseShift() {
	}

	/**
	 * The arm the gauntlet is worn on, or null. With Trinkets it goes in a glove slot (the off-hand one is
	 * the one it asks for); without, it is held, and worn on the arm holding it.
	 */
	public static Arm wornArm(LivingEntity entity) {
		Arm main = entity.getMainArm();
		String group = PilotGear.trinketGroup(entity, TitanfallItems.PILOT_PHASE_SHIFT);
		if (group != null) {
			return "hand".equals(group) ? main : main.getOpposite();
		}

		if (entity.getOffHandStack().isOf(TitanfallItems.PILOT_PHASE_SHIFT)) {
			return main.getOpposite();
		}

		return entity.getMainHandStack().isOf(TitanfallItems.PILOT_PHASE_SHIFT) ? main : null;
	}

	public static boolean isWorn(LivingEntity entity) {
		return wornArm(entity) != null;
	}

	/** True while the entity, a player, is phased. */
	public static boolean isPhased(Entity entity) {
		if (!(entity instanceof PlayerEntity)) {
			return false;
		}

		return entity.getWorld().isClient() ? clientLookup.test(entity) : PhaseServer.isPhased(entity);
	}

	/** The client's record of phased players (set by the client entrypoint). */
	public static void setClientLookup(Predicate<Entity> lookup) {
		clientLookup = lookup;
	}
}
