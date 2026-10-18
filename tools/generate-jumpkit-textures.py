#!/usr/bin/env python3
"""Generate the Pilot Jumpkit textures (entity model texture + inventory icon).

The UV layout mirrors JumpkitModel.getTexturedModelData(): each cuboid uses Minecraft's box UV
layout, so this script paints each face region of each box. Run from the repository root:

    python3 tools/generate-jumpkit-textures.py

Requires Pillow (pip install pillow). All art is original, drawn procedurally here.
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement", "textures")

GUN_DARK = (34, 38, 43, 255)
GUN = (58, 63, 70, 255)
GUN_LIGHT = (86, 93, 101, 255)
STRAP = (46, 41, 36, 255)
STRAP_LIGHT = (66, 59, 51, 255)
ORANGE = (232, 134, 42, 255)
CYAN = (70, 212, 255, 255)
NOZZLE = (24, 24, 27, 255)
NOZZLE_RIM = (120, 112, 104, 255)
GLOW = (255, 196, 110, 255)
GLOW_CORE = (255, 240, 200, 255)

random.seed(7)


def jitter(color, amount=6):
    r, g, b, a = color
    d = random.randint(-amount, amount)
    return (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


def fill(img, x, y, w, h, color, noise=5):
    for i in range(x, x + w):
        for j in range(y, y + h):
            img.putpixel((i, j), jitter(color, noise) if noise else color)


def box_faces(u, v, w, h, d):
    """Face rectangles (x, y, w, h) of a Minecraft box-UV cuboid."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(img, u, v, w, h, d, base, edge=None, noise=5):
    for face in box_faces(u, v, w, h, d).values():
        fx, fy, fw, fh = face
        fill(img, fx, fy, fw, fh, base, noise)
        if edge and fw > 1 and fh > 1:
            for i in range(fx, fx + fw):
                img.putpixel((i, fy + fh - 1), edge)
    return box_faces(u, v, w, h, d)


def entity_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    # belt: 8 x 2 x 4 at (0, 0)
    faces = paint_box(img, 0, 0, 8, 2, 4, STRAP, STRAP_LIGHT)
    for name in ("front", "back", "east", "west"):
        fx, fy, fw, fh = faces[name]
        for i in range(fx, fx + fw):
            img.putpixel((i, fy), STRAP_LIGHT)
    fx, fy, fw, fh = faces["front"]
    img.putpixel((fx, fy + 1), ORANGE)
    img.putpixel((fx + fw - 1, fy + 1), ORANGE)

    # buckle: 3 x 2 x 1 at (0, 6)
    faces = paint_box(img, 0, 6, 3, 2, 1, GUN_LIGHT, GUN_DARK, 3)
    fx, fy, fw, fh = faces["front"]
    img.putpixel((fx + 1, fy), CYAN)
    img.putpixel((fx + 1, fy + 1), CYAN)

    # core: 6 x 5 x 2 at (24, 0)
    faces = paint_box(img, 24, 0, 6, 5, 2, GUN, GUN_DARK)
    fx, fy, fw, fh = faces["back"]
    fill(img, fx + 1, fy + 1, fw - 2, 1, ORANGE, 0)
    for j in range(fy + 2, fy + fh - 1):
        img.putpixel((fx + 1, j), GUN_LIGHT)
        img.putpixel((fx + fw - 2, j), GUN_LIGHT)
    img.putpixel((fx + fw // 2, fy + 3), CYAN)

    # thruster housings: 2 x 5 x 3 at (0, 10) and (0, 18)
    for v in (10, 18):
        faces = paint_box(img, 0, v, 2, 5, 3, GUN, GUN_DARK)
        for name in ("east", "west", "front", "back"):
            fx, fy, fw, fh = faces[name]
            for i in range(fx, fx + fw):
                img.putpixel((i, fy + 1), ORANGE)
                img.putpixel((i, fy + 3), GUN_LIGHT)
        # nozzles: 2 x 2 x 2 at (10, v)
        faces = paint_box(img, 10, v, 2, 2, 2, NOZZLE_RIM, None, 4)
        fx, fy, fw, fh = faces["bottom"]
        fill(img, fx, fy, fw, fh, NOZZLE, 0)

    # exhaust glow plate (eyes layer): 2 x 1 x 2 at (20, 10)
    faces = paint_box(img, 20, 10, 2, 1, 2, GLOW, None, 0)
    for name in ("top", "bottom"):
        fx, fy, fw, fh = faces[name]
        fill(img, fx, fy, fw, fh, GLOW_CORE, 0)

    return img


def item_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = (20, 22, 26, 255)
    # belt band
    fill(img, 3, 7, 10, 3, STRAP, 4)
    for i in range(3, 13):
        img.putpixel((i, 7), STRAP_LIGHT)
        img.putpixel((i, 10), outline)
    # buckle
    fill(img, 7, 7, 2, 3, GUN_LIGHT, 0)
    img.putpixel((7, 8), CYAN)
    img.putpixel((8, 8), CYAN)
    # core on top
    fill(img, 5, 3, 6, 4, GUN, 4)
    fill(img, 6, 4, 4, 1, ORANGE, 0)
    for i in range(5, 11):
        img.putpixel((i, 3), GUN_LIGHT)
    # thruster pods on each side
    for x in (1, 12):
        fill(img, x, 5, 3, 7, GUN, 4)
        for j in range(5, 12):
            img.putpixel((x, j), GUN_LIGHT if x == 1 else GUN)
        for i in range(x, x + 3):
            img.putpixel((i, 6), ORANGE)
        fill(img, x, 12, 3, 1, NOZZLE_RIM, 0)
        img.putpixel((x + 1, 13), GLOW)
        img.putpixel((x + 1, 14), (255, 140, 60, 200))
    # outline pass
    src = img.copy()
    for x in range(16):
        for y in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] == 255 and src.getpixel((nx, ny)) not in (GLOW,):
                        img.putpixel((x, y), outline)
                        break
    return img


def main():
    entity_dir = os.path.join(ROOT, "entity")
    item_dir = os.path.join(ROOT, "item")
    os.makedirs(entity_dir, exist_ok=True)
    os.makedirs(item_dir, exist_ok=True)
    entity_texture().save(os.path.join(entity_dir, "pilot_jumpkit.png"))
    item_icon().save(os.path.join(item_dir, "pilot_jumpkit.png"))
    print("wrote", entity_dir, item_dir)


if __name__ == "__main__":
    main()
