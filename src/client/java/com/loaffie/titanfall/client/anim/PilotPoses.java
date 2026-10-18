package com.loaffie.titanfall.client.anim;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.physics.HVec;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns {@link PilotAnimations.State} weights into poses, in two layers that vanilla already exposes:
 *
 * <ol>
 *   <li><b>Whole body</b> ({@link #applyBody}), from the tail of {@code PlayerEntityRenderer.setupTransforms}.
 *   The matrix there is the entity's own frame after its body yaw: +X is the player's right, +Y up,
 *   -Z forward. Leans, reclines and launch tilts rotate the whole model (and with it the jumpkit) about
 *   a pivot at hip height, so feet and head move the way a body pivoting at the hips would.</li>
 *   <li><b>Limbs</b> ({@link #applyLimbs}), from the tail of {@code PlayerEntityModel.setAngles}. Each
 *   part is blended from its vanilla angle toward the pilot pose by the pose's weight, then the outer
 *   skin layers are re-copied from the parts they follow.</li>
 * </ol>
 *
 * The state chosen for the body pass is remembered and handed to the limb pass of the same entity in
 * the same render call; first-person arm rendering, which calls {@code setAngles} without a body pass,
 * therefore never picks up a pose.
 */
public final class PilotPoses {
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final float WALL_PIVOT = 0.9F;
	/** Player models render at 15/16 scale: the hip joint is 12 model pixels = 0.703 blocks above the feet. */
	private static final float HIP_HEIGHT = 12.0F / 16.0F * 0.9375F;
	/** Half a leg's thickness (4 px): a horizontal leg rests on the ground when the hip is this high. */
	private static final float LEG_HALF_THICKNESS = 2.0F / 16.0F * 0.9375F;
	private static final float GROUND_CLEARANCE = 0.01F;
	/** Forward lean of the whole body over the tucked knees in the slide-hop coil, degrees. */
	private static final float COIL_BODY_LEAN = 10.0F;
	/** Forward drive of the whole body during the push-off, degrees. */
	private static final float PUSH_BODY_LEAN = 8.0F;
	/** Coil knee position in model pixels (body space: +Y down, -Z forward): chest height, out in front. */
	private static final float KNEE_X = 1.6F;
	private static final float KNEE_Y = 5.5F;
	private static final float KNEE_Z = -6.0F;
	/** Wall run: how far the arm away from the wall is held out from the body, radians (~57 degrees). */
	private static final float OFF_ARM_RAISE = 1.0F;

	private static LivingEntity pendingEntity;
	private static PilotAnimations.State pendingState;
	private static float pendingYaw;
	private static Matrix4f pendingBody;

	private PilotPoses() {
	}

	public static void applyBody(LivingEntity entity, PilotAnimations.State state, MatrixStack matrices, float bodyYaw) {
		pendingEntity = entity;
		pendingState = state;
		pendingYaw = bodyYaw;
		TitanfallConfig config = TitanfallConfig.get();
		// The same transform is mirrored into a plain matrix, so the limb pass can work out exactly where the
		// wall is relative to the model and lay the wall-side limbs on its surface.
		Matrix4f body = new Matrix4f();

		// Wall run: feet toward the wall, upper body leaning out - about the horizontal axis along the wall,
		// so the lean is always straight away from the surface whichever way the body faces. Running
		// forward leans into the run, running backward leans back.
		float forwardness = (state.wallForward + 1.0F) * 0.5F;
		float lean = (float) config.wallRunLean * state.wall;
		float pitch = state.wall * lerp(forwardness, 10.0F, -6.0F);
		Quaternionf rotation = new Quaternionf();
		Vector3f normal = wallNormalLocal(state, bodyYaw);
		if (normal != null) {
			// Axis = up x normal: rotating "up" about it tilts the top of the body along the normal, off the wall.
			rotation.rotateAxis(lean * DEG, normal.z, 0.0F, -normal.x);
		} else {
			// Positive Z tilts the top to the left, i.e. away from a wall on the right.
			rotation.rotateZ(lean * state.wallSide * DEG);
		}

		// Wall jump: a quick extra push of the body off the wall and forward, then back.
		rotation.rotateZ(18.0F * state.launch * state.launchSide * DEG);
		pitch -= 12.0F * state.launch;

		// Slide hop: a slight forward drive on the push-off, then a settled forward lean in the coil.
		pitch -= PUSH_BODY_LEAN * state.push + COIL_BODY_LEAN * coil(state.hop);
		rotation.rotateX(pitch * DEG);

		if (Math.abs(lean) > 0.01F || Math.abs(pitch) > 0.01F || state.launch > 0.001F) {
			matrices.translate(0.0F, WALL_PIVOT, 0.0F);
			matrices.multiply(rotation);
			matrices.translate(0.0F, -WALL_PIVOT, 0.0F);
			body.translate(0.0F, WALL_PIVOT, 0.0F).rotate(rotation).translate(0.0F, -WALL_PIVOT, 0.0F);
		}

		// Slide: recline the torso back about the hip joint and lower the hips until the legs - which the
		// limb pass lays flat, forward - rest on the ground. With the hip at leg half-thickness above the
		// feet plane, a horizontal leg's underside touches the ground exactly, and the reclined torso and
		// head stay under the 0.8-block slide hitbox (recline 55: hip 0.12 + torso 0.40 + head 0.27).
		if (state.slide > 0.01F) {
			float recline = (float) config.slideRecline * state.slide;
			float hipTarget = LEG_HALF_THICKNESS + GROUND_CLEARANCE;
			float hip = HIP_HEIGHT - (HIP_HEIGHT - hipTarget) * state.slide;
			matrices.translate(0.0F, hip, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(recline));
			matrices.translate(0.0F, -HIP_HEIGHT, 0.0F);
			body.translate(0.0F, hip, 0.0F).rotateX(recline * DEG).translate(0.0F, -HIP_HEIGHT, 0.0F);
		}

		pendingBody = body;
	}

	/**
	 * The wall's outward normal in the entity frame that {@code setupTransforms} leaves (+X right, +Y up,
	 * +Z back), from the smoothed world normal and this frame's body yaw; null when no wall is known.
	 */
	private static Vector3f wallNormalLocal(PilotAnimations.State state, float bodyYaw) {
		return toEntityFrame(state.wallNormalX, state.wallNormalZ, bodyYaw);
	}

	private static Vector3f toEntityFrame(float worldX, float worldZ, float bodyYaw) {
		if (worldX * worldX + worldZ * worldZ < 0.01F) {
			return null;
		}

		HVec right = HVec.rightFromYaw(bodyYaw);
		HVec forward = HVec.forwardFromYaw(bodyYaw);
		Vector3f local = new Vector3f((float) (worldX * right.x() + worldZ * right.z()), 0.0F,
				(float) -(worldX * forward.x() + worldZ * forward.z()));
		return local.normalize();
	}

	public static void applyLimbs(LivingEntity entity, PlayerEntityModel<?> model) {
		if (entity != pendingEntity || pendingState == null) {
			return;
		}

		PilotAnimations.State state = pendingState;
		pendingEntity = null;
		pendingState = null;
		TitanfallConfig config = TitanfallConfig.get();

		// --- wall run: the wall-side arm lies flat along the wall and slides over it, the off arm pumps ---
		float w = state.wall;
		if (w > 0.01F) {
			boolean wallRight = state.wallSide >= 0.0F;
			ModelPart wallArm = wallRight ? model.rightArm : model.leftArm;
			ModelPart offArm = wallRight ? model.leftArm : model.rightArm;
			float outward = wallRight ? 1.0F : -1.0F;
			// Off arm: held out from the body for balance, a little forward - the same whichever way the pilot
			// runs along the wall. Held steady rather than pumping, so the "carrying" thruster module beside it - which
			// the jumpkit poser keeps clear of this arm every frame - has a calm shape to fit round.
			offArm.pitch = lerp(w, offArm.pitch, -0.35F);
			offArm.yaw = lerp(w, offArm.yaw, 0.0F);
			offArm.roll = lerp(w, offArm.roll, -outward * OFF_ARM_RAISE);
			WallFrame wall = wallFrame(state);
			if (wall != null) {
				boolean slim = entity instanceof AbstractClientPlayerEntity player
						&& player.getSkinTextures().model() == SkinTextures.Model.SLIM;
				layArmOnWall(wallArm, wallRight, slim, wall, w);
				keepLegOffWall(model.rightLeg, wall, w);
				keepLegOffWall(model.leftLeg, wall, w);
			}

			// Keep the head nearer level than the leaning body.
			model.head.roll = -(float) config.wallRunLean * DEG * state.wallSide * w * 0.6F;
		}

		// --- wall jump: arms swing up and out, legs scissor ---
		float l = state.launch;
		if (l > 0.01F) {
			model.rightArm.pitch = lerp(l, model.rightArm.pitch, -2.1F);
			model.leftArm.pitch = lerp(l, model.leftArm.pitch, -1.7F);
			model.rightArm.roll = lerp(l, model.rightArm.roll, 0.45F);
			model.leftArm.roll = lerp(l, model.leftArm.roll, -0.45F);
			model.rightLeg.pitch = lerp(l, model.rightLeg.pitch, -0.9F);
			model.leftLeg.pitch = lerp(l, model.leftLeg.pitch, 0.5F);
		}

		// --- slide: legs laid flat along the ground, lead hand forward, trailing hand planted behind ---
		float s = state.slide;
		if (s > 0.01F) {
			standUp(model, s);
			// The body is reclined by R about the hip, so a leg hanging along the body already points R
			// forward of straight down; swinging it a further (90 - R) forward makes it exactly horizontal.
			// (Negative pitch swings a limb forward.) The old fixed -1.35 rad over-rotated the legs into the air.
			float recline = (float) config.slideRecline * DEG;
			float flat = -((float) Math.PI / 2.0F - recline);
			model.rightLeg.pitch = lerp(s, model.rightLeg.pitch, flat);
			model.rightLeg.yaw = lerp(s, model.rightLeg.yaw, 0.06F);
			model.rightLeg.roll = lerp(s, model.rightLeg.roll, 0.04F);
			// Trailing leg: a touch higher and splayed, as if the knee were bent - still clear of the ground.
			model.leftLeg.pitch = lerp(s, model.leftLeg.pitch, flat - 0.12F);
			model.leftLeg.yaw = lerp(s, model.leftLeg.yaw, -0.08F);
			model.leftLeg.roll = lerp(s, model.leftLeg.roll, -0.12F);
			// Lead arm reaches along the legs; the trailing arm swings back to vertical to plant the hand.
			model.rightArm.pitch = lerp(s, model.rightArm.pitch, flat * 0.7F);
			model.rightArm.roll = lerp(s, model.rightArm.roll, 0.15F);
			model.leftArm.pitch = lerp(s, model.leftArm.pitch, recline * 0.9F);
			model.leftArm.roll = lerp(s, model.leftArm.roll, -0.35F);
			// Tilt the head forward by the recline so the gaze stays where the player looks.
			model.head.pitch = Math.min(model.head.pitch + recline * s, 1.5F);
		}

		// --- slide hop, part 1: push-off - legs drive straight down and slightly back, arms swing up ---
		float push = state.push;
		if (push > 0.01F) {
			standUp(model, push);
			model.rightLeg.pitch = lerp(push, model.rightLeg.pitch, 0.22F);
			model.leftLeg.pitch = lerp(push, model.leftLeg.pitch, 0.12F);
			model.rightArm.pitch = lerp(push, model.rightArm.pitch, -2.1F);
			model.leftArm.pitch = lerp(push, model.leftArm.pitch, -2.1F);
			model.rightArm.roll = lerp(push, model.rightArm.roll, 0.15F);
			model.leftArm.roll = lerp(push, model.leftArm.roll, -0.15F);
		}

		// --- slide hop, part 2: coil (tuck jump) ---
		// Player legs have no knee joint, so - as Roblox parkour animators do - the top of each leg is used
		// as the knee: the leg's joint is lifted from the hip to chest height and moved forward out in front
		// of the torso, and the leg hangs down from there as the shin. The knees are drawn together and the
		// arms reach forward-down and inward so the hands clasp the knees, holding the legs in the tuck.
		float c = coil(state.hop);
		if (c > 0.01F) {
			standUp(model, c);
			model.rightLeg.pivotX = lerp(c, model.rightLeg.pivotX, -KNEE_X);
			model.leftLeg.pivotX = lerp(c, model.leftLeg.pivotX, KNEE_X);
			model.rightLeg.pivotY = lerp(c, model.rightLeg.pivotY, KNEE_Y);
			model.leftLeg.pivotY = lerp(c, model.leftLeg.pivotY, KNEE_Y);
			model.rightLeg.pivotZ = lerp(c, model.rightLeg.pivotZ, KNEE_Z);
			model.leftLeg.pivotZ = lerp(c, model.leftLeg.pivotZ, KNEE_Z);
			// Shins hang from the knees, tipped slightly back so the feet tuck under.
			model.rightLeg.pitch = lerp(c, model.rightLeg.pitch, 0.18F);
			model.leftLeg.pitch = lerp(c, model.leftLeg.pitch, 0.14F);
			model.rightLeg.yaw = lerp(c, model.rightLeg.yaw, 0.0F);
			model.leftLeg.yaw = lerp(c, model.leftLeg.yaw, 0.0F);
			model.rightLeg.roll = lerp(c, model.rightLeg.roll, 0.03F);
			model.leftLeg.roll = lerp(c, model.leftLeg.roll, -0.03F);
			// Shoulder (+-5, 2, 0) to just in front of the knee (+-1.6, 6, -8): about 63 degrees forward of
			// straight down, and ~20 degrees inward (negative roll moves the right hand in, positive the left).
			model.rightArm.pitch = lerp(c, model.rightArm.pitch, -1.1F);
			model.leftArm.pitch = lerp(c, model.leftArm.pitch, -1.1F);
			model.rightArm.roll = lerp(c, model.rightArm.roll, -0.36F);
			model.leftArm.roll = lerp(c, model.leftArm.roll, 0.36F);
			model.rightArm.yaw = lerp(c, model.rightArm.yaw, 0.0F);
			model.leftArm.yaw = lerp(c, model.leftArm.yaw, 0.0F);
			// Keep the gaze level although the body leans forward over the knees.
			model.head.pitch -= COIL_BODY_LEAN * DEG * c;
		}

		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftPants.copyTransform(model.leftLeg);
		model.rightPants.copyTransform(model.rightLeg);
	}

	/** Model pixels to blocks at the player model's 15/16 render scale. */
	private static final float PX = 0.9375F / 16.0F;
	/** Model Y offset the living-entity renderer applies after flipping the model (blocks, before scale). */
	private static final float MODEL_Y_OFFSET = 1.501F;
	/** The wall arm points this far from straight down toward the run direction, in the wall plane. */
	private static final float WALL_ARM_SWING = 62.0F * (float) (Math.PI / 180.0);
	/** Gap kept between limbs and the wall surface, blocks: just enough to avoid z-fighting. */
	private static final float WALL_SKIN = 0.004F;

	/** The wall relative to the model for this frame: entity-frame directions and the surface distance. */
	private record WallFrame(Matrix4f body, Matrix4f inverse, Vector3f toWall, Vector3f along, Vector3f armAlong, float distance) {
	}

	private static WallFrame wallFrame(PilotAnimations.State state) {
		Vector3f normal = wallNormalLocal(state, pendingYaw);
		if (normal == null || pendingBody == null) {
			return null;
		}

		Vector3f toWall = new Vector3f(normal).mul(-1.0F);
		Vector3f along = toEntityFrame(state.wallAlongX, state.wallAlongZ, pendingYaw);
		if (along == null) {
			along = new Vector3f(0.0F, 0.0F, -1.0F);
		}

		// Keep the run direction exactly in the wall plane.
		along.sub(new Vector3f(normal).mul(along.dot(normal)));
		if (along.lengthSquared() < 1.0E-4F) {
			along = new Vector3f(normal.z, 0.0F, -normal.x);
		}

		along.normalize();
		// The wall arm reaches the way the body faces along the wall, not the way it runs, so running backward
		// along the wall keeps the same arm pose instead of wrenching the arm round behind the body.
		Vector3f facing = new Vector3f(0.0F, 0.0F, -1.0F);
		facing.sub(new Vector3f(normal).mul(facing.dot(normal)));
		Vector3f armAlong = facing.lengthSquared() > 0.0025F ? facing.normalize() : new Vector3f(along);
		return new WallFrame(pendingBody, new Matrix4f(pendingBody).invert(), toWall, along, armAlong, state.wallDistance - WALL_SKIN);
	}

	/** A model-space point (pixels) to the entity frame (blocks), through the flip, 15/16 scale and body pose. */
	private static Vector3f modelToEntity(WallFrame wall, Vector3f modelPx) {
		Vector3f p = new Vector3f(-modelPx.x * PX, -(modelPx.y / 16.0F - MODEL_Y_OFFSET) * 0.9375F, modelPx.z * PX);
		return wall.body().transformPosition(p);
	}

	/** An entity-frame direction to model space (unit). */
	private static Vector3f entityToModelDir(WallFrame wall, Vector3f direction) {
		Vector3f v = wall.inverse().transformDirection(new Vector3f(direction));
		return v.set(-v.x, -v.y, v.z).normalize();
	}

	/**
	 * Lays the wall-side arm flat on the wall: the whole arm lies in the wall plane, angled from the shoulder
	 * down and along the run, with its outer face resting on the surface. The rotation is solved exactly from
	 * the wall's direction in model space - after the body's lean, pitch and yaw - and the shoulder pivot is
	 * slid along the wall normal by however much is needed for the face to sit on the surface, so the arm
	 * neither sinks into the block nor floats off it while it slides along.
	 */
	private static void layArmOnWall(ModelPart arm, boolean rightArm, boolean slim, WallFrame wall, float weight) {
		Vector3f directionEntity = new Vector3f(wall.armAlong()).mul(MathHelper.sin(WALL_ARM_SWING))
				.add(0.0F, -MathHelper.cos(WALL_ARM_SWING), 0.0F);
		Vector3f down = entityToModelDir(wall, directionEntity);
		Vector3f toWall = entityToModelDir(wall, wall.toWall());
		// Exactly perpendicular to the arm axis.
		toWall.sub(new Vector3f(down).mul(toWall.dot(down))).normalize();

		// Rotation columns: local +Y (down the arm) -> down; the outer side (-X right arm, +X left arm) -> wall.
		Vector3f c0 = rightArm ? new Vector3f(toWall).mul(-1.0F) : new Vector3f(toWall);
		Vector3f c1 = down;
		Vector3f c2 = new Vector3f(c0).cross(c1);
		float[] euler = eulerZYX(c0, c1, c2);
		float pitch = euler[0];
		float yaw = euler[1];
		float roll = euler[2];

		// Outer face of the arm (and its sleeve layer) relative to the shoulder pivot, in pixels.
		float face = (slim ? 2.0F : 3.0F) + 0.25F;
		Vector3f pivot = new Vector3f(arm.pivotX, arm.pivotY, arm.pivotZ);
		float reach = modelToEntity(wall, new Vector3f(toWall).mul(face).add(pivot)).dot(wall.toWall());
		float shift = MathHelper.clamp((wall.distance() - reach) / PX, -6.0F, 6.0F);
		pivot.add(new Vector3f(toWall).mul(shift));

		arm.pitch = lerpAngle(weight, arm.pitch, pitch);
		arm.yaw = lerpAngle(weight, arm.yaw, yaw);
		arm.roll = lerpAngle(weight, arm.roll, roll);
		arm.pivotX = lerp(weight, arm.pivotX, pivot.x);
		arm.pivotY = lerp(weight, arm.pivotY, pivot.y);
		arm.pivotZ = lerp(weight, arm.pivotZ, pivot.z);
	}

	/**
	 * The lean swings the feet toward the wall; if a foot would poke into the block, swing that leg back
	 * about the horizontal axis along the wall - the one direction that moves a foot straight off the
	 * surface, whichever way the body faces - just far enough for its nearest corner (pants layer
	 * included) to rest on the surface.
	 */
	private static void keepLegOffWall(ModelPart leg, WallFrame wall, float weight) {
		Quaternionf current = new Quaternionf().rotationZYX(leg.roll, leg.yaw, leg.pitch);
		float limit = wall.distance();
		if (footReach(leg, current, wall) <= limit) {
			return;
		}

		Vector3f axis = entityToModelDir(wall, wall.along());
		for (int step = 1; step <= 30; step++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				float angle = sign * step * 0.04F;
				Quaternionf swung = new Quaternionf().rotateAxis(angle, axis.x, axis.y, axis.z).mul(current);
				if (footReach(leg, swung, wall) <= limit) {
					float[] euler = eulerZYX(swung.transform(new Vector3f(1.0F, 0.0F, 0.0F)),
							swung.transform(new Vector3f(0.0F, 1.0F, 0.0F)), swung.transform(new Vector3f(0.0F, 0.0F, 1.0F)));
					leg.pitch = lerpAngle(weight, leg.pitch, euler[0]);
					leg.yaw = lerpAngle(weight, leg.yaw, euler[1]);
					leg.roll = lerpAngle(weight, leg.roll, euler[2]);
					return;
				}
			}
		}
	}

	/** How far the foot end of a leg reaches toward the wall, blocks from the player's centre line. */
	private static float footReach(ModelPart leg, Quaternionf rotation, WallFrame wall) {
		float reach = -Float.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			Vector3f local = new Vector3f((corner & 1) == 0 ? -2.25F : 2.25F, 12.25F, (corner & 2) == 0 ? -2.25F : 2.25F);
			Vector3f point = rotation.transform(local).add(leg.pivotX, leg.pivotY, leg.pivotZ);
			reach = Math.max(reach, modelToEntity(wall, point).dot(wall.toWall()));
		}

		return reach;
	}

	/**
	 * Euler angles {pitch, yaw, roll} for {@code ModelPart.rotate}'s rotationZYX(roll, yaw, pitch) = Rz Ry Rx,
	 * from the rotation's columns (where it sends local X, Y and Z).
	 */
	private static float[] eulerZYX(Vector3f c0, Vector3f c1, Vector3f c2) {
		float yaw = (float) Math.asin(MathHelper.clamp(-c0.z, -1.0F, 1.0F));
		float pitch = (float) Math.atan2(c1.z, c2.z);
		float roll = (float) Math.atan2(c0.y, c0.x);
		return new float[]{pitch, yaw, roll};
	}

	private static float lerpAngle(float t, float from, float to) {
		return from + MathHelper.wrapDegrees((to - from) / DEG) * DEG * t;
	}

	/**
	 * Blends vanilla's crouch pose back toward standing. Slides hold the sneak key, so vanilla poses the
	 * model crouched (body pitched forward, pivots shifted); the slide pose builds on a straight body.
	 * Standing values are the ones {@code BipedEntityModel.setAngles} uses when not sneaking.
	 */
	private static void standUp(PlayerEntityModel<?> model, float weight) {
		if (model.sneaking) {
			model.rightArm.pitch -= 0.4F * weight;
			model.leftArm.pitch -= 0.4F * weight;
		}

		model.body.pitch = lerp(weight, model.body.pitch, 0.0F);
		model.body.pivotY = lerp(weight, model.body.pivotY, 0.0F);
		model.head.pivotY = lerp(weight, model.head.pivotY, 0.0F);
		model.rightArm.pivotY = lerp(weight, model.rightArm.pivotY, 2.0F);
		model.leftArm.pivotY = lerp(weight, model.leftArm.pivotY, 2.0F);
		model.rightLeg.pivotY = lerp(weight, model.rightLeg.pivotY, 12.0F);
		model.leftLeg.pivotY = lerp(weight, model.leftLeg.pivotY, 12.0F);
		model.rightLeg.pivotZ = lerp(weight, model.rightLeg.pivotZ, 0.0F);
		model.leftLeg.pivotZ = lerp(weight, model.leftLeg.pivotZ, 0.0F);
	}

	/**
	 * Eases the coil weight with smoothstep, so the tuck accelerates out of the slide and settles into the
	 * held coil instead of moving at a constant rate - the weight itself is already lerped per frame.
	 */
	private static float coil(float weight) {
		float w = Math.max(0.0F, Math.min(1.0F, weight));
		return w * w * (3.0F - 2.0F * w);
	}

	private static float lerp(float t, float from, float to) {
		return from + (to - from) * t;
	}
}
