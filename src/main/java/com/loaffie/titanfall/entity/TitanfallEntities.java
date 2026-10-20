package com.loaffie.titanfall.entity;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.pulse.PulseBladeEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class TitanfallEntities {
	/**
	 * The thrown Pulse Blade. Tracked from far off (its pulses reach 32 blocks), with frequent position
	 * updates: it flies at nearly 90 blocks a second.
	 */
	public static final EntityType<PulseBladeEntity> PULSE_BLADE = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(TitanfallMovement.MOD_ID, "pulse_blade"),
			EntityType.Builder.<PulseBladeEntity>create(PulseBladeEntity::new, SpawnGroup.MISC)
					.dimensions(0.25F, 0.25F).maxTrackingRange(8).trackingTickInterval(2).build());

	private TitanfallEntities() {
	}

	/** Forces class loading, and so registration, from the mod initializer. */
	public static void register() {
	}
}
