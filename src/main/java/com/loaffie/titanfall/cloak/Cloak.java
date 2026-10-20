package com.loaffie.titanfall.cloak;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.item.TitanfallItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;

import java.util.function.Predicate;

/**
 * Cloak: Titanfall 2's Cloak Pilot tactical ({@code mp_ability_cloak}). A field projector on the off-hand
 * forearm bends light round the pilot for fifteen seconds: they all but vanish - a faint shimmer that shows
 * more as they move or when they are hit - mobs barely notice them, and firing a weapon drops the cloak.
 *
 * <p>Who is cloaked is known on both sides: the server's own record ({@link CloakServer}), and on the client
 * the local pilot's cloak and the ones the server relays, through {@link #setClientLookup}.
 */
public final class Cloak {
	private static Predicate<Entity> clientLookup = entity -> false;

	private Cloak() {
	}

	/**
	 * The arm the bracer is worn on, or null. With Trinkets it goes in a glove slot (the off-hand one is the one
	 * it asks for); without, it is held, and worn on the arm holding it.
	 */
	public static Arm wornArm(LivingEntity entity) {
		Arm main = entity.getMainArm();
		String group = PilotGear.trinketGroup(entity, TitanfallItems.PILOT_CLOAK);
		if (group != null) {
			return "hand".equals(group) ? main : main.getOpposite();
		}

		if (entity.getOffHandStack().isOf(TitanfallItems.PILOT_CLOAK)) {
			return main.getOpposite();
		}

		return entity.getMainHandStack().isOf(TitanfallItems.PILOT_CLOAK) ? main : null;
	}

	public static boolean isWorn(LivingEntity entity) {
		return wornArm(entity) != null;
	}

	/** True while the entity, a player, is cloaked. */
	public static boolean isCloaked(Entity entity) {
		if (!(entity instanceof PlayerEntity)) {
			return false;
		}

		return entity.getWorld().isClient() ? clientLookup.test(entity) : CloakServer.isCloaked(entity);
	}

	/** The client's record of cloaked players (set by the client entrypoint). */
	public static void setClientLookup(Predicate<Entity> lookup) {
		clientLookup = lookup;
	}

	/** Cloak length, ticks ({@code fire_duration} 15 s). */
	public static int durationTicks(TitanfallConfig config) {
		return Math.max(1, (int) Math.round(config.cloakDurationSeconds * 20.0));
	}

	/**
	 * The charge left after a cloak that ran {@code ticks} of its {@link #durationTicks}: it drains over the
	 * cloak ({@code ammo_drains_to_empty_on_fire}), so one cut short leaves some behind.
	 */
	public static double chargeLeft(TitanfallConfig config, int ticks) {
		return Math.max(0.0, 1.0 - ticks / (double) durationTicks(config));
	}

	/** Charge regained a tick: 200 at {@code regen_ammo_refill_rate} 10 a second is a 20 s refill. */
	public static double refillPerTick(TitanfallConfig config) {
		return config.cloakCooldownSeconds <= 0.0 ? 1.0 : 1.0 / (config.cloakCooldownSeconds * 20.0);
	}
}
