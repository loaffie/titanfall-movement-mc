#!/usr/bin/env python3
"""Build the Pulse Blade: the kunai and its hip sheath (OBJ), texture atlas and item sprite.

Titanfall 2's Pulse Blade is a throwing kunai with a sonar emitter in its grip (Respawn's "Pilot Kunai"
concept: the ARC ExD). From the pommel: a steel ring, a square head with a small read-out window ("SAFE"
on the hip, "ARMED" once thrown), a crenellated collar, a black grip with a warning label, a ribbed steel
sleeve, a blue band, and a long leaf-shaped steel blade with a carbon-weave inlay whose edges glow blue.
On the pilot it rides blade-down in an olive sheath on the left hip.

Usage (from the repository root):

    python3 tools/generate-pulse-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/pulse_blade.obj
    src/main/resources/assets/titanfall_movement/textures/entity/pulse_blade.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_pulse_blade.png   (sprite, from the mesh)

Mesh space is in player-model pixels with the kunai's axis on +Y: the ring at y = 0 and the blade's tip at
y = TIP_Y. The blade lies flat in the XY plane (its faces toward +-Z), as does the ring.

Objects:
    kunai   - ring, head, collar, grip, sleeve and blade (lit)
    band    - the blue band (full-bright, tinted)
    edge    - the blade's glowing rim (full-bright, tinted)
    display - the read-out window (full-bright: green on the hip, orange armed)
    sheath  - the hip sheath round the blade, with its belt clip (lit)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np

from meshkit import Mesh, bevel_box, capsule_segment, cylinder, icon, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/pulse_blade.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/pulse_blade.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_pulse_blade.png"

# The renderer has the same numbers: where the blade starts and its tip, and the grip.
BLADE_Y = 4.2
TIP_Y = 9.55
AXIS = (0.0, 0.0, 0.0)


def leaf(base_y, base_half, wide_y, wide_half, tip_y, thick):
    """The blade's flat leaf: a diamond in the XY plane, ``thick`` through, as one convex solid."""
    pts = []
    for z in (-thick, thick):
        pts += [(-base_half, base_y, z), (base_half, base_y, z), (-wide_half, wide_y, z), (wide_half, wide_y, z)]
    # The faces are ground to the edges: the tip and the widest points are thinner than the spine.
    pts += [(0.0, tip_y, 0.0), (0.0, wide_y, thick * 1.4), (0.0, wide_y, -thick * 1.4)]
    return np.array(pts, dtype=float)


def build():
    m = Mesh()
    k = "kunai"
    # Pommel ring, in the blade's plane: twelve short bars round a circle.
    ring_c, ring_r = 0.68, 0.6
    n = 12
    for i in range(n):
        a0 = 2 * math.pi * i / n
        a1 = 2 * math.pi * (i + 1) / n
        p0 = (math.cos(a0) * ring_r, ring_c + math.sin(a0) * ring_r, 0.0)
        p1 = (math.cos(a1) * ring_r, ring_c + math.sin(a1) * ring_r, 0.0)
        m.add(k, capsule_segment(p0, p1, 0.26), "metal")
    # Head: a squarish steel block, and the neck down to the ring.
    m.add(k, bevel_box(-0.62, 1.15, -0.5, 0.62, 1.9, 0.5, 0.14), "metal")
    m.add(k, cylinder("y", AXIS, 0.3, 1.0, 1.2, 10, 0.04), "metal")
    # Crenellated collar: a dark drum with six teeth standing out of it toward the head.
    m.add(k, cylinder("y", AXIS, 0.5, 1.9, 2.45, 12, 0.06), "dark")
    for i in range(6):
        a = 2 * math.pi * i / 6
        x, z = math.cos(a) * 0.5, math.sin(a) * 0.5
        m.add(k, bevel_box(x - 0.13, 1.88, z - 0.13, x + 0.13, 2.2, z + 0.13, 0.03), "dark")
    # Grip: black, with the amber warning label on both faces.
    m.add(k, cylinder("y", AXIS, 0.46, 2.45, 3.45, 12, 0.05), "black")
    for side in (-1, 1):
        m.add(k, bevel_box(-0.17, 2.75, side * 0.44 - 0.03, 0.17, 3.05, side * 0.44 + 0.03, 0.01), "accent")
    # Ribbed steel sleeve, a thin white-steel stripe, and the blade's root collar.
    m.add(k, cylinder("y", AXIS, 0.48, 3.45, 3.85, 12, 0.04), "metal")
    for y in (3.55, 3.72):
        m.add(k, cylinder("y", AXIS, 0.5, y, y + 0.06, 12), "black")
    m.add(k, cylinder("y", AXIS, 0.49, 3.85, 3.93, 12), "metal")
    m.add(k, cylinder("y", AXIS, 0.38, 4.15, 4.3, 10, 0.03), "dark")
    # The blade: steel leaf with a carbon-weave inlay on each face.
    m.add(k, leaf(BLADE_Y, 0.28, 5.6, 0.86, TIP_Y, 0.11), "metal")
    m.add(k, leaf(BLADE_Y + 0.15, 0.2, 5.6, 0.58, 8.55, 0.135), "cable")

    m.add("band", cylinder("y", AXIS, 0.5, 3.93, 4.15, 12), "light")
    # The glowing rim: the leaf grown a little in its plane but thinner, so only its edges show.
    m.add("edge", leaf(BLADE_Y - 0.02, 0.33, 5.6, 0.95, TIP_Y + 0.12, 0.05), "light")
    for side in (-1, 1):
        m.add("display", bevel_box(-0.24, 1.28, side * 0.5 - 0.02, 0.24, 1.76, side * 0.5 + 0.02, 0.01), "light")

    s = "sheath"
    # A flat olive scabbard round the blade, its mouth at the band, a dark rim and a belt clip on its back (+Z).
    m.add(s, bevel_box(-1.1, 4.0, -0.38, 1.1, 6.0, 0.38, 0.18), "shell")
    m.add(s, leaf(5.95, 1.08, 6.0, 1.1, 10.1, 0.36), "shell")
    m.add(s, bevel_box(-1.18, 3.95, -0.44, 1.18, 4.3, 0.44, 0.08), "dark")
    m.add(s, bevel_box(-0.45, 4.6, 0.3, 0.45, 7.2, 0.62, 0.1), "black")
    m.add(s, bevel_box(-0.7, 6.2, -0.42, 0.7, 6.5, 0.42, 0.06), "accent")
    return m


def main():
    mesh = build()
    count = mesh.write(OBJ, ["Pulse Blade kunai and sheath, generated by tools/generate-pulse-model.py - do not edit by hand.",
                             "Units: player-model pixels; the kunai's axis on +Y (ring at the origin, tip at +Y)."])
    paint(PNG)
    # Sprite: the kunai laid diagonally like a vanilla sword, tip up and to the right, flat face to the viewer.
    view = rotation("z", -45) @ rotation("x", 12)
    icon(mesh, PNG, ICON, [("kunai", (0, 0, 0), None), ("band", (0, 0, 0), (60, 140, 255)),
                           ("edge", (0, 0, 0), (150, 205, 255)), ("display", (0, 0, 0), (90, 230, 160))], view)
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects")
    print(f"{PNG.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
