package com.loaffie.titanfall.client;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.client.grapple.GrappleFeatureRenderer;
import com.loaffie.titanfall.client.grapple.GrappleItemRenderer;
import com.loaffie.titanfall.client.grapple.GrappleLines;
import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.client.grapple.GrappleRenderer;
import com.loaffie.titanfall.client.particle.PilotParticle;
import com.loaffie.titanfall.client.phase.PhaseFeatureRenderer;
import com.loaffie.titanfall.client.phase.PhaseGhost;
import com.loaffie.titanfall.client.phase.PhaseItemRenderer;
import com.loaffie.titanfall.client.phase.PhaseModel;
import com.loaffie.titanfall.client.phase.PhaseScreen;
import com.loaffie.titanfall.client.phase.PhaseStates;
import com.loaffie.titanfall.client.pulse.PulseArc;
import com.loaffie.titanfall.client.pulse.PulseBladeRenderer;
import com.loaffie.titanfall.client.pulse.PulseFeatureRenderer;
import com.loaffie.titanfall.client.pulse.PulseHud;
import com.loaffie.titanfall.client.pulse.PulseItemRenderer;
import com.loaffie.titanfall.client.pulse.PulseModel;
import com.loaffie.titanfall.client.pulse.PulseSonar;
import com.loaffie.titanfall.client.pulse.PulseStates;
import com.loaffie.titanfall.client.particle.ThrusterFlameParticle;
import com.loaffie.titanfall.client.render.JumpkitFeatureRenderer;
import com.loaffie.titanfall.client.render.JumpkitItemRenderer;
import com.loaffie.titanfall.client.render.JumpkitMesh;
import com.loaffie.titanfall.client.render.JumpkitModel;
import com.loaffie.titanfall.client.stim.StimFeatureRenderer;
import com.loaffie.titanfall.client.stim.StimHud;
import com.loaffie.titanfall.client.stim.StimItemRenderer;
import com.loaffie.titanfall.client.stim.StimModel;
import com.loaffie.titanfall.client.stim.StimStates;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.entity.TitanfallEntities;
import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.grapple.GrappleSyncPayload;
import com.loaffie.titanfall.item.TitanfallItems;
import com.loaffie.titanfall.movement.GrappleController;
import com.loaffie.titanfall.movement.PhaseController;
import com.loaffie.titanfall.movement.PulseController;
import com.loaffie.titanfall.movement.StimController;
import com.loaffie.titanfall.movement.TitanfallController;
import com.loaffie.titanfall.particle.TitanfallParticles;
import com.loaffie.titanfall.phase.PhaseShift;
import com.loaffie.titanfall.phase.PhaseSyncPayload;
import com.loaffie.titanfall.pulse.PulseBlade;
import com.loaffie.titanfall.pulse.PulseBladeEntity;
import com.loaffie.titanfall.pulse.PulseSyncPayload;
import com.loaffie.titanfall.stim.Stim;
import com.loaffie.titanfall.stim.StimSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public class TitanfallMovementClient implements ClientModInitializer {
	/**
	 * Uses the tactical ability, the first one worn of: the Grapple (fire, and let go), the Phase Shift, the
	 * Pulse Blade (hold to aim, let go to throw) and the Stim. Titanfall's tactical key is Q, which Minecraft
	 * uses to drop items.
	 */
	public static final KeyBinding TACTICAL_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.titanfall_movement.tactical", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.titanfall_movement"));
	/** The Stim on its own key, for pilots wearing it alongside the Grapple. Unbound by default. */
	public static final KeyBinding STIM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.titanfall_movement.stim", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.categories.titanfall_movement"));
	/** The Phase Shift on its own key, for pilots wearing it alongside another tactical. Unbound by default. */
	public static final KeyBinding PHASE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.titanfall_movement.phase_shift", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.categories.titanfall_movement"));
	/** The Pulse Blade on its own key, likewise. Unbound by default. */
	public static final KeyBinding PULSE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.titanfall_movement.pulse_blade", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.categories.titanfall_movement"));

	@Override
	public void onInitializeClient() {
		// The movement itself runs from the travel mixin; the tick event only clears state between worlds.
		ClientTickEvents.END_CLIENT_TICK.register(TitanfallController::clientTick);
		// Pose targets for every visible pilot (the local one from the controller, others inferred).
		ClientTickEvents.END_CLIENT_TICK.register(PilotAnimations::tick);

		// Tactical keys are read at the start of the tick, so a grapple shot or a stim takes effect in that tick's
		// movement.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			while (TACTICAL_KEY.wasPressed()) {
				if (client.player == null || Grapple.isWorn(client.player)) {
					GrappleController.onKeyPressed(client);
				} else if (PhaseShift.isWorn(client.player)) {
					PhaseController.onKeyPressed(client);
				} else if (PulseBlade.isWorn(client.player)) {
					PulseController.onKeyPressed(client);
				} else if (Stim.isWorn(client.player)) {
					StimController.onKeyPressed(client);
				}
			}

			while (STIM_KEY.wasPressed()) {
				StimController.onKeyPressed(client);
			}

			while (PHASE_KEY.wasPressed()) {
				PhaseController.onKeyPressed(client);
			}

			while (PULSE_KEY.wasPressed()) {
				PulseController.onKeyPressed(client);
			}

			// The Pulse Blade is held to aim: it throws when whichever key drew it is let go.
			PulseController.tick(client, TACTICAL_KEY.isPressed() || PULSE_KEY.isPressed());

			GrappleController.preClientTick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(PulseController::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(PulseStates::tick);
		ClientTickEvents.END_CLIENT_TICK.register(PulseSonar::tick);
		PulseBladeEntity.setListener(PulseSonar.INSTANCE);
		ClientPlayNetworking.registerGlobalReceiver(PulseSyncPayload.ID, (payload, context) -> PulseStates.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(PulseSonar::render);
		WorldRenderEvents.AFTER_ENTITIES.register(PulseArc::render);
		EntityRendererRegistry.register(TitanfallEntities.PULSE_BLADE, PulseBladeRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(PhaseController::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(PhaseStates::tick);
		ClientTickEvents.END_CLIENT_TICK.register(PhaseGhost::tick);
		PhaseStates.register();
		ClientPlayNetworking.registerGlobalReceiver(PhaseSyncPayload.ID, (payload, context) -> PhaseStates.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(PhaseGhost::render);
		ClientTickEvents.END_CLIENT_TICK.register(StimController::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(StimStates::tick);
		ClientPlayNetworking.registerGlobalReceiver(StimSyncPayload.ID, (payload, context) -> StimStates.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(GrappleController::clientTick);
		ClientTickEvents.END_CLIENT_TICK.register(GrappleLines::tick);
		ClientPlayNetworking.registerGlobalReceiver(GrappleSyncPayload.ID, (payload, context) -> GrappleLines.receive(payload));
		WorldRenderEvents.START.register(GrappleRenderer::startFrame);
		WorldRenderEvents.AFTER_ENTITIES.register(GrappleRenderer::render);
		HudRenderCallback.EVENT.register(StimHud::render);
		HudRenderCallback.EVENT.register(PhaseScreen::renderHud);
		HudRenderCallback.EVENT.register(PulseHud::render);
		HudRenderCallback.EVENT.register(TacticalHud::render);
		// Pilots cannot fire weapons while the line is pulling them in (melee still works).
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient() && player instanceof ClientPlayerEntity local && TitanfallConfig.get().grappleBlocksItemUse
					&& GrappleController.isAttached(local)) {
				return TypedActionResult.fail(player.getStackInHand(hand));
			}

			return TypedActionResult.pass(player.getStackInHand(hand));
		});

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(JumpkitMesh.reloadListener());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(GrappleModel.reloadListener());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(StimModel.reloadListener());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(PhaseModel.reloadListener());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(PulseModel.reloadListener());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(PhaseScreen.reloadListener());
		ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
		particles.register(TitanfallParticles.THRUSTER_JET, sprites -> new PilotParticle.Factory(sprites, PilotParticle.JET));
		particles.register(TitanfallParticles.THRUSTER_SPARK, sprites -> new PilotParticle.Factory(sprites, PilotParticle.SPARK));
		particles.register(TitanfallParticles.SHOCKWAVE, sprites -> new PilotParticle.Factory(sprites, PilotParticle.SHOCKWAVE));
		particles.register(TitanfallParticles.AFTERGLOW, sprites -> new PilotParticle.Factory(sprites, PilotParticle.AFTERGLOW));
		particles.register(TitanfallParticles.IGNITION, sprites -> new PilotParticle.Factory(sprites, PilotParticle.IGNITION));
		particles.register(TitanfallParticles.EXHAUST_RING, sprites -> new PilotParticle.Factory(sprites, PilotParticle.EXHAUST_RING));
		particles.register(TitanfallParticles.THRUSTER_FLAME, ThrusterFlameParticle.Factory::new);
		particles.register(TitanfallParticles.GRAPPLE_FLASH, sprites -> new PilotParticle.Factory(sprites, PilotParticle.GRAPPLE_FLASH));
		particles.register(TitanfallParticles.GRAPPLE_RING, sprites -> new PilotParticle.Factory(sprites, PilotParticle.GRAPPLE_RING));
		particles.register(TitanfallParticles.GRAPPLE_TRAIL, sprites -> new PilotParticle.Factory(sprites, PilotParticle.GRAPPLE_TRAIL));
		particles.register(TitanfallParticles.STIM_TRAIL, sprites -> new PilotParticle.Factory(sprites, PilotParticle.STIM_TRAIL));
		particles.register(TitanfallParticles.STIM_FLASH, sprites -> new PilotParticle.Factory(sprites, PilotParticle.STIM_FLASH));
		particles.register(TitanfallParticles.PHASE_SPARK, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PHASE_SPARK));
		particles.register(TitanfallParticles.PHASE_FLASH, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PHASE_FLASH));
		particles.register(TitanfallParticles.PHASE_RING, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PHASE_RING));
		particles.register(TitanfallParticles.PHASE_WISP, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PHASE_WISP));
		particles.register(TitanfallParticles.PULSE_TRAIL, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PULSE_TRAIL));
		particles.register(TitanfallParticles.PULSE_SPARK, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PULSE_SPARK));
		particles.register(TitanfallParticles.PULSE_RING, sprites -> new PilotParticle.Factory(sprites, PilotParticle.PULSE_RING));

		// The jumpkit item is drawn with the same OBJ mesh as the worn kit (inventory, hands, ground, frames).
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_JUMPKIT, new JumpkitItemRenderer());
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_GRAPPLE, new GrappleItemRenderer());
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_STIM, new StimItemRenderer());
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_PHASE_SHIFT, new PhaseItemRenderer());
		BuiltinItemRendererRegistry.INSTANCE.register(TitanfallItems.PILOT_PULSE_BLADE, new PulseItemRenderer());

		EntityModelLayerRegistry.registerModelLayer(JumpkitModel.LAYER, JumpkitModel::getTexturedModelData);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				registrationHelper.register(new JumpkitFeatureRenderer(playerRenderer, context.getModelLoader()));
				registrationHelper.register(new GrappleFeatureRenderer(playerRenderer));
				registrationHelper.register(new StimFeatureRenderer(playerRenderer));
				registrationHelper.register(new PhaseFeatureRenderer(playerRenderer));
				registrationHelper.register(new PulseFeatureRenderer(playerRenderer));
			}
		});
	}
}
