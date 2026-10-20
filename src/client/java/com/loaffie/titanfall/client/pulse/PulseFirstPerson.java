package com.loaffie.titanfall.client.pulse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Pulse Blade in first person, left-handed: the left hand comes up from the hip into the left of the
 * screen holding the kunai overhand, tip forward, and holds it cocked while the pilot aims along the arc;
 * on the throw the hand whips forward and in toward the middle of the view, letting go, and drops away.
 *
 * <p>As with the Stim, the arm is drawn as a forearm on its own (its joint at the elbow, off screen) and
 * placed by key points in view space - x right, y up, -z ahead, blocks.
 */
public final class PulseFirstPerson {
	/** The fist down by the hip (out of view), cocked up at the left of the view, and followed through. */
	private static final Vector3f FIST_DOWN = new Vector3f(-0.75F, -1.2F, -0.7F);
	private static final Vector3f FIST_COCKED = new Vector3f(-0.52F, -0.3F, -0.88F);
	private static final Vector3f FIST_THROWN = new Vector3f(0.05F, -0.62F, -0.9F);
	/** The forearm, elbow to fist: cocked it stands up, leaning in; thrown it reaches ahead. */
	private static final Vector3f FOREARM_COCKED = new Vector3f(0.32F, 0.92F, -0.1F).normalize();
	private static final Vector3f FOREARM_THROWN = new Vector3f(0.3F, 0.4F, -0.86F).normalize();
	/** Elbow to fist, blocks (9 px of the 12 px arm). */
	private static final float FOREARM_LENGTH = 9.0F / 16.0F;
	/** Where the kunai's tip points from the cocked fist: ahead, up and in across the view, so it reads clear of the hand. */
	private static final Vector3f TIP = new Vector3f(0.5F, 0.4F, -1.0F).normalize();
	/** The fist in the arm's own space, centred on its axis (pixels). */
	private static final Vector3f GRIP = new Vector3f(0.0F, 9.0F, 0.0F);

	private PulseFirstPerson() {
	}

	/** True while the throwing hand is up: vanilla leaves the left hand to it. */
	public static boolean replacesArm(ClientPlayerEntity player, Arm arm) {
		PulseStates.State state = PulseStates.of(player);
		return arm == Arm.LEFT && state != null && state.raise(0.0F) > 0.0F;
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		PulseStates.State state = PulseStates.of(player);
		if (state == null || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		float up = state.raise(tickDelta);
		if (up < 0.01F) {
			return;
		}

		float swing = state.swing(tickDelta);
		Vector3f fist = new Vector3f(FIST_DOWN).lerp(FIST_COCKED, up).lerp(FIST_THROWN, swing);
		Vector3f forearm = new Vector3f(FOREARM_COCKED).lerp(FOREARM_THROWN, swing).normalize();
		Vector3f elbow = new Vector3f(fist).sub(new Vector3f(forearm).mul(FOREARM_LENGTH));
		Quaternionf view = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		// Arm +Y (toward the hand) along the forearm, the back of the hand turned out to the left.
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), forearm).rotateY((float) Math.toRadians(35.0)));
		// Centre the left arm's box (x -1..3 px) on the forearm's axis; the kunai mesh is in pixels.
		matrices.translate(-1.0F / 16.0F, 0.0F, 0.0F);
		renderForearm(matrices, consumers, player, light);
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		if (state.inHand()) {
			// The tip's target, a stride ahead of the fist, in the same camera-relative space the matrices end in.
			Vector3f target = view.transform(new Vector3f(fist).add(new Vector3f(TIP).mul(0.6F)));
			PulseModel.renderInHand(matrices, consumers, light, new Vector3f(GRIP), target, true);
		}

		matrices.pop();
	}

	/** The left arm and sleeve, drawn at the current matrix with no joint transform of their own. */
	private static void renderForearm(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, int light) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
		PlayerEntityModel<?> model = renderer.getModel();
		ModelPart arm = model.leftArm;
		ModelPart sleeve = model.leftSleeve;
		ModelTransform armTransform = arm.getTransform();
		ModelTransform sleeveTransform = sleeve.getTransform();
		arm.setTransform(ModelTransform.NONE);
		sleeve.setTransform(ModelTransform.NONE);
		Identifier skin = player.getSkinTextures().texture();
		arm.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
		if (sleeve.visible) {
			sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
		}

		arm.setTransform(armTransform);
		sleeve.setTransform(sleeveTransform);
	}
}
