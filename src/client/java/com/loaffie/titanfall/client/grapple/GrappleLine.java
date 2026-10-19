package com.loaffie.titanfall.client.grapple;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * What one pilot's grapple line looks like right now: the hook's flight, where it bit, and the reel back
 * in. Everything the renderer, the effects and the sounds need, for the local pilot (filled in by
 * {@code GrappleController}) and for other pilots (filled in from the server's relayed events).
 *
 * <p>Positions are kept per tick ({@link #hook} and {@link #prevHook}) and interpolated per frame. A hook in
 * an entity follows it: its position is the entity's interpolated position plus the offset where it bit.
 */
public final class GrappleLine {
	public enum Phase { NONE, FLYING, ATTACHED, RETRACTING }

	/** Ticks the hook takes to reel back in after letting go or missing. */
	public static final int RETRACT_TICKS = 5;

	public Phase phase = Phase.NONE;
	/** Ticks spent in the current phase. */
	public int age;
	public Vec3d hook = Vec3d.ZERO;
	public Vec3d prevHook = Vec3d.ZERO;
	/** Flight direction (unit). */
	public Vec3d dir = new Vec3d(0.0, 0.0, 1.0);
	/** Outward normal of the surface the hook bit, or null for an entity (or while flying). */
	public Vec3d normal;
	/** Hooked entity's id, or -1. */
	public int entityId = -1;
	public Vec3d entityOffset = Vec3d.ZERO;
	/** Where the hook was when the line let go: the reel-in starts there. */
	public Vec3d retractFrom = Vec3d.ZERO;
	/** Remote pilots: where the server said the hook bit, while the local flight is still catching up. */
	public Vec3d pendingAnchor;
	public Vec3d pendingNormal;
	public int pendingEntity = -1;
	/** Line tension 0..1 (slack in flight, taut while pulling), and how hard it is pulling (b/s), for looks. */
	public float tension;
	public float pull;

	/** Muzzle of the launcher in world space as last drawn, and the frame it was drawn in. */
	public Vec3d tip;
	public long tipFrame = -1L;

	public boolean active() {
		return phase != Phase.NONE;
	}

	public void fly(Vec3d origin, Vec3d direction) {
		phase = Phase.FLYING;
		age = 0;
		hook = origin;
		prevHook = origin;
		dir = direction.normalize();
		normal = null;
		entityId = -1;
		pendingAnchor = null;
		pendingEntity = -1;
		tension = 0.0F;
		pull = 0.0F;
	}

	public void attach(Vec3d at, Vec3d surfaceNormal, Entity entity) {
		phase = Phase.ATTACHED;
		age = 0;
		prevHook = hook;
		hook = at;
		normal = surfaceNormal;
		entityId = entity == null ? -1 : entity.getId();
		entityOffset = entity == null ? Vec3d.ZERO : at.subtract(entity.getPos());
		pendingAnchor = null;
		pendingEntity = -1;
	}

	public void retract(World world) {
		if (phase == Phase.NONE || phase == Phase.RETRACTING) {
			return;
		}

		retractFrom = position(world, 1.0F);
		phase = Phase.RETRACTING;
		age = 0;
		entityId = -1;
		normal = null;
		pendingAnchor = null;
	}

	public void clear() {
		phase = Phase.NONE;
		age = 0;
		entityId = -1;
		normal = null;
		pendingAnchor = null;
		tip = null;
	}

	/** The hooked entity, or null (none, or it is gone). */
	public Entity entity(World world) {
		return entityId < 0 || world == null ? null : world.getEntityById(entityId);
	}

	/**
	 * The hook's position for a frame. While retracting it returns the point the reel-in started from; the
	 * renderer draws it travelling back to the launcher with {@link #retractProgress}.
	 */
	public Vec3d position(World world, float tickDelta) {
		if (phase == Phase.RETRACTING) {
			return retractFrom;
		}

		Entity entity = phase == Phase.ATTACHED ? entity(world) : null;
		if (entity != null) {
			return entity.getLerpedPos(tickDelta).add(entityOffset);
		}

		return phase == Phase.ATTACHED ? hook : prevHook.lerp(hook, tickDelta);
	}

	/** 0 when the reel-in starts, 1 when the hook is home, eased so it snaps in at the end. */
	public float retractProgress(float tickDelta) {
		float t = MathHelper.clamp((age + tickDelta) / RETRACT_TICKS, 0.0F, 1.0F);
		return t * t;
	}
}
