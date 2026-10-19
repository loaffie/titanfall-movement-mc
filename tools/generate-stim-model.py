#!/usr/bin/env python3
"""Build the Stim: the autoinjector and its hip holster (OBJ), texture atlas and item sprite.

Titanfall 2's Stim is a one-shot autoinjector the pilot jabs to flood their system: a speed boost and fast
healing. Here it is a short gunmetal injector with grip rings, a caged glass vial of glowing stim fluid,
an amber plunger cap at one end and a steel nose and needle at the other, carried in an olive holster
with two clamp bands that rides on the front of the right hip.

Usage (from the repository root):

    python3 tools/generate-stim-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/stim.obj
    src/main/resources/assets/titanfall_movement/textures/entity/stim.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_stim.png   (sprite, from the mesh)
    src/main/resources/assets/titanfall_movement/textures/gui/stim_vignette.png (screen-edge glow)

Mesh space is in player-model pixels with the injector's axis on +Y: the plunger cap at the top (y < 0),
the needle tip at y = NEEDLE_Y. The holster's back plate is on +Z (the side against the body).

Objects:
    injector - body, grip rings, vial cage, cap, nose and needle (lit)
    glass    - the vial's glass, a faint glow (full-bright, dim)
    fluid    - the stim fluid inside the vial (full-bright); the renderer scales it along Y toward the
               needle end as the dose is pushed out
    holster  - the hip holster: back plate, clamp bands and belt tab (lit)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np
from PIL import Image

from meshkit import Mesh, bevel_box, capsule_segment, cone, cylinder, icon, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/stim.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/stim.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_stim.png"
VIGNETTE = ROOT / "src/main/resources/assets/titanfall_movement/textures/gui/stim_vignette.png"

# The renderer has the same numbers: the vial's ends (the fluid drains toward VIAL_END) and the needle tip.
VIAL_START = 1.9
VIAL_END = 3.05
NEEDLE_Y = 5.0
AXIS = (0.0, 0.0, 0.0)


def build():
    m = Mesh()
    i = "injector"
    # Plunger cap and its button.
    m.add(i, cylinder("y", AXIS, 0.5, -0.55, 0.05, 12, 0.08), "accent")
    m.add(i, cylinder("y", AXIS, 0.28, -0.8, -0.5, 10, 0.05), "brass")
    # Body, with two rubber grip rings.
    m.add(i, cylinder("y", AXIS, 0.62, 0.0, VIAL_START, 14, 0.08), "dark")
    m.add(i, cylinder("y", AXIS, 0.69, 0.45, 0.75, 14, 0.04), "black")
    m.add(i, cylinder("y", AXIS, 0.69, 1.05, 1.35, 14, 0.04), "black")
    # The vial cage: end rings and four struts round the glass.
    m.add(i, cylinder("y", AXIS, 0.66, VIAL_START - 0.05, VIAL_START + 0.12, 14, 0.03), "metal")
    m.add(i, cylinder("y", AXIS, 0.66, VIAL_END - 0.12, VIAL_END + 0.05, 14, 0.03), "metal")
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        x, z = math.cos(a) * 0.6, math.sin(a) * 0.6
        m.add(i, bevel_box(x - 0.09, VIAL_START, z - 0.09, x + 0.09, VIAL_END, z + 0.09, 0.03), "metal")
    # Nose, collar and needle.
    m.add(i, cylinder("y", AXIS, 0.58, VIAL_END, 3.45, 14, 0.06), "dark")
    m.add(i, cylinder("y", AXIS, 0.62, 3.3, 3.45, 14, 0.03), "accent")
    m.add(i, cone("y", AXIS, 0.5, 3.45, 4.25, 12), "metal")
    m.add(i, capsule_segment((0.0, 4.15, 0.0), (0.0, NEEDLE_Y, 0.0), 0.12), "metal")
    # Glass and the fluid inside it.
    m.add("glass", cylinder("y", AXIS, 0.58, VIAL_START + 0.1, VIAL_END - 0.1, 14), "light")
    m.add("fluid", cylinder("y", AXIS, 0.5, VIAL_START + 0.12, VIAL_END - 0.12, 12), "light")

    h = "holster"
    # Back plate against the body, a belt tab above it, and two clamp bands round the injector.
    m.add(h, bevel_box(-0.85, -0.3, 0.7, 0.85, 3.6, 1.1, 0.15), "shell")
    m.add(h, bevel_box(-0.6, -1.3, 0.85, 0.6, -0.2, 1.2, 0.1), "black")
    m.add(h, bevel_box(-0.82, 0.5, -0.82, 0.82, 0.85, 0.95, 0.12), "dark")
    m.add(h, bevel_box(-0.8, 2.75, -0.8, 0.8, 3.1, 0.95, 0.12), "dark")
    m.add(h, bevel_box(-0.86, 1.3, 0.95, 0.86, 1.55, 1.15, 0.04), "accent")
    return m


def vignette(size=256):
    """White glow round the screen edges, clear in the middle (tinted and pulsed in game). The falloff is on
    a rounded-rectangle distance, so it hugs the edges of any aspect ratio once stretched to the screen."""
    u = (np.arange(size) + 0.5) / size * 2 - 1
    x, y = np.meshgrid(u, u)
    # Superellipse distance: ~1 at the edges, 0 in the middle, rounder toward the corners.
    d = (np.abs(x) ** 6 + np.abs(y) ** 6) ** (1 / 6)
    alpha = np.clip((d - 0.62) / 0.38, 0, 1) ** 1.8
    img = np.zeros((size, size, 4), dtype=np.uint8)
    img[..., :3] = 255
    img[..., 3] = (alpha * 255).astype(np.uint8)
    VIGNETTE.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(VIGNETTE)


def main():
    mesh = build()
    count = mesh.write(OBJ, ["Stim autoinjector and holster, generated by tools/generate-stim-model.py - do not edit by hand.",
                             "Units: player-model pixels; the injector's axis on +Y (cap at the top, needle at +Y)."])
    paint(PNG)
    # Sprite: the injector alone, laid diagonally like a vanilla tool with the needle down and left, turned a
    # little so the vial window faces the viewer.
    view = rotation("z", 135) @ rotation("y", 30)
    icon(mesh, PNG, ICON, [("injector", (0, 0, 0), None), ("glass", (0, 0, 0), (60, 150, 190)),
                           ("fluid", (0, 0, 0), (110, 225, 255))], view)
    vignette()
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects")
    print(f"{PNG.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
