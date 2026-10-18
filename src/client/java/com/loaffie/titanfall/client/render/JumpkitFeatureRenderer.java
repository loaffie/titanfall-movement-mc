package com.loaffie.titanfall.client.render;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.client.particle.PilotEffects;
import com.loaffie.titanfall.client.particle.PilotThrusters;
import com.loaffie.titanfall.item.Jumpkit;
import com.loaffie.titanfall.client.anim.PilotAnimations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.model.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Pilot Jumpkit on any player wearing it in the chest slot - the local player in third person
 * and every other player in view, since equipment is synced to all tracking clients.
 *
 * <p>Draws the OBJ model ({@link JumpkitMesh}) with a lit base pass and a fully-bright emissive pass for the
 * thruster exhausts, or the built-in cuboid kit if the OBJ cannot be loaded.
 *
 * <p>The kit is attached to the player model's body part: {@code body.rotate()} applies the body's
 * pivot and rotation (including the crouch lean), so the pack and its thruster pods follow the torso
 * through every pose without any animation code of their own.
 */
public class JumpkitFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	/** Fallback cuboid kit texture. */
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/pilot_jumpkit.png");
	/** OBJ kit: Grapple-pilot colour atlas, and the emissive map (black except the thruster exhaust glow). */
	public static final Identifier OBJ_TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/jumpkit.png");
	public static final Identifier OBJ_EMISSIVE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/jumpkit_emissive.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	/** The turbine disc: radius (just inside the plate's) and how far in front of the plate it sits, mesh units. */
	private static final float TURBINE_RADIUS = 21.0F;
	private static final float TURBINE_OFFSET = 1.2F;
	/** Clearance kept round an arm, pixels (the sleeve layer is 0.25 of it). */
	private static final float ARM_MARGIN = 0.75F;

	private final JumpkitModel model;

	public JumpkitFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
			EntityModelLoader modelLoader) {
		super(context);
		this.model = new JumpkitModel(modelLoader.getModelPart(JumpkitModel.LAYER));
	}

	/**
	 * The articulated kit: the pack (raised, tucked or rolled as {@link JumpkitPoser} solves it), then each
	 * thruster module on its hub with its flaps and exhaust glow, and the hoses skinned between the two.
	 * The thruster ports are recorded from each module's own matrix, so effects leave the nozzles wherever
	 * the modules have swung.
	 */
	private static void renderArticulated(JumpkitMesh mesh, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			int glowColor, float[] flaps, AbstractClientPlayerEntity player, float level, float tickDelta, JumpkitPoser.ArmBox[] arms) {
		Matrix4f kitToCamera = new Matrix4f(matrices.peek().getPositionMatrix());
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		Vec3d feet = player.getLerpedPos(tickDelta);
		Vector3f center = new Vector3f((float) (feet.x - camera.x), (float) (feet.y - camera.y), (float) (feet.z - camera.z));
		// The inventory doll is drawn in GUI space: no contact with the world there.
		Vector3f origin = kitToCamera.transformPosition(new Vector3f());
		boolean inWorld = origin.distanceSquared(center) < 9.0F;
		JumpkitPoser.Pose pose = JumpkitPoser.solve(player, PilotAnimations.current(player), mesh, kitToCamera, center, inWorld, level, tickDelta, arms);

		matrices.push();
		matrices.translate(0.0F, -pose.raise, -pose.tuck);
		if (pose.roll != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.roll));
		}

		// Base pass. Each layer's buffer is fetched right before it is filled and never held across layers:
		// fetching another layer's buffer can finish the previous one.
		VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(OBJ_TEXTURE));
		mesh.render("body", matrices.peek(), base, light, OverlayTexture.DEFAULT_UV, WHITE);
		for (int side = 0; side < 2; side++) {
			mesh.renderSkinned(JumpkitMesh.HOSES[side], matrices.peek(), base, light, OverlayTexture.DEFAULT_UV, WHITE,
					mesh.pivot(side), JumpkitPoser.podRotation(pose, side));
			pushModule(mesh, matrices, pose, side);
			PilotEffects.recordPort(player, side, matrices.peek());
			mesh.render(JumpkitMesh.PODS[side], matrices.peek(), base, light, OverlayTexture.DEFAULT_UV, WHITE);
			mesh.renderFlaps(side, matrices, base, light, OverlayTexture.DEFAULT_UV, WHITE, flaps);
			matrices.pop();
		}

		// Emissive pass: the exhaust plates again with the eyes layer - additive and drawn at full brightness,
		// so the thrusters glow in pitch darkness whatever the block or sky light.
		VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEyes(OBJ_EMISSIVE));
		float flare = PilotThrusters.flare(player, tickDelta);
		for (int side = 0; side < 2; side++) {
			pushModule(mesh, matrices, pose, side);
			MatrixStack.Entry entry = matrices.peek();
			float burn = PilotThrusters.level(player, side);
			// Exhaust plate: always glowing, brighter as the thruster burns.
			mesh.render(JumpkitMesh.GLOWS[side], entry, glow, FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
					grey(glowColor, Math.min(1.0F, 0.55F + 0.45F * burn + 0.4F * flare)));
			// Nozzle inner wall: a faint blue when idle, lit up from the plate when firing.
			mesh.render(JumpkitMesh.INNER_GLOWS[side], entry, glow, FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
					grey(0xFFFFFFFF, Math.min(1.0F, 0.25F + 0.75F * burn + 0.5F * flare)));
			// Turbine: a vane disc just in front of the plate, spinning up with the burn.
			float sign = side == 0 ? 1.0F : -1.0F;
			Vector3f axis = new Vector3f(sign * JumpkitModel.NOZZLE_DIR_X, JumpkitModel.NOZZLE_DIR_Y, JumpkitModel.NOZZLE_DIR_Z).normalize();
			Vector3f centre = new Vector3f(sign * JumpkitModel.NOZZLE_X, JumpkitModel.NOZZLE_Y, JumpkitModel.NOZZLE_Z).add(new Vector3f(axis).mul(TURBINE_OFFSET));
			JumpkitMesh.renderDisc(entry, glow, centre, axis, TURBINE_RADIUS, sign * PilotThrusters.spin(player, side), OverlayTexture.DEFAULT_UV,
					grey(0xFFFFFFFF, Math.min(1.0F, 0.35F + 0.65F * burn + 0.3F * flare)));
			matrices.pop();
		}

		matrices.pop();
	}

	/** Scales an opaque white-ish ARGB colour's RGB by {@code k}. */
	private static int grey(int color, float k) {
		int r = Math.round(((color >> 16) & 0xFF) * k);
		int g = Math.round(((color >> 8) & 0xFF) * k);
		int b = Math.round((color & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/**
	 * An arm as the poser sees it: the inverse of the arm's own render transform (model root, then the
	 * part's pivot and rotation, in pixels), and its cuboid widened by {@link #ARM_MARGIN} (sleeve included).
	 * The x range is the wide arm's; slim arms are a pixel narrower, so they are covered too.
	 */
	private static JumpkitPoser.ArmBox armBox(Matrix4f modelRoot, ModelPart arm, float x0, float x1) {
		Matrix4f toCamera = new Matrix4f(modelRoot)
				.translate(arm.pivotX / 16.0F, arm.pivotY / 16.0F, arm.pivotZ / 16.0F)
				.rotate(new Quaternionf().rotationZYX(arm.roll, arm.yaw, arm.pitch))
				.scale(1.0F / 16.0F);
		return new JumpkitPoser.ArmBox(toCamera.invert(), x0 - ARM_MARGIN, -2.0F - ARM_MARGIN, -2.0F - ARM_MARGIN,
				x1 + ARM_MARGIN, 10.0F + ARM_MARGIN, 2.0F + ARM_MARGIN);
	}

	/** Pushes a thruster module's articulation (rotation about its hub) onto the stack. */
	private static void pushModule(JumpkitMesh mesh, MatrixStack matrices, JumpkitPoser.Pose pose, int side) {
		float[] pivot = mesh.pivot(side);
		matrices.push();
		matrices.translate(pivot[0], pivot[1], pivot[2]);
		matrices.multiply(JumpkitPoser.podRotation(pose, side));
		matrices.translate(-pivot[0], -pivot[1], -pivot[2]);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!Jumpkit.isWorn(player) || player.isInvisible()) {
			return;
		}

		matrices.push();
		// The model root (before the body part's own transform), where the arms hang: their boxes are
		// handed to the poser so the thruster modules keep out of them.
		Matrix4f modelRoot = new Matrix4f(matrices.peek().getPositionMatrix());
		// The body part's pivot and rotation: the kit rides the torso through every pose - crouch, the wall
		// run lean, the slide recline and the slide-hop coil all move the body part (or the whole model).
		this.getContextModel().body.rotate(matrices);

		// The exhaust burns brighter in the air, where the kit is actually doing work, but never goes dark.
		float intensity = player.isOnGround() ? 0.7F : 1.0F;
		int channel = Math.round(255 * intensity);
		int glowColor = 0xFF000000 | (channel << 16) | (channel << 8) | channel;

		JumpkitMesh mesh = JumpkitMesh.get();
		if (mesh != null) {
			matrices.push();
			matrices.translate(JumpkitModel.OBJ_OFFSET_X / 16.0F, JumpkitModel.OBJ_OFFSET_Y / 16.0F, JumpkitModel.OBJ_OFFSET_Z / 16.0F);
			float scale = JumpkitModel.OBJ_SCALE / 16.0F;
			matrices.scale(scale, scale, scale);
			float level = PilotThrusters.level(player);
			float[] flaps = new float[JumpkitMesh.FLAPS.length];
			for (int i = 0; i < flaps.length; i++) {
				// Flaps 0-2 are on the +X module (port 0), 3-5 on the -X one: each opens with its own burn.
				flaps[i] = PilotThrusters.flapAngle(player, i, PilotThrusters.level(player, i / 3), tickDelta);
			}

			if (mesh.articulated()) {
				JumpkitPoser.ArmBox[] arms = {armBox(modelRoot, this.getContextModel().rightArm, -3.0F, 1.0F),
						armBox(modelRoot, this.getContextModel().leftArm, -1.0F, 3.0F)};
				renderArticulated(mesh, matrices, vertexConsumers, light, glowColor, flaps, player, level, tickDelta, arms);
			} else {
				// Single-piece model (e.g. from a resource pack): drawn rigidly.
				MatrixStack.Entry entry = matrices.peek();
				PilotEffects.recordPort(player, 0, entry);
				PilotEffects.recordPort(player, 1, entry);
				// Each layer's buffer is fetched right before it is filled: fetching another layer's buffer can
				// finish the previous one, so consumers must never be held across layers.
				VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(OBJ_TEXTURE));
				mesh.render("body", entry, base, light, OverlayTexture.DEFAULT_UV, WHITE);
				mesh.renderFlaps(0, matrices, base, light, OverlayTexture.DEFAULT_UV, WHITE, flaps);
				mesh.renderFlaps(1, matrices, base, light, OverlayTexture.DEFAULT_UV, WHITE, flaps);
				mesh.render("glow", entry, vertexConsumers.getBuffer(RenderLayer.getEyes(OBJ_EMISSIVE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glowColor);
			}

			matrices.pop();
		} else {
			VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
			model.kit().render(matrices, solid, light, OverlayTexture.DEFAULT_UV);
			VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE));
			model.glow().render(matrices, glow, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glowColor);
		}

		matrices.pop();
	}
}
