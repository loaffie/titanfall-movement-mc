#!/usr/bin/env python3
"""Build the A-Wall: the pack on the pilot's back, the emitter disc it throws, their texture atlas, the item
sprite and the wall's field texture.

Titanfall 2's A-Wall is a disc the pilot tosses (`sentry_shield_proj`); where it lands it holds up a
curved wall of amber energy. Here the disc rides in a compact pack between the shoulder blades - clear of the
jumpkit, which sits lower down - clipped into a round cradle on the pack's back, with a column of four charge
lights down each side of it that fill as the next disc is ready, held on by short straps over the shoulders
and round the ribs.

Usage (from the repository root):

    python3 tools/generate-awall-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/awall.obj       (the pack)
    src/main/resources/assets/titanfall_movement/models/entity/awall_disc.obj  (the emitter disc)
    src/main/resources/assets/titanfall_movement/textures/entity/awall.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_awall.png  (sprite, from the mesh)
    src/main/resources/assets/titanfall_movement/textures/entity/awall_field.png (the wall's cells)

The pack is in the player model's body space, in pixels: +X the pilot's left, +Y down from the base of the
neck (the body runs y = 0..12), +Z the back; the back's surface is z = 2. The renderer draws it on the body as
it is posed.

The disc is in its own space, in pixels, +Y up, standing on y = 0 with its legs folded under it. Each leg
hinges at the rim (LEG_HINGE_R, LEG_HINGE_Y) about the tangent there; the mast telescopes up from MAST_BASE.

Pack objects:
    rig        - plate, housing, cradle, side rails, clamps and straps (lit)
    disc       - the stowed disc in its cradle (lit; hidden while the next one is coming)
    disc_light - the stowed disc's core (full-bright)
    pip0..pip3 - the charge lights, bottom to top, one each side (full-bright)

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

from meshkit import Mesh, bevel_box, capsule_segment, cylinder, icon, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/titanfall_movement"
OBJ = ASSETS / "models/entity/awall.obj"
DISC_OBJ = ASSETS / "models/entity/awall_disc.obj"
PNG = ASSETS / "textures/entity/awall.png"
ICON = ASSETS / "textures/item/pilot_awall.png"
FIELD = ASSETS / "textures/entity/awall_field.png"

# The cradle's centre on the pack, body space.
CRADLE = (0.0, 3.1)
PIP_X = 1.95
PIP_YS = (4.55, 3.75, 2.95, 2.15)

DISC_R = 3.0
LEG_HINGE_R = 2.55
LEG_HINGE_Y = -0.05
MAST_BASE = 1.45


def build_pack():
    m = Mesh()
    r = "rig"
    # The plate against the shoulder blades, and the housing on it.
    m.add(r, bevel_box(-2.4, 0.55, 1.98, 2.4, 5.6, 2.5, 0.2), "dark")
    m.add(r, bevel_box(-2.05, 0.85, 2.4, 2.05, 5.3, 3.75, 0.45), "shell")
    # The cradle: a steel bezel round a dark recess the disc clips into.
    cx, cy = CRADLE
    m.add(r, cylinder("z", (cx, cy, 0.0), 1.72, 3.6, 3.95, 18, 0.06), "metal")
    m.add(r, cylinder("z", (cx, cy, 0.0), 1.45, 3.7, 4.02, 18), "black")
    # Clamps over the disc's edge, top and bottom.
    for sy in (-1, 1):
        m.add(r, bevel_box(cx - 0.45, cy + sy * 1.55 - 0.22, 3.85, cx + 0.45, cy + sy * 1.55 + 0.22, 4.55, 0.06), "metal")
    # Rails down both sides, the charge lights set into them; an amber stripe across the top.
    for side in (-1, 1):
        m.add(r, bevel_box(side * PIP_X - 0.3, 1.6, 2.6, side * PIP_X + 0.3, 5.0, 3.85, 0.08), "dark")
    m.add(r, bevel_box(-1.3, 0.95, 3.6, 1.3, 1.25, 3.85, 0.05), "accent")
    # Straps: up over the shoulders, and short ones round the ribs.
    for side in (-1, 1):
        m.add(r, capsule_segment((side * 1.75, 0.75, 2.15), (side * 2.55, -0.12, 1.2), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.55, -0.12, 1.2), (side * 2.55, -0.12, -1.3), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.55, -0.12, -1.3), (side * 2.3, 1.4, -2.12), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.2, 5.1, 2.15), (side * 4.05, 5.7, 1.0), 0.34), "cable")
        m.add(r, bevel_box(side * 1.95 - 0.28, 0.6, 2.35, side * 1.95 + 0.28, 1.05, 2.7, 0.06), "metal")

    # The stowed disc, face out, and its core.
    m.add("disc", cylinder("z", (cx, cy, 0.0), 1.3, 3.95, 4.35, 18, 0.08), "dark")
    m.add("disc", cylinder("z", (cx, cy, 0.0), 1.05, 4.3, 4.5, 18, 0.05), "metal")
    m.add("disc_light", cylinder("z", (cx, cy, 0.0), 0.42, 4.45, 4.62, 12), "light")
    for i, y in enumerate(PIP_YS):
        for side in (-1, 1):
            m.add(f"pip{i}", bevel_box(side * PIP_X - 0.17, y - 0.24, 3.8, side * PIP_X + 0.17, y + 0.24, 3.93, 0.02), "light")
    return m


def build_disc():
    m = Mesh()
    # Body: a squat puck with a chamfered edge, a steel cap on top, the rim band and core lit.
    m.add("body", cylinder("y", (0.0, 0.0, 0.0), DISC_R, 0.0, 1.05, 20, 0.2), "dark")
    m.add("body", cylinder("y", (0.0, 0.0, 0.0), DISC_R - 0.45, 1.0, 1.45, 20, 0.12), "metal")
    m.add("glow", cylinder("y", (0.0, 0.0, 0.0), DISC_R + 0.04, 0.42, 0.68, 20), "light")
    m.add("glow", cylinder("y", (0.0, 0.0, 0.0), 0.8, 1.4, 1.58, 12), "light")
    # Three vents in the cap, between the legs.
    for k in range(3):
        a = math.radians(60 + k * 120)
        x, z = math.cos(a) * 1.6, math.sin(a) * 1.6
        m.add("body", bevel_box(x - 0.35, 1.38, z - 0.35, x + 0.35, 1.52, z + 0.35, 0.05), "accent")
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


def field_texture():
    """The wall's look, drawn additively (black is clear) and tinted amber in game: hexagonal cells with
    bright edges and faint centres, four cells across and four rows down the 64 px tile (the rows are
    squeezed together a little so they tile: true hexagons would need rows 0.866 of a cell apart)."""
    size = 64
    yy, xx = np.mgrid[0:size, 0:size].astype(float) + 0.5
    # Pointy-top hex lattice fitted to the tile: centres every 16 px across and down, odd rows offset half a cell.
    centres = {(col * 16.0 + (8.0 if row % 2 else 0.0), row * 16.0) for row in range(-1, 6) for col in range(-1, 6)}
    best = np.full((size, size), np.inf)
    second = np.full((size, size), np.inf)
    for cx, cy in centres:
        d = np.hypot(xx - cx, (yy - cy) * 0.866)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        best = np.where(closer, d, best)
    edge = second - best
    lines = np.clip(1.0 - edge / 1.6, 0.0, 1.0) ** 1.5
    fill = 0.14 + 0.12 * np.clip(best / 9.0, 0.0, 1.0)
    rng = np.random.default_rng(31)
    img = np.clip(lines * 0.95 + fill + rng.normal(0.0, 0.025, (size, size)), 0.0, 1.0)
    a = (img * 255).astype(np.uint8)
    Image.fromarray(np.stack([a, a, a, np.full_like(a, 255)], -1), "RGBA").save(FIELD)


def main():
    pack = build_pack()
    count = pack.write(OBJ, ["A-Wall pack, generated by tools/generate-awall-model.py - do not edit by hand.",
                             "Units: player-model pixels, body space (+Y down from the neck, +Z the back)."])
    disc = build_disc()
    disc_count = disc.write(DISC_OBJ, ["A-Wall emitter disc, generated by tools/generate-awall-model.py - do not edit by hand.",
                                       "Units: pixels, +Y up, standing on y = 0, legs folded."])
    paint(PNG)
    # Sprite: the pack from behind, a little from the side and above.
    to_screen = np.array([[1.0, 0, 0], [0, -1.0, 0], [0, 0, 1.0]])
    view = rotation("x", 15) @ rotation("y", -25) @ to_screen
    amber = (255, 168, 70)
    icon(pack, PNG, ICON, [("rig", (0, 0, 0), None), ("disc", (0, 0, 0), None), ("disc_light", (0, 0, 0), amber)]
         + [(f"pip{i}", (0, 0, 0), amber) for i in range(4)], view)
    field_texture()
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(pack.objects)} objects")
    print(f"{DISC_OBJ.relative_to(ROOT)}: {disc_count} triangles in {len(disc.objects)} objects")
    print(f"{FIELD.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
