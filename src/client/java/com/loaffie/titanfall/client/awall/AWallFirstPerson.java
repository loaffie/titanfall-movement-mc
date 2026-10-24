package com.loaffie.titanfall.client.awall;

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
 * The toss in first person ({@link AWallAnimation}). Whatever the off hand holds drops out of view; that
 * forearm comes up from below on its side of the screen with the disc held flat in the fist (it was pulled from
 * the pack over the shoulder, out of sight), swings it in across the bottom of the view, and flicks it out
 * ahead - the disc leaving the hand - then drops away.
 *
 * <p>Drawn as {@code StimFirstPerson} draws its arm: a forearm on its own, the fist placed by key points in view
 * space (x right, y up, -z ahead, blocks), for a left arm; a right off hand mirrors x.
 */
public final class AWallFirstPerson {
	/** Below the screen; wound in across the bottom of the view; flung out ahead. */
	private static final Vector3f FIST_DOWN = new Vector3f(-0.6F, -1.0F, -0.5F);
	private static final Vector3f FIST_WOUND = new Vector3f(0.02F, -0.24F, -0.62F);
	private static final Vector3f FIST_FLUNG = new Vector3f(-0.42F, -0.08F, -1.0F);
	/** Elbow to fist: across the body while wound, out along the view when flung. */
	private static final Vector3f FOREARM_WOUND = new Vector3f(0.75F, 0.35F, -0.3F).normalize();
	private static final Vector3f FOREARM_FLUNG = new Vector3f(0.0F, 0.25F, -0.95F).normalize();
	private static final float FOREARM_LENGTH = 9.0F / 16.0F;
	/** The fist in the arm's own space, centred on its axis (pixels), and the disc's size there. */
	private static final float GRIP = 11.5F;
	private static final float DISC_SCALE = 0.8F;
	/** The disc tipped toward the eye, and banked into the throw, degrees. */
	private static final float DISC_TIP = 25.0F;
	private static final float DISC_BANK = 12.0F;

	private AWallFirstPerson() {
	}

	/** How far vanilla's off-hand item is pushed down out of view, 0..1, so the tossing hand has its side of the screen. */
	public static float lowerOffHand(ClientPlayerEntity player, float tickDelta) {
		float t = AWallAnimation.progress(player, tickDelta);
		return t >= 1.0F ? 0.0F : AWallAnimation.smooth(0.0F, 0.15F, t) * (1.0F - AWallAnimation.smooth(0.8F, 1.0F, t));
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		float t = AWallAnimation.progress(player, tickDelta);
		if (t >= 1.0F || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		// Up into view while the hand comes round from the pack, out on the throw, and down after it.
		float up = AWallAnimation.smooth(AWallAnimation.GRAB * 0.6F, AWallAnimation.WIND, t) * (1.0F - AWallAnimation.smooth(AWallAnimation.FOLLOW, 0.95F, t));
		if (up < 0.01F) {
			return;
		}

		float fling = AWallAnimation.smooth(AWallAnimation.WIND, AWallAnimation.RELEASE, t);
		Vector3f fist = new Vector3f(FIST_WOUND).lerp(FIST_FLUNG, fling);
		fist = new Vector3f(FIST_DOWN).lerp(fist, up);
		Vector3f forearm = new Vector3f(FOREARM_WOUND).lerp(FOREARM_FLUNG, fling).normalize();
		Arm arm = player.getMainArm().getOpposite();
		float side = arm == Arm.LEFT ? 1.0F : -1.0F;
		fist.x *= side;
		forearm.x *= side;
		Vector3f elbow = new Vector3f(fist).sub(new Vector3f(forearm).mul(FOREARM_LENGTH));

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		// Arm +Y (toward the hand) along the forearm, palm turned up under the disc.
		Quaternionf armRotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), forearm).rotateY((float) Math.toRadians(side * 80.0));
		matrices.multiply(armRotation);
		matrices.translate(side * -1.0F / 16.0F, 0.0F, 0.0F);
		renderForearm(matrices, consumers, player, light, arm);
		if (AWallAnimation.inHand(t)) {
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			matrices.translate(side * 1.0F, GRIP, 0.0F);
			// Flat in the fist like a frisbee, its rim in the fingers: level in the view whatever the arm does, tipped
			// toward the eye so its lit top shows, and banked a little into the throw.
			matrices.multiply(new Quaternionf(armRotation).conjugate());
			matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(DISC_TIP)).rotateZ((float) Math.toRadians(side * DISC_BANK)));
			matrices.scale(DISC_SCALE, DISC_SCALE, DISC_SCALE);
			// Its rim in the fingers: the disc's middle a radius ahead of the fist.
			matrices.translate(side * 1.0F, -0.7F, -2.6F);
			AWallModel.renderDisc(matrices, consumers, light, 0.0F, AWallModel.AMBER);
		}

		matrices.pop();
	}

	/** The off arm and sleeve, drawn at the current matrix with no joint transform of their own. */
	private static void renderForearm(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, int light, Arm arm) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
		PlayerEntityModel<?> model = renderer.getModel();
		ModelPart part = arm == Arm.LEFT ? model.leftArm : model.rightArm;
		ModelPart sleeve = arm == Arm.LEFT ? model.leftSleeve : model.rightSleeve;
		ModelTransform armTransform = part.getTransform();
		ModelTransform sleeveTransform = sleeve.getTransform();
		part.setTransform(ModelTransform.NONE);
		sleeve.setTransform(ModelTransform.NONE);
		Identifier skin = player.getSkinTextures().texture();
		part.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
		if (sleeve.visible) {
			sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
		}

		part.setTransform(armTransform);
		sleeve.setTransform(sleeveTransform);
	}
}
