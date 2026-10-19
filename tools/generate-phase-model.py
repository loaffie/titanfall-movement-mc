#!/usr/bin/env python3
"""Build the Phase Shift gauntlet: its mesh (OBJ), texture atlas and item sprite.

Titanfall 2's Phase Shift pilot wears the shifting device on the back of the off hand: a round coil housing
over the back of the hand with a segmented ring round a hub, a small red read-out on a pack strapped over the
wrist, cables running from the pack into the coil, and straps over a fingerless glove. When the pilot
phases the coil lights up blue and throws off sparks (the game drives that from the objects below).

Usage (from the repository root):

    python3 tools/generate-phase-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/phase_shift.obj
    src/main/resources/assets/titanfall_movement/textures/entity/phase_shift.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_phase_shift.png   (sprite, from the mesh)

Mesh space is the Grapple's: the player model's arm space in pixels for a left arm whose centre line is
x = 0, +Y from the shoulder (y = -2) down to the hand (y = 10), -Z the front of the arm - the back of the
hand when the palm faces down. The renderer moves it onto either arm (mirroring it for the right).

Objects:
    gauntlet  - glove, straps, coil housing, hub, wrist pack and cables (lit)
    coil      - the coil's ring segments (lit, dark when idle)
    coil_glow - the same segments a hair proud of them (full-bright, drawn blue while phasing)
    core      - the hub's centre (full-bright: a dim blue at rest, white-blue while phasing)
    led       - the pack's read-out (full-bright red)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np

from meshkit import Mesh, bevel_box, capsule_segment, cylinder, icon, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/phase_shift.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/phase_shift.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_phase_shift.png"

# The coil's centre on the back of the hand, and its face. The renderer has the same numbers (the sparks
# leave from the coil's rim).
COIL_Y = 8.0
COIL_FACE_Z = -3.25
SEGMENTS = 12


def segment(k, r_in, r_out, z0, z1, gap=0.32):
    """One ring segment: a curved block between two radii, ``gap`` radians short of its neighbours."""
    step = 2 * math.pi / SEGMENTS
    a0 = k * step + gap / 2
    a1 = (k + 1) * step - gap / 2
    pts = []
    for a in (a0, (a0 + a1) / 2, a1):
        for r in (r_in, r_out):
            for z in (z0, z1):
                pts.append((math.cos(a) * r, COIL_Y + math.sin(a) * r, z))
    return np.array(pts, dtype=float)


def build():
    m = Mesh()
    g = "gauntlet"
    # Fingerless glove over the hand and the wrist cuff, straps over the knuckles and the forearm.
    m.add(g, bevel_box(-2.18, 6.3, -2.18, 2.18, 9.2, 2.18, 0.2), "black")
    m.add(g, bevel_box(-2.32, 5.75, -2.32, 2.32, 6.7, 2.32, 0.25), "shell")
    m.add(g, bevel_box(-2.26, 9.15, -2.26, 2.26, 9.6, 2.26, 0.1), "dark")
    m.add(g, bevel_box(-2.24, 3.4, -2.24, 2.24, 3.9, 2.24, 0.1), "black")
    m.add(g, bevel_box(-2.24, 5.0, -2.24, 2.24, 5.45, 2.24, 0.1), "black")
    # Back plate over the hand, and the coil housing on it.
    m.add(g, bevel_box(-1.95, 6.5, -2.6, 1.95, 9.5, -2.1, 0.2), "dark")
    m.add(g, cylinder("z", (0.0, COIL_Y, 0.0), 1.78, -3.0, -2.45, 20, 0.14), "metal")
    # Inside the ring: a dark well, and the hub standing out of it.
    m.add(g, cylinder("z", (0.0, COIL_Y, 0.0), 1.02, -3.1, -2.95, 16, 0.03), "black")
    m.add(g, cylinder("z", (0.0, COIL_Y, 0.0), 0.62, -3.4, -2.95, 12, 0.08), "metal")
    # Wrist pack: the box, its lid, side ribs, and the frame of the read-out.
    m.add(g, bevel_box(-1.55, 2.9, -3.2, 1.55, 5.7, -2.05, 0.3), "shell")
    m.add(g, bevel_box(-1.2, 3.2, -3.36, 1.2, 5.45, -3.05, 0.12), "dark")
    for side in (-1, 1):
        m.add(g, bevel_box(side * 1.5 - 0.12, 3.2, -3.0, side * 1.5 + 0.12, 5.4, -2.2, 0.05), "metal")
    m.add(g, bevel_box(-1.0, 3.95, -3.46, 1.0, 5.05, -3.25, 0.06), "black")
    # Cables from the top of the pack into the coil housing, and one looping out round the outside.
    for side in (-1, 1):
        a = (side * 0.75, 5.6, -2.95)
        b = (side * 0.95, 6.2, -3.15)
        c = (side * 1.05, 6.75, -3.0)
        m.add(g, capsule_segment(a, b, 0.36), "cable")
        m.add(g, capsule_segment(b, c, 0.36), "cable")
    loop = [(1.6, 4.4, -2.55), (2.35, 5.2, -2.45), (2.45, 6.2, -2.5), (1.75, 7.1, -2.75)]
    for p0, p1 in zip(loop, loop[1:]):
        m.add(g, capsule_segment(p0, p1, 0.4), "cable")
    # Brass fittings where the cables go in.
    for side in (-1, 1):
        m.add(g, cylinder("z", (side * 1.05, 6.75, 0.0), 0.28, -3.2, -2.9, 8, 0.04), "brass")

    # The coil: twelve segments round the hub, and their glowing copy a hair proud of them.
    for k in range(SEGMENTS):
        m.add("coil", segment(k, 1.08, 1.62, COIL_FACE_Z, -2.95), "dark")
        m.add("coil_glow", segment(k, 1.1, 1.6, COIL_FACE_Z - 0.03, -3.0), "light")
    m.add("core", cylinder("z", (0.0, COIL_Y, 0.0), 0.4, -3.47, -3.38, 12), "light")
    m.add("led", bevel_box(-0.82, 4.1, -3.52, 0.82, 4.9, -3.4, 0.04), "light")
    return m


def main():
    mesh = build()
    count = mesh.write(OBJ, ["Phase Shift gauntlet, generated by tools/generate-phase-model.py - do not edit by hand.",
                             "Units: player-model pixels, left-arm space (+Y toward the hand, -Z the back of the hand)."])
    paint(PNG)
    # Sprite: the back of the hand facing the viewer, fingers up and to the right, tipped back a little.
    # The coil is drawn lit up, so the sprite reads as the Phase Shift at a glance.
    to_screen = np.array([[-1.0, 0, 0], [0, 1.0, 0], [0, 0, -1.0]])
    view = rotation("z", -40) @ rotation("x", -15) @ to_screen
    icon(mesh, PNG, ICON, [("gauntlet", (0, 0, 0), None), ("coil_glow", (0, 0, 0), (70, 170, 255)),
                           ("core", (0, 0, 0), (200, 240, 255)), ("led", (0, 0, 0), (255, 60, 40))], view)
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects")
    print(f"{PNG.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
