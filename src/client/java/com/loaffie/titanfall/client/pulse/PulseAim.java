package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.pulse.PulseBlade;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Where a Pulse Blade thrown now would go: from the throwing hand, converging on what's under the crosshair.
 * The aiming arc traces exactly this throw, and the throw itself sends its direction to the server, so the
 * kunai lands where the arc - and the crosshair - said it would.
 */
public final class PulseAim {
	/** How far the crosshair is looked along for something to converge on, blocks. */
	private static final double RANGE = 160.0;

	/** A throw: the hand, the starting velocity, and its direction as a yaw and pitch. */
	public record Aim(Vec3d origin, Vec3d velocity, float aimYaw, float aimPitch) {
	}

	private PulseAim() {
	}

	public static Aim of(ClientPlayerEntity player, float tickDelta) {
		TitanfallConfig config = TitanfallConfig.get();
		float yaw = player.getYaw(tickDelta);
		float pitch = player.getPitch(tickDelta);
		Vec3d eye = player.getCameraPosVec(tickDelta);
		Vec3d look = player.getRotationVec(tickDelta);
		Vec3d origin = PulseBlade.origin(eye, yaw, pitch, PulseBlade.throwingArm(player));
		Vec3d motion = motion(player);
		Vec3d direction = PulseBlade.aimAt(config, origin, crosshairTarget(player, eye, look), motion);
		if (direction == null) {
			direction = look;
		}

		Vec3d velocity = direction.multiply(PulseBlade.speed(config)).add(PulseBlade.inherited(motion));
		return new Aim(origin, velocity, PulseBlade.yawOf(direction), PulseBlade.pitchOf(direction));
	}

	/**
	 * The pilot's motion as the kunai carries it. Standing still, Minecraft keeps a small downward speed on a
	 * grounded player (gravity, cancelled by the ground each tick): that is no part of the throw.
	 */
	public static Vec3d motion(ClientPlayerEntity player) {
		Vec3d velocity = player.getVelocity();
		return player.isOnGround() ? new Vec3d(velocity.x, Math.max(velocity.y, 0.0), velocity.z) : velocity;
	}

	/** The point under the crosshair: the first block or living thing along the view, or far off along it. */
	private static Vec3d crosshairTarget(ClientPlayerEntity player, Vec3d eye, Vec3d look) {
		Vec3d end = eye.add(look.multiply(RANGE));
		BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d target = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		double reach = eye.squaredDistanceTo(target);
		EntityHitResult entity = ProjectileUtil.raycast(player, eye, target, player.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.0),
				candidate -> candidate instanceof LivingEntity && candidate.canHit() && !candidate.isSpectator(), reach);
		return entity != null ? entity.getPos() : target;
	}
}
