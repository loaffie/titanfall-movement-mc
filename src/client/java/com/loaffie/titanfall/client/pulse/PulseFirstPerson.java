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
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Pulse Blade in first person, left-handed. The hand comes up from the hip into the lower left of the view
 * with the kunai gripped overhand, blade up and forward past the fingers, and holds it ready - breathing a
 * little - while the pilot aims along the arc. The throw is one continuous motion: a short wind-up back
 * toward the shoulder, a fast forward snap that lets the kunai go as the arm reaches out, and a follow-through
 * down across the view that carries the hand away.
 *
 * <p>The arm is a forearm on its own (its joint at the elbow, below the view), posed by keyframes in view
 * space (x right, y up, -z ahead, blocks): where the fist is and how the hand is turned. Positions run along
 * a Catmull-Rom curve through the keys and the hand's turn is slerped between them, with easing that keeps
 * the speed continuous through the release, so the swing has no corners. The kunai is fixed in the fist and
 * moves with the wrist.
 */
public final class PulseFirstPerson {
	/** One pose: the fist (view space, blocks) and the hand's turn (arm space to view space). */
	private record Key(Vector3f fist, Quaternionf turn) {
		/** A pose from the fist, the forearm's direction (elbow to fist) and where the front of the hand faces. */
		static Key of(float x, float y, float z, Vector3f forearm, Vector3f front) {
			Vector3f up = new Vector3f(forearm).normalize();
			// Arm space: +Y toward the hand, -Z the front of the arm (the knuckles, for a fist held overhand).
			Vector3f back = new Vector3f(front).negate();
			back.sub(new Vector3f(up).mul(back.dot(up))).normalize();
			Vector3f side = new Vector3f(up).cross(back).normalize();
			return new Key(new Vector3f(x, y, z), new Quaternionf().setFromNormalized(new Matrix3f(side, up, back)));
		}
	}

	/** Down by the hip, out of view: where the draw starts. */
	private static final Key HIP = Key.of(-0.75F, -1.25F, -0.7F, new Vector3f(0.35F, 0.9F, 0.0F), new Vector3f(0.2F, 0.0F, -1.0F));
	/** Held ready at the lower left: the forearm leaning in across the view, knuckles forward, blade up past them. */
	private static final Key READY = Key.of(-0.42F, -0.3F, -0.86F, new Vector3f(0.45F, 0.85F, -0.2F), new Vector3f(0.25F, 0.15F, -1.0F));
	/** The wind-up: back and up toward the shoulder, the wrist cocked. */
	private static final Key WINDUP = Key.of(-0.48F, -0.18F, -0.76F, new Vector3f(0.35F, 0.9F, 0.2F), new Vector3f(0.15F, 0.45F, -0.88F));
	/** The release: the arm reaching out ahead, the wrist snapped over. */
	private static final Key RELEASE = Key.of(-0.12F, -0.28F, -1.05F, new Vector3f(0.35F, 0.5F, -0.8F), new Vector3f(0.2F, -0.75F, -0.6F));
	/** The follow-through: down and across toward the middle. */
	private static final Key FOLLOW = Key.of(0.04F, -0.52F, -0.95F, new Vector3f(0.38F, 0.3F, -0.88F), new Vector3f(0.12F, -0.95F, -0.25F));
	/** Gone, below the view. */
	private static final Key OUT = Key.of(0.1F, -1.25F, -0.7F, new Vector3f(0.25F, 0.3F, -0.92F), new Vector3f(0.12F, -0.95F, -0.25F));

	/** The throw's keys and the share of the throw each one is reached at. */
	private static final Key[] THROW = {READY, WINDUP, RELEASE, FOLLOW, OUT};
	private static final float[] THROW_AT = {0.0F, 0.16F, 0.38F, 0.7F, 1.0F};

	/**
	 * The kunai in the fist (arm space, pixels): gripped just behind the knuckles, its axis up and forward from
	 * the fist - mostly out past the knuckles, tilted up along the fingers - with the ring behind the hand.
	 */
	private static final Vector3f GRIP = new Vector3f(0.0F, 8.8F, -0.6F);
	private static final Vector3f BLADE = new Vector3f(0.0F, 0.72F, -0.7F).normalize();
	/** Breathing while held ready: blocks, and radians a tick. */
	private static final float SWAY = 0.005F;

	private PulseFirstPerson() {
	}

	/** True while the throwing hand is in view: vanilla leaves the left hand to it. */
	public static boolean replacesArm(ClientPlayerEntity player, Arm arm) {
		return arm == Arm.LEFT && PulseStates.of(player) != null;
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		PulseStates.State state = PulseStates.of(player);
		if (state == null || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		Key pose = pose(state, player.age + tickDelta, tickDelta);
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		// The left arm's box is x -1..3 px (slim -1..2): centre it on the forearm's axis.
		float centre = slim ? 0.5F : 1.0F;
		Vector3f fistOnArm = new Vector3f(centre, GRIP.y, 0.0F).div(16.0F);
		Vector3f elbow = new Vector3f(pose.fist()).sub(pose.turn().transform(fistOnArm));

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		matrices.multiply(pose.turn());
		renderForearm(matrices, consumers, player, light);
		if (state.inHand()) {
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			matrices.translate(centre + GRIP.x, GRIP.y, GRIP.z);
			matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), BLADE));
			matrices.translate(0.0F, -PulseModel.GRIP_Y, 0.0F);
			PulseModel.renderKunai(matrices, consumers, light, true, 0.0F);
		}

		matrices.pop();
	}

	/** The hand's pose for this point of the draw, the aim or the throw. */
	private static Key pose(PulseStates.State state, float time, float tickDelta) {
		float t = state.ticks + tickDelta;
		return switch (state.stage) {
			case DRAW -> blend(HIP, READY, easeOut(t / PulseStates.DRAW_TICKS));
			case AIM -> {
				// Breathing: a slow figure-eight drift of a few millimetres.
				Vector3f fist = new Vector3f(READY.fist()).add(SWAY * MathHelper.sin(time * 0.09F), SWAY * MathHelper.sin(time * 0.18F) * 0.8F, 0.0F);
				yield new Key(fist, READY.turn());
			}
			case THROW -> throwPose(MathHelper.clamp(t / PulseStates.THROW_TICKS, 0.0F, 1.0F));
		};
	}

	/**
	 * The throw at {@code u} 0..1: along the curve through the keys. The wind-up eases in and out (a beat at
	 * the top), the snap forward speeds up into the release, the follow-through slows from it, and the hand
	 * drops away gathering speed again.
	 */
	private static Key throwPose(float u) {
		int i = 0;
		while (i < THROW.length - 2 && u > THROW_AT[i + 1]) {
			i++;
		}

		float local = MathHelper.clamp((u - THROW_AT[i]) / (THROW_AT[i + 1] - THROW_AT[i]), 0.0F, 1.0F);
		float eased = switch (i) {
			case 0 -> easeInOut(local);
			case 1 -> easeIn(local);
			case 2 -> easeOut(local);
			default -> easeIn(local);
		};

		Vector3f p0 = THROW[Math.max(i - 1, 0)].fist();
		Vector3f p1 = THROW[i].fist();
		Vector3f p2 = THROW[i + 1].fist();
		Vector3f p3 = THROW[Math.min(i + 2, THROW.length - 1)].fist();
		return new Key(catmullRom(p0, p1, p2, p3, eased), new Quaternionf(THROW[i].turn()).slerp(THROW[i + 1].turn(), eased));
	}

	private static Key blend(Key from, Key to, float k) {
		return new Key(new Vector3f(from.fist()).lerp(to.fist(), k), new Quaternionf(from.turn()).slerp(to.turn(), k));
	}

	/** Uniform Catmull-Rom: the curve through p1 (t 0) and p2 (t 1), shaped by their neighbours. */
	private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
		float t2 = t * t;
		float t3 = t2 * t;
		Vector3f out = new Vector3f(p1).mul(2.0F);
		out.add(new Vector3f(p2).sub(p0).mul(t));
		out.add(new Vector3f(p0).mul(2.0F).sub(new Vector3f(p1).mul(5.0F)).add(new Vector3f(p2).mul(4.0F)).sub(p3).mul(t2));
		out.add(new Vector3f(p1).mul(3.0F).sub(p0).sub(new Vector3f(p2).mul(3.0F)).add(p3).mul(t3));
		return out.mul(0.5F);
	}

	private static float easeIn(float x) {
		return x * x;
	}

	private static float easeOut(float x) {
		return 1.0F - (1.0F - x) * (1.0F - x);
	}

	private static float easeInOut(float x) {
		return x * x * (3.0F - 2.0F * x);
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
