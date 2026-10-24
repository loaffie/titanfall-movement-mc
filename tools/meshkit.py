"""Shared building blocks for the mod's generated meshes (the Grapple, the Stim).

Parts are convex solids - bevelled boxes, cylinders, cones, bars - each the convex hull of a few rings of
points, so every edge is chamfered and round parts are round. Each part is textured from one swatch of a
small material atlas painted here in the jumpkit's Grapple-pilot palette, so all the gear matches.

Not a script: imported by ``generate-grapple-model.py`` and ``generate-stim-model.py``.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image
from scipy.spatial import ConvexHull

ATLAS = 64
SWATCH = 16
# Palette taken from the jumpkit's Grapple-pilot texture, so the gear matches the kit.
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


def box(x0, y0, z0, x1, y1, z1):
    """A plain box: eight corners, twelve triangles."""
    return np.array([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)


def oriented_box(centre, u, v, half):
    """A box at ``centre`` turned so its first two axes lie along ``u`` and ``v`` (the third completes them),
    ``half`` its half-extents along each."""
    u = np.asarray(u, dtype=float) / np.linalg.norm(u)
    v = np.asarray(v, dtype=float)
    v = v - u * np.dot(u, v)
    v /= np.linalg.norm(v)
    w = np.cross(u, v)
    c = np.asarray(centre, dtype=float)
    return np.array([c + su * half[0] * u + sv * half[1] * v + sw * half[2] * w
                     for su in (-1, 1) for sv in (-1, 1) for sw in (-1, 1)], dtype=float)


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
        # Each solid part's hull planes (outward normal n and offset d: inside where n.x + d <= 0), for cull().
        self.parts = []

    def _triangle(self, obj, a, b, c, n, lo, span, material, part):
        """One triangle with planar UVs on the face's dominant axis, the part's extent spread across the swatch
        (with a one-texel margin so the filter never reads the neighbouring swatch)."""
        ox, oy = swatch_origin(MATERIALS[material][1])
        axis = int(np.argmax(np.abs(n)))
        u_axis, v_axis = [(1, 2), (0, 2), (0, 1)][axis]
        corners = []
        for p in (a, b, c):
            u = ox + 1 + (p[u_axis] - lo[u_axis]) / span[u_axis] * (SWATCH - 2)
            v = oy + 1 + (p[v_axis] - lo[v_axis]) / span[v_axis] * (SWATCH - 2)
            corners.append((p, (u / ATLAS, v / ATLAS)))
        self.objects.setdefault(obj, []).append((corners, n, part))

    def add(self, obj, points, material):
        """Adds the convex hull of ``points`` to ``obj``, with flat normals and atlas UVs from ``material``."""
        hull = ConvexHull(points)
        centre = points.mean(axis=0)
        lo, hi = points.min(axis=0), points.max(axis=0)
        span = np.maximum(hi - lo, 1e-6)
        part = len(self.parts)
        self.parts.append((obj, hull.equations))
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
            self._triangle(obj, a, b, c, n, lo, span, material, part)

    def add_face(self, obj, points, material, facing):
        """Adds a flat convex polygon (its corners in order round it), facing the ``facing`` direction: a decal,
        a light or a read-out laid on a solid, two triangles for a quad instead of a slab's twelve."""
        pts = np.asarray(points, dtype=float)
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        n /= np.linalg.norm(n)
        if np.dot(n, facing) < 0:
            pts = pts[::-1]
            n = -n
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        span = np.maximum(hi - lo, 1e-6)
        for k in range(1, len(pts) - 1):
            self._triangle(obj, pts[0], pts[k], pts[k + 1], n, lo, span, material, None)

    def cull(self, occluders, eps=1e-3):
        """Drops the triangles no one can see: those lying wholly inside, or flat against, another solid part of
        an ``occluders`` object (objects always drawn). A face flush with an occluder's face that points the
        same way is kept, so two flush parts never cancel each other's outside. Returns how many went."""
        dropped = 0
        for obj, tris in self.objects.items():
            kept = []
            for corners, n, part in tris:
                pts = np.array([c[0] for c in corners] + [np.mean([c[0] for c in corners], axis=0)])
                hidden = False
                for other, (other_obj, planes) in enumerate(self.parts):
                    if other == part or other_obj not in occluders:
                        continue
                    dist = pts @ planes[:, :3].T + planes[:, 3]
                    if np.all(dist <= eps):
                        on = np.all(np.abs(dist) <= eps, axis=0)
                        if not np.any(planes[on, :3] @ n > 0.99):
                            hidden = True
                            break
                if hidden:
                    dropped += 1
                else:
                    kept.append((corners, n, part))
            self.objects[obj] = kept
        return dropped

    def write(self, path, header):
        """Writes the mesh as an OBJ, one object per part group, with ``header`` as its leading comments."""
        lines = ["# " + line for line in header]
        vi = vt = vn = 0
        for name, tris in self.objects.items():
            lines.append(f"o {name}")
            for corners, n, _ in tris:
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


# ------------------------------------------------------------------------------------------------
# texture and sprite
# ------------------------------------------------------------------------------------------------

def paint(path):
    """Paints the material atlas: one 16 x 16 swatch per material, with grain, scuffs and patterns."""
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
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(path)


def icon(mesh, atlas_path, path, parts, view, size=16, supersample=8):
    """A flat item sprite rendered from the mesh: z-buffered, simple top-left lighting, box-filtered down
    and outlined like vanilla sprites. ``parts`` is a list of ``(object, offset, glow_rgb_or_None)``;
    ``view`` turns mesh space into screen space (x right, y up, z toward the viewer)."""
    atlas = np.asarray(Image.open(atlas_path).convert("RGB"), dtype=float)
    n = size * supersample
    tris = []
    for name, offset, glow in parts:
        for corners, normal, _ in mesh.objects.get(name, []):
            pts = np.array([c[0] for c in corners]) + np.array(offset)
            uv = np.mean([c[1] for c in corners], axis=0)
            colour = atlas[min(int(uv[1] * ATLAS), ATLAS - 1), min(int(uv[0] * ATLAS), ATLAS - 1)]
            if glow is not None:
                colour = np.array(glow, dtype=float)
            tris.append(((view @ pts.T).T, view @ normal, colour, glow is not None))
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
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGBA").save(path)


def rotation(axis, degrees):
    """3x3 rotation about a coordinate axis ("x", "y" or "z")."""
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
            "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]
