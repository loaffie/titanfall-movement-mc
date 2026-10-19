#!/usr/bin/env python3
"""Build the Grapple gauntlet: its mesh (OBJ) and texture atlas.

Titanfall 2's Grapple is a launcher built into the forearm of the pilot's gauntlet: an armoured bracer
round the forearm, the launcher housing along the top of it with the barrel running out over the back of
the hand, a cable spool on the side, and the three-clawed anchor hook seated in the muzzle. This script
models that from simple convex solids (bevelled boxes, cylinders, cones), each the convex hull of a few
rings of points, so every edge is chamfered and the round parts are actually round.

Usage (from the repository root):

    python3 tools/generate-grapple-model.py

Requires numpy, scipy and Pillow. Writes:

    src/main/resources/assets/titanfall_movement/models/entity/grapple.obj
    src/main/resources/assets/titanfall_movement/textures/entity/grapple.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_grapple.png   (sprite, from the mesh)

Mesh space is the player model's arm space in pixels, for a left arm whose centre line is x = 0: +Y runs
from the shoulder (y = -2) down to the hand (y = 10), -Z is the front of the arm (the top of the forearm
when the arm is raised to aim), +X is the outside of a left arm. The renderer moves it onto either arm
(mirroring it for the right) and narrows it for slim arms. The ``hook`` object is in its own space: its
eyelet at the origin and the claws pointing along +Y.

Objects:
    gauntlet  - the bracer, launcher, barrel and spool (lit, normal texture)
    glow      - the status light strip on the launcher (drawn full-bright, tinted by the charge)
    hook      - the anchor hook (drawn in the muzzle when loaded, and in flight / in the wall)
    hook_glow - the hook's beacon ring (full-bright)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np
from PIL import Image
from scipy.spatial import ConvexHull

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/grapple.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/grapple.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_grapple.png"

ATLAS = 64
SWATCH = 16
# Palette taken from the jumpkit's Grapple-pilot texture, so the gauntlet matches the kit.
MATERIALS = {
    "shell": ((0x4d, 0x57, 0x45), 0),    # olive armour
    "dark": ((0x27, 0x2f, 0x3a), 1),     # gunmetal
    "black": ((0x18, 0x1b, 0x20), 2),    # under-suit / rubber
    "metal": ((0x79, 0x81, 0x89), 3),    # steel
    "accent": ((0xc3, 0x8b, 0x37), 4),   # amber stripe
    "cable": ((0x2e, 0x2c, 0x29), 5),    # braided line
    "light": ((0xff, 0xff, 0xff), 6),    # emissive (tinted in game)
    "brass": ((0x96, 0x60, 0x2f), 7),    # worn copper fittings
}


def swatch_origin(index):
    per_row = ATLAS // SWATCH
    return (index % per_row) * SWATCH, (index // per_row) * SWATCH


# ------------------------------------------------------------------------------------------------
# solids: each is a point cloud whose convex hull is the part
# ------------------------------------------------------------------------------------------------

def bevel_box(x0, y0, z0, x1, y1, z1, bevel):
    """A box with every edge chamfered by ``bevel``: three points per corner, pulled in along each axis."""
    pts = []
    for x in (x0, x1):
        for y in (y0, y1):
            for z in (z0, z1):
                sx = 1 if x == x0 else -1
                sy = 1 if y == y0 else -1
                sz = 1 if z == z0 else -1
                pts.append((x + sx * bevel, y, z))
                pts.append((x, y + sy * bevel, z))
                pts.append((x, y, z + sz * bevel))
    return np.array(pts, dtype=float)


def ring(axis, centre, radius, at, segments):
    """Points of a circle of ``radius`` round the ``axis`` line through ``centre``, at ``at`` along it."""
    pts = []
    for k in range(segments):
        a = 2 * math.pi * (k + 0.5) / segments
        c, s = math.cos(a) * radius, math.sin(a) * radius
        if axis == "x":
            pts.append((at, centre[1] + c, centre[2] + s))
        elif axis == "y":
            pts.append((centre[0] + c, at, centre[2] + s))
        else:
            pts.append((centre[0] + c, centre[1] + s, at))
    return pts


def cylinder(axis, centre, radius, start, end, segments=12, chamfer=0.0):
    pts = []
    if chamfer > 0:
        pts += ring(axis, centre, radius - chamfer, start, segments)
        pts += ring(axis, centre, radius, start + chamfer, segments)
        pts += ring(axis, centre, radius, end - chamfer, segments)
        pts += ring(axis, centre, radius - chamfer, end, segments)
    else:
        pts += ring(axis, centre, radius, start, segments)
        pts += ring(axis, centre, radius, end, segments)
    return np.array(pts, dtype=float)


def cone(axis, centre, radius, start, tip, segments=12):
    pts = ring(axis, centre, radius, start, segments)
    if axis == "y":
        pts.append((centre[0], tip, centre[2]))
    elif axis == "x":
        pts.append((tip, centre[1], centre[2]))
    else:
        pts.append((centre[0], centre[1], tip))
    return np.array(pts, dtype=float)


def capsule_segment(a, b, thickness):
    """A thin bar between two points: the hull of two small cubes."""
    pts = []
    h = thickness / 2
    for p in (a, b):
        for dx in (-h, h):
            for dy in (-h, h):
                for dz in (-h, h):
                    pts.append((p[0] + dx, p[1] + dy, p[2] + dz))
    return np.array(pts, dtype=float)


# ------------------------------------------------------------------------------------------------
# mesh building
# ------------------------------------------------------------------------------------------------

class Mesh:
    def __init__(self):
        self.objects = {}

    def add(self, obj, points, material):
        """Adds the convex hull of ``points`` to ``obj``, with flat normals and atlas UVs from ``material``."""
        hull = ConvexHull(points)
        centre = points.mean(axis=0)
        lo, hi = points.min(axis=0), points.max(axis=0)
        span = np.maximum(hi - lo, 1e-6)
        ox, oy = swatch_origin(MATERIALS[material][1])
        tris = self.objects.setdefault(obj, [])
        for simplex in hull.simplices:
            a, b, c = (points[i] for i in simplex)
            n = np.cross(b - a, c - a)
            length = np.linalg.norm(n)
            if length < 1e-9:
                continue
            n /= length
            if np.dot(n, a - centre) < 0:
                b, c = c, b
                n = -n
            # Planar UVs on the face's dominant axis, the part's extent spread across the swatch (with a
            # one-texel margin so the filter never reads the neighbouring swatch).
            axis = int(np.argmax(np.abs(n)))
            u_axis, v_axis = [(1, 2), (0, 2), (0, 1)][axis]
            corners = []
            for p in (a, b, c):
                u = ox + 1 + (p[u_axis] - lo[u_axis]) / span[u_axis] * (SWATCH - 2)
                v = oy + 1 + (p[v_axis] - lo[v_axis]) / span[v_axis] * (SWATCH - 2)
                corners.append((p, (u / ATLAS, v / ATLAS)))
            tris.append((corners, n))

    def write(self, path):
        lines = ["# Grapple gauntlet, generated by tools/generate-grapple-model.py - do not edit by hand.",
                 "# Units: player-model pixels, left-arm space (+Y toward the hand, -Z the top of the forearm)."]
        vi = vt = vn = 0
        for name, tris in self.objects.items():
            lines.append(f"o {name}")
            for corners, n in tris:
                face = []
                lines.append(f"vn {n[0]:.4f} {n[1]:.4f} {n[2]:.4f}")
                vn += 1
                for p, (u, v) in corners:
                    lines.append(f"v {p[0]:.4f} {p[1]:.4f} {p[2]:.4f}")
                    lines.append(f"vt {u:.5f} {v:.5f}")
                    vi += 1
                    vt += 1
                    face.append(f"{vi}/{vt}/{vn}")
                lines.append("f " + " ".join(face))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(lines) + "\n")
        return sum(len(t) for t in self.objects.values())


# Where the line leaves the launcher (muzzle centre), and the barrel axis. The renderer has the same numbers.
BARREL_Z = -3.3
MUZZLE_Y = 11.8


def build():
    m = Mesh()
    g = "gauntlet"
    # Bracer round the forearm, and its cuffs at the elbow and wrist.
    m.add(g, bevel_box(-2.42, 4.6, -2.42, 2.42, 9.9, 2.42, 0.35), "black")
    m.add(g, bevel_box(-2.62, 4.3, -2.62, 2.62, 5.25, 2.62, 0.3), "shell")
    m.add(g, bevel_box(-2.66, 8.85, -2.66, 2.66, 10.1, 2.66, 0.32), "shell")
    # Armour plate down the outside of the forearm, with an amber stripe.
    m.add(g, bevel_box(2.3, 5.35, -1.85, 2.85, 8.7, 1.85, 0.2), "shell")
    m.add(g, bevel_box(2.78, 5.75, -1.55, 2.92, 6.25, 1.55, 0.04), "accent")
    # Under-plate on the inside and the back of the forearm.
    m.add(g, bevel_box(-2.75, 5.5, -1.6, -2.3, 8.5, 1.6, 0.18), "dark")
    m.add(g, bevel_box(-1.9, 5.5, 2.3, 1.9, 8.6, 2.75, 0.18), "dark")
    # Launcher housing along the top of the forearm, and its rail.
    m.add(g, bevel_box(-1.75, 4.9, -4.35, 1.75, 9.45, -2.3, 0.45), "shell")
    m.add(g, bevel_box(-0.95, 5.35, -4.8, 0.95, 8.7, -4.2, 0.16), "dark")
    m.add(g, bevel_box(-1.8, 7.2, -4.42, 1.8, 7.55, -2.25, 0.05), "accent")
    # Barrel over the back of the hand, its muzzle collar, and the guard plate under it.
    m.add(g, cylinder("y", (0.0, 0.0, BARREL_Z), 0.72, 8.8, 11.2, 12, 0.08), "metal")
    m.add(g, cylinder("y", (0.0, 0.0, BARREL_Z), 0.95, 11.0, MUZZLE_Y, 12, 0.12), "dark")
    m.add(g, cylinder("y", (0.0, 0.0, BARREL_Z), 0.98, 9.3, 9.75, 12, 0.08), "brass")
    m.add(g, bevel_box(-1.85, 9.9, -2.62, 1.85, 11.05, -2.25, 0.15), "dark")
    # Cable spool on the outer side of the housing: drum, wound line, hub.
    m.add(g, cylinder("x", (0.0, 6.4, BARREL_Z), 1.2, 1.65, 2.55, 14, 0.12), "dark")
    m.add(g, cylinder("x", (0.0, 6.4, BARREL_Z), 1.02, 2.5, 2.7, 14), "cable")
    m.add(g, cylinder("x", (0.0, 6.4, BARREL_Z), 0.5, 2.6, 2.95, 10, 0.06), "metal")
    # Actuator rod along the inner side of the housing.
    m.add(g, cylinder("y", (-1.95, 0.0, -3.0), 0.32, 5.4, 9.1, 8), "metal")
    m.add(g, cylinder("y", (-1.95, 0.0, -3.0), 0.42, 8.2, 9.2, 8, 0.05), "dark")
    # Status light strip on the rail: lit by the charge.
    m.add("glow", bevel_box(-0.45, 5.75, -4.87, 0.45, 8.15, -4.76, 0.03), "light")

    # The anchor hook: eyelet, shaft, head, and three claws swept back.
    h = "hook"
    m.add(h, cylinder("y", (0.0, 0.0, 0.0), 0.3, 0.0, 1.5, 10, 0.05), "metal")
    m.add(h, cylinder("y", (0.0, 0.0, 0.0), 0.55, 1.2, 1.75, 10, 0.08), "dark")
    m.add(h, cone("y", (0.0, 0.0, 0.0), 0.5, 1.7, 2.75, 10), "metal")
    for k in range(3):
        a = 2 * math.pi * k / 3
        d = np.array([math.sin(a), 0.0, math.cos(a)])
        p0 = d * 0.4 + np.array([0, 1.45, 0])
        p1 = d * 1.05 + np.array([0, 1.25, 0])
        p2 = d * 1.35 + np.array([0, 0.6, 0])
        p3 = d * 1.2 + np.array([0, 0.15, 0])
        m.add(h, capsule_segment(p0, p1, 0.32), "metal")
        m.add(h, capsule_segment(p1, p2, 0.28), "metal")
        m.add(h, capsule_segment(p2, p3, 0.22), "metal")
    m.add("hook_glow", cylinder("y", (0.0, 0.0, 0.0), 0.58, 1.38, 1.58, 10), "light")
    return m


# ------------------------------------------------------------------------------------------------
# texture
# ------------------------------------------------------------------------------------------------

def paint():
    rng = np.random.default_rng(7)
    img = np.zeros((ATLAS, ATLAS, 4), dtype=np.uint8)
    for name, (rgb, index) in MATERIALS.items():
        ox, oy = swatch_origin(index)
        base = np.array(rgb, dtype=float)
        yy, xx = np.mgrid[0:SWATCH, 0:SWATCH]
        if name == "light":
            tile = np.full((SWATCH, SWATCH, 3), 255.0)
        else:
            # Fine grain, a soft top-lit gradient, and a few scuffs that catch the light.
            grain = rng.normal(0.0, 1.0, (SWATCH, SWATCH))
            shade = 1.06 - 0.12 * (yy / (SWATCH - 1))
            tile = base[None, None, :] * shade[..., None] * (1.0 + 0.05 * grain[..., None])
            if name in ("shell", "dark", "metal"):
                for _ in range(5):
                    x, y = rng.integers(1, SWATCH - 2, 2)
                    length = rng.integers(2, 5)
                    for t in range(length):
                        if 0 <= x + t < SWATCH:
                            tile[y, x + t] = np.minimum(tile[y, x + t] * 1.35 + 12, 255)
            if name == "cable":
                # Braid: diagonal strands.
                braid = ((xx + yy) % 3 == 0) * 0.35 + ((xx - yy) % 4 == 0) * 0.2
                tile *= (0.8 + braid)[..., None]
            if name == "accent":
                # Hazard chevrons.
                tile *= (0.82 + 0.25 * (((xx + yy) // 3) % 2))[..., None]
        img[oy:oy + SWATCH, ox:ox + SWATCH, :3] = np.clip(tile, 0, 255).astype(np.uint8)
        img[oy:oy + SWATCH, ox:ox + SWATCH, 3] = 255
    PNG.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(PNG)


def icon(mesh, size=16, supersample=8):
    """A flat 16x16 sprite of the gauntlet (the item's break particles and any flat fallback), rendered from
    the mesh: three-quarter view, z-buffered, simple top-left lighting, then box-filtered down."""
    atlas = np.asarray(Image.open(PNG).convert("RGB"), dtype=float)
    n = size * supersample
    # Seen from the top of the forearm, a little from the spool side, laid diagonally like a vanilla tool:
    # mesh +Y (toward the hand) points up and right on screen. Screen axes: x right, y up, z to the viewer.
    to_screen = np.array([[-1.0, 0, 0], [0, 1.0, 0], [0, 0, -1.0]])
    turn = math.radians(-35)
    side = np.array([[math.cos(turn), 0, math.sin(turn)], [0, 1, 0], [-math.sin(turn), 0, math.cos(turn)]])
    roll = math.radians(-45)
    plane = np.array([[math.cos(roll), -math.sin(roll), 0], [math.sin(roll), math.cos(roll), 0], [0, 0, 1]])
    view = plane @ side @ to_screen
    tris = []
    for name in ("gauntlet", "glow", "hook"):
        for corners, normal in mesh.objects.get(name, []):
            pts = np.array([c[0] for c in corners])
            if name == "hook":
                pts = pts + np.array([0.0, 11.8 - 0.9, -3.3])
            uv = np.mean([c[1] for c in corners], axis=0)
            colour = atlas[min(int(uv[1] * ATLAS), ATLAS - 1), min(int(uv[0] * ATLAS), ATLAS - 1)]
            if name == "glow":
                colour = np.array([95.0, 216.0, 255.0])
            tris.append(((view @ pts.T).T, view @ normal, colour, name == "glow"))
    allpts = np.concatenate([t[0] for t in tris])
    lo, hi = allpts.min(axis=0), allpts.max(axis=0)
    scale = (n - 2 * supersample) / max(hi[0] - lo[0], hi[1] - lo[1])
    offset = (np.array([n, n]) - (hi[:2] - lo[:2]) * scale) / 2
    depth = np.full((n, n), -np.inf)
    img = np.zeros((n, n, 4))
    light = np.array([-0.5, 0.7, 0.5]) / np.linalg.norm([-0.5, 0.7, 0.5])
    for pts, normal, colour, glow in tris:
        if normal[2] <= 0:
            continue
        p2 = (pts[:, :2] - lo[:2]) * scale + offset
        p2[:, 1] = n - p2[:, 1]
        x0, y0 = np.floor(p2.min(axis=0)).astype(int)
        x1, y1 = np.ceil(p2.max(axis=0)).astype(int)
        xs, ys = np.meshgrid(np.arange(max(x0, 0), min(x1, n - 1) + 1), np.arange(max(y0, 0), min(y1, n - 1) + 1))
        px, py = xs + 0.5, ys + 0.5
        (ax, ay), (bx, by), (cx, cy) = p2
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
        w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
        w2 = 1 - w0 - w1
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        z = w0 * pts[0, 2] + w1 * pts[1, 2] + w2 * pts[2, 2]
        closer = inside & (z > depth[ys, xs])
        shade = 1.0 if glow else 0.45 + 0.75 * max(float(np.dot(normal / np.linalg.norm(normal), light)), 0.0)
        depth[ys[closer], xs[closer]] = z[closer]
        img[ys[closer], xs[closer], :3] = np.clip(colour * shade, 0, 255)
        img[ys[closer], xs[closer], 3] = 255
    small = img.reshape(size, supersample, size, supersample, 4).mean(axis=(1, 3))
    alpha = small[..., 3:4]
    rgb = np.where(alpha > 0, small[..., :3] * 255 / np.maximum(alpha, 1e-6), 0)
    out = np.concatenate([rgb, np.where(alpha > 96, 255, 0)], axis=2).astype(np.uint8)
    # A dark outline, as vanilla item sprites have.
    a = out[..., 3] > 0
    edge = np.zeros_like(a)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        edge |= np.roll(np.roll(a, dy, 0), dx, 1) & ~a
    out[edge] = (20, 22, 26, 255)
    ICON.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGBA").save(ICON)


def main():
    mesh = build()
    count = mesh.write(OBJ)
    paint()
    icon(mesh)
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects")
    print(f"{PNG.relative_to(ROOT)}: {ATLAS}x{ATLAS}")


if __name__ == "__main__":
    main()
