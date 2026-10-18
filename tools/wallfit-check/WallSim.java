package com.loaffie.titanfall.movement;

import com.loaffie.titanfall.movement.physics.HVec;
import java.util.*;

/**
 * Headless check of diagonal wall running: runs a hitbox along walls of blocks with Minecraft-style
 * axis-by-axis collision, re-fitting the wall with {@link WallFit} every tick the way the controller does,
 * and reports collisions, lost walls and speed kept.
 *
 * <p>Build the mod (or just compile WallFit and HVec), then from this folder:
 * <pre>
 * javac -cp ../../build/classes/java/client:../../build/classes/java/main -d out WallSim.java
 * java -cp out:../../build/classes/java/client:../../build/classes/java/main com.loaffie.titanfall.movement.WallSim
 * </pre>
 * Add {@code -DvanillaOrder=true} to see the runs without the controller's outward-axis-first move,
 * and {@code -Dverbose=true} for a per-tick trace.
 */
public class WallSim {
  static final double HW = 0.3;
  static List<WallFit.Rect> scene;
  static int collisions;
  static final boolean VERBOSE = Boolean.getBoolean("verbose");

  public static void main(String[] a) {
    if (a.length > 0) { if (a.length > 1 && a[1].equals("cor")) run("corridor", corridor(), 3.5, 0.6, 0, Double.parseDouble(a[0]), 10); else if (a.length > 1 && a[1].equals("in")) run("inside", inside(), 3.5, 0.6, 0, Double.parseDouble(a[0]), 30); else if (a.length > 1 && a[1].equals("12")) run("1:2 staircase", stair(1, 2, 60), 4.0, 5.6, 0, Double.parseDouble(a[0]), 30); else if (a.length > 1) run("straight -> 45-degree", straightThenStair(), 3.5, 0.6, 0, Double.parseDouble(a[0]), 30); else run("45-degree staircase", stair(1, 1, 60), 3.8, 3.0, 0, Double.parseDouble(a[0]), 30); return; }
    double[] speeds = {7.0, 10.0, 14.0};
    for (double v : speeds) {
      run("straight wall", straight(), 0.5, 0.6, 0, v, 50);
      run("45-degree staircase", stair(1, 1, 60), 3.8, 3.0, 0, v, 50);
      run("2:1 staircase", stair(2, 1, 60), 6.5, 2.3, 0, v, 50);
      run("1:2 staircase", stair(1, 2, 60), 4.0, 5.6, 0, v, 50);
      run("wall posts on a diagonal", posts(60), 3.6, 3.0, 0, v, 50);
      run("straight -> 45-degree", straightThenStair(), 3.5, 0.6, 0, v, 50);
      run("45-degree -> straight", stairThenStraight(), 3.8, 3.0, 0, v, 50);
      run("corridor 2 wide", corridor(), 3.5, 0.6, 0, v, 50);
      run("outside 90-degree corner", outside(), 3.5, 0.6, 0, v, 40);
      run("inside 90-degree corner", inside(), 3.5, 0.6, 0, v, 40);
    }
  }

  static List<WallFit.Rect> straight() { List<WallFit.Rect> r = new ArrayList<>(); for (int i = -5; i < 60; i++) r.add(new WallFit.Rect(i, 1, i + 1, 2)); return r; }
  // blocks stepping +a in x and +b in z per step, wall lying to the +z/-x side of the run
  static List<WallFit.Rect> stair(int a, int b, int n) { List<WallFit.Rect> r = new ArrayList<>(); int x = 0, z = 1; for (int i = 0; i < n; i++) { for (int k = 0; k < a; k++) { r.add(new WallFit.Rect(x, z, x + 1, z + 1)); x++; } z += b; for (int k = 1; k < b; k++) r.add(new WallFit.Rect(x - 1, z - b + k, x, z - b + k + 1)); } return r; }
  static List<WallFit.Rect> corridor() { List<WallFit.Rect> r = straight(); for (int i = -5; i < 60; i++) r.add(new WallFit.Rect(i, -1, i + 1, 0)); return r; }
  static List<WallFit.Rect> outside() { List<WallFit.Rect> r = new ArrayList<>(); for (int i = -5; i < 10; i++) r.add(new WallFit.Rect(i, 1, i + 1, 2)); for (int k = 2; k < 30; k++) r.add(new WallFit.Rect(9, k, 10, k + 1)); return r; }
  static List<WallFit.Rect> inside() { List<WallFit.Rect> r = new ArrayList<>(); for (int i = -5; i < 10; i++) r.add(new WallFit.Rect(i, 1, i + 1, 2)); for (int k = -20; k < 2; k++) r.add(new WallFit.Rect(10, k, 11, k + 1)); return r; }
  static List<WallFit.Rect> posts(int n) { List<WallFit.Rect> r = new ArrayList<>(); for (int i = 0; i < n; i++) r.add(new WallFit.Rect(i + 0.25, i + 1.25, i + 0.75, i + 1.75)); return r; }
  static List<WallFit.Rect> straightThenStair() { List<WallFit.Rect> r = new ArrayList<>(); for (int i = -5; i < 10; i++) r.add(new WallFit.Rect(i, 1, i + 1, 2)); int x = 10, z = 2; for (int i = 0; i < 40; i++) { r.add(new WallFit.Rect(x, z, x + 1, z + 1)); x++; z++; } return r; }
  static List<WallFit.Rect> stairThenStraight() { List<WallFit.Rect> r = new ArrayList<>(); int x = 0, z = 1; for (int i = 0; i < 12; i++) { r.add(new WallFit.Rect(x, z, x + 1, z + 1)); x++; z++; } for (int i = 0; i < 40; i++) r.add(new WallFit.Rect(x + i, z - 1, x + i + 1, z)); return r; }

  static void run(String name, List<WallFit.Rect> rects, double x0, double z0, int dummy, double speed, int ticks) {
    scene = rects; collisions = 0;
    // start: find wall from a point in the open, then step back to the latch gap
    double x = x0, z = z0;
    // place player in front of first wall element with a small gap using a fit
    WallFit.Query q = new WallFit.Query(x, z, HW, 1.6, 0.5);
    WallFit.Surface s = null;
    double bestScore = -1e9; HVec intent = new HVec(1, 0.2).normalize();
    for (int k = 0; k < 8; k++) { WallFit.Surface c = WallFit.search(q, near(x, z), near(x, z), k * Math.PI / 4, Math.toRadians(30)); if (c == null) continue; double sc = 1 - Math.abs(intent.dot(c.normal())) - c.gap(); if (sc > bestScore) { bestScore = sc; s = c; } }
    if (s == null) { System.out.println(name + ": no wall found at start"); return; }
    x += s.normal().x() * (s.gap() - 0.03); z += s.normal().z() * (s.gap() - 0.03);
    double angle = Math.atan2(s.normal().z(), s.normal().x());
    HVec tangent = s.tangent(); if (tangent.x() < 0) tangent = tangent.scale(-1);
    double minGap = 9, maxGap = -9; double runSpeed = speed; int lost = -1; StringBuilder trace = new StringBuilder();
    for (int t = 0; t < ticks; t++) {
      q = new WallFit.Query(x, z, HW, 1.6, 0.5);
      List<WallFit.Rect> local = near(x, z);
      WallFit.Surface f = WallFit.search(q, local, local, angle, Math.toRadians(70));
      if (f == null) { lost = t; break; }
      double fa = Math.atan2(f.normal().z(), f.normal().x());
      double d = Math.atan2(Math.sin(fa - angle), Math.cos(fa - angle));
      double maxTurn = Math.toRadians(20);
      angle += Math.max(-maxTurn, Math.min(maxTurn, d));
      HVec n = new HVec(Math.cos(angle), Math.sin(angle));
      HVec tn = new HVec(-n.z(), n.x()); if (tn.dot(tangent) < 0) tn = tn.scale(-1); tangent = tn;
      // gap measured against the smoothed normal
      WallFit.Surface g = WallFit.evaluate(q, local, local, angle);
      double gap = g == null ? f.gap() : g.gap();
      minGap = Math.min(minGap, gap); maxGap = Math.max(maxGap, gap);
      double stick = Math.max(-0.1, Math.min(0.1, gap - 0.03));
      double vx = tangent.x() * runSpeed * 0.05 - n.x() * stick, vz = tangent.z() * runSpeed * 0.05 - n.z() * stick;
      double[] moved = SAFE ? moveSafe(x, z, vx, vz, n) : move(x, z, vx, vz);
      boolean bx = moved[2] > 0, bz = moved[3] > 0;
      if ((bx || bz) && VERBOSE) trace.append(String.format(" COLLIDE(t%d bx%b bz%b pos %.3f,%.3f v %.3f,%.3f)", t, bx, bz, x, z, vx, vz));
      if (bx || bz) { collisions++; HVec m = new HVec(bx ? 0 : tangent.x() * runSpeed, bz ? 0 : tangent.z() * runSpeed); runSpeed = Math.abs(m.dot(tangent)); }
      x = moved[0]; z = moved[1];
      if (VERBOSE) trace.append(String.format(" [%d: ang %.0f gap %.3f pos %.3f,%.3f fit %.1f]", t, Math.toDegrees(angle), gap, x, z, Math.toDegrees(fa)));
    }
    System.out.printf("%-26s v=%4.1f collisions %d, final speed %.2f/%.2f, gap %.3f..%.3f, lost %s%s%n", name, speed, collisions, runSpeed, speed, minGap, maxGap, lost < 0 ? "no" : "at tick " + lost, VERBOSE ? "\n   " + trace : "");
  }

  static List<WallFit.Rect> near(double x, double z) { List<WallFit.Rect> out = new ArrayList<>(); for (WallFit.Rect r : scene) if (r.x1() > x - 3 && r.x0() < x + 3 && r.z1() > z - 3 && r.z0() < z + 3) out.add(r); return out; }

  static final boolean SAFE = !Boolean.getBoolean("vanillaOrder");
  /** The controller's pre-move: the axis heading away from the wall first (one move call), then vanilla's move for the rest. */
  static double[] moveSafe(double x, double z, double dx, double dz, HVec n) {
    boolean xOutward = dx * n.x() > 0, zOutward = dz * n.z() > 0;
    if (xOutward == zOutward) return move(x, z, dx, dz);
    double[] first = xOutward ? move(x, z, dx, 0) : move(x, z, 0, dz);
    double[] second = xOutward ? move(first[0], first[1], 0, dz) : move(first[0], first[1], dx, 0);
    return new double[]{second[0], second[1], Math.max(first[2], second[2]), Math.max(first[3], second[3])};
  }

  /** Minecraft-style: larger axis first, each clipped against boxes. Returns x, z, blockedX, blockedZ. */
  static double[] move(double x, double z, double dx, double dz) {
    boolean zFirst = Math.abs(dx) < Math.abs(dz);
    double[] p = {x, z}; double bX = 0, bZ = 0;
    for (int pass = 0; pass < 2; pass++) {
      boolean doX = (pass == 0) != zFirst;
      double want = doX ? dx : dz;
      double got = clip(p[0], p[1], doX, want);
      if (Math.abs(got - want) > 1e-7) { if (doX) bX = 1; else bZ = 1; }
      if (doX) p[0] += got; else p[1] += got;
    }
    return new double[]{p[0], p[1], bX, bZ};
  }
  static double clip(double x, double z, boolean alongX, double d) {
    double x0 = x - HW, x1 = x + HW, z0 = z - HW, z1 = z + HW;
    for (WallFit.Rect r : scene) {
      if (alongX) {
        if (r.z1() <= z0 + 1e-7 || r.z0() >= z1 - 1e-7) continue;
        if (d > 0 && r.x0() >= x1 - 1e-7) d = Math.min(d, r.x0() - x1);
        if (d < 0 && r.x1() <= x0 + 1e-7) d = Math.max(d, r.x1() - x0);
      } else {
        if (r.x1() <= x0 + 1e-7 || r.x0() >= x1 - 1e-7) continue;
        if (d > 0 && r.z0() >= z1 - 1e-7) d = Math.min(d, r.z0() - z1);
        if (d < 0 && r.z1() <= z0 + 1e-7) d = Math.max(d, r.z1() - z0);
      }
    }
    return d;
  }
}
