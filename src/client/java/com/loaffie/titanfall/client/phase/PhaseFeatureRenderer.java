package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the Phase Shift gauntlet on the back of the hand that wears it, on every player in view, with its
 * coil lit while they phase; records where the coil is, so its sparks fly from it. A phased pilot - only
 * ever drawn for other phased pilots, or for themselves in third person - also gets a crawling blue energy
 * sheen over the whole body, the out-of-phase look.
 */
public class PhaseFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final Identifier ENERGY = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
	/** Coils further than this from the player are not from the world render (the inventory doll). */
	private static final double COIL_SANITY_DISTANCE = 4.0;
	/** Where each player's coil was last drawn in the world, and on which tick. */
	private static final Map<AbstractClientPlayerEntity, Vec3d> COILS = new WeakHashMap<>();
	private static final Map<AbstractClientPlayerEntity, Integer> COIL_AGES = new WeakHashMap<>();

	public PhaseFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
		PhaseStates.State state = PhaseStates.of(player);
		if (state != null && state.phased()) {
			float pulse = 0.75F + 0.25F * MathHelper.sin(animationProgress * 0.5F);
			model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEnergySwirl(ENERGY, animationProgress * 0.012F, animationProgress * 0.008F)),
					light, OverlayTexture.DEFAULT_UV, GrappleModel.dim(0xFF4F95FF, 0.55F * pulse));
		}

		Arm arm = PhaseShift.wornArm(player);
		if (arm == null || player.isInvisible()) {
			return;
		}

		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		float glow = state == null ? 0.0F : state.glow(tickDelta);
		matrices.push();
		part.rotate(matrices);
		GrappleModel.onArm(matrices, arm, slim);
		PhaseModel.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, glow, animationProgress, player.getId());
		if (glow > 0.0F) {
			recordCoil(player, matrices.peek().getPositionMatrix().transformPosition(new Vector3f(PhaseModel.COIL)));
		}

		matrices.pop();
	}

	/** Entity rendering is camera-relative: the camera position turns the coil into world space. */
	private static void recordCoil(AbstractClientPlayerEntity player, Vector3f cameraRelative) {
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		Vec3d coil = camera.add(cameraRelative.x, cameraRelative.y, cameraRelative.z);
		if (coil.squaredDistanceTo(player.getPos()) < COIL_SANITY_DISTANCE * COIL_SANITY_DISTANCE) {
			COILS.put(player, coil);
			COIL_AGES.put(player, player.age);
		}
	}

	/** The coil as drawn in the world within the last tick or so, or null. */
	static Vec3d coil(AbstractClientPlayerEntity player) {
		Integer age = COIL_AGES.get(player);
		return age != null && player.age - age <= 1 ? COILS.get(player) : null;
	}
}
