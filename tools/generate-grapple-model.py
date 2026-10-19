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

from meshkit import Mesh, bevel_box, capsule_segment, cone, cylinder, icon, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/grapple.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/grapple.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_grapple.png"


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


def main():
    mesh = build()
    count = mesh.write(OBJ, ["Grapple gauntlet, generated by tools/generate-grapple-model.py - do not edit by hand.",
                             "Units: player-model pixels, left-arm space (+Y toward the hand, -Z the top of the forearm)."])
    paint(PNG)
    # Sprite: seen from the top of the forearm, a little from the spool side, laid diagonally like a vanilla
    # tool, mesh +Y (toward the hand) pointing up and right.
    to_screen = np.array([[-1.0, 0, 0], [0, 1.0, 0], [0, 0, -1.0]])
    view = rotation("z", -45) @ rotation("y", -35) @ to_screen
    icon(mesh, PNG, ICON, [("gauntlet", (0, 0, 0), None), ("glow", (0, 0, 0), (95, 216, 255)),
                           ("hook", (0.0, MUZZLE_Y - 0.9, BARREL_Z), None)], view)
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects")
    print(f"{PNG.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
