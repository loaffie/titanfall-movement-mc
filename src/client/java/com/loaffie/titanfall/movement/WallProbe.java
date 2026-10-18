package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.movement.physics.HVec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds walls to run on from the block collision shapes around a player. Works for any player, so the
 * animation code can recognise other pilots' wall runs too.
 *
 * <p>The collision boxes beside the player are flattened to plan view at two heights, hip and head. A
 * wall is wherever there is collision at both, which rules out slabs, carpets, single fence posts at foot
 * height and other short obstacles, while counting full blocks, fences, wall posts, glass panes and modded
 * shapes alike. {@link WallFit} then finds the straight wall line those boxes form - a block face, or a
 * diagonal row of blocks, or a staircase of them - and the clearance to it.
 */
public final class WallProbe {
	/** Half-width of the stretch of wall either side of the player that the fit looks at, blocks. */
	private static final double LATERAL = 1.6;
	/** Fraction of that stretch that must be wall. */
	private static final double MIN_COVERAGE = 0.5;
	/** Collision lower than this above the feet is stepped over, not run on (slabs, stairs). */
	private static final double STEP = 0.6;
	/** Search half-range around a tracked wall's normal, and per search sector when looking all round. */
	private static final double TRACK_RANGE = Math.toRadians(70.0);
	private static final double SECTOR_RANGE = Math.toRadians(30.0);
	private static final int SECTORS = 8;

	private WallProbe() {
	}

	/**
	 * A wall line next to the player.
	 *
	 * @param normal unit normal pointing out of the wall, toward the player
	 * @param plane  where the line is: points on it satisfy {@code p . normal == plane}
	 * @param gap    clearance between the player's hitbox and the wall (negative if something sticks into it)
	 */
	public record Wall(HVec normal, double plane, double gap) {
		/** Unit vector along the wall (the normal turned 90 degrees); its sign follows the normal. */
		public HVec tangent() {
			return new HVec(-normal.z(), normal.x());
		}

		/** The normal as an angle, {@code atan2(z, x)}. */
		public double angle() {
			return Math.atan2(normal.z(), normal.x());
		}

		/** Whether two walls are the same stretch of wall: nearly the same direction and line. */
		public boolean samePlaneAs(Wall other) {
			return other != null && normal.dot(other.normal) > 0.97 && Math.abs(plane - other.plane) < 0.3;
		}

		static Wall of(WallFit.Surface surface) {
			return surface == null ? null : new Wall(surface.normal(), surface.plane(), surface.gap());
		}
	}

	/** The collision around one player this tick, in plan view, ready for any number of fits. */
	public static final class Scan {
		private final WallFit.Query query;
		private final List<WallFit.Rect> walls;
		private final List<WallFit.Rect> solids;

		private Scan(WallFit.Query query, List<WallFit.Rect> walls, List<WallFit.Rect> solids) {
			this.query = query;
			this.walls = walls;
			this.solids = solids;
		}

		/** The best wall within {@code range} of normal angle {@code centre}, or null. */
		Wall search(double centre, double range) {
			return Wall.of(WallFit.search(query, walls, solids, centre, range));
		}

		/** The wall measured at exactly this normal angle, or null if there is not enough wall that way. */
		public Wall at(double angle) {
			return Wall.of(WallFit.evaluate(query, walls, solids, angle));
		}
	}

	/** Collects the collision boxes within {@code reach} of the player's sides. */
	public static Scan scan(PlayerEntity player, double reach) {
		double halfWidth = player.getWidth() / 2.0;
		double feet = player.getY();
		double head = feet + player.getHeight();
		double low = feet + Math.min(0.7, player.getHeight() * 0.4);
		double high = head - 0.3;
		double margin = halfWidth + reach + LATERAL + 0.8;
		Box region = new Box(player.getX() - margin, feet + 0.05, player.getZ() - margin, player.getX() + margin, head - 0.05, player.getZ() + margin);

		List<WallFit.Rect> lowRects = new ArrayList<>();
		List<WallFit.Rect> highRects = new ArrayList<>();
		List<WallFit.Rect> solids = new ArrayList<>();
		for (VoxelShape shape : player.getWorld().getBlockCollisions(player, region)) {
			for (Box box : shape.getBoundingBoxes()) {
				WallFit.Rect rect = new WallFit.Rect(box.minX, box.minZ, box.maxX, box.maxZ);
				if (box.maxY > feet + STEP && box.minY < head - 0.05) {
					solids.add(rect);
				}

				if (box.minY <= low && box.maxY >= low) {
					lowRects.add(rect);
				}

				if (box.minY <= high && box.maxY >= high) {
					highRects.add(rect);
				}
			}
		}

		// Wall = collision at both heights: where a box at hip height and one at head height overlap.
		List<WallFit.Rect> walls = new ArrayList<>();
		for (WallFit.Rect a : lowRects) {
			for (WallFit.Rect b : highRects) {
				double x0 = Math.max(a.x0(), b.x0());
				double z0 = Math.max(a.z0(), b.z0());
				double x1 = Math.min(a.x1(), b.x1());
				double z1 = Math.min(a.z1(), b.z1());
				if (x1 - x0 > 1.0E-4 && z1 - z0 > 1.0E-4) {
					walls.add(new WallFit.Rect(x0, z0, x1, z1));
				}
			}
		}

		return new Scan(new WallFit.Query(player.getX(), player.getZ(), halfWidth, LATERAL, MIN_COVERAGE), walls, solids);
	}

	/**
	 * The wall to run on, if any is within {@code reach} of the hitbox. There is no facing or approach-angle
	 * filter: the caller decides from momentum and input whether there is motion along the wall. Of several
	 * (a corner, a corridor) the one the pilot's {@code intent} runs most parallel to wins, then the nearest.
	 * {@code exclude} skips that wall (used when transferring round a corner).
	 */
	public static Wall find(PlayerEntity player, HVec intent, double reach, Wall exclude) {
		return find(scan(player, reach), intent, reach, exclude);
	}

	static Wall find(Scan scan, HVec intent, double reach, Wall exclude) {
		HVec direction = intent.normalize();
		Wall best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int sector = 0; sector < SECTORS; sector++) {
			Wall wall = scan.search(sector * 2.0 * Math.PI / SECTORS, SECTOR_RANGE);
			if (wall == null || wall.gap() > reach || wall.samePlaneAs(exclude)) {
				continue;
			}

			// 1 for a wall parallel to the intent, 0 for one met head-on; closer walls break ties.
			double parallel = direction.isZero() ? 0.5 : 1.0 - Math.abs(direction.dot(wall.normal()));
			double score = parallel - wall.gap();
			if (score > bestScore) {
				best = wall;
				bestScore = score;
			}
		}

		return best;
	}

	/**
	 * Re-fits the wall being run on, searching around its current direction so it follows the wall as it
	 * turns - from a face onto a diagonal row and back, or round an outside corner. Null when the wall has
	 * run out or fallen out of reach.
	 */
	public static Wall track(Scan scan, Wall current, double reach) {
		Wall wall = scan.search(current.angle(), TRACK_RANGE);
		return wall != null && wall.gap() <= reach + 0.1 ? wall : null;
	}

	/** The nearest wall within reach on any side of the hitbox, or null (used for the wall bump). */
	public static Wall touching(PlayerEntity player, double reach) {
		Scan scan = scan(player, reach);
		Wall best = null;
		for (int sector = 0; sector < SECTORS; sector++) {
			Wall wall = scan.search(sector * 2.0 * Math.PI / SECTORS, SECTOR_RANGE);
			if (wall != null && wall.gap() <= reach && (best == null || wall.gap() < best.gap())) {
				best = wall;
			}
		}

		return best;
	}
}
