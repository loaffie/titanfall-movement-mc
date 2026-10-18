package com.loaffie.titanfall.client.render;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.client.particle.PilotThrusters;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.loaffie.titanfall.movement.physics.HVec;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Articulates the jumpkit each frame: where the two thruster modules sit relative to the pack, and where
 * the pack sits on the back.
 *
 * <p>Two layers:
 * <ol>
 *   <li><b>Style.</b> From the pilot's smoothed animation weights and thrust level: in free air the modules
 *   splay slightly and angle their nozzles back; a double jump kicks them down and out; the slide-hop coil
 *   tucks them back; a slide folds them forward and rolls the pack in. On the ground with nothing going on
 *   every value is zero and the kit is the plain model.</li>
 *   <li><b>Contact.</b> Then the pose is tested against the world through the exact matrix the kit is drawn
 *   with: a module that would poke into the wall being run on swings back (or forward, when the wall is
 *   behind) about its hub just far enough to clear it, the pack tucks in against the back if it would touch
 *   that wall, and in a slide the whole kit rides up the spine just enough to clear the floor - so the kit
 *   no longer sinks through the floor in a slide or into the wall on a wall run. Each part is tested by a
 *   small precomputed hull ({@link JumpkitMesh#podHull}), and each solve is a short bisection.</li>
 * </ol>
 * Contact results and the slide aim follow critically damped springs (quick when they have to grow, gentler
 * when they relax), so the modules glide in and out of position without snapping at either end. The wall is
 * remembered as a plane in the world, so after a wall jump the modules come back exactly as the wall falls
 * away.
 *
 * <p>In a slide the modules are aimed: each one's fold is solved so its nozzle points back against the
 * direction of travel, as if the thrusters were driving the slide.
 *
 * <p>Mesh space is the model's body space in OBJ units (X side, Y down, Z back); module "r" (side 0) is the
 * +X one. A positive swing moves a module backward, a positive lift raises it outward, a positive fold
 * tips its bottom backward.
 */
public final class JumpkitPoser {
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Clearance kept from the floor and walls, blocks. */
	private static final double SKIN = 0.006;
	/** Furthest a module swings to clear a wall, degrees. */
	private static final float MAX_SWING = 100.0F;
	/** Furthest the pack rides up the spine (mesh units; ~10 px) or tucks into the back (~3 px). */
	private static final float MAX_RAISE = 260.0F;
	private static final float MAX_TUCK = 80.0F;
	private static final int BISECT_STEPS = 12;
	/** Spring stiffness of contact results, rad/s: growing (to stay clear in time) and relaxing. */
	private static final float GROW_OMEGA = 40.0F;
	private static final float RELAX_OMEGA = 12.0F;
	/** Spring stiffness of the slide aim, rad/s. */
	private static final float AIM_OMEGA = 18.0F;
	/** Slide aim: degrees below horizontal, and the fold range searched. */
	private static final float AIM_DOWN = 8.0F;
	private static final float AIM_MIN = -150.0F;
	private static final float AIM_MAX = 170.0F;
	/** Beyond this distance from the wall (blocks) the wall contact is not solved at all. */
	private static final double WALL_IGNORE = 0.8;
	private static final int SWING_SLOT = 0;
	private static final int RAISE_SLOT = 2;
	private static final int TUCK_SLOT = 3;
	private static final int AIM_SLOT = 4;
	private static final int CARRY_SLOT = 6;
	private static final int CARRY_LIFT_SLOT = 8;
	private static final int CARRY_FOLD_SLOT = 10;
	private static final int WALL_AIM_SLOT = 12;
	private static final int WALL_AIM_WEIGHT_SLOT = 14;
	/** Wall run, module against the wall: aim back along the run, tipped down by this much, within this fold range. */
	private static final float WALL_AIM_DOWN = 0.6F;
	private static final float WALL_AIM_MIN = -100.0F;
	private static final float WALL_AIM_MAX = 100.0F;
	/** Carry pose: weight of leaning the jet back against the run, relative to straight down. */
	private static final float CARRY_RUN = 0.9F;
	/** Spring stiffness of the wall-run carry pose, rad/s. */
	private static final float CARRY_OMEGA = 14.0F;
	/** Carry pose: how much the jet leans out from straight down, away from the wall (0 = straight down). */
	private static final float CARRY_OUT = 0.55F;
	/** Carry pose: weight of keeping the module's body level (parallel to the ground) against aiming the jet. */
	private static final float CARRY_LEVEL = 0.6F;
	/** Score lost per module hull point inside an arm: large enough that any clear pose wins. */
	private static final float ARM_PENALTY = 0.5F;

	private static final Map<PlayerEntity, Memory> MEMORY = new WeakHashMap<>();

	private static final class Memory {
		final float[] swing = new float[2];
		final float[] aimFold = new float[2];
		boolean aimSet;
		float raise;
		float tuck;
		/** Wall-run "carry" weight per module (1 = the module away from the wall, held level). */
		final float[] carry = new float[2];
		final float[] carryLift = new float[2];
		/** Last carry-pose search result, the tick it ran on and for which module. */
		float[] carryTarget;
		int carryAge = -1;
		int carrySide = -1;
		final float[] carryFold = new float[2];
		/** Wall-side module's aim back along the run: fold, its blend weight, and whether it is initialised. */
		final float[] wallAim = new float[2];
		final float[] wallAimWeight = new float[2];
		final boolean[] wallAimSet = new boolean[2];
		/** Spring velocities: swing r/l, raise, tuck, aim r/l, carry r/l, carry lift r/l, carry fold r/l,
		 * wall aim r/l, wall aim weight r/l. */
		final float[] velocity = new float[16];
		boolean[] carrySet = new boolean[2];
		long lastNanos;
	}

	/** The solved pose: pack transform parameters and each module's swing, lift and fold, in degrees / mesh units. */
	public static final class Pose {
		public float raise;
		public float tuck;
		public float roll;
		public final float[] swing = new float[2];
		public final float[] lift = new float[2];
		public final float[] fold = new float[2];

		public boolean rest() {
			return raise == 0.0F && tuck == 0.0F && roll == 0.0F && swing[0] == 0.0F && swing[1] == 0.0F
					&& lift[0] == 0.0F && lift[1] == 0.0F && fold[0] == 0.0F && fold[1] == 0.0F;
		}
	}

	private JumpkitPoser() {
	}

	/** Pack transform in mesh space: roll in against the back (about X at the mount), tuck forward, ride up the spine. */
	public static Matrix4f packMatrix(Pose pose) {
		return new Matrix4f().translate(0.0F, -pose.raise, -pose.tuck).rotateX(pose.roll * DEG);
	}

	/** A thruster module's transform in mesh space, about its hub. */
	public static Matrix4f podMatrix(JumpkitMesh mesh, Pose pose, int side) {
		return podMatrix(mesh, side, pose.swing[side], pose.lift[side], pose.fold[side]);
	}

	private static Matrix4f podMatrix(JumpkitMesh mesh, int side, float swing, float lift, float fold) {
		float[] p = mesh.pivot(side);
		return new Matrix4f().translate(p[0], p[1], p[2]).rotate(podRotation(side, swing, lift, fold)).translate(-p[0], -p[1], -p[2]);
	}

	/** A module's rotation about its hub. */
	public static Quaternionf podRotation(Pose pose, int side) {
		return podRotation(side, pose.swing[side], pose.lift[side], pose.fold[side]);
	}

	private static Quaternionf podRotation(int side, float swing, float lift, float fold) {
		float sign = side == 0 ? 1.0F : -1.0F;
		return new Quaternionf().rotateY(-sign * swing * DEG).rotateZ(-sign * lift * DEG).rotateX(fold * DEG);
	}

	/**
	 * Solves this frame's pose.
	 *
	 * @param kitToCamera mesh space to camera-relative world space, exactly as the kit is drawn
	 * @param center the player's interpolated position minus the camera position (camera-relative feet)
	 * @param inWorld false when the kit is not drawn in the world (inventory doll): contact is skipped
	 */
	public static Pose solve(PlayerEntity player, PilotAnimations.State state, JumpkitMesh mesh, Matrix4f kitToCamera,
			Vector3f center, boolean inWorld, float thrust, float tickDelta) {
		return solve(player, state, mesh, kitToCamera, center, inWorld, thrust, tickDelta, null);
	}

	/**
	 * An arm as an obstacle for the modules: the transform from camera-relative world space into the arm's
	 * own model space (pixels, the space its cuboid is defined in) and that cuboid's bounds, already
	 * widened by a margin. A module pose whose hull pokes into it is steered away.
	 */
	public record ArmBox(Matrix4f cameraToArm, float x0, float y0, float z0, float x1, float y1, float z1) {
		/** How many of the given points (already in the arm's space) lie inside the box. */
		int inside(Vector3f point) {
			return point.x > x0 && point.x < x1 && point.y > y0 && point.y < y1 && point.z > z0 && point.z < z1 ? 1 : 0;
		}
	}

	/** As {@link #solve(PlayerEntity, PilotAnimations.State, JumpkitMesh, Matrix4f, Vector3f, boolean, float, float)}, keeping the modules out of {@code arms}. */
	public static Pose solve(PlayerEntity player, PilotAnimations.State state, JumpkitMesh mesh, Matrix4f kitToCamera,
			Vector3f center, boolean inWorld, float thrust, float tickDelta, ArmBox[] arms) {
		Pose pose = new Pose();
		float flare = PilotThrusters.flare(player, tickDelta);
		float air = Math.min(thrust, 1.0F);
		float slide = state == null ? 0.0F : state.slide;
		float coil = state == null ? 0.0F : smooth(state.hop);
		float push = state == null ? 0.0F : state.push;
		float launch = state == null ? 0.0F : state.launch;
		float wall = state == null ? 0.0F : state.wall;

		Memory memory = MEMORY.computeIfAbsent(player, ignored -> new Memory());
		long now = Util.getMeasuringTimeNano();
		float dt = memory.lastNanos == 0L ? 0.0F : Math.min((now - memory.lastNanos) / 1.0E9F, 0.1F);
		memory.lastNanos = now;

		// --- style ---
		// Free air: modules splay slightly and angle back; a double jump kicks them down and out; the coil
		// tucks them back. Slides aim them separately below; the air term is scaled out by the slide weight.
		float airFold = 8.0F * air * (1.0F - slide) - 14.0F * flare + 10.0F * coil - 8.0F * push;
		float lift = 6.0F * air + 10.0F * flare + 8.0F * wall;
		float swing = 12.0F * coil + 6.0F * launch;
		pose.roll = -15.0F * slide;
		for (int side = 0; side < 2; side++) {
			pose.fold[side] = airFold;
			pose.lift[side] = lift;
			pose.swing[side] = swing;
		}

		// --- slide: aim the nozzles back along the slide, as if the thrusters were driving it ---
		if (slide > 0.01F) {
			Vector3f aim = slideAim(player, tickDelta);
			Matrix4f pack = new Matrix4f(kitToCamera).mul(packMatrix(pose));
			for (int side = 0; side < 2; side++) {
				float target = aimFold(mesh, pack, pose, side, aim);
				memory.aimFold[side] = memory.aimSet ? spring(memory, AIM_SLOT + side, memory.aimFold[side], target, AIM_OMEGA, dt) : target;
				pose.fold[side] = MathHelper.lerp(slide, airFold, memory.aimFold[side]);
			}

			memory.aimSet = true;
		} else {
			memory.aimSet = false;
		}

		float[] swingTarget = {pose.swing[0], pose.swing[1]};
		float raiseTarget = 0.0F;
		float tuckTarget = 0.0F;
		if (inWorld) {
			// --- contact: walls. The wall is a fixed plane in the world, so after a wall jump the modules
			// relax exactly as fast as the wall really falls away behind the pilot. ---
			Vector3f toWall = null;
			double wallDistance = 0.0;
			if (state != null && (state.wallPlaneNx != 0.0F || state.wallPlaneNz != 0.0F)) {
				Vec3d feet = player.getLerpedPos(tickDelta);
				toWall = new Vector3f(-state.wallPlaneNx, 0.0F, -state.wallPlaneNz).normalize();
				wallDistance = feet.x * state.wallPlaneNx + feet.z * state.wallPlaneNz - state.wallPlane - SKIN;
				if (wallDistance > WALL_IGNORE) {
					toWall = null;
				}
			}

			// Against the run: the jets lean back along the wall opposite to the way the pilot is running, so
			// they look as if they drive the run - forward or backward (purely cosmetic).
			Vector3f runBack = new Vector3f();
			if (state != null && (state.wallAlongX != 0.0F || state.wallAlongZ != 0.0F)) {
				runBack.set(-state.wallAlongX, 0.0F, -state.wallAlongZ);
				if (runBack.lengthSquared() > 1.0E-4F) {
					runBack.normalize();
				}
			}

			// Wall run: the module away from the wall swings out level with the ground and fires down and away,
			// as if carrying the pilot along the wall. Which module that is follows the geometry (the hub
			// further from the wall), so it is right whichever way the pilot faces.
			int away = -1;
			if (toWall != null && wall > 0.01F) {
				Matrix4f pack = new Matrix4f(kitToCamera).mul(packMatrix(pose));
				// (A pivot is a single xyz point, so the hull reach works on it directly.)
				float reach0 = (float) reach(mesh.pivot(0), pack, toWall, center);
				float reach1 = (float) reach(mesh.pivot(1), pack, toWall, center);
				away = reach0 <= reach1 ? 0 : 1;
				// The pose search is the costliest part of the solve; once per game tick is plenty, as the
				// springs below glide between results.
				if (memory.carryAge != player.age || memory.carrySide != away) {
					memory.carryTarget = carryPose(mesh, pack, pose, away, new Vector3f(toWall).mul(-1.0F), runBack, arms);
					memory.carryAge = player.age;
					memory.carrySide = away;
				}

				float[] carry = memory.carryTarget;
				memory.carry[away] = spring(memory, CARRY_SLOT + away, memory.carry[away], 1.0F, CARRY_OMEGA, dt);
				// The carry angles glide too, so the module never jumps between poses (arms move every frame).
				if (memory.carrySet[away]) {
					memory.carryLift[away] = spring(memory, CARRY_LIFT_SLOT + away, memory.carryLift[away], carry[0], CARRY_OMEGA, dt);
					memory.carryFold[away] = spring(memory, CARRY_FOLD_SLOT + away, memory.carryFold[away], carry[1], CARRY_OMEGA, dt);
				} else {
					memory.carryLift[away] = carry[0];
					memory.carryFold[away] = carry[1];
					memory.carrySet[away] = true;
				}
			}

			// The module against the wall tips its nozzle back against the run as well (its swing, solved below,
			// then keeps it clear of the wall).
			for (int side = 0; side < 2; side++) {
				boolean aiming = side != away && away >= 0 && runBack.lengthSquared() > 0.5F;
				if (aiming) {
					Matrix4f pack = new Matrix4f(kitToCamera).mul(packMatrix(pose));
					Vector3f aim = new Vector3f(runBack).add(0.0F, -WALL_AIM_DOWN, 0.0F).normalize();
					float target = aimFold(mesh, pack, pose, side, aim, WALL_AIM_MIN, WALL_AIM_MAX);
					memory.wallAim[side] = memory.wallAimSet[side]
							? spring(memory, WALL_AIM_SLOT + side, memory.wallAim[side], target, CARRY_OMEGA, dt) : target;
					memory.wallAimSet[side] = true;
				}

				memory.wallAimWeight[side] = spring(memory, WALL_AIM_WEIGHT_SLOT + side, memory.wallAimWeight[side], aiming ? wall : 0.0F, CARRY_OMEGA, dt);
				float aimWeight = MathHelper.clamp(memory.wallAimWeight[side], 0.0F, 1.0F);
				if (aimWeight > 0.0F) {
					pose.fold[side] = MathHelper.lerp(aimWeight, pose.fold[side], memory.wallAim[side]);
				} else if (!aiming) {
					memory.wallAimSet[side] = false;
				}
			}

			for (int side = 0; side < 2; side++) {
				if (side != away) {
					memory.carry[side] = spring(memory, CARRY_SLOT + side, memory.carry[side], 0.0F, CARRY_OMEGA, dt);
					if (memory.carry[side] <= 0.001F) {
						memory.carrySet[side] = false;
					}
				}

				float weight = MathHelper.clamp(memory.carry[side], 0.0F, 1.0F);
				if (weight > 0.0F) {
					pose.lift[side] = MathHelper.lerp(weight, pose.lift[side], memory.carryLift[side]);
					pose.fold[side] = MathHelper.lerp(weight, pose.fold[side], memory.carryFold[side]);
				}
			}

			if (toWall != null) {
				Matrix4f pack = new Matrix4f(kitToCamera).mul(packMatrix(pose));
				for (int side = 0; side < 2; side++) {
					swingTarget[side] = clearWall(mesh, pack, pose, side, toWall, center, wallDistance);
				}

				for (int side = 0; side < 2; side++) {
					pose.swing[side] = swingTarget[side];
				}

				tuckTarget = tuckForWall(mesh, kitToCamera, pose, toWall, center, wallDistance);
			}

			// --- contact: floor. Only a slide brings the kit near the floor (standing, crouching or running it
			// rides 0.4+ blocks up), so the solve - and its cost - is skipped otherwise. The feet are the floor
			// line, which is also safe while a slide hop is briefly airborne.
			if (slide > 0.01F) {
				pose.tuck = tuckTarget;
				raiseTarget = raiseForFloor(mesh, kitToCamera, pose, center.y + SKIN);
			}
		}

		// Contact results follow critically damped springs: they ease in and out with no jolt at either end
		// (an exponential approach starts at full speed, which reads as a snap), quickly when they must grow
		// to stay clear and more gently when they relax.
		for (int side = 0; side < 2; side++) {
			float target = swingTarget[side];
			boolean growing = Math.abs(target) > Math.abs(memory.swing[side]) && target * memory.swing[side] >= 0.0F;
			memory.swing[side] = spring(memory, SWING_SLOT + side, memory.swing[side], target, growing ? GROW_OMEGA : RELAX_OMEGA, dt);
			pose.swing[side] = memory.swing[side];
		}

		memory.raise = spring(memory, RAISE_SLOT, memory.raise, raiseTarget, raiseTarget > memory.raise ? GROW_OMEGA : RELAX_OMEGA, dt);
		memory.tuck = spring(memory, TUCK_SLOT, memory.tuck, tuckTarget, tuckTarget > memory.tuck ? GROW_OMEGA : RELAX_OMEGA, dt);
		pose.raise = Math.max(0.0F, memory.raise);
		pose.tuck = Math.max(0.0F, memory.tuck);
		return pose;
	}

	/**
	 * One step of a critically damped spring toward {@code target} (exact solution, so it is stable at any
	 * frame time), using and updating the velocity in {@code memory.velocity[slot]}.
	 */
	private static float spring(Memory memory, int slot, float current, float target, float omega, float dt) {
		if (dt <= 0.0F) {
			memory.velocity[slot] = 0.0F;
			return target;
		}

		float offset = current - target;
		float velocity = memory.velocity[slot];
		float b = velocity + omega * offset;
		float decay = (float) Math.exp(-omega * dt);
		memory.velocity[slot] = (velocity - omega * b * dt) * decay;
		float next = target + (offset + b * dt) * decay;
		if (Math.abs(next - target) < 1.0E-3F && Math.abs(memory.velocity[slot]) < 1.0E-2F) {
			memory.velocity[slot] = 0.0F;
			return target;
		}

		return next;
	}

	/**
	 * The way the exhaust should point in a slide: straight back against the direction of travel (from the
	 * last tick's movement, which is right for other players too), tipped a little toward the ground. Falls
	 * back to behind the body when barely moving.
	 */
	private static Vector3f slideAim(PlayerEntity player, float tickDelta) {
		double dx = player.getX() - player.prevX;
		double dz = player.getZ() - player.prevZ;
		double speed = Math.sqrt(dx * dx + dz * dz);
		float x;
		float z;
		if (speed > 0.02) {
			x = (float) (-dx / speed);
			z = (float) (-dz / speed);
		} else {
			HVec forward = HVec.forwardFromYaw(MathHelper.lerp(tickDelta, player.prevBodyYaw, player.bodyYaw));
			x = (float) -forward.x();
			z = (float) -forward.z();
		}

		float down = MathHelper.sin(AIM_DOWN * DEG);
		float flat = MathHelper.cos(AIM_DOWN * DEG);
		return new Vector3f(x * flat, -down, z * flat);
	}

	/** The fold that points a module's nozzle closest to {@code aim} (world direction): a coarse scan, then a fine one. */
	private static float aimFold(JumpkitMesh mesh, Matrix4f pack, Pose pose, int side, Vector3f aim) {
		return aimFold(mesh, pack, pose, side, aim, AIM_MIN, AIM_MAX);
	}

	private static float aimFold(JumpkitMesh mesh, Matrix4f pack, Pose pose, int side, Vector3f aim, float min, float max) {
		float sign = side == 0 ? 1.0F : -1.0F;
		Vector3f nozzle = new Vector3f(sign * JumpkitModel.NOZZLE_DIR_X, JumpkitModel.NOZZLE_DIR_Y, JumpkitModel.NOZZLE_DIR_Z);
		float best = 0.0F;
		float bestDot = -Float.MAX_VALUE;
		for (int pass = 0; pass < 2; pass++) {
			float from = pass == 0 ? min : Math.max(min, best - 10.0F);
			float to = pass == 0 ? max : Math.min(max, best + 10.0F);
			float step = pass == 0 ? 10.0F : 1.0F;
			for (float fold = from; fold <= to + 1.0E-3F; fold += step) {
				Matrix4f m = new Matrix4f(pack).rotate(podRotation(side, pose.swing[side], pose.lift[side], fold));
				Vector3f d = m.transformDirection(new Vector3f(nozzle)).normalize();
				float dot = d.dot(aim);
				if (dot > bestDot) {
					bestDot = dot;
					best = fold;
				}
			}
		}

		return MathHelper.clamp(best, min, max);
	}

	/**
	 * The wall-run carry pose of a module: the lift and fold (degrees) that best hold its body level with
	 * the ground while pointing its jet down and out, away from the wall. Coarse grid, then a fine one.
	 */
	private static float[] carryPose(JumpkitMesh mesh, Matrix4f pack, Pose pose, int side, Vector3f awayFromWall, Vector3f runBack, ArmBox[] arms) {
		// Dense surface sample: an arm passing through the middle of the module has surface points of the
		// module all round where it enters, which the extreme-point hull would miss.
		float[] hull = mesh.podSample(side);
		// Pack space straight into each arm's space, so a candidate pose only needs one extra multiply.
		Matrix4f[] packToArm = null;
		if (arms != null) {
			packToArm = new Matrix4f[arms.length];
			for (int i = 0; i < arms.length; i++) {
				packToArm[i] = new Matrix4f(arms[i].cameraToArm()).mul(pack);
			}
		}

		float sign = side == 0 ? 1.0F : -1.0F;
		Vector3f nozzleDir = new Vector3f(sign * JumpkitModel.NOZZLE_DIR_X, JumpkitModel.NOZZLE_DIR_Y, JumpkitModel.NOZZLE_DIR_Z);
		float[] pivot = mesh.pivot(side);
		Vector3f bodyAxis = new Vector3f(sign * JumpkitModel.NOZZLE_X - pivot[0], JumpkitModel.NOZZLE_Y - pivot[1], JumpkitModel.NOZZLE_Z - pivot[2]);
		Vector3f aim = new Vector3f(awayFromWall).mul(CARRY_OUT).add(0.0F, -1.0F, 0.0F).add(new Vector3f(runBack).mul(CARRY_RUN)).normalize();
		float bestLift = 0.0F;
		float bestFold = 0.0F;
		float bestScore = -Float.MAX_VALUE;
		Matrix4f m = new Matrix4f();
		Vector3f jet = new Vector3f();
		Vector3f axis = new Vector3f();
		for (int pass = 0; pass < 2; pass++) {
			float liftFrom = pass == 0 ? 0.0F : bestLift - 8.0F;
			float liftTo = pass == 0 ? 100.0F : bestLift + 8.0F;
			float foldFrom = pass == 0 ? -90.0F : bestFold - 8.0F;
			float foldTo = pass == 0 ? 90.0F : bestFold + 8.0F;
			float step = pass == 0 ? 10.0F : 2.0F;
			for (float lift = liftFrom; lift <= liftTo + 1.0E-3F; lift += step) {
				for (float fold = foldFrom; fold <= foldTo + 1.0E-3F; fold += step) {
					Quaternionf rotation = podRotation(side, pose.swing[side], lift, fold);
					m.set(pack).rotate(rotation);
					m.transformDirection(jet.set(nozzleDir)).normalize();
					m.transformDirection(axis.set(bodyAxis)).normalize();
					float score = jet.dot(aim) + CARRY_LEVEL * (1.0F - Math.abs(axis.y));
					if (packToArm != null && score > bestScore) {
						// Any hull point inside an arm rules the pose out (the pose would show the arm passing
						// through the module); a near-miss is preferred over a clean aim through an arm.
						score -= ARM_PENALTY * armHits(hull, packToArm, arms, rotation, pivot, m);
					}
					if (score > bestScore) {
						bestScore = score;
						bestLift = lift;
						bestFold = fold;
					}
				}
			}
		}

		return new float[]{MathHelper.clamp(bestLift, 0.0F, 100.0F), MathHelper.clamp(bestFold, -90.0F, 90.0F)};
	}

	/** Hull points of a module (rotated about its hub) that fall inside any arm box. */
	private static int armHits(float[] hull, Matrix4f[] packToArm, ArmBox[] arms, Quaternionf rotation, float[] pivot, Matrix4f scratch) {
		Matrix4f pod = new Matrix4f().translate(pivot[0], pivot[1], pivot[2]).rotate(rotation).translate(-pivot[0], -pivot[1], -pivot[2]);
		int hits = 0;
		Vector3f point = new Vector3f();
		for (int a = 0; a < arms.length; a++) {
			Matrix4f m = scratch.set(packToArm[a]).mul(pod);
			for (int i = 0; i < hull.length; i += 3) {
				hits += arms[a].inside(m.transformPosition(point.set(hull[i], hull[i + 1], hull[i + 2])));
			}
		}

		return hits;
	}

	/** How far a hull reaches toward the wall, blocks past the player's centre line (positive = toward the wall). */
	private static double reach(float[] hull, Matrix4f transform, Vector3f toWall, Vector3f center) {
		double best = -Double.MAX_VALUE;
		Vector3f point = new Vector3f();
		for (int i = 0; i < hull.length; i += 3) {
			transform.transformPosition(point.set(hull[i], hull[i + 1], hull[i + 2]));
			double d = (point.x - center.x) * toWall.x + (point.z - center.z) * toWall.z;
			best = Math.max(best, d);
		}

		return best;
	}

	/** Lowest point of a hull, camera-relative world Y. */
	private static double lowest(float[] hull, Matrix4f transform) {
		double best = Double.MAX_VALUE;
		Vector3f point = new Vector3f();
		for (int i = 0; i < hull.length; i += 3) {
			transform.transformPosition(point.set(hull[i], hull[i + 1], hull[i + 2]));
			best = Math.min(best, point.y);
		}

		return best;
	}

	/**
	 * The swing that keeps a module out of the wall: the styled swing if it is already clear, otherwise the
	 * smallest extra swing backward or forward (bisected) that clears it, preferring backward on a tie.
	 */
	private static float clearWall(JumpkitMesh mesh, Matrix4f pack, Pose pose, int side, Vector3f toWall, Vector3f center, double limit) {
		float base = pose.swing[side];
		float[] hull = mesh.podHull(side);
		if (reach(hull, swung(mesh, pack, pose, side, base), toWall, center) <= limit) {
			return base;
		}

		float best = Float.NaN;
		for (int step = 0; step < 2; step++) {
			float direction = step == 0 ? 1.0F : -1.0F;
			float amount = bisect(0.0F, MAX_SWING, extra -> reach(hull, swung(mesh, pack, pose, side, base + direction * (float) extra), toWall, center) <= limit);
			if (!Float.isNaN(amount) && (Float.isNaN(best) || amount < Math.abs(best - base) - 0.5F)) {
				best = base + direction * amount;
			}
		}

		return Float.isNaN(best) ? base + MAX_SWING * 0.6F : best;
	}

	private static Matrix4f swung(JumpkitMesh mesh, Matrix4f pack, Pose pose, int side, float swing) {
		return new Matrix4f(pack).mul(podMatrix(mesh, side, swing, pose.lift[side], pose.fold[side]));
	}

	/** How far the pack tucks into the back so it does not touch a wall behind the pilot. */
	private static float tuckForWall(JumpkitMesh mesh, Matrix4f kitToCamera, Pose pose, Vector3f toWall, Vector3f center, double limit) {
		float[] hull = mesh.packHull();
		Pose probe = copyPack(pose);
		if (reach(hull, new Matrix4f(kitToCamera).mul(packMatrix(probe)), toWall, center) <= limit) {
			return 0.0F;
		}

		float tuck = bisect(0.0F, MAX_TUCK, amount -> {
			probe.tuck = (float) amount;
			return reach(hull, new Matrix4f(kitToCamera).mul(packMatrix(probe)), toWall, center) <= limit;
		});
		return Float.isNaN(tuck) ? MAX_TUCK : tuck;
	}

	/** How far the whole kit rides up the spine so no part of it sinks below {@code floorY}. */
	private static float raiseForFloor(JumpkitMesh mesh, Matrix4f kitToCamera, Pose pose, double floorY) {
		Pose probe = copyPack(pose);
		System.arraycopy(pose.swing, 0, probe.swing, 0, 2);
		System.arraycopy(pose.lift, 0, probe.lift, 0, 2);
		System.arraycopy(pose.fold, 0, probe.fold, 0, 2);
		java.util.function.DoublePredicate clear = amount -> {
			probe.raise = (float) amount;
			Matrix4f pack = new Matrix4f(kitToCamera).mul(packMatrix(probe));
			if (lowest(mesh.packHull(), pack) < floorY) {
				return false;
			}

			for (int side = 0; side < 2; side++) {
				if (lowest(mesh.podHull(side), new Matrix4f(pack).mul(podMatrix(mesh, probe, side))) < floorY) {
					return false;
				}
			}

			return true;
		};
		if (clear.test(0.0)) {
			return 0.0F;
		}

		float raise = bisect(0.0F, MAX_RAISE, amount -> clear.test(amount));
		return Float.isNaN(raise) ? MAX_RAISE : raise;
	}

	private static Pose copyPack(Pose pose) {
		Pose copy = new Pose();
		copy.raise = pose.raise;
		copy.tuck = pose.tuck;
		copy.roll = pose.roll;
		return copy;
	}

	/** Smallest value in [lo, hi] for which {@code ok} holds (assumed monotonic), or NaN if even hi fails. */
	private static float bisect(float lo, float hi, java.util.function.DoublePredicate ok) {
		if (!ok.test(hi)) {
			return Float.NaN;
		}

		for (int i = 0; i < BISECT_STEPS; i++) {
			float mid = (lo + hi) * 0.5F;
			if (ok.test(mid)) {
				hi = mid;
			} else {
				lo = mid;
			}
		}

		return hi;
	}

	private static float smooth(float weight) {
		float w = MathHelper.clamp(weight, 0.0F, 1.0F);
		return w * w * (3.0F - 2.0F * w);
	}
}
