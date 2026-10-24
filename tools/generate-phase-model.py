#!/usr/bin/env python3
"""Build the Phase Shift gauntlet: its mesh (OBJ), texture atlas and item sprite.

Modelled on Respawn's render of the shifting gauntlet (the campaign's time gauntlet, which the multiplayer
Phase Shift is built from): a fingerless glove with a padded cuff, and on the back of the hand a round
housing - an outer ring of segments between radial ribs, a raised lip round a recessed well, and a hub
split by a bar. A slanted red read-out sits on the housing's thumb-side rim with a small capacitor
canister below it, three clamps stand on the rim, and a grille-faced power pack on the wrist feeds the
housing through two leads. When the pilot phases the ring and the hub light up blue (the game drives that
from the objects below).

Low-poly by design: plain boxes and few-sided cylinders, lights laid on as flat faces rather than slabs, and
every face hidden inside or against another part dropped (``Mesh.cull``).

Usage (from the repository root):

    python3 tools/generate-phase-model.py

Requires numpy, scipy and Pillow (and tools/meshkit.py). Writes:

    src/main/resources/assets/titanfall_movement/models/entity/phase_shift.obj
    src/main/resources/assets/titanfall_movement/textures/entity/phase_shift.png
    src/main/resources/assets/titanfall_movement/textures/item/pilot_phase_shift.png   (sprite, from the mesh)

Mesh space is the Grapple's: the player model's arm space in pixels for a left arm whose centre line is
x = 0, +Y from the shoulder (y = -2) down to the hand (y = 10), -Z the front of the arm - the back of the
hand when the palm faces down, the thumb then toward -X. The renderer moves it onto either arm (mirroring
it for the right).

Objects:
    gauntlet  - glove, cuff, straps, housing, ribs, lip, well, hub bar, clamps, read-out housing, canister,
                power pack and leads (lit)
    coil      - the ring's segments (lit, dark when idle)
    coil_glow - the segments' faces a hair proud of them (full-bright, drawn blue while phasing)
    core      - the hub's face either side of its bar (full-bright: a dim blue at rest, white-blue while phasing)
    led       - the read-out's digits (full-bright red)
"""
from __future__ import annotations

import math
import pathlib

import numpy as np
from PIL import Image

import meshkit
from meshkit import Mesh, box, capsule_segment, cylinder, icon, oriented_box, paint, rotation

ROOT = pathlib.Path(__file__).resolve().parent.parent
OBJ = ROOT / "src/main/resources/assets/titanfall_movement/models/entity/phase_shift.obj"
PNG = ROOT / "src/main/resources/assets/titanfall_movement/textures/entity/phase_shift.png"
ICON = ROOT / "src/main/resources/assets/titanfall_movement/textures/item/pilot_phase_shift.png"

# Two swatches of this gauntlet's own: the power pack's grille and the read-out's digits.
meshkit.MATERIALS["grille"] = ((0x22, 0x27, 0x2e), 8)
meshkit.MATERIALS["readout"] = ((0x00, 0x00, 0x00), 9)

# The housing's centre on the back of the hand and the face of its ring. The renderer has the same numbers
# (the arcs crawl over the ring's face and the sparks leave its rim).
COIL_Y = 7.95
COIL_FACE_Z = -3.05
HAND_Z = -2.15          # the glove's back
HOUSING_R = 1.72
HOUSING_TOP = -2.72
RING_IN, RING_OUT = 1.04, 1.62
RING_TOP = -3.0
RIB_OUT = 1.64           # inside the housing's flats, so the ribs never poke out of its side
SEGMENTS = 8
LIP_R = 0.98
LIP_TOP = -3.12


def polar(theta, r, z):
    """A point on the back of the hand at angle ``theta`` round the housing: 0 toward the little finger (+X),
    90 degrees toward the fingertips (+Y)."""
    return np.array([math.cos(theta) * r, COIL_Y + math.sin(theta) * r, z])


def build():
    m = Mesh()
    g = "gauntlet"
    # Fingerless glove over the hand, its knuckle band, a knuckle pad on the little finger's side, the
    # padded cuff (an octagon in section) and a strap round the forearm above it.
    m.add(g, box(-2.15, 6.35, -2.15, 2.15, 9.3, 2.15), "black")
    m.add(g, box(-2.25, 9.2, -2.25, 2.25, 9.62, 2.25), "dark")
    m.add(g, box(2.15, 8.55, -1.55, 2.42, 9.55, 0.9), "dark")
    cut = 0.42
    cuff = []
    for y in (5.45, 6.55):
        for x, z in ((-2.42 + cut, -2.42), (2.42 - cut, -2.42), (2.42, -2.42 + cut), (2.42, 2.42 - cut),
                     (2.42 - cut, 2.42), (-2.42 + cut, 2.42), (-2.42, 2.42 - cut), (-2.42, -2.42 + cut)):
            cuff.append((x, y, z))
    m.add(g, np.array(cuff), "shell")
    m.add(g, box(-2.25, 3.55, -2.25, 2.25, 4.05, 2.25), "black")

    # The housing: a squat drum on the back of the hand.
    m.add(g, cylinder("z", (0.0, COIL_Y, 0.0), HOUSING_R, HOUSING_TOP, HAND_Z, 12), "metal")
    # Radial ribs between the ring's segments, standing a touch proud of them.
    step = 2 * math.pi / SEGMENTS
    for k in range(SEGMENTS):
        theta = k * step
        rib_top = RING_TOP - 0.06
        mid = polar(theta, (RING_IN + RIB_OUT) / 2, (HOUSING_TOP + rib_top) / 2)
        radial = (math.cos(theta), math.sin(theta), 0.0)
        m.add(g, oriented_box(mid, radial, (0, 0, 1), ((RIB_OUT - RING_IN) / 2, (HOUSING_TOP - rib_top) / 2, 0.12)), "dark")
    # The ring's segments between the ribs; their lit faces a hair above them.
    gap = 0.2
    for k in range(SEGMENTS):
        a0, a1 = k * step + gap / 2, (k + 1) * step - gap / 2
        pts = [polar(a, r, z) for a in (a0, a1) for r in (RING_IN, RING_OUT) for z in (HOUSING_TOP, RING_TOP)]
        m.add("coil", np.array(pts), "dark")
        face = [polar(a0, RING_IN + 0.04, RING_TOP - 0.03), polar(a0, RING_OUT - 0.04, RING_TOP - 0.03),
                polar(a1, RING_OUT - 0.04, RING_TOP - 0.03), polar(a1, RING_IN + 0.04, RING_TOP - 0.03)]
        m.add_face("coil_glow", face, "light", (0, 0, -1))
    # The raised lip round the well, the well's dark floor, and the hub: two lit halves split by a bar.
    m.add(g, cylinder("z", (0.0, COIL_Y, 0.0), LIP_R, LIP_TOP, HOUSING_TOP, 10), "metal")
    well = [polar(2 * math.pi * (k + 0.5) / 10, 0.8, LIP_TOP - 0.02) for k in range(10)]
    m.add_face(g, well, "black", (0, 0, -1))
    hub = [polar(2 * math.pi * (k + 0.5) / 8, 0.52, LIP_TOP - 0.04) for k in range(8)]
    m.add_face("core", hub, "light", (0, 0, -1))
    bar = (math.cos(math.radians(45)), math.sin(math.radians(45)), 0.0)
    m.add(g, oriented_box(polar(0, 0, LIP_TOP - 0.12), bar, (0, 0, 1), (0.62, 0.13, 0.1)), "metal")

    # Three clamps on the rim, as in the render: a pair toward the wrist on the little finger's side, one
    # toward the fingertips.
    for deg, h in ((-40, 0.42), (-78, 0.5), (48, 0.46)):
        theta = math.radians(deg)
        radial = (math.cos(theta), math.sin(theta), 0.0)
        centre = polar(theta, HOUSING_R + 0.05, HAND_Z - h)
        m.add(g, oriented_box(centre, radial, (0, 0, 1), (0.22, 0.3, h)), "dark")

    # The read-out on the thumb-side rim: a housing whose outer top edge is cut away into a slanted face,
    # the digits on it.
    x_in, x_out, y0, y1 = -1.5, -2.32, COIL_Y - 1.0, COIL_Y + 1.0
    top = HOUSING_TOP - 0.1
    slant = 0.45
    m.add(g, np.array([(x, y, z) for y in (y0, y1) for x, z in
                       ((x_in, HAND_Z), (x_out, HAND_Z), (x_out, top + slant), (x_out + slant, top), (x_in, top))]), "dark")
    inset = 0.08
    led = [(x_out + inset * 0.7, y0 + 0.15, top + slant - inset * 0.7), (x_out + inset * 0.7, y1 - 0.15, top + slant - inset * 0.7),
           (x_out + slant - inset * 0.7, y1 - 0.15, top + inset * 0.7), (x_out + slant - inset * 0.7, y0 + 0.15, top + inset * 0.7)]
    led = [np.array(p) + np.array([-0.02, 0.0, -0.02]) for p in led]
    m.add_face("led", led, "readout", (-1, 0, -1))
    # The capacitor canister below it, toward the wrist, lying along the arm, with a copper end cap.
    m.add(g, cylinder("y", (-1.55, 0.0, -2.5), 0.36, 6.0, 6.85, 6), "metal")
    m.add(g, cylinder("y", (-1.55, 0.0, -2.5), 0.26, 5.75, 6.0, 6), "brass")

    # The power pack on the wrist, on the little finger's side of the cuff, its grille facing out, and two
    # leads from its end into the housing.
    m.add(g, box(0.3, 4.0, -3.0, 1.8, 5.95, -2.3), "grille")
    for x0, x1 in ((0.65, 0.35), (1.45, 1.0)):
        a = (x0, 5.95, -2.75)
        b = (x0 * 0.9, 6.35, -2.9)
        c = (x1, COIL_Y - HOUSING_R + 0.15, -2.55)
        m.add(g, capsule_segment(a, b, 0.22), "cable")
        m.add(g, capsule_segment(b, c, 0.22), "cable")

    dropped = m.cull({"gauntlet", "coil"})
    return m, dropped


def paint_extras(path):
    """The grille's perforations and the read-out's dot-matrix digits ("808", so it reads on either arm)."""
    img = np.array(Image.open(path).convert("RGBA"))
    ox, oy = meshkit.swatch_origin(meshkit.MATERIALS["grille"][1])
    base = np.array(meshkit.MATERIALS["grille"][0], dtype=float)
    tile = np.tile(base, (16, 16, 1))
    yy, xx = np.mgrid[0:16, 0:16]
    holes = (xx % 2 == 1) & (yy % 2 == 1) & (xx > 1) & (xx < 14) & (yy > 1) & (yy < 14)
    tile[holes] *= 0.35
    frame = (xx <= 1) | (xx >= 14) | (yy <= 1) | (yy >= 14)
    tile[frame] *= 1.3
    img[oy:oy + 16, ox:ox + 16, :3] = np.clip(tile, 0, 255).astype(np.uint8)

    ox, oy = meshkit.swatch_origin(meshkit.MATERIALS["readout"][1])
    digits = {"8": ["111", "101", "111", "101", "111"], "0": ["111", "101", "101", "101", "111"]}
    tile = np.zeros((16, 16, 3))
    tile[:, :] = 18  # the unlit cells glow faintly
    x = 2
    for ch in "808":
        for r, row in enumerate(digits[ch]):
            for c, on in enumerate(row):
                if on == "1":
                    tile[4 + r * 2:6 + r * 2 - 1, x + c] = 255
        x += 4
    img[oy:oy + 16, ox:ox + 16, :3] = tile.astype(np.uint8)
    img[oy:oy + 16, ox:ox + 16, 3] = 255
    Image.fromarray(img, "RGBA").save(path)


def main():
    mesh, dropped = build()
    count = mesh.write(OBJ, ["Phase Shift gauntlet, generated by tools/generate-phase-model.py - do not edit by hand.",
                             "Units: player-model pixels, left-arm space (+Y toward the hand, -Z the back of the hand)."])
    paint(PNG)
    paint_extras(PNG)
    # Sprite: the back of the hand facing the viewer, fingers up and to the right, tipped back a little.
    # The ring is drawn lit up, so the sprite reads as the Phase Shift at a glance.
    to_screen = np.array([[-1.0, 0, 0], [0, 1.0, 0], [0, 0, -1.0]])
    view = rotation("z", -40) @ rotation("x", -15) @ to_screen
    icon(mesh, PNG, ICON, [("gauntlet", (0, 0, 0), None), ("coil", (0, 0, 0), None), ("coil_glow", (0, 0, 0), (70, 170, 255)),
                           ("core", (0, 0, 0), (200, 240, 255)), ("led", (0, 0, 0), (255, 60, 40))], view)
    print(f"{OBJ.relative_to(ROOT)}: {count} triangles in {len(mesh.objects)} objects ({dropped} hidden ones dropped)")
    print(f"{PNG.relative_to(ROOT)}: 64x64")


if __name__ == "__main__":
    main()
