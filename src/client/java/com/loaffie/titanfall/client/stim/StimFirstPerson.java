package com.loaffie.titanfall.client.stim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/**
 * The injection in first person: whatever the right hand holds drops out of view, the bare right hand comes
 * up from the hip with the injector and jabs it into the chest just below the view - the vial emptying as
 * the plunger goes down - and drops away again, after which the held item comes back up.
 */
public final class StimFirstPerson {
	/**
	 * Where the needle is aimed, in view space (x right, y up, -z ahead): down and to the left of the fist,
	 * into the chest - across the fist, so the injector is seen side-on rather than end-on or hidden behind
	 * the arm.
	 */
	private static final Vector3f CHEST_VIEW = new Vector3f(-0.9F, -0.75F, -0.25F);

	private StimFirstPerson() {
	}

	/**
	 * How far vanilla's right-hand item is pushed down out of view (as its equip animation does), 0..1, so
	 * the injecting hand has the frame to itself.
	 */
	public static float lowerRightHand(ClientPlayerEntity player, float tickDelta) {
		float t = StimAnimation.progress(player, tickDelta);
		return StimAnimation.handAway(t);
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		float t = StimAnimation.progress(player, tickDelta);
		float raise = StimAnimation.raise(t);
		if (raise < 0.01F || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) client.getEntityRenderDispatcher().getRenderer(player);
		float jab = StimAnimation.jab(t);

		matrices.push();
		// Swing in toward the middle of the view and pull back toward the chest on the jab.
		matrices.translate(-0.3F * raise, 0.2F * raise - 0.12F * jab, 0.1F * jab);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(26.0F * raise));
		// Vanilla's empty right hand (HeldItemRenderer#renderArmHoldingItem, no swing), raised from below.
		float equip = 1.0F - raise;
		matrices.translate(0.64F, -0.6F + equip * -0.7F, -0.72F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
		matrices.translate(-1.0F, 3.6F, 3.5F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(120.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200.0F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-135.0F));
		matrices.translate(5.6F, 0.0F, 0.0F);
		renderer.renderRightArm(matrices, consumers, light, player);

		if (StimAnimation.inHand(t)) {
			renderer.getModel().rightArm.rotate(matrices);
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
			Vector3f fist = new Vector3f(slim ? -0.5F : -1.0F, 9.0F, -0.4F);
			// The hand pass works in camera-relative, world-aligned space: turn the view-space aim point into it.
			Vector3f chest = client.gameRenderer.getCamera().getRotation().transform(new Vector3f(CHEST_VIEW));
			StimModel.renderInFist(matrices, consumers, light, fist, chest, jab * 1.5F, StimAnimation.fill(player, tickDelta));
		}

		matrices.pop();
	}
}
