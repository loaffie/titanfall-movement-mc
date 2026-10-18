package com.loaffie.titanfall.client;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.client.particle.PilotParticle;
import com.loaffie.titanfall.client.particle.ThrusterFlameParticle;
import com.loaffie.titanfall.client.render.JumpkitFeatureRenderer;
import com.loaffie.titanfall.client.render.JumpkitItemRenderer;
import com.loaffie.titanfall.item.TitanfallItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import com.loaffie.titanfall.client.render.JumpkitMesh;
import com.loaffie.titanfall.client.render.JumpkitModel;
import com.loaffie.titanfall.movement.TitanfallController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.resource.ResourceType;

public class TitanfallMovementClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The movement itself runs from the travel mixin; the tick event only clears state between worlds.
		ClientTickEvents.END_CLIENT_TICK.register(TitanfallController::clientTick);
		// Pose targets for every visible pilot (the local one from the controller, others inferred).
		ClientTickEvents.END_CLIENT_TICK.register(PilotAnimations::tick);

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(JumpkitMesh.reloadListener());
		ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
		particles.register(TitanfallParticles.THRUSTER_JET, sprites -> new PilotParticle.Factory(sprites, PilotParticle.JET));
		particles.register(TitanfallParticles.THRUSTER_SPARK, sprites -> new PilotParticle.Factory(sprites, PilotParticle.SPARK));
		particles.register(TitanfallParticles.SHOCKWAVE, sprites -> new PilotParticle.Factory(sprites, PilotParticle.SHOCKWAVE));
		particles.register(TitanfallParticles.AFTERGLOW, sprites -> new PilotParticle.Factory(sprites, PilotParticle.AFTERGLOW));
		particles.register(TitanfallParticles.IGNITION, sprites -> new PilotParticle.Factory(sprites, PilotParticle.IGNITION));
		particles.register(TitanfallParticles.EXHAUST_RING, sprites -> new PilotParticle.Factory(sprites, PilotParticle.EXHAUST_RING));
		particles.register(TitanfallParticles.THRUSTER_FLAME, ThrusterFlameParticle.Factory::new);

		// The jumpkit item is drawn with the same OBJ mesh as the worn kit (inventory, hands, ground, frames).
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_JUMPKIT, new JumpkitItemRenderer());

		EntityModelLayerRegistry.registerModelLayer(JumpkitModel.LAYER, JumpkitModel::getTexturedModelData);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				registrationHelper.register(new JumpkitFeatureRenderer(playerRenderer, context.getModelLoader()));
			}
		});
	}
}
