package com.loaffie.titanfall.entity;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.awall.AWallEntity;
import com.loaffie.titanfall.holo.HoloDecoyEntity;
import com.loaffie.titanfall.pulse.PulseBladeEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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

	/**
	 * A Holo Pilot decoy: a player-sized hologram, tracked as far off as players are and updated as often, so it
	 * moves as smoothly as the pilot it copies.
	 */
	public static final EntityType<HoloDecoyEntity> HOLO_DECOY = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(TitanfallMovement.MOD_ID, "holo_decoy"),
			EntityType.Builder.<HoloDecoyEntity>create(HoloDecoyEntity::new, SpawnGroup.MISC).disableSaving().disableSummon()
					.dimensions(0.6F, 1.8F).eyeHeight(1.62F).maxTrackingRange(32).trackingTickInterval(2).build());

	/**
	 * The A-Wall's emitter disc, and the wall it holds up once planted: seen from as far off as a pilot's
	 * fight, its flight updated every tick.
	 */
	public static final EntityType<AWallEntity> AWALL = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(TitanfallMovement.MOD_ID, "awall"),
			EntityType.Builder.<AWallEntity>create(AWallEntity::new, SpawnGroup.MISC).disableSaving().disableSummon()
					.dimensions(0.4F, 0.35F).maxTrackingRange(8).trackingTickInterval(1).build());

	private TitanfallEntities() {
	}

	/** Forces class loading, and so registration, from the mod initializer. */
	public static void register() {
		FabricDefaultAttributeRegistry.register(HOLO_DECOY, HoloDecoyEntity.createAttributes());
	}
}
