package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Grapple particles: a muzzle flash and a spit of sparks when the launcher fires, a faint tracer behind the
 * hook in flight, and where it bites a flash, a ring punched out of the surface, chips of the block itself
 * and sparks off the hook - or a burst of crit sparks on a creature. They use the jumpkit's additive glow
 * particles, and the same "thruster particles" switch turns them off.
 */
public final class GrappleEffects {
	private GrappleEffects() {
	}

	public static void fire(PlayerEntity player, GrappleLine line) {
		if (!enabled()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		Vec3d dir = line.dir;
		Vec3d muzzle = GrappleRenderer.muzzle(player, line, 1.0F);
		MinecraftClient client = MinecraftClient.getInstance();
		boolean ownView = player == client.player && client.options.getPerspective().isFirstPerson();
		if (ownView) {
			// In first person the muzzle is a hand's breadth from the eye, where a spark fills the screen: the
			// burst is set off a little way down the line instead, and stays small and clear of the view.
			muzzle = muzzle.add(dir.multiply(1.1));
		}

		spawn(world, TitanfallParticles.GRAPPLE_FLASH, muzzle.add(dir.multiply(0.12)), Vec3d.ZERO);
		for (int i = 0; i < (ownView ? 3 : 6); i++) {
			spawn(world, TitanfallParticles.THRUSTER_SPARK, muzzle, dir.multiply(0.35 + random.nextDouble() * 0.3).add(jitter(random, 0.12)));
		}

		if (!ownView) {
			for (int i = 0; i < 2; i++) {
				spawn(world, ParticleTypes.SMOKE, muzzle, dir.multiply(0.04).add(jitter(random, 0.02)));
			}
		}
	}

	/** Per tick while the line is out: the tracer in flight, and sparks off the hook while it bears the pull. */
	static void tick(PlayerEntity player, GrappleLine line) {
		if (!enabled() || !line.active()) {
			return;
		}

		World world = player.getWorld();
		Random random = world.getRandom();
		if (line.phase == GrappleLine.Phase.FLYING) {
			for (int i = 0; i < 3; i++) {
				Vec3d at = line.prevHook.lerp(line.hook, random.nextDouble());
				spawn(world, TitanfallParticles.GRAPPLE_TRAIL, at, line.dir.multiply(0.25).add(jitter(random, 0.02)));
			}
		} else if (line.phase == GrappleLine.Phase.ATTACHED && line.normal != null && line.pull > 8.0F && line.age % 5 == 0) {
			Vec3d at = line.position(world, 1.0F);
			spawn(world, TitanfallParticles.THRUSTER_SPARK, at, line.normal.multiply(0.12).add(jitter(random, 0.08)));
		}
	}

	/** The hook bites. {@code normal} is the surface's outward normal, null for an entity. */
	static void impact(World world, GrappleLine line, Vec3d at, Vec3d normal, Entity entity) {
		if (!enabled()) {
			return;
		}

		Random random = world.getRandom();
		Vec3d facing = normal != null ? normal : line.dir.multiply(-1.0);
		Vec3d out = at.add(facing.multiply(0.06));
		spawn(world, TitanfallParticles.GRAPPLE_FLASH, out, Vec3d.ZERO);
		if (entity != null) {
			for (int i = 0; i < 10; i++) {
				spawn(world, ParticleTypes.CRIT, out, facing.multiply(0.25).add(jitter(random, 0.25)));
			}

			return;
		}

		spawn(world, TitanfallParticles.GRAPPLE_RING, out, facing);
		// Chips of whatever the hook bit into, thrown back out of the surface.
		BlockPos pos = BlockPos.ofFloored(at.subtract(facing.multiply(0.05)));
		BlockState state = world.getBlockState(pos);
		if (!state.isAir()) {
			ParticleEffect chips = new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
			for (int i = 0; i < 12; i++) {
				spawn(world, chips, out, facing.multiply(0.12 + random.nextDouble() * 0.15).add(jitter(random, 0.12)));
			}
		}

		// The hook's claws strike sparks, sprayed back off the surface the way the hook came in.
		Vec3d bounce = line.dir.subtract(facing.multiply(2.0 * line.dir.dotProduct(facing)));
		for (int i = 0; i < 9; i++) {
			spawn(world, TitanfallParticles.THRUSTER_SPARK, out, bounce.multiply(0.22 + random.nextDouble() * 0.2).add(jitter(random, 0.15)));
		}
	}

	private static boolean enabled() {
		return TitanfallConfig.get().thrusterParticles;
	}

	private static Vec3d jitter(Random random, double amount) {
		return new Vec3d((random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount);
	}

	private static void spawn(World world, ParticleEffect effect, Vec3d pos, Vec3d velocity) {
		world.addParticle(effect, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}
}
