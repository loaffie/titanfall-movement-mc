package com.loaffie.titanfall.awall;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.entity.TitanfallEntities;
import com.loaffie.titanfall.movement.TitanfallSounds;
import com.loaffie.titanfall.particle.TitanfallParticles;
import com.loaffie.titanfall.phase.PhaseShift;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the A-Wall, as Titanfall 2 runs it ({@code mp_weapon_deployable_cover}):
 *
 * <ul>
 *   <li>One charge, back twenty seconds after the throw ({@code ammo_per_shot} 100 at {@code regen_ammo_refill_rate}
 *   5 a second). The client runs the meter; the server refuses a throw that comes back much sooner than it
 *   could, or one from a phased pilot ({@code OnWeaponAttemptOffhandSwitch}).</li>
 *   <li>A standing wall stops every shot that meets it from the front
 *   ({@code TRACE_COLLISION_GROUP_BLOCK_WEAPONS_AND_PHYSICS}) without harm to itself - only its base can be
 *   destroyed ({@link AWallEntity#damage}) - and lets through and amps every shot going out
 *   through it from behind ({@code SetPassThroughFlags( PTF_ADDS_MODS )}, {@code pass_through_amps_weapon}) - its
 *   pilot's, their team's, anyone's ({@code TEAM_BOTH}). Amping doesn't stack: a shot is amped once.</li>
 *   <li>It stops blasts across it either way ({@code SetBlocksRadiusDamage}).</li>
 * </ul>
 *
 * <p>Walls standing are kept per world here, so the per-projectile check costs nothing while there are none.
 */
public final class AWallServer {
	/** Ticks of slack on the recharge check, for latency. */
	private static final int RECHARGE_SLACK_TICKS = 20;

	private static final Map<UUID, Long> LAST_THROW = new HashMap<>();
	private static final Map<World, List<AWallEntity>> STANDING = new HashMap<>();

	private AWallServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(AWallThrowPayload.ID, AWallThrowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AWallRefusedPayload.ID, AWallRefusedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AWallThrowPayload.ID, (payload, context) -> receive(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_THROW.remove(handler.player.getUuid()));
	}

	private static void receive(ServerPlayerEntity player, AWallThrowPayload payload) {
		TitanfallConfig config = TitanfallConfig.get();
		long now = player.getServer().getTicks();
		Long last = LAST_THROW.get(player.getUuid());
		if (!AWall.isWorn(player) || player.isSpectator() || !player.isAlive() || PhaseShift.isPhased(player)
				|| (last != null && now - last < AWall.rechargeTicks(config) - RECHARGE_SLACK_TICKS)
				|| !Float.isFinite(payload.yaw()) || !Float.isFinite(payload.pitch())) {
			ServerPlayNetworking.send(player, new AWallRefusedPayload());
			return;
		}

		LAST_THROW.put(player.getUuid(), now);
		float pitch = MathHelper.clamp(payload.pitch(), -90.0F, 90.0F);
		Vec3d motion = new Vec3d(payload.vx(), payload.vy(), payload.vz());
		if (!Double.isFinite(motion.lengthSquared()) || motion.lengthSquared() > 4.0) {
			motion = Vec3d.ZERO;
		}

		AWallEntity disc = new AWallEntity(TitanfallEntities.AWALL, player.getServerWorld());
		disc.setOwner(player);
		disc.setPosition(origin(player, payload.yaw(), pitch));
		disc.setVelocity(AWall.launch(payload.yaw(), pitch, motion));
		disc.setYaw(payload.yaw());
		disc.thrown(payload.yaw());
		player.getServerWorld().spawnEntity(disc);
		player.getServerWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(), TitanfallSounds.AWALL_TOSS, SoundCategory.PLAYERS,
				0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
	}

	/**
	 * Where the disc leaves the hand: low at the off-hand side, a little ahead - or from the eyes, if a block is
	 * in the way of that.
	 */
	private static Vec3d origin(ServerPlayerEntity player, float yaw, float pitch) {
		float side = player.getMainArm() == net.minecraft.util.Arm.RIGHT ? 1.0F : -1.0F;
		Vec3d eye = player.getEyePos();
		Vec3d forward = Vec3d.fromPolar(0.0F, yaw);
		Vec3d left = new Vec3d(forward.z, 0.0, -forward.x);
		Vec3d hand = eye.add(0.0, -0.35, 0.0).add(forward.multiply(0.35)).add(left.multiply(0.3 * side));
		var hit = player.getWorld().raycast(new RaycastContext(eye, hand, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? hand : eye;
	}

	static void planted(AWallEntity wall) {
		STANDING.computeIfAbsent(wall.getWorld(), w -> new ArrayList<>()).add(wall);
	}

	static void removed(AWallEntity wall) {
		List<AWallEntity> walls = STANDING.get(wall.getWorld());
		if (walls != null) {
			walls.remove(wall);
			if (walls.isEmpty()) {
				STANDING.remove(wall.getWorld());
			}
		}
	}

	/**
	 * A projectile is about to make this tick's move: if it meets a wall on the way, from the front it is stopped
	 * at the wall (and gone, or for an arrow or a trident, dropped there), from behind it is amped. Called at the
	 * start of every projectile's tick, server side.
	 */
	public static void beforeMove(ProjectileEntity projectile) {
		List<AWallEntity> walls = STANDING.get(projectile.getWorld());
		if (walls == null || projectile instanceof AWallEntity || projectile instanceof FishingBobberEntity
				|| (projectile instanceof FireworkRocketEntity rocket && !rocket.wasShotAtAngle())) {
			return;
		}

		Vec3d from = projectile.getPos();
		Vec3d velocity = projectile.getVelocity();
		if (velocity.lengthSquared() < 1.0E-6) {
			return;
		}

		// Explosive projectiles add their push after moving; a little extra reach covers it. The disc sits behind the
		// wall, so a shot from the front meets the wall first and never reaches it.
		Vec3d to = from.add(velocity.multiply(projectile instanceof ExplosiveProjectileEntity ? 1.2 : 1.0));
		AWall.Crossing first = null;
		for (AWallEntity wall : walls) {
			if (!wall.standing() || !wall.wallBox().intersects(from, to)) {
				continue;
			}

			AWall.Crossing crossing = AWall.cross(wall.wallBase(), wall.facing(), from, to);
			if (crossing != null && (first == null || crossing.t() < first.t())) {
				first = crossing;
			}
		}

		if (first == null) {
			return;
		}

		ServerWorld world = (ServerWorld) projectile.getWorld();
		Vec3d at = first.point();
		if (first.outward()) {
			AWallAmped amped = (AWallAmped) projectile;
			if (!amped.titanfall$amped()) {
				amped.titanfall$amp();
				world.spawnParticles(TitanfallParticles.AWALL_FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				world.playSound(null, at.x, at.y, at.z, TitanfallSounds.AWALL_AMP, SoundCategory.PLAYERS, 0.6F,
						0.9F + world.getRandom().nextFloat() * 0.2F);
			}

			return;
		}

		Vec3d normal = first.normal();
		// Sparks thrown back off the face, a ripple on it, and the shot is gone.
		world.spawnParticles(TitanfallParticles.AWALL_RIPPLE, at.x, at.y, at.z, 0, normal.x, normal.y, normal.z, 1.0);
		world.spawnParticles(TitanfallParticles.AWALL_FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 6; i++) {
			Vec3d spray = normal.multiply(0.12).add((world.getRandom().nextDouble() - 0.5) * 0.18, world.getRandom().nextDouble() * 0.12,
					(world.getRandom().nextDouble() - 0.5) * 0.18);
			world.spawnParticles(TitanfallParticles.AWALL_SPARK, at.x, at.y, at.z, 0, spray.x, spray.y, spray.z, 1.0);
		}

		world.playSound(null, at.x, at.y, at.z, TitanfallSounds.AWALL_BLOCK, SoundCategory.PLAYERS, 0.9F, 0.9F + world.getRandom().nextFloat() * 0.2F);
		projectile.setPosition(at.add(normal.multiply(0.05)));
		if (projectile instanceof PersistentProjectileEntity) {
			// Arrows and tridents are things people want back: stopped dead, they drop at the foot of the wall, as off
			// a shield, rather than vanish.
			projectile.setVelocity(normal.multiply(0.03));
			projectile.velocityDirty = true;
		} else {
			projectile.setVelocity(Vec3d.ZERO);
			projectile.discard();
		}
	}

	/** An amped shot, every tick it flies: a hot trail behind it, crackling with arcs. */
	public static void ampedTrail(ProjectileEntity projectile) {
		Vec3d velocity = projectile.getVelocity();
		if (velocity.lengthSquared() < 0.01 || !(projectile.getWorld() instanceof ServerWorld world)) {
			return;
		}

		double y = projectile.getY() + projectile.getHeight() * 0.5;
		world.spawnParticles(TitanfallParticles.AWALL_AMP, projectile.getX(), y, projectile.getZ(), 0, velocity.x, velocity.y, velocity.z, 0.15);
		// The amped round crackles as it goes: a spit of arcs off it every other tick.
		if ((projectile.age & 1) == 0) {
			Random random = world.getRandom();
			world.spawnParticles(TitanfallParticles.PHASE_SPARK, projectile.getX(), y, projectile.getZ(), 0,
					random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), 0.08);
		}
	}

	/** True if a standing wall lies across the line from a blast to {@code entity}, so the blast doesn't reach it. */
	public static boolean blocksBlast(Vec3d source, Entity entity) {
		List<AWallEntity> walls = STANDING.get(entity.getWorld());
		if (walls == null || !TitanfallConfig.get().awallBlocksExplosions) {
			return false;
		}

		Vec3d target = entity.getBoundingBox().getCenter();
		for (AWallEntity wall : walls) {
			if (wall.standing() && wall.wallBox().intersects(source, target) && AWall.cross(wall.wallBase(), wall.facing(), source, target) != null) {
				return true;
			}
		}

		return false;
	}

	/** Damage multiplier for a hit by {@code source}: the wall's amp if it went out through one. */
	public static float amp(Entity source) {
		return source instanceof AWallAmped amped && amped.titanfall$amped() ? (float) TitanfallConfig.get().awallDamageAmp : 1.0F;
	}
}
