package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.pulse.PulseBlade;
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
 * The Pulse Blade in first person, thrown overhand like a baseball with the pilot's main hand (the right,
 * mirrored for a left-handed pilot), as Titanfall 2's kunai viewmodel does. The weapon dips out of view; the
 * hand draws the kunai across the body from the sheath on the left hip and pulls it right back beside the
 * head - the forearm filling the side of the view, the kunai gripped with its blade pointing back, away from
 * the throw - and holds it there, breathing, while the pilot aims along the arc. The throw: a last pull
 * back, the arm whipping over the top and forward, letting go as it reaches out, and the follow-through
 * down and across the body and out of view.
 *
 * <p>The arm is a forearm on its own (its joint at the elbow, below the view), posed by keyframes in view
 * space (x right, y up, -z ahead, blocks): where the fist is and how the hand is turned. Positions run along
 * a Catmull-Rom curve through the keys and the hand's turn is slerped between them, with easing that keeps
 * the speed continuous through the release, so the swing has no corners. The kunai is fixed in the fist and
 * turns with the wrist.
 */
public final class PulseFirstPerson {
	/** One pose: the fist (view space, blocks) and the hand's turn (arm space to view space). */
	private record Key(Vector3f fist, Quaternionf turn) {
		/**
		 * A pose for a right hand (x is mirrored for a left one) from the fist, the forearm's direction (elbow to
		 * fist) and where the knuckles face.
		 */
		static Key of(float side, float x, float y, float z, Vector3f forearm, Vector3f knuckles) {
			Vector3f up = new Vector3f(forearm.x * side, forearm.y, forearm.z).normalize();
			// Arm space: +Y toward the hand, -Z the front of the arm (the knuckles of a closed fist).
			Vector3f back = new Vector3f(-knuckles.x * side, -knuckles.y, -knuckles.z);
			back.sub(new Vector3f(up).mul(back.dot(up))).normalize();
			Vector3f across = new Vector3f(up).cross(back).normalize();
			return new Key(new Vector3f(x * side, y, z), new Quaternionf().setFromNormalized(new Matrix3f(across, up, back)));
		}
	}

	/** The keys of one hand: the draw's start, and the throw's ready pose through to gone. */
	private record Keys(Key hip, Key[] toss) {
		static Keys of(float side) {
			return new Keys(
					// Low across the body at the left hip, where the hand takes the kunai from its sheath.
					Key.of(side, -0.3F, -1.25F, -0.62F, new Vector3f(-0.35F, 0.9F, 0.1F), new Vector3f(-0.2F, 0.1F, -1.0F)),
					new Key[] {
							// Pulled back beside the head: the forearm rising up the side of the view, back toward the eye;
							// the knuckles, and the blade with them, pointing back over the shoulder.
							Key.of(side, 0.36F, 0.06F, -0.5F, new Vector3f(0.22F, 0.9F, 0.38F), new Vector3f(0.35F, 0.3F, 0.88F)),
							// The last pull back before the throw.
							Key.of(side, 0.4F, 0.1F, -0.43F, new Vector3f(0.26F, 0.85F, 0.46F), new Vector3f(0.4F, 0.45F, 0.8F)),
							// Over the top and out ahead: the release.
							Key.of(side, 0.1F, 0.02F, -0.95F, new Vector3f(-0.05F, 0.6F, -0.8F), new Vector3f(0.0F, 0.75F, -0.66F)),
							// The follow-through, down and across the body.
							Key.of(side, -0.22F, -0.5F, -0.82F, new Vector3f(-0.45F, 0.2F, -0.87F), new Vector3f(-0.3F, -0.6F, -0.75F)),
							// Gone, below the view on the far side.
							Key.of(side, -0.32F, -1.3F, -0.6F, new Vector3f(-0.35F, 0.25F, -0.9F), new Vector3f(-0.3F, -0.6F, -0.75F))});
		}
	}

	private static final Keys RIGHT = Keys.of(1.0F);
	private static final Keys LEFT = Keys.of(-1.0F);
	/** The share of the throw each key is reached at; the release comes just before the arm is out. */
	private static final float[] TOSS_AT = {0.0F, 0.12F, 0.4F, 0.7F, 1.0F};

	/** The fist's place along the arm (pixels from the joint): the middle of the hand. */
	private static final float FIST_Y = 9.0F;
	/** Breathing while held ready, blocks. */
	private static final float SWAY = 0.005F;

	private PulseFirstPerson() {
	}

	/**
	 * How far the main-hand item is lowered out of view (as its equip animation does), 0..1: down as the kunai
	 * is drawn, back up once it has gone.
	 */
	public static float lowerMainHand(ClientPlayerEntity player, float tickDelta) {
		PulseStates.State state = PulseStates.of(player);
		if (state == null) {
			return 0.0F;
		}

		float t = state.ticks + tickDelta;
		return switch (state.stage) {
			case DRAW -> easeInOut(MathHelper.clamp(t / 2.0F, 0.0F, 1.0F));
			case AIM -> 1.0F;
			case THROW -> 1.0F - easeInOut(MathHelper.clamp((t / PulseStates.THROW_TICKS - 0.7F) / 0.3F, 0.0F, 1.0F));
		};
	}

	/** True while the throwing hand is in view: vanilla leaves that hand to it. */
	public static boolean replacesArm(ClientPlayerEntity player, Arm arm) {
		return arm == PulseBlade.throwingArm(player) && PulseStates.of(player) != null;
	}

	/** Called from the held-item renderer, after vanilla's hands, in its view-space matrix stack. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity player, float tickDelta, int light) {
		PulseStates.State state = PulseStates.of(player);
		if (state == null || player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}

		Arm arm = PulseBlade.throwingArm(player);
		Keys keys = arm == Arm.RIGHT ? RIGHT : LEFT;
		Key pose = pose(keys, state, player.age + tickDelta, tickDelta);
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		// The arm's box sits off its joint (a right arm's is x -3..1 px, a left's -1..3; slim ones 0.5 px less):
		// centre it on the forearm's axis.
		float centre = (arm == Arm.RIGHT ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F);
		Vector3f fistOnArm = new Vector3f(centre, FIST_Y, 0.0F).div(16.0F);
		Vector3f elbow = new Vector3f(pose.fist()).sub(pose.turn().transform(fistOnArm));

		matrices.push();
		matrices.translate(elbow.x, elbow.y, elbow.z);
		matrices.multiply(pose.turn());
		renderForearm(matrices, consumers, player, arm, light);
		if (state.inHand()) {
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			PulseModel.renderHeld(matrices, consumers, light, centre);
		}

		matrices.pop();
	}

	/** The hand's pose for this point of the draw, the aim or the throw. */
	private static Key pose(Keys keys, PulseStates.State state, float time, float tickDelta) {
		float t = state.ticks + tickDelta;
		Key ready = keys.toss()[0];
		return switch (state.stage) {
			case DRAW -> blend(keys.hip(), ready, easeOut(t / PulseStates.DRAW_TICKS));
			case AIM -> {
				// Breathing: a slow figure-eight drift of a few millimetres.
				Vector3f fist = new Vector3f(ready.fist()).add(SWAY * MathHelper.sin(time * 0.09F), SWAY * MathHelper.sin(time * 0.18F) * 0.8F, 0.0F);
				yield new Key(fist, ready.turn());
			}
			case THROW -> toss(keys.toss(), MathHelper.clamp(t / PulseStates.THROW_TICKS, 0.0F, 1.0F));
		};
	}

	/**
	 * The throw at {@code u} 0..1, along the curve through the keys. The last pull back eases in and out (a beat
	 * at the top), the whip forward speeds up into the release, the follow-through slows from it, and the hand
	 * drops away gathering speed again.
	 */
	private static Key toss(Key[] keys, float u) {
		int i = 0;
		while (i < keys.length - 2 && u > TOSS_AT[i + 1]) {
			i++;
		}

		float local = MathHelper.clamp((u - TOSS_AT[i]) / (TOSS_AT[i + 1] - TOSS_AT[i]), 0.0F, 1.0F);
		float eased = switch (i) {
			case 0 -> easeInOut(local);
			case 1 -> easeIn(local);
			case 2 -> easeOut(local);
			default -> easeIn(local);
		};

		Vector3f p0 = keys[Math.max(i - 1, 0)].fist();
		Vector3f p1 = keys[i].fist();
		Vector3f p2 = keys[i + 1].fist();
		Vector3f p3 = keys[Math.min(i + 2, keys.length - 1)].fist();
		return new Key(catmullRom(p0, p1, p2, p3, eased), new Quaternionf(keys[i].turn()).slerp(keys[i + 1].turn(), eased));
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
