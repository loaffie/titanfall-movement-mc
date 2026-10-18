package com.loaffie.titanfall.movement.physics;

/**
 * Immutable horizontal (X/Z) vector used by the movement model.
 *
 * <p>The physics core deliberately works on its own two-component type instead of Minecraft's
 * {@code Vec3d}: every Source formula in this mod is a horizontal one, a 2D type cannot accidentally
 * leak vertical velocity into a horizontal projection, and it keeps the core free of game classes so
 * the maths can be exercised outside the game.
 */
public record HVec(double x, double z) {
	public static final HVec ZERO = new HVec(0.0, 0.0);

	public HVec add(HVec other) {
		return new HVec(x + other.x, z + other.z);
	}

	public HVec subtract(HVec other) {
		return new HVec(x - other.x, z - other.z);
	}

	public HVec scale(double factor) {
		return new HVec(x * factor, z * factor);
	}

	public double dot(HVec other) {
		return x * other.x + z * other.z;
	}

	public double lengthSquared() {
		return x * x + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSquared());
	}

	/** Unit vector, or {@link #ZERO} for a (near) zero vector. */
	public HVec normalize() {
		double length = length();
		return length < 1.0E-9 ? ZERO : new HVec(x / length, z / length);
	}

	/** The same direction with the given magnitude; {@link #ZERO} stays zero. */
	public HVec withLength(double length) {
		double current = length();
		return current < 1.0E-9 ? ZERO : scale(length / current);
	}

	/** Removes the component along {@code unitNormal}: what is left lies in the plane of a wall. */
	public HVec rejectFrom(HVec unitNormal) {
		return subtract(unitNormal.scale(dot(unitNormal)));
	}

	public boolean isZero() {
		return lengthSquared() < 1.0E-12;
	}

	/**
	 * Unit forward vector for a Minecraft yaw in degrees. Minecraft yaw 0 looks toward +Z and yaw 90
	 * toward -X, so forward is {@code (-sin(yaw), cos(yaw))}.
	 */
	public static HVec forwardFromYaw(double yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		return new HVec(-Math.sin(radians), Math.cos(radians));
	}

	/** Unit vector pointing to the player's right for a given yaw: forward x up. */
	public static HVec rightFromYaw(double yawDegrees) {
		HVec forward = forwardFromYaw(yawDegrees);
		return new HVec(-forward.z, forward.x);
	}
}
