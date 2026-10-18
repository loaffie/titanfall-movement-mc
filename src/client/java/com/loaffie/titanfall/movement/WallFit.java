package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.movement.physics.HVec;

import java.util.List;

/**
 * Fits a straight wall line to the blocks beside the player, in plan view.
 *
 * <p>Walls are not always block faces. A row of blocks set corner to corner, a 2:1 staircase of blocks or
 * a line of wall posts reads as one straight wall to a player, but has no single face to run on. So this
 * treats the blocks on one side of the player as an outline - the corners of their collision boxes - and
 * takes the wall to be the outline's edge nearest the player. Its direction is the one in which the
 * player stands furthest out from the outline (the direction from the nearest outline point to the
 * player), which is exactly a face's normal in front of a face, exactly the staircase's slope in front of
 * a staircase, and turns smoothly round the corner where two such walls meet. The edge itself runs through
 * the outermost block corners, so a hitbox held in front of it never catches a step.
 *
 * <p>A depth profile along a window either side of the player then checks that there really is wall
 * there (not just one block), and the clearance to everything solid gives the gap to keep.
 *
 * <p>Everything here is plain 2D geometry on axis-aligned rectangles (block collision boxes seen from
 * above), with no game classes, so it can be tested outside the game.
 */
public final class WallFit {
	/** Depths past this count as "no wall here", blocks. */
	private static final double DEPTH_CAP = 1.0;
	/** A sample is wall if the surface is no deeper than this behind the support line. */
	private static final double COVERED_DEPTH = 0.75;
	/** Extra radius, past the lateral window, from which corners join the outline. */
	private static final double OUTLINE_MARGIN = 0.8;
	/** How far past the player's centre (along the search normal) a block's centre may lie and still count. */
	private static final double SIDE_TOLERANCE = 0.5;
	/** Lateral samples across the window. */
	private static final int SAMPLES = 25;

	private WallFit() {
	}

	/** An axis-aligned rectangle in plan view (a collision box's X/Z footprint). */
	public record Rect(double x0, double z0, double x1, double z1) {
	}

	/**
	 * A fitted wall.
	 *
	 * @param normal unit normal pointing out of the wall, toward the player
	 * @param plane  the support line: points {@code p} on it satisfy {@code p . normal == plane}
	 * @param gap    clearance between the player's hitbox and the nearest solid block in front of the
	 *               line (the support line itself, or anything sticking out further near the player)
	 * @param coverage fraction of the window where the wall is present
	 * @param depth  mean depth of the surface behind the line (0 for a flat face, ~0.35 for a 45-degree
	 *               staircase of full blocks)
	 */
	public record Surface(HVec normal, double plane, double gap, double coverage, double depth) {
		/** Unit vector along the wall (the normal turned 90 degrees). */
		public HVec tangent() {
			return new HVec(-normal.z(), normal.x());
		}

		/** Distance from a point to the support line, positive on the open side. */
		public double distance(double x, double z) {
			return x * normal.x() + z * normal.z() - plane;
		}
	}

	/** What the fit is searching: where the player stands and which directions to try. */
	public record Query(double x, double z, double halfWidth, double lateral, double minCoverage) {
	}

	/**
	 * The wall on the side of the player that outward normal {@code centre} (radians, {@code atan2(z, x)})
	 * looks back from, searching normal angles within {@code range} of it; null if there is none.
	 *
	 * <p>The blocks taken into account are those near the player on that side. Their corners form an
	 * outline, and the wall's normal is the direction from the nearest point of that outline to the player:
	 * the direction in which the player stands furthest out from the outline's support line. In front of a
	 * flat face or a staircase that is exactly the face's or the staircase's normal, with no wobble as the
	 * player moves along it. Where a straight wall turns into a diagonal one the nearest outline point is
	 * the corner between them, and the normal turns smoothly from one wall's to the other's as the player
	 * passes it.
	 */
	public static Surface search(Query query, List<Rect> walls, List<Rect> solids, double centre, double range) {
		double[] corners = sideCorners(query, walls, centre);
		if (corners.length == 0) {
			return null;
		}

		// Coarse sweep for the bracket, then golden-section search: separation is unimodal in the angle
		// (it is the player's distance to a convex outline, seen along each direction).
		int steps = 16;
		int bestIndex = 0;
		double bestValue = -Double.MAX_VALUE;
		for (int i = 0; i <= steps; i++) {
			double value = separation(query, corners, centre - range + 2.0 * range * i / steps);
			if (value > bestValue) {
				bestValue = value;
				bestIndex = i;
			}
		}

		// A maximum on the edge of the range means the wall's normal lies outside it: leave that wall to a
		// search centred nearer to it.
		if (bestIndex == 0 || bestIndex == steps) {
			return null;
		}

		double best = centre - range + 2.0 * range * bestIndex / steps;

		double bracket = 2.0 * range / steps;
		double lo = Math.max(centre - range, best - bracket);
		double hi = Math.min(centre + range, best + bracket);
		final double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
		double c = hi - ratio * (hi - lo);
		double d = lo + ratio * (hi - lo);
		double fc = separation(query, corners, c);
		double fd = separation(query, corners, d);
		for (int i = 0; i < 24; i++) {
			if (fc >= fd) {
				hi = d;
				d = c;
				fd = fc;
				c = hi - ratio * (hi - lo);
				fc = separation(query, corners, c);
			} else {
				lo = c;
				c = d;
				fc = fd;
				d = lo + ratio * (hi - lo);
				fd = separation(query, corners, d);
			}
		}

		return evaluate(query, walls, solids, (lo + hi) * 0.5);
	}

	/**
	 * Corners (flattened x, z) of the wall rectangles near the player on the side the normal {@code centre}
	 * faces away from: within the search radius, and not on the far side of the player. A block whose centre
	 * is up to half a block past the player's centre still counts - the steps of a steep staircase sit
	 * beside the player - while a wall across a one-block corridor (its centre at least 0.8 away) does not.
	 */
	private static double[] sideCorners(Query query, List<Rect> walls, double centre) {
		double nx = Math.cos(centre);
		double nz = Math.sin(centre);
		// Reach a little past the window, so the outline is complete along it: the next corner of a steep
		// staircase can sit two blocks along from the last one.
		double radius = query.lateral() + query.halfWidth() + OUTLINE_MARGIN;
		double[] out = new double[walls.size() * 8];
		int count = 0;
		for (Rect rect : walls) {
			double cx = (rect.x0() + rect.x1()) * 0.5 - query.x();
			double cz = (rect.z0() + rect.z1()) * 0.5 - query.z();
			if (cx * nx + cz * nz >= SIDE_TOLERANCE) {
				continue;
			}

			double dx = Math.max(rect.x0() - query.x(), Math.max(0.0, query.x() - rect.x1()));
			double dz = Math.max(rect.z0() - query.z(), Math.max(0.0, query.z() - rect.z1()));
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}

			out[count++] = rect.x0();
			out[count++] = rect.z0();
			out[count++] = rect.x1();
			out[count++] = rect.z0();
			out[count++] = rect.x0();
			out[count++] = rect.z1();
			out[count++] = rect.x1();
			out[count++] = rect.z1();
		}

		return java.util.Arrays.copyOf(out, count);
	}

	/** How far the player's centre stands out from the support line of {@code corners} along this normal. */
	private static double separation(Query query, double[] corners, double angle) {
		double nx = Math.cos(angle);
		double nz = Math.sin(angle);
		double support = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < corners.length; i += 2) {
			support = Math.max(support, corners[i] * nx + corners[i + 1] * nz);
		}

		return query.x() * nx + query.z() * nz - support;
	}

	/**
	 * The wall at one fixed normal angle, or null if there is not enough of it. Used to re-measure a known
	 * wall (its distance) without searching.
	 */
	public static Surface evaluate(Query query, List<Rect> walls, List<Rect> solids, double angle) {
		HVec n = new HVec(Math.cos(angle), Math.sin(angle));
		Profile profile = profile(query, walls, n);
		if (profile == null || profile.coverage < query.minCoverage()) {
			return null;
		}

		// Clearance: the hitbox's extent toward the wall, against the support line and anything solid that
		// sticks out further within the hitbox's own lateral reach (plus a stride ahead either way).
		double extentN = query.halfWidth() * (Math.abs(n.x()) + Math.abs(n.z()));
		double extentT = query.halfWidth() * (Math.abs(n.z()) + Math.abs(n.x()));
		HVec t = new HVec(-n.z(), n.x());
		double pt = query.x() * t.x() + query.z() * t.z();
		double pn = query.x() * n.x() + query.z() * n.z();
		double front = profile.plane;
		for (Rect rect : solids) {
			double reach = maxAlongNormal(rect, n, t, pt - extentT - 0.5, pt + extentT + 0.5);
			if (reach > front && reach < pn) {
				front = reach;
			}
		}

		double gap = pn - extentN - front;
		return new Surface(n, profile.plane, gap, profile.coverage, profile.depth);
	}

	private record Profile(double plane, double coverage, double depth) {
	}

	/**
	 * Support line and depth profile for one normal. Only rectangles behind the player (on the far side of
	 * the player's centre along the normal) take part, so the wall behind the player's back or a block
	 * the player stands beside on the other side never counts.
	 */
	private static Profile profile(Query query, List<Rect> walls, HVec n) {
		HVec t = new HVec(-n.z(), n.x());
		double pn = query.x() * n.x() + query.z() * n.z();
		double pt = query.x() * t.x() + query.z() * t.z();
		double lo = pt - query.lateral();
		double hi = pt + query.lateral();
		// The hitbox's lateral extent seen along this normal.
		double side = query.halfWidth() * (Math.abs(n.x()) + Math.abs(n.z()));

		// A block level with the player's centre within the hitbox's width means the player stands beside
		// it, not in front of it: this is not the direction of a wall. Blocks further along the window that
		// come level with the player are an inside corner or an obstacle ahead, and only drop out of the fit.
		double plane = Double.NEGATIVE_INFINITY;
		for (Rect rect : walls) {
			double reach = maxAlongNormal(rect, n, t, lo, hi);
			if (reach == Double.NEGATIVE_INFINITY) {
				continue;
			}

			if (reach > pn - 1.0E-4) {
				if (maxAlongNormal(rect, n, t, pt - side, pt + side) > pn - 1.0E-4 && minAlongNormal(rect, n) < pn) {
					return null;
				}
				continue;
			}

			plane = Math.max(plane, reach);
		}

		if (plane == Double.NEGATIVE_INFINITY) {
			return null;
		}

		double depthSum = 0.0;
		boolean[] hit = new boolean[SAMPLES];
		for (int k = 0; k < SAMPLES; k++) {
			double s = lo + (hi - lo) * k / (SAMPLES - 1);
			double surface = Double.NEGATIVE_INFINITY;
			for (Rect rect : walls) {
				double reach = maxAlongLine(rect, n, t, s);
				if (reach <= plane + 1.0E-9 && reach > surface) {
					surface = reach;
				}
			}

			double depth = Math.min(plane - surface, DEPTH_CAP);
			depthSum += depth;
			hit[k] = depth <= COVERED_DEPTH;
		}

		// A gap narrower than the hitbox cannot be fallen into (fence posts, wall posts set corner to
		// corner), so wall within half a hitbox either side of a sample covers it too.
		int reach = (int) Math.ceil(query.halfWidth() / ((hi - lo) / (SAMPLES - 1)));
		int covered = 0;
		for (int k = 0; k < SAMPLES; k++) {
			for (int j = Math.max(0, k - reach); j <= Math.min(SAMPLES - 1, k + reach); j++) {
				if (hit[j]) {
					covered++;
					break;
				}
			}
		}

		return new Profile(plane, covered / (double) SAMPLES, depthSum / SAMPLES);
	}

	/** Nearest point of a rectangle along {@code n} (its minimum projection). */
	private static double minAlongNormal(Rect rect, HVec n) {
		return Math.min(rect.x0() * n.x(), rect.x1() * n.x()) + Math.min(rect.z0() * n.z(), rect.z1() * n.z());
	}

	/**
	 * Furthest point of a rectangle along {@code n}, over the part of it whose position along {@code t}
	 * lies in {@code [lo, hi]}; negative infinity if none of it does. The maximum of a linear function over
	 * the rectangle clipped to the slab is reached at a corner of that clipped polygon, which is either a
	 * rectangle corner inside the slab or a crossing of a slab boundary with a rectangle edge.
	 */
	static double maxAlongNormal(Rect rect, HVec n, HVec t, double lo, double hi) {
		double best = Double.NEGATIVE_INFINITY;
		double[] xs = {rect.x0(), rect.x1()};
		double[] zs = {rect.z0(), rect.z1()};
		for (double x : xs) {
			for (double z : zs) {
				double along = x * t.x() + z * t.z();
				if (along >= lo && along <= hi) {
					best = Math.max(best, x * n.x() + z * n.z());
				}
			}
		}

		best = Math.max(best, maxAlongLine(rect, n, t, lo));
		best = Math.max(best, maxAlongLine(rect, n, t, hi));
		return best;
	}

	/**
	 * Furthest point along {@code n} of the rectangle's intersection with the line of points whose position
	 * along {@code t} is {@code s} (a line parallel to {@code n}); negative infinity if they do not meet.
	 */
	static double maxAlongLine(Rect rect, HVec n, HVec t, double s) {
		// Points on the line: p(u) = s * t + u * n. Find the u-interval inside the rectangle.
		double uMin = Double.NEGATIVE_INFINITY;
		double uMax = Double.POSITIVE_INFINITY;
		double[] origin = {s * t.x(), s * t.z()};
		double[] dir = {n.x(), n.z()};
		double[] min = {rect.x0(), rect.z0()};
		double[] max = {rect.x1(), rect.z1()};
		for (int axis = 0; axis < 2; axis++) {
			if (Math.abs(dir[axis]) < 1.0E-9) {
				if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
					return Double.NEGATIVE_INFINITY;
				}
				continue;
			}

			double a = (min[axis] - origin[axis]) / dir[axis];
			double b = (max[axis] - origin[axis]) / dir[axis];
			uMin = Math.max(uMin, Math.min(a, b));
			uMax = Math.min(uMax, Math.max(a, b));
		}

		return uMin <= uMax ? uMax : Double.NEGATIVE_INFINITY;
	}
}
