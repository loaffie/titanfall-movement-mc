package com.loaffie.titanfall.movement.physics;

/**
 * The Source engine movement primitives ({@code gamemovement.cpp}), ported 1:1 to blocks per second.
 *
 * <p>Everything here is a pure function of its arguments so the same code drives the game and can be
 * stepped in isolation. Units: speeds in blocks/s, accelerations in blocks/s^2, {@code dt} in seconds.
 * Titanfall 2 scripts treat one world unit as one inch (0.0254 m), and Minecraft is 1 block = 1 m.
 */
public final class SourceMovement {
	/** One Source/Titanfall world unit in blocks (1 block = 1 metre). */
	public static final double SOURCE_UNIT = 0.0254;

	private SourceMovement() {
	}

	/**
	 * Air acceleration with vector projection, exactly the Titanfall/Source form:
	 *
	 * <pre>
	 * vProj    = dot(velocity, wishDir)
	 * addSpeed = wishSpeedCap - vProj            (how much more speed may go along wishDir)
	 * accel    = min(addSpeed, airAccel * dt)    (never more than the accelerator allows)
	 * velocity = velocity + wishDir * accel
	 * </pre>
	 *
	 * <p>The cap applies to the <em>projection</em>, not to the speed. Steering the camera while
	 * holding a strafe key keeps {@code wishDir} nearly perpendicular to the velocity, so the projection
	 * stays small, {@code addSpeed} stays positive and every step adds a little velocity at an angle:
	 * the total speed grows without any upper bound in this function. That is air strafing.
	 */
	public static HVec airAccelerate(HVec velocity, HVec wishDir, double wishSpeedCap, double airAccel, double dt) {
		if (wishDir.isZero() || airAccel <= 0.0) {
			return velocity;
		}

		double projected = velocity.dot(wishDir);
		double addSpeed = wishSpeedCap - projected;
		if (addSpeed <= 0.0) {
			return velocity;
		}

		double accelSpeed = Math.min(addSpeed, airAccel * dt);
		return velocity.add(wishDir.scale(accelSpeed));
	}

	/**
	 * Source ground {@code Accelerate()}: identical projection test, but the accelerator scales with the
	 * wish speed ({@code accel * wishSpeed * dt}) as sv_accelerate does.
	 */
	public static HVec groundAccelerate(HVec velocity, HVec wishDir, double wishSpeed, double accel, double dt) {
		if (wishDir.isZero() || accel <= 0.0 || wishSpeed <= 0.0) {
			return velocity;
		}

		double addSpeed = wishSpeed - velocity.dot(wishDir);
		if (addSpeed <= 0.0) {
			return velocity;
		}

		double accelSpeed = Math.min(addSpeed, accel * wishSpeed * dt);
		return velocity.add(wishDir.scale(accelSpeed));
	}

	/**
	 * Source {@code Friction()}: {@code drop = max(speed, stopSpeed) * friction * dt}. The stop speed
	 * makes slow movement end in finite time instead of decaying forever.
	 */
	public static HVec friction(HVec velocity, double friction, double stopSpeed, double dt) {
		double speed = velocity.length();
		if (speed < 0.05) {
			return HVec.ZERO;
		}

		double control = Math.max(speed, stopSpeed);
		double newSpeed = Math.max(speed - control * friction * dt, 0.0);
		return velocity.scale(newSpeed / speed);
	}

	/**
	 * Bends {@code velocity} toward {@code wishDir} by at most {@code maxTurn} blocks/s of lateral change
	 * while keeping its magnitude. Pulling back against the motion instead trims speed by {@code maxTurn}.
	 * This is how a slide steers: it keeps the speed brought into it but turns reluctantly.
	 */
	public static HVec steer(HVec velocity, HVec wishDir, double maxTurn) {
		double speed = velocity.length();
		if (wishDir.isZero() || maxTurn <= 0.0 || speed < 0.1) {
			return velocity;
		}

		HVec direction = velocity.scale(1.0 / speed);
		double along = wishDir.dot(direction);
		if (along < -0.5) {
			// Pulling more than 120 degrees against the motion brakes instead of turning.
			return velocity.scale(Math.max(speed - maxTurn, 0.0) / speed);
		}

		// Turn by the angle that changes the velocity laterally by maxTurn, never past the wish direction.
		double cross = direction.x() * wishDir.z() - direction.z() * wishDir.x();
		double angleToWish = Math.atan2(cross, along);
		double maxAngle = maxTurn / speed;
		double angle = Math.max(-maxAngle, Math.min(maxAngle, angleToWish));
		return rotate(direction, angle).scale(speed);
	}

	/** Rotates a horizontal vector by {@code radians} (positive turns +X toward +Z). */
	public static HVec rotate(HVec vector, double radians) {
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new HVec(vector.x() * cos - vector.z() * sin, vector.x() * sin + vector.z() * cos);
	}

	/**
	 * Rotates the direction of {@code velocity} toward {@code target} by {@code fraction} (0 keeps it,
	 * 1 points it fully at the target) without changing the speed. Used by the double jump redirect.
	 */
	public static HVec redirect(HVec velocity, HVec target, double fraction) {
		double speed = velocity.length();
		if (speed < 1.0E-6 || target.isZero() || fraction <= 0.0) {
			return velocity;
		}

		HVec from = velocity.scale(1.0 / speed);
		HVec blended = from.scale(1.0 - Math.min(fraction, 1.0)).add(target.normalize().scale(Math.min(fraction, 1.0)));
		if (blended.isZero()) {
			// Exactly opposite: the blend cancels, so fall back to the target direction itself.
			blended = target.normalize();
		}

		return blended.normalize().scale(speed);
	}

	/** Clamps the magnitude to {@code max}, keeping direction. */
	public static HVec clampLength(HVec velocity, double max) {
		double speed = velocity.length();
		return speed > max && speed > 0.0 ? velocity.scale(max / speed) : velocity;
	}
}
