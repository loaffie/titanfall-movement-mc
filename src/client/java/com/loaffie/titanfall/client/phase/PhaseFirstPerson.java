package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.phase.PhaseShift;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The shift in first person, one-handed: the gauntlet hand comes up from below into the bottom corner of
 * the screen on its own side - bottom left for a left hand, bottom right for a right - with the back of the
 * hand turned to the eyes; the fingers flick, the coil fires blue and arcs crawl over it; it holds a moment,
 * then drops out of view. Whatever that hand held stays down meanwhile.
 *
 * <p>As with the Stim, the arm is drawn as a forearm on its own (its joint at the elbow, below the screen)
 * and placed by key points in view space - x right, y up, -z ahead, blocks - mirrored for the right hand.
 */
public final class PhaseFirstPerson {
	/** The coil's centre with the hand up (a left hand; x is mirrored for a right one), and down out of view. */
	private static final Vector3f COIL_UP = new Vector3f(-0.33F, -0.21F, -0.76F);
	private static final Vector3f COIL_DOWN = new Vector3f(-0.62F, -1.1F, -0.5F);
	/** The flick pushes the hand forward and in a touch. */
	private static final Vector3f FLICK = new Vector3f(0.025F, 0.02F, -0.05F);
	/** Elbow to hand: leaning up, in toward the middle of the screen and away from the eye. */
	private static final Vector3f FOREARM = new Vector3f(0.32F, 0.86F, -0.4F);
	/** The back of the hand faces the eye, tipped a little up and in. */
	private static final Vector3f BACK_OF_HAND = new Vector3f(0.18F, 0.25F, 1.0F);
	/** How far the forearm rolls as the fingers flick, radians. */
	private static final float FLICK_ROLL = 0.3F;

	private PhaseFirstPerson() {
	}

	/** True while the raised gauntlet hand is drawn on {@code arm}: vanilla leaves that hand to it. */
	public static boolean replacesArm(ClientPlayerEntity player, Arm arm) {
		return PhaseShift.wornArm(player) == arm && PhaseAnimation.raise(PhaseAnimation.progress(player, 0.0F)) > 0.0F;
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		Arm arm = PhaseShift.wornArm(player);
		PhaseStates.State state = PhaseStates.of(player);
		if (arm == null || state == null || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		float t = state.seconds(tickDelta);
		float up = PhaseAnimation.raise(t);
		if (up < 0.01F) {
			return;
		}

		float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
		float flick = PhaseAnimation.flick(t);
		Vector3f coil = new Vector3f(COIL_DOWN).lerp(COIL_UP, up).add(new Vector3f(FLICK).mul(flick));
		coil.x *= -side;

		// The forearm's frame: +Y along the forearm, -Z out of the back of the hand, X completing it.
		Vector3f forearm = new Vector3f(FOREARM.x * -side, FOREARM.y, FOREARM.z).normalize();
		Vector3f back = new Vector3f(BACK_OF_HAND.x * -side, BACK_OF_HAND.y, BACK_OF_HAND.z);
		back.sub(new Vector3f(forearm).mul(back.dot(forearm))).normalize();
		Vector3f z = new Vector3f(back).negate();
		Vector3f x = new Vector3f(forearm).cross(z).normalize();
		Quaternionf frame = new Quaternionf().setFromNormalized(new Matrix3f(x, forearm, z))
				.rotateY(side * FLICK_ROLL * flick);

		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		// The coil in the arm part's own space, blocks: on the arm's centre line (wide arms' boxes sit 1 px off
		// the joint, slim ones 0.5 px), on the back of the hand.
		float centre = (arm == Arm.LEFT ? 1.0F : -1.0F) * (slim ? 0.5F : 1.0F);
		Vector3f coilOnArm = new Vector3f(centre, PhaseModel.COIL.y, PhaseModel.COIL.z).div(16.0F);
		Vector3f elbow = new Vector3f(coil).sub(frame.transform(new Vector3f(coilOnArm)));

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		matrices.multiply(frame);
		renderForearm(matrices, consumers, player, arm, light);
		GrappleModel.onArm(matrices, arm, slim);
		float glow = state.glow(tickDelta) * PhaseAnimation.ignition(t);
		PhaseModel.render(matrices, consumers, light, OverlayTexture.DEFAULT_UV, glow, player.age + tickDelta, player.getId());
		matrices.pop();
	}

	/** The arm and sleeve, drawn at the current matrix with no joint transform of their own. */
	private static void renderForearm(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, Arm side, int light) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
		PlayerEntityModel<?> model = renderer.getModel();
		ModelPart arm = side == Arm.LEFT ? model.leftArm : model.rightArm;
		ModelPart sleeve = side == Arm.LEFT ? model.leftSleeve : model.rightSleeve;
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
