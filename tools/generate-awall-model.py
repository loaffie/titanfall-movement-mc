#!/usr/bin/env python3
"""Build the A-Wall: the pack on the pilot's back, the emitter disc it throws, their texture atlas, the item
sprite and the wall's field texture.

Titanfall 2's A-Wall is a disc the pilot tosses (`sentry_shield_proj`); where it lands it holds up a
curved wall of amber energy. The A-Wall pilot carries a rack of six of them on their back, two columns of
three, each lit by a cyan light (Respawn's character art). Here that rack is a compact plate between the
shoulder blades - clear of the jumpkit, which sits lower down - held on by short straps over the shoulders and
round the ribs; the top slot on the pilot's left is empty, its ring glowing, while the next disc is made.

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
    rig        - plate, frame, spine, the six sockets and the straps (lit)
    disc0      - the next disc, in slot 0 (lit; gone while it is being made)
    light0     - its light (full-bright)
    discs      - the other five discs (lit)
    lights     - their lights (full-bright)
    socket0    - the ring inside slot 0's socket (full-bright, lit while the next disc is made)

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

# The rack's six disc slots, body space: two columns of three, as on the A-Wall pilot's back. Slot 0 (top,
# on the pilot's left) holds the next disc to be thrown.
DISC_SLOT_R = 0.8
SLOTS = [(x, y) for y in (1.6, 3.2, 4.8) for x in (1.2, -1.2)]

DISC_R = 3.0
LEG_HINGE_R = 2.55
LEG_HINGE_Y = -0.05
MAST_BASE = 1.45


def build_pack():
    m = Mesh()
    r = "rig"
    # The rack: a dark plate against the shoulder blades in a steel-edged frame, a spine down its middle.
    m.add(r, bevel_box(-2.45, 0.5, 1.98, 2.45, 5.75, 2.55, 0.2), "dark")
    m.add(r, bevel_box(-2.3, 0.65, 2.45, 2.3, 5.6, 2.95, 0.25), "shell")
    m.add(r, bevel_box(-0.28, 0.75, 2.85, 0.28, 5.5, 3.3, 0.08), "metal")
    m.add(r, bevel_box(-1.6, 0.7, 2.85, 1.6, 0.95, 3.15, 0.05), "accent")
    # A socket for each disc: a steel collar the disc clips into.
    for x, y in SLOTS:
        m.add(r, cylinder("z", (x, y, 0.0), DISC_SLOT_R + 0.14, 2.85, 3.12, 12, 0.05), "metal")
        m.add(r, cylinder("z", (x, y, 0.0), DISC_SLOT_R - 0.05, 2.95, 3.16, 12), "black")
    # Straps: up over the shoulders, and short ones round the ribs.
    for side in (-1, 1):
        m.add(r, capsule_segment((side * 1.75, 0.75, 2.15), (side * 2.55, -0.12, 1.2), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.55, -0.12, 1.2), (side * 2.55, -0.12, -1.3), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.55, -0.12, -1.3), (side * 2.3, 1.4, -2.12), 0.34), "cable")
        m.add(r, capsule_segment((side * 2.25, 5.2, 2.15), (side * 4.05, 5.75, 1.0), 0.34), "cable")

    # The discs, face out: slot 0 holds the next one to be thrown; the other five ride along.
    for k, (x, y) in enumerate(SLOTS):
        body = "disc0" if k == 0 else "discs"
        light = "light0" if k == 0 else "lights"
        m.add(body, cylinder("z", (x, y, 0.0), DISC_SLOT_R, 3.05, 3.5, 12, 0.08), "dark")
        m.add(body, cylinder("z", (x, y, 0.0), DISC_SLOT_R - 0.2, 3.45, 3.62, 12, 0.04), "metal")
        m.add(light, cylinder("z", (x, y, 0.0), 0.26, 3.58, 3.7, 8), "light")
    # The empty socket's ring, lit while the next disc is made.
    x, y = SLOTS[0]
    m.add("socket0", cylinder("z", (x, y, 0.0), DISC_SLOT_R - 0.12, 3.14, 3.2, 12), "light")
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
    # Sprite: the pack from behind, a little from the side and above.
    to_screen = np.array([[1.0, 0, 0], [0, -1.0, 0], [0, 0, 1.0]])
    view = rotation("x", 15) @ rotation("y", -25) @ to_screen
    cyan = (150, 232, 255)
    icon(pack, PNG, ICON, [("rig", (0, 0, 0), None), ("disc0", (0, 0, 0), None), ("discs", (0, 0, 0), None),
                           ("light0", (0, 0, 0), cyan), ("lights", (0, 0, 0), cyan)], view)
    field_texture()
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(pack.objects)} objects")
    print(f"{DISC_OBJ.relative_to(ROOT)}: {disc_count} triangles in {len(disc.objects)} objects")
    print(f"{FIELD.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
