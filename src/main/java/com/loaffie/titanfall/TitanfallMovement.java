package com.loaffie.titanfall;

import com.loaffie.titanfall.cloak.CloakServer;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.entity.TitanfallEntities;
import com.loaffie.titanfall.grapple.GrappleServer;
import com.loaffie.titanfall.holo.HoloServer;
import com.loaffie.titanfall.item.TitanfallItems;
import com.loaffie.titanfall.movement.TitanfallSounds;
import com.loaffie.titanfall.particle.TitanfallParticles;
import com.loaffie.titanfall.phase.PhaseServer;
import com.loaffie.titanfall.pulse.PulseServer;
import com.loaffie.titanfall.slide.SlidePayload;
import com.loaffie.titanfall.stim.StimServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint (client and dedicated server). The Pilot Jumpkit is a registered item, so the mod
 * must now be installed on both sides of a multiplayer connection; the movement itself still runs on
 * the client, which is authoritative for its own player's motion in Minecraft.
 */
public class TitanfallMovement implements ModInitializer {
	public static final String MOD_ID = "titanfall_movement";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TitanfallConfig.get();
		TitanfallSounds.register();
		TitanfallItems.register();
		TitanfallEntities.register();
		SlidePayload.register();
		GrappleServer.register();
		StimServer.register();
		PhaseServer.register();
		PulseServer.register();
		CloakServer.register();
		HoloServer.register();
		TitanfallParticles.register();
		LOGGER.info("Initializing Titanfall Movement");
	}
}
