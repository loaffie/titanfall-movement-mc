#!/usr/bin/env python3
"""Build the A-Wall: the pack on the pilot's back, the emitter disc it throws, their texture atlas, the item
sprite and the wall's field texture.

Titanfall 2's A-Wall is a disc the pilot tosses (`sentry_shield_proj`); where it lands it holds up a
curved wall of amber energy. The A-Wall pilot carries six of them on their back (Respawn's character art): a
padded plate with three rows, each a disc either side of a latch that clips both in, a cyan slit down the latch
beside each disc; the discs' faces are ringed with six chambers round a hub, like a revolver's cylinder, and a
hose runs from the plate's top corner over the shoulder blade and down the side. Here that plate sits between
the shoulder blades, clear of the jumpkit lower down, held on by short straps; the top slot on the pilot's left
is empty, its socket and slit glowing amber, while the next disc is made.

Usage (from the repository root):

    python3 tools/generate-awall-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/awall.obj       (the pack)
    src/main/resources/assets/titanfall_movement/models/entity/awall_disc.obj  (the emitter disc)
    src/main/resources/assets/titanfall_movement/textures/entity/awall.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_awall.png  (sprite, drawn by hand)
    src/main/resources/assets/titanfall_movement/textures/entity/awall_field.png (the wall's cells)

The pack is in the player model's body space, in pixels: +X the pilot's left, +Y down from the base of the
neck (the body runs y = 0..12), +Z the back; the back's surface is z = 2. The renderer draws it on the body as
it is posed.

The disc is in its own space, in pixels, +Y up, standing on y = 0 with its legs folded under it. Each leg
hinges at the rim (LEG_HINGE_R, LEG_HINGE_Y) about the tangent there; the mast telescopes up from MAST_BASE.

Pack objects:
    rig        - plate, frame, spine, latches and slot 0's socket (lit)
    straps     - the straps and the hose (lit)
    disc0      - the next disc, in slot 0 (lit; gone while it is being made)
    light0     - the slit beside it (full-bright)
    discs      - the other five discs (lit)
    lights     - their slits (full-bright)
    socket0    - the contact in slot 0's socket (full-bright, lit while the next disc is made)

The pack is kept light: plain boxes where a bevel would never be seen, disc edges chamfered only at the front, the
faces' chambers painted into a swatch of the texture rather than modelled.

Disc objects:
    body       - the disc's body and top cap (lit)
    leg0..leg2 - the three legs (lit)
    mast       - the telescoping mast (lit)
    glow       - the rim band and the core (full-bright)
    tip        - the mast's tip (full-bright)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np
from PIL import Image

from meshkit import MATERIALS, SWATCH, Mesh, bevel_box, capsule_segment, cylinder, paint, ring, swatch_origin

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/titanfall_movement"
OBJ = ASSETS / "models/entity/awall.obj"
DISC_OBJ = ASSETS / "models/entity/awall_disc.obj"
PNG = ASSETS / "textures/entity/awall.png"
ICON = ASSETS / "textures/item/pilot_awall.png"
FIELD = ASSETS / "textures/entity/awall_field.png"

# The rack, body space, as on the A-Wall pilot's back (Respawn's character art): a padded plate between the
# shoulder blades carrying three rows, each a disc either side of a latch with a cyan light slit beside each
# disc. Slot 0 (top row, on the pilot's left) holds the next disc to be thrown.
PLATE_X = 2.6
PLATE_TOP, PLATE_BOTTOM = 0.3, 6.2
PLATE_CORNER = 0.7
BACK = 1.98
PLATE_FRONT = 2.42
ROWS = (1.3, 3.25, 5.2)
DISC_X = 1.55
DISC_SLOT_R = 0.86
DISC_FRONT = 3.22
SLOTS = [(x, y) for y in ROWS for x in (DISC_X, -DISC_X)]
SLIT_X = 0.56
# The disc's face (a swatch of its own): steel rim, six chambers round a hub, like a revolver's cylinder.
MATERIALS["face"] = ((0x3a, 0x42, 0x4c), 8)

DISC_R = 3.0
LEG_HINGE_R = 2.55
LEG_HINGE_Y = -0.05
MAST_BASE = 1.45


def box(x0, y0, z0, x1, y1, z1):
    """A plain box: eight corners, twelve triangles (for parts too small or too hidden to want a bevel)."""
    return np.array([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)


def slab(outline, z0, z1, inset):
    """A flat plate: ``outline`` (x, y pairs, convex) at the back, drawn in by ``inset`` at the front."""
    pts = np.array(outline, dtype=float)
    middle = pts.mean(axis=0)
    out = [(x, y, z0) for x, y in pts]
    for x, y in pts:
        d = np.array([x, y]) - middle
        d = d / np.abs(d).max() * inset
        out.append((x - d[0], y - d[1], z1))
    return np.array(out)


def puck(x, y, radius, z0, z1, segments, chamfer):
    """A disc standing out of the back along +Z, its edge chamfered only at the front (the back is hidden)."""
    pts = ring("z", (x, y, 0.0), radius, z0, segments)
    pts += ring("z", (x, y, 0.0), radius, z1 - chamfer, segments)
    pts += ring("z", (x, y, 0.0), radius - chamfer, z1, segments)
    return np.array(pts, dtype=float)


def build_pack():
    m = Mesh()
    r = "rig"
    # The plate: an octagon, its corners cut, lipped by a padded frame; a spine up its middle behind the latches.
    c = PLATE_CORNER
    outline = [(-PLATE_X + c, PLATE_TOP), (PLATE_X - c, PLATE_TOP), (PLATE_X, PLATE_TOP + c), (PLATE_X, PLATE_BOTTOM - c),
               (PLATE_X - c, PLATE_BOTTOM), (-PLATE_X + c, PLATE_BOTTOM), (-PLATE_X, PLATE_BOTTOM - c), (-PLATE_X, PLATE_TOP + c)]
    m.add(r, slab(outline, BACK, PLATE_FRONT, 0.12), "dark")
    for i in range(len(outline)):
        (ax, ay), (bx, by) = outline[i], outline[(i + 1) % len(outline)]
        mid = np.array([(ax + bx) / 2, (ay + by) / 2])
        inward = -mid / np.linalg.norm(mid) * 0.2
        m.add(r, capsule_segment((ax + inward[0], ay + inward[1], PLATE_FRONT - 0.02), (bx + inward[0], by + inward[1], PLATE_FRONT - 0.02), 0.32),
              "black")
    m.add(r, box(-0.22, PLATE_TOP + 0.4, PLATE_FRONT - 0.05, 0.22, PLATE_BOTTOM - 0.4, PLATE_FRONT + 0.12), "metal")
    for y in ROWS:
        # The latch between the row's discs: a steel block, and a clip over its top that holds both discs in.
        m.add(r, bevel_box(-0.42, y - 0.62, PLATE_FRONT - 0.05, 0.42, y + 0.62, 2.95, 0.1), "metal")
        m.add(r, bevel_box(-0.3, y - 0.78, 2.85, 0.3, y - 0.2, 3.3, 0.08), "dark")
        m.add(r, box(-0.16, y + 0.1, 2.9, 0.16, y + 0.45, 3.02), "black")
    # Slot 0's socket: a dark recess, the contact at its middle lit while the next disc is made.
    x, y = SLOTS[0]
    m.add(r, cylinder("z", (x, y, 0.0), DISC_SLOT_R - 0.1, PLATE_FRONT - 0.05, PLATE_FRONT + 0.03, 10), "black")
    m.add("socket0", cylinder("z", (x, y, 0.0), 0.34, PLATE_FRONT, PLATE_FRONT + 0.1, 8), "light")
    # A hose out of the plate's top corner on the pilot's left, over the shoulder blade and down their side.
    hose = [(1.9, 0.55, 2.55), (2.7, 0.2, 2.45), (3.3, 0.9, 2.3), (3.45, 3.0, 2.2), (3.35, 5.6, 2.12), (3.0, 7.4, 2.08)]
    for a, b in zip(hose, hose[1:]):
        m.add("straps", capsule_segment(a, b, 0.4), "cable")
    m.add("straps", cylinder("x", (0.0, 0.55, 2.55), 0.32, 1.65, 2.05, 6), "metal")
    # Straps: up over the shoulders, and round the ribs below the plate.
    for side in (-1, 1):
        m.add("straps", capsule_segment((side * 1.75, 0.55, 2.15), (side * 2.55, -0.12, 1.2), 0.34), "cable")
        m.add("straps", capsule_segment((side * 2.55, -0.12, 1.2), (side * 2.55, -0.12, -1.3), 0.34), "cable")
        m.add("straps", capsule_segment((side * 2.55, -0.12, -1.3), (side * 2.3, 1.4, -2.12), 0.34), "cable")
        m.add("straps", capsule_segment((side * 2.2, 5.9, 2.15), (side * 4.05, 6.3, 1.0), 0.34), "cable")

    # The discs, face out: slot 0 holds the next one to be thrown; the other five ride along.
    for k, (x, y) in enumerate(SLOTS):
        body = "disc0" if k == 0 else "discs"
        m.add(body, puck(x, y, DISC_SLOT_R, PLATE_FRONT - 0.05, DISC_FRONT, 10, 0.1), "dark")
        m.add(body, cylinder("z", (x, y, 0.0), DISC_SLOT_R - 0.1, DISC_FRONT - 0.04, DISC_FRONT + 0.03, 10), "face")
        m.add(body, cylinder("z", (x, y, 0.0), 0.2, DISC_FRONT, DISC_FRONT + 0.16, 6), "metal")
        # Its light: a slit down the latch's side, toward the disc.
        light = "light0" if k == 0 else "lights"
        sx = SLIT_X if x > 0 else -SLIT_X
        m.add(light, box(sx - 0.07, y - 0.42, PLATE_FRONT - 0.05, sx + 0.07, y + 0.42, 2.72), "light")
    return m


def paint_face(path):
    """The disc face swatch: a bevelled steel rim, six dark chambers round a raised hub."""
    img = np.asarray(Image.open(path).convert("RGBA")).copy()
    ox, oy = swatch_origin(MATERIALS["face"][1])
    n = SWATCH - 2
    yy, xx = np.mgrid[0:n, 0:n].astype(float) + 0.5
    # The swatch spans the face's square, one texel of margin round it.
    u, v = xx / n * 2.0 - 1.0, yy / n * 2.0 - 1.0
    rad = np.hypot(u, v)
    base = np.array(MATERIALS["face"][0], dtype=float)
    tile = np.tile(base, (n, n, 1)) * (1.08 - 0.16 * (yy / n))[..., None]
    rim = np.clip((rad - 0.78) / 0.08, 0.0, 1.0)
    tile = tile * (1.0 - rim[..., None]) + np.array([0x86, 0x8e, 0x96]) * rim[..., None]
    for k in range(6):
        a = math.radians(30 + 60 * k)
        d = np.hypot(u - math.cos(a) * 0.5, v - math.sin(a) * 0.5)
        hole = np.clip((0.25 - d) / 0.07, 0.0, 1.0)
        tile = tile * (1.0 - hole[..., None]) + np.array([0x10, 0x12, 0x16]) * hole[..., None]
    hub = np.clip((0.2 - rad) / 0.06, 0.0, 1.0)
    tile = tile * (1.0 - hub[..., None]) + np.array([0x79, 0x81, 0x89]) * hub[..., None]
    img[oy:oy + SWATCH, ox:ox + SWATCH, :3] = tuple(base)
    img[oy + 1:oy + 1 + n, ox + 1:ox + 1 + n, :3] = np.clip(tile, 0, 255).astype(np.uint8)
    img[oy:oy + SWATCH, ox:ox + SWATCH, 3] = 255
    Image.fromarray(img, "RGBA").save(path)


def build_disc():
    m = Mesh()
    # Body: a squat puck with a chamfered edge, its cap the face the discs on the pack show (six chambers round a
    # hub), the rim band and core lit.
    m.add("body", cylinder("y", (0.0, 0.0, 0.0), DISC_R, 0.0, 1.05, 20, 0.2), "dark")
    m.add("body", cylinder("y", (0.0, 0.0, 0.0), DISC_R - 0.45, 1.0, 1.45, 20), "face")
    m.add("glow", cylinder("y", (0.0, 0.0, 0.0), DISC_R + 0.04, 0.42, 0.68, 20), "light")
    m.add("glow", cylinder("y", (0.0, 0.0, 0.0), 0.5, 1.4, 1.58, 10), "light")
    # Legs, folded flat under the body, each hinged at the rim and reaching in toward the middle.
    for k in range(3):
        a = math.radians(k * 120)
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        side = np.array([-math.sin(a), 0.0, math.cos(a)])
        hinge = out * LEG_HINGE_R + np.array([0.0, LEG_HINGE_Y, 0.0])
        foot = out * 0.9 + np.array([0.0, LEG_HINGE_Y, 0.0])
        pts = []
        for p, w in ((hinge, 0.32), (foot, 0.24)):
            for s in (-w, w):
                for dy in (-0.12, 0.12):
                    pts.append(p + side * s + np.array([0.0, dy, 0.0]))
        m.add(f"leg{k}", np.array(pts), "metal")
        m.add(f"leg{k}", cylinder("y", tuple(foot), 0.3, LEG_HINGE_Y - 0.16, LEG_HINGE_Y + 0.1, 8), "black")
    # The mast, collapsed into the cap until the wall goes up, with a lit tip.
    m.add("mast", cylinder("y", (0.0, 0.0, 0.0), 0.3, MAST_BASE, MAST_BASE + 3.4, 10, 0.05), "metal")
    m.add("mast", cylinder("y", (0.0, 0.0, 0.0), 0.42, MAST_BASE, MAST_BASE + 0.5, 10, 0.05), "dark")
    m.add("tip", cylinder("y", (0.0, 0.0, 0.0), 0.38, MAST_BASE + 3.35, MAST_BASE + 3.85, 10, 0.12), "light")
    return m


def sprite():
    """The item sprite, drawn by hand: at 16 px a render of the mesh turns to mud. The rack from behind - the plate
    with its cut corners, three rows of discs (steel rim, dark chambers, a bright hub) either side of the latches,
    a cyan slit by each disc - in a dark outline, as vanilla sprites have."""
    plate, rim, chamber, hub, latch, clip, cyan = ((44, 52, 64), (128, 136, 146), (30, 34, 42), (200, 206, 212),
                                                   (150, 158, 166), (70, 78, 90), (150, 232, 255))
    img = np.zeros((16, 16, 4), dtype=np.uint8)

    def put(x, y, rgb):
        img[y, x] = (*rgb, 255)

    for y in range(1, 15):
        for x in range(1, 15):
            # Corners cut on the diagonal.
            if min(x - 1, 14 - x) + min(y - 1, 14 - y) >= 1:
                put(x, y, plate)
    for top in (1, 6, 11):
        for left in (2, 10):
            disc = ((1, 0), (2, 0), (0, 1), (3, 1), (0, 2), (3, 2), (1, 3), (2, 3))
            for dx, dy in disc:
                put(left + dx, top + dy, rim)
            for dx, dy in ((1, 1), (2, 1), (1, 2), (2, 2)):
                put(left + dx, top + dy, chamber)
            put(left + (2 if left == 2 else 1), top + 1, hub)
        put(7, top + 1, latch)
        put(8, top + 1, latch)
        put(7, top + 2, latch)
        put(8, top + 2, latch)
        put(7, top, clip)
        put(8, top, clip)
        put(6, top + 1, cyan)
        put(6, top + 2, cyan)
        put(9, top + 1, cyan)
        put(9, top + 2, cyan)
    # The outline: every clear pixel touching the sprite.
    solid = img[..., 3] > 0
    edge = np.zeros_like(solid)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        edge |= np.roll(np.roll(solid, dy, 0), dx, 1) & ~solid
    img[edge] = (20, 22, 26, 255)
    Image.fromarray(img, "RGBA").save(ICON)


def field_texture():
    """The wall's surface: white, its alpha the pattern, tinted amber and faded in game. Titanfall's wall is mostly
    clear: a faint tint crossed by fine diagonal hatching, with the cells of the field only just showing. Four
    cells across and four rows down the 64 px tile, the hatching every 8 px."""
    size = 64
    yy, xx = np.mgrid[0:size, 0:size].astype(float) + 0.5
    # Pointy-top hex lattice fitted to the tile: centres every 16 px across and down, odd rows offset half a cell
    # (the rows are squeezed together a little so they tile: true hexagons would need rows 0.866 of a cell apart).
    centres = {(col * 16.0 + (8.0 if row % 2 else 0.0), row * 16.0) for row in range(-1, 6) for col in range(-1, 6)}
    best = np.full((size, size), np.inf)
    second = np.full((size, size), np.inf)
    for cx, cy in centres:
        d = np.hypot(xx - cx, (yy - cy) * 0.866)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        best = np.where(closer, d, best)
    cells = np.clip(1.0 - (second - best) / 1.4, 0.0, 1.0) ** 2
    phase = (xx + yy) % 8.0
    hatch = np.clip(1.0 - np.abs(phase - 4.0) / 1.2, 0.0, 1.0)
    rng = np.random.default_rng(31)
    img = np.clip(0.22 + 0.38 * hatch + 0.3 * cells + rng.normal(0.0, 0.02, (size, size)), 0.0, 1.0)
    a = (img * 255).astype(np.uint8)
    white = np.full_like(a, 255)
    Image.fromarray(np.stack([white, white, white, a], -1), "RGBA").save(FIELD)


def main():
    pack = build_pack()
    count = pack.write(OBJ, ["A-Wall pack, generated by tools/generate-awall-model.py - do not edit by hand.",
                             "Units: player-model pixels, body space (+Y down from the neck, +Z the back)."])
    disc = build_disc()
    disc_count = disc.write(DISC_OBJ, ["A-Wall emitter disc, generated by tools/generate-awall-model.py - do not edit by hand.",
                                       "Units: pixels, +Y up, standing on y = 0, legs folded."])
    paint(PNG)
    paint_face(PNG)
    sprite()
    field_texture()
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(pack.objects)} objects")
    print(f"{DISC_OBJ.relative_to(ROOT)}: {disc_count} triangles in {len(disc.objects)} objects")
    print(f"{FIELD.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
