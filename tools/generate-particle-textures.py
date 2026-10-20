#!/usr/bin/env python3
"""Generate the pilot particle sprites (white, coloured at runtime by the particle):

    glow.png   soft radial falloff        - thruster jets, afterglow wisps
    spark.png  hot core with a short halo - sparks (drawn stretched along their velocity)
    ring.png   thin luminous ring         - shockwaves (drawn flat, facing a normal)
    flare.png  four-point star            - nozzle ignition flash
    holo_pixel.png  soft-edged square     - Holo Pilot hologram flecks

Run from the repository root: python3 tools/generate-particle-textures.py (needs Pillow).
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement", "textures", "particle")
SIZE = 32


def make(name, fn):
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x + 0.5 - SIZE / 2) / (SIZE / 2)
            dy = (y + 0.5 - SIZE / 2) / (SIZE / 2)
            a = max(0.0, min(1.0, fn(dx, dy)))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    img.save(os.path.join(OUT, name + ".png"))


def glow(dx, dy):
    r = math.hypot(dx, dy)
    return (1.0 - r) ** 2.2 if r < 1 else 0.0


def spark(dx, dy):
    r = math.hypot(dx, dy)
    return max(math.exp(-(r / 0.18) ** 2), 0.35 * (1.0 - r) ** 3 if r < 1 else 0.0)


def ring(dx, dy):
    r = math.hypot(dx, dy)
    return math.exp(-((r - 0.78) / 0.09) ** 2) + 0.25 * math.exp(-((r - 0.6) / 0.2) ** 2)


def flare(dx, dy):
    r = math.hypot(dx, dy)
    core = math.exp(-(r / 0.22) ** 2)
    rays = math.exp(-(abs(dx) / 0.05) ** 2) * max(0.0, 1 - abs(dy)) + math.exp(-(abs(dy) / 0.05) ** 2) * max(0.0, 1 - abs(dx))
    return core + 0.8 * rays


def holo_pixel(dx, dy):
    edge = max(abs(dx), abs(dy))
    return 1.0 if edge < 0.55 else max(0.0, 1.0 - (edge - 0.55) / 0.3) ** 2


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (("glow", glow), ("spark", spark), ("ring", ring), ("flare", flare), ("holo_pixel", holo_pixel)):
        make(name, fn)
    print("wrote", OUT)
