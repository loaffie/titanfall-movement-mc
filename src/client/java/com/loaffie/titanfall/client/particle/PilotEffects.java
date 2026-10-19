package com.loaffie.titanfall.client.particle;

import com.loaffie.titanfall.client.phase.PhaseStates;
import com.loaffie.titanfall.client.render.JumpkitModel;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.physics.HVec;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Jumpkit effects, emitted from the real thruster ports of the model.
 *
 * <p>Nozzle positions come from the OBJ placement constants in {@link JumpkitModel}: a point in body space
 * (model pixels: X side, Y down from the neck, Z back) maps to the world through the player renderer's
 * own transform - 15/16 scale, the 24 px neck height, and the body yaw - so a burst leaves the hips exactly
 * where the pods are drawn. Called once per tick per visible pilot from {@code PilotAnimations}.
 */
public final class PilotEffects {
	/** Model pixels to blocks, with the player model's 15/16 render scale. */
	private static final double PX = 0.9375 / 16.0;
	private static final double NECK_PX = 24.016;

	private PilotEffects() {
	}

	/** A thruster port in world space and its exhaust direction. */
	record Port(Vec3d pos, Vec3d dir) {
	}

	/** Recorded ports further than this from the player (blocks) are not from the world render - e.g. the
	 * player doll in the inventory screen - and are ignored. */
	private static final double PORT_SANITY_DISTANCE = 3.0;

	/** Ports as last drawn by the renderer, per player, with the player's age at that time. */
	private static final Map<PlayerEntity, RenderedPorts> RENDERED = new WeakHashMap<>();

	private record RenderedPorts(Port[] ports, int age) {
	}

	/** Ports of the frame being drawn, filled one module at a time. */
	private static final Port[] PENDING = new Port[2];

	/**
	 * Called by the jumpkit renderer with the exact matrix a thruster module is drawn with (mesh units,
	 * entity render space) - after the module's articulation - for port {@code index} (0 = the mesh's +X
	 * module, 1 = the -X one). Entity rendering is camera-relative and world-aligned, so adding the camera
	 * position gives world coordinates; the normal matrix carries the exhaust direction through the same
	 * pose. Once both ports of a frame are in, they replace the player's recorded ports.
	 */
	public static void recordPort(PlayerEntity player, int index, MatrixStack.Entry entry) {
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		int side = index == 0 ? 1 : -1;
		Vector4f pos = entry.getPositionMatrix().transform(new Vector4f(side * JumpkitModel.NOZZLE_X, JumpkitModel.NOZZLE_Y, JumpkitModel.NOZZLE_Z, 1.0F));
		Vector3f dir = entry.getNormalMatrix().transform(new Vector3f(side * JumpkitModel.NOZZLE_DIR_X, JumpkitModel.NOZZLE_DIR_Y, JumpkitModel.NOZZLE_DIR_Z));
		if (dir.lengthSquared() > 1.0E-8F) {
			dir.normalize();
		}

		Port port = new Port(camera.add(pos.x(), pos.y(), pos.z()), new Vec3d(dir.x(), dir.y(), dir.z()));
		// The inventory screen draws the player doll through the same feature renderer, in GUI space; those
		// "ports" are nowhere near the player and would send every effect to a random spot.
		if (port.pos().squaredDistanceTo(player.getPos().add(0.0, 0.8, 0.0)) > PORT_SANITY_DISTANCE * PORT_SANITY_DISTANCE) {
			PENDING[0] = null;
			PENDING[1] = null;
			return;
		}

		PENDING[index] = port;
		if (index == 1 && PENDING[0] != null) {
			RENDERED.put(player, new RenderedPorts(new Port[]{PENDING[0], PENDING[1]}, player.age));
			PENDING[0] = null;
			PENDING[1] = null;
		}
	}

	/**
	 * The thruster ports: as rendered in the last frame when the player is being drawn (exact in every
	 * pose), otherwise - first person, or the player just came into view - computed from the standing
	 * pose, which matches the renderer exactly while upright.
	 */
	private static Port[] ports(PlayerEntity player) {
		RenderedPorts rendered = RENDERED.get(player);
		if (rendered != null && player.age - rendered.age() <= 1) {
			return rendered.ports();
		}

		return standingPorts(player, player.getPos(), player.bodyYaw);
	}

	/**
	 * One port for a frame being drawn: the position the renderer recorded this frame (third person, other
	 * players), otherwise the standing-pose port at the interpolated position and body yaw, so an attached
	 * effect moves smoothly between ticks.
	 */
	static Port livePort(PlayerEntity player, int index, float tickDelta) {
		RenderedPorts rendered = RENDERED.get(player);
		if (rendered != null && player.age - rendered.age() <= 1) {
			return rendered.ports()[index];
		}

		Vec3d position = new Vec3d(MathHelper.lerp(tickDelta, player.prevX, player.getX()),
				MathHelper.lerp(tickDelta, player.prevY, player.getY()),
				MathHelper.lerp(tickDelta, player.prevZ, player.getZ()));
		return standingPorts(player, position, MathHelper.lerp(tickDelta, player.prevBodyYaw, player.bodyYaw))[index];
	}

	private static Port[] standingPorts(PlayerEntity player, Vec3d position, float bodyYaw) {
		HVec right = HVec.rightFromYaw(bodyYaw);
		HVec forward = HVec.forwardFromYaw(bodyYaw);
		float scale = JumpkitModel.OBJ_SCALE;
		Port[] ports = new Port[2];
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? 1 : -1;
			double mx = side * JumpkitModel.NOZZLE_X * scale + JumpkitModel.OBJ_OFFSET_X;
			double my = JumpkitModel.NOZZLE_Y * scale + JumpkitModel.OBJ_OFFSET_Y;
			double mz = JumpkitModel.NOZZLE_Z * scale + JumpkitModel.OBJ_OFFSET_Z;
			ports[i] = new Port(toWorld(position, right, forward, mx, my, mz, true),
					toWorld(position, right, forward, side * JumpkitModel.NOZZLE_DIR_X, JumpkitModel.NOZZLE_DIR_Y, JumpkitModel.NOZZLE_DIR_Z, false).normalize());
		}

		return ports;
	}

	/** Lights the steady flame on both nozzles if it is not burning there already. */
	private static void ensureFlames(PlayerEntity player) {
		Port[] ports = null;
		for (int i = 0; i < 2; i++) {
			if (!ThrusterFlameParticle.burning(player, i)) {
				if (ports == null) {
					ports = ports(player);
				}

				Vec3d pos = ports[i].pos();
				player.getWorld().addParticle(TitanfallParticles.THRUSTER_FLAME, pos.x, pos.y, pos.z, player.getId(), i, 0.0);
			}
		}
	}

	/**
	 * Every airborne tick that is not a wall run - a jump, after a double jump, or simply falling: a small
	 * steady blue flame on both thrusters.
	 */
	public static void airborneTick(PlayerEntity player) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		PilotThrusters.fuel(player, PilotThrusters.AIR);
		ensureFlames(player);
	}

	/** Body-space model pixels (X side, Y down, Z back) to world: local right = -X, up = neck - Y, forward = -Z. */
	private static Vec3d toWorld(Vec3d position, HVec right, HVec forward, double mx, double my, double mz, boolean point) {
		double r = -mx * (point ? PX : 1.0);
		double u = point ? (NECK_PX - my) * PX : -my;
		double f = -mz * (point ? PX : 1.0);
		double x = right.x() * r + forward.x() * f;
		double z = right.z() * r + forward.z() * f;
		return point ? new Vec3d(position.x + x, position.y + u, position.z + z) : new Vec3d(x, u, z);
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	/**
	 * Double jump: both thrusters ignite and fire a jet with sparks, and each one blows a train of exhaust
	 * rings straight down its own nozzle axis - from the visible port, facing along the exhaust, so the
	 * rings show exactly where and which way the thrusters fire.
	 */
	public static void doubleJump(PlayerEntity player) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		// The steady flames surge with the jump and the flaps fling open.
		PilotThrusters.flare(player);
		PilotThrusters.fuel(player, PilotThrusters.AIR);
		ensureFlames(player);
		Vec3d inherit = motion(player).multiply(0.35, 0.0, 0.35);
		for (Port port : ports(player)) {
			spawn(world, TitanfallParticles.IGNITION, port.pos(), Vec3d.ZERO);
			// Shock-diamond train: same axis, staggered speeds, so the rings spread out along the jet.
			for (double speed : EXHAUST_RING_SPEEDS) {
				spawn(world, TitanfallParticles.EXHAUST_RING, port.pos().add(port.dir().multiply(0.06)), port.dir().multiply(speed));
			}

			for (int i = 0; i < 16; i++) {
				double speed = 0.32 + random.nextDouble() * 0.28;
				spawn(world, TitanfallParticles.THRUSTER_JET, port.pos(), port.dir().multiply(speed).add(jitter(random, 0.05)).add(inherit));
			}

			for (int i = 0; i < 7; i++) {
				spawn(world, TitanfallParticles.THRUSTER_SPARK, port.pos(), port.dir().multiply(0.25 + random.nextDouble() * 0.2).add(jitter(random, 0.16)));
			}

			for (int i = 0; i < 3; i++) {
				spawn(world, TitanfallParticles.AFTERGLOW, port.pos().add(port.dir().multiply(0.3 * i)), port.dir().multiply(0.04).add(jitter(random, 0.02)));
			}
		}
	}

	/** Initial speeds (blocks per tick) of the double-jump exhaust rings on each nozzle axis. */
	private static final double[] EXHAUST_RING_SPEEDS = {0.42, 0.28, 0.16};

	/** Wall kick: a ring flashes on the wall where the pilot pushed off, and the jets fire back into it. */
	public static void wallJump(PlayerEntity player, HVec wallNormal) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		Vec3d normal = new Vec3d(wallNormal.x(), 0, wallNormal.z());
		Vec3d onWall = player.getPos().add(0, 0.9, 0).subtract(normal.multiply(hitboxReach(player, wallNormal) + 0.05));
		spawn(world, TitanfallParticles.SHOCKWAVE, onWall, normal);
		for (Port port : ports(player)) {
			spawn(world, TitanfallParticles.IGNITION, port.pos(), Vec3d.ZERO);
			Vec3d toward = port.dir().multiply(0.6).subtract(normal.multiply(0.6)).normalize();
			for (int i = 0; i < 12; i++) {
				spawn(world, TitanfallParticles.THRUSTER_JET, port.pos(), toward.multiply(0.3 + random.nextDouble() * 0.25).add(jitter(random, 0.08)));
			}
		}

		for (int i = 0; i < 10; i++) {
			spawn(world, TitanfallParticles.THRUSTER_SPARK, onWall.add(jitter(random, 0.3)), normal.multiply(0.12).add(jitter(random, 0.15)));
		}
	}

	/**
	 * Every wall-run tick: both thrusters hold a steady blue flame (one attached flame particle per nozzle,
	 * kept lit while the run lasts and faded out after it), and the boots strike sparks off the wall that
	 * are flung back against the run.
	 */
	public static void wallRunTick(PlayerEntity player, HVec wallNormal, HVec wallVelocity, int age) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		// The module away from the wall "carries" the pilot with the stronger jet; the one against the wall
		// idles. The away one is the port further out along the wall's outward normal.
		Port[] carry = ports(player);
		double out0 = carry[0].pos().x * wallNormal.x() + carry[0].pos().z * wallNormal.z();
		double out1 = carry[1].pos().x * wallNormal.x() + carry[1].pos().z * wallNormal.z();
		boolean port0Away = out0 >= out1;
		PilotThrusters.fuel(player, port0Away ? PilotThrusters.WALL_CARRY : PilotThrusters.WALL_SIDE,
				port0Away ? PilotThrusters.WALL_SIDE : PilotThrusters.WALL_CARRY);
		ensureFlames(player);

		Vec3d normal = new Vec3d(wallNormal.x(), 0, wallNormal.z());
		Vec3d along = new Vec3d(wallVelocity.x(), 0, wallVelocity.z()).multiply(1.0 / 20.0);
		if (age % 2 == 0) {
			Vec3d foot = player.getPos().add(0, 0.1 + random.nextDouble() * 0.4, 0).subtract(normal.multiply(hitboxReach(player, wallNormal)));
			for (int i = 0; i < 2; i++) {
				spawn(world, TitanfallParticles.THRUSTER_SPARK, foot, along.multiply(-0.6).add(normal.multiply(0.06)).add(0, 0.08, 0).add(jitter(random, 0.06)));
			}
		}
	}

	/**
	 * Every slide tick: the thrusters - aimed back along the slide by the jumpkit poser - hold a strong steady
	 * burn as if driving the slide (it is only for show), and sparks skip off the ground behind the pilot.
	 */
	public static void slideTick(PlayerEntity player, int age) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		PilotThrusters.fuel(player, PilotThrusters.SLIDE);
		ensureFlames(player);
		Vec3d motion = motion(player).multiply(1.0, 0.0, 1.0);
		if (age % 2 == 0) {
			Vec3d heel = player.getPos().add(0, 0.05, 0);
			spawn(world, TitanfallParticles.THRUSTER_SPARK, heel, motion.multiply(-0.5).add(0, 0.12, 0).add(jitter(random, 0.07)));
		}
	}

	/** Slide hop: a quick paired thruster puff and a small ring off the ground. */
	public static void slideHop(PlayerEntity player) {
		if (PhaseStates.hidden(player)) {
			return;
		}

		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		for (Port port : ports(player)) {
			spawn(world, TitanfallParticles.IGNITION, port.pos(), Vec3d.ZERO);
			for (int i = 0; i < 6; i++) {
				spawn(world, TitanfallParticles.THRUSTER_JET, port.pos(), port.dir().multiply(0.22 + random.nextDouble() * 0.15).add(jitter(random, 0.05)));
			}
		}

		spawn(world, TitanfallParticles.SHOCKWAVE, player.getPos().add(0, 0.05, 0), new Vec3d(0, 1, 0));
	}

	/**
	 * How far the square hitbox reaches toward a wall with this normal: its half-width on a block face, up
	 * to its half-diagonal on a diagonal wall, where the hitbox's corner is what touches the wall.
	 */
	private static double hitboxReach(PlayerEntity player, HVec normal) {
		return player.getWidth() / 2.0 * (Math.abs(normal.x()) + Math.abs(normal.z()));
	}

	/**
	 * Movement over the last tick, blocks per tick. Unlike {@code getVelocity()} this is right for other
	 * players too, whose client-side velocity is not kept up to date (they move by position updates).
	 */
	static Vec3d motion(PlayerEntity player) {
		return new Vec3d(player.getX() - player.prevX, player.getY() - player.prevY, player.getZ() - player.prevZ);
	}

	private static Vec3d jitter(Random random, double amount) {
		return new Vec3d((random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount);
	}

	private static void spawn(World world, ParticleEffect effect, Vec3d pos, Vec3d velocity) {
		world.addParticle(effect, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}
}
