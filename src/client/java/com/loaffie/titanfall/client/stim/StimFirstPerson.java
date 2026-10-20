package com.loaffie.titanfall.client.stim;

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
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The injection in first person. Whatever the right hand holds drops out of view; the right forearm comes up
 * from below into the lower right of the screen with the injector in the fist, needle down, and stabs it
 * into the right side of the chest just below the view - the vial emptying as the plunger goes down - then
 * drops away, and the held item comes back up.
 *
 * <p>The arm is drawn as a forearm on its own: its joint is treated as the elbow, below the screen, and the
 * fist is placed by key points in view space (x right, y up, -z ahead, blocks) instead of vanilla's hand
 * transform, which can only swing the whole arm from the shoulder.
 */
public final class StimFirstPerson {
	/** The fist hidden below the screen (drawing from the hip), and up in front of the right side of the chest. */
	private static final Vector3f FIST_DOWN = new Vector3f(0.46F, -1.1F, -0.5F);
	private static final Vector3f FIST_UP = new Vector3f(0.28F, -0.08F, -0.7F);
	/** The stab: back toward the chest (toward the camera), a little down - small enough to stay in view. */
	private static final Vector3f STAB = new Vector3f(-0.04F, -0.05F, 0.1F);
	/** The forearm's direction, elbow to fist: leaning up, in toward the middle and away from the eye. */
	private static final Vector3f FOREARM = new Vector3f(-0.3F, 0.88F, -0.36F).normalize();
	/** Elbow to fist, blocks: the fist's distance along the arm (9 px of the 12 px arm). */
	private static final float FOREARM_LENGTH = 9.0F / 16.0F;
	/**
	 * Where the needle points, from the fist: down and in toward the middle of the chest (the bottom middle of
	 * the screen), a little toward the camera - so the injector lies across the lower right of the view.
	 */
	private static final Vector3f NEEDLE = new Vector3f(-0.5F, -0.35F, 0.45F).normalize();
	/** The fist in the arm's own space, centred on its axis (pixels). */
	private static final Vector3f GRIP = new Vector3f(0.0F, 9.0F, 0.0F);

	private StimFirstPerson() {
	}

	/**
	 * How far vanilla's right-hand item is pushed down out of view (as its equip animation does), 0..1, so
	 * the injecting hand has the frame to itself.
	 */
	public static float lowerRightHand(ClientPlayerEntity player, float tickDelta) {
		return StimAnimation.handAway(StimAnimation.progress(player, tickDelta));
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		float t = StimAnimation.progress(player, tickDelta);
		float up = StimAnimation.raise(t) * (1.0F - StimAnimation.toHip(t));
		if (up < 0.01F || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		// The hand pass takes view-space positions (its stack starts with the inverse of the view rotation that
		// the model-view matrix then applies), and its matrices output camera-relative, world-aligned points:
		// the camera's rotation carries a view-space point into that output space, for the needle's target.
		Quaternionf view = client.gameRenderer.getCamera().getRotation();
		Vector3f fist = new Vector3f(FIST_DOWN).lerp(FIST_UP, up).add(new Vector3f(STAB).mul(StimAnimation.jab(t)));
		Vector3f elbow = new Vector3f(fist).sub(new Vector3f(FOREARM).mul(FOREARM_LENGTH));

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		// Arm +Y (toward the hand) along the forearm, with the back of the hand turned toward the camera.
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), FOREARM).rotateY((float) Math.toRadians(-35.0)));
		// Centre the arm box (x -3..1 px) on the forearm's axis. Model parts are drawn in blocks (they divide their
		// pixel geometry by 16 themselves); the injector mesh is in pixels, so it gets the scale afterwards.
		matrices.translate(1.0F / 16.0F, 0.0F, 0.0F);
		renderForearm(matrices, consumers, player, light);
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

		if (StimAnimation.inHand(t)) {
			Vector3f grip = new Vector3f(GRIP);
			// The needle's target, a hand's length past the fist along the needle, in the same camera-relative space.
			Vector3f target = view.transform(new Vector3f(fist).add(new Vector3f(NEEDLE).mul(0.5F)));
			StimModel.renderInFist(matrices, consumers, light, grip, target, 0.0F, StimAnimation.fill(player, tickDelta), StimModel.CAP_GRIP_Y);
		}

		matrices.pop();
	}

	/** The right arm and sleeve, drawn at the current matrix with no joint transform of their own. */
	private static void renderForearm(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, int light) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
		PlayerEntityModel<?> model = renderer.getModel();
		ModelPart arm = model.rightArm;
		ModelPart sleeve = model.rightSleeve;
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
