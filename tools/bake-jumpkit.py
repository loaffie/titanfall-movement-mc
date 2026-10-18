#!/usr/bin/env python3
"""Bake the Pilot Jumpkit OBJ into the mod: material atlas UVs, base + emissive textures, glow group.

Usage (from the repository root):

    python3 tools/bake-jumpkit.py path/to/jumpkit_source.obj

Input: the untextured OBJ exported from Maya/FBX (objects pCube1, loftedSurface1, polySurface1-6, in
centimetre-ish units, +Y up, the pack's flat mounting face toward -X, the thruster pods at +-Z).

Output (overwritten):
    src/main/resources/assets/titanfall_movement/models/entity/jumpkit.obj
    src/main/resources/assets/titanfall_movement/textures/entity/jumpkit.png
    src/main/resources/assets/titanfall_movement/textures/entity/jumpkit_emissive.png

How it works
------------
Every triangle is classified into a material (charcoal chassis, olive webbing, navy webbing, hazard
stripes, orange accent, dark steel, steel trim, vents, thruster exhaust) by which mesh it belongs to and
by its position and normal. The textures are a 4x4 atlas of 64 px material cells, and each triangle's
original UVs are squeezed into its material's cell, so the hand-painted detail of each cell (weave,
stripes, brushing, bolts, scratches) flows over the surface. Exhaust plates get planar UVs around the
nozzle axis so the emissive cell's radial glow is centred on each port. Exhaust triangles are written a
second time as object "glow", which the renderer draws with the emissive layer.

Positions are converted to the player model's body space: X = side (OBJ Z), Y = down (-OBJ Y),
Z = backward (OBJ X), with the origin at the top-centre of the mounting face. Units stay OBJ units; the
Java renderer scales them (JumpkitModel.SCALE).

Requires numpy and Pillow.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement")
CELL = 64
GRID = 4
SIZE = CELL * GRID
PAD = 3

# material -> atlas cell index
CHARCOAL, CHARCOAL_DARK, OLIVE, NAVY, HAZARD, ORANGE, DARK_STEEL, STEEL_TRIM, VENT, EXHAUST = range(10)
NAMES = ["charcoal", "charcoal_dark", "olive", "navy", "hazard", "orange", "dark_steel", "steel_trim", "vent", "exhaust"]


# ----------------------------------------------------------------------------------------------
# OBJ IO
# ----------------------------------------------------------------------------------------------

def load_obj(path):
    objects, V, T = [], [], []
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == "o":
            objects.append([p[1], []])
        elif p[0] == "v":
            V.append([float(x) for x in p[1:4]])
        elif p[0] == "vt":
            T.append([float(x) for x in p[1:3]])
        elif p[0] == "f":
            idx = [[int(x) - 1 if x else -1 for x in t.split("/")] for t in p[1:]]
            for k in range(1, len(idx) - 1):  # fan-triangulate
                objects[-1][1].append([idx[0], idx[k], idx[k + 1]])
    return objects, np.array(V), np.array(T)


# ----------------------------------------------------------------------------------------------
# classification
# ----------------------------------------------------------------------------------------------

def face_geometry(V, faces):
    F = np.array([[v[0] for v in f] for f in faces])
    P = V[F]
    n = np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0])
    area = np.linalg.norm(n, axis=1) / 2
    n = n / np.maximum(2 * area[:, None], 1e-12)
    return P, P.mean(1), n, area


def nozzle_of(c, n, area):
    """Centre and axis of a pod's exhaust: the dense fan of tiny downward triangles under the pod."""
    down = (area < 8) & (n[:, 1] < -0.3)
    low = c[:, 1] < np.percentile(c[down, 1], 60) if down.any() else down
    pick = down & low
    if pick.sum() < 6:
        pick = down
    centre = c[pick].mean(0)
    near = pick & (np.linalg.norm(c - centre, axis=1) < 25)
    centre = c[near].mean(0)
    axis = n[near].mean(0)
    axis /= np.linalg.norm(axis)
    return centre, axis


def classify(name, c, n, area):
    mats = np.full(len(c), CHARCOAL)
    side = 1.0 if c[:, 2].mean() >= 0 else -1.0
    az = np.abs(c[:, 2])
    if name == "pCube1":
        mats[:] = STEEL_TRIM
    elif name in ("polySurface5", "polySurface6"):
        mats[:] = DARK_STEEL
    elif name in ("polySurface1", "polySurface2"):
        mats[:] = OLIVE
        mats[n[:, 1] < -0.3] = NAVY
        mats[(n[:, 1] > 0.8)] = HAZARD
    elif name == "loftedSurface1":
        # Lower shell (capsule tank + hoses) vs upper vented box.
        lower = c[:, 1] < 98
        caps = lower & (np.abs(c[:, 2]) < 55)
        if caps.any():
            lo, hi = c[caps].min(0), c[caps].max(0)
            ctr, rad = (lo + hi) / 2, np.maximum((hi - lo) / 2, 1e-3)
        e = (((c - ctr) / (rad * 1.08)) ** 2).sum(1)
        mats[lower & (e <= 1.0)] = CHARCOAL_DARK
        mats[lower & (e > 1.0)] = OLIVE  # hoses
        box = ~lower
        mats[box] = CHARCOAL
        mats[box & (n[:, 0] > 0.55) & (c[:, 1] > 95) & (c[:, 1] < 152)] = VENT
        mats[box & (n[:, 1] > 0.7)] = HAZARD
        mats[box & (np.abs(n[:, 2]) > 0.8)] = ORANGE
    elif name in ("polySurface3", "polySurface4"):
        outward = n[:, 2] * side
        mats[(outward > 0.8) & (az > 160) & (az < 184)] = OLIVE          # recessed side panel
        mats[(outward > 0.8) & (az >= 184)] = STEEL_TRIM                 # panel frame / outer rim
        mats[(c[:, 1] > 112) & (c[:, 1] < 126) & (outward > 0.5)] = HAZARD  # hazard band
        mats[(c[:, 0] < 5) & (az < 110)] = DARK_STEEL                    # mounting joint
        mats[(c[:, 1] < 12) & (n[:, 1] > -0.5)] = DARK_STEEL             # fins / skirts
        centre, axis = nozzle_of(c, n, area)
        exhaust = (np.linalg.norm(c - centre, axis=1) < 22) & (n @ axis > 0.6)
        mats[exhaust] = EXHAUST
        return mats, (centre, axis)
    return mats, None


# ----------------------------------------------------------------------------------------------
# textures
# ----------------------------------------------------------------------------------------------

def cell_origin(mat):
    return (mat % GRID) * CELL, (mat // GRID) * CELL


def noise(rng, base, amount):
    return tuple(max(0, min(255, int(b + rng.uniform(-amount, amount)))) for b in base[:3]) + (255,)


def paint_base():
    rng = random.Random(1337)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()

    def fill(mat, fn):
        ox, oy = cell_origin(mat)
        for y in range(CELL):
            for x in range(CELL):
                px[ox + x, oy + y] = fn(x, y)

    def chassis(base):
        def f(x, y):
            col = list(noise(rng, base, 5))
            if x % 32 == 0 or y % 32 == 0:               # panel seams
                col = [max(0, v - 18) for v in col[:3]] + [255]
            if (x % 32 in (3, 28)) and (y % 32 in (3, 28)):  # rivets
                col = [min(255, v + 30) for v in col[:3]] + [255]
            if rng.random() < 0.012:                     # chips / wear
                col = [min(255, v + 45) for v in col[:3]] + [255]
            return tuple(col)
        return f

    fill(CHARCOAL, chassis((58, 61, 64)))
    fill(CHARCOAL_DARK, chassis((42, 45, 49)))

    def webbing(base, line):
        def f(x, y):
            col = list(noise(rng, base, 6))
            if (x + (y // 4) % 2 * 2) % 4 == 0:           # weave
                col = [max(0, v - 14) for v in col[:3]] + [255]
            if x % 16 in (0, 15):                         # stitched edges
                col = [min(255, v + 24) for v in line] + [255]
            return tuple(col)
        return f

    fill(OLIVE, webbing((79, 90, 46), (110, 118, 70)))
    fill(NAVY, webbing((36, 50, 74), (70, 86, 110)))

    def hazard(x, y):
        stripe = ((x + y) // 8) % 2 == 0
        base = (227, 155, 27) if stripe else (29, 29, 29)
        col = noise(rng, base, 8)
        if rng.random() < 0.03:                           # scuffed paint shows metal
            col = noise(rng, (120, 124, 128), 10)
        return col

    fill(HAZARD, hazard)

    def orange(x, y):
        col = noise(rng, (207, 106, 26), 9)
        if y % 21 == 0:
            col = noise(rng, (150, 75, 20), 6)
        if rng.random() < 0.02:
            col = noise(rng, (110, 114, 118), 10)
        return col

    fill(ORANGE, orange)

    def steel(base, bolts):
        def f(x, y):
            col = list(noise(rng, base, 3))
            col = [min(255, max(0, v + int(6 * math.sin(y * 0.9)))) for v in col[:3]] + [255]  # brushing
            if bolts and (x % 16 in (7, 8)) and (y % 16 in (7, 8)):
                col = [min(255, v + 40) for v in col[:3]] + [255]
            return tuple(col)
        return f

    fill(DARK_STEEL, steel((75, 80, 86), True))

    def trim(x, y):
        col = list(noise(rng, (128, 134, 140), 6))
        if rng.random() < 0.05:                           # scratches
            col = [min(255, v + 35) for v in col[:3]] + [255]
        if (x * 7 + y * 3) % 23 == 0:
            col = [max(0, v - 25) for v in col[:3]] + [255]  # grime streaks
        return tuple(col)

    fill(STEEL_TRIM, trim)

    def vent(x, y):
        return noise(rng, (34, 36, 39), 3) if y % 6 < 2 else noise(rng, (80, 85, 90), 4)

    fill(VENT, vent)

    def exhaust(x, y):
        r = math.hypot(x - CELL / 2 + 0.5, y - CELL / 2 + 0.5) / (CELL / 2)
        ring = 0.5 + 0.5 * math.cos(r * 18)
        v = int(18 + 14 * ring * (1 - r))
        return (v, v + 4, v + 8, 255)

    fill(EXHAUST, exhaust)
    return img


def paint_emissive():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    px = img.load()
    ox, oy = cell_origin(EXHAUST)
    for y in range(CELL):
        for x in range(CELL):
            r = math.hypot(x - CELL / 2 + 0.5, y - CELL / 2 + 0.5) / (CELL / 2)
            r = min(r, 1.0)
            core = max(0.0, 1.0 - r * 1.6)
            glow = max(0.0, 1.0 - r) ** 0.6
            ring = 0.85 + 0.15 * math.cos(r * 18)
            red = int(255 * min(1.0, 0.85 * core))
            green = int(255 * min(1.0, (0.55 * glow + 0.45 * core) * ring))
            blue = int(255 * min(1.0, (0.95 * glow + 0.05) * ring))
            px[ox + x, oy + y] = (red, green, blue, 255)
    return img


# ----------------------------------------------------------------------------------------------
# bake
# ----------------------------------------------------------------------------------------------

def to_body_space(p, top, back):
    """OBJ (x back, y up, z side) -> body space (X side, Y down, Z back), origin top-centre of the mount face."""
    return np.stack([p[..., 2], top - p[..., 1], p[..., 0] - back], axis=-1)


def main(src):
    objects, V, T = load_obj(src)
    top, back = V[:, 1].max(), V[:, 0].min()
    out_v, out_vt, out_vn = {}, {}, {}
    groups = {"body": [], "glow": []}
    counts = {}

    def key_index(table, key):
        if key not in table:
            table[key] = len(table) + 1
        return table[key]

    for name, faces in objects:
        P, c, n, area = face_geometry(V, faces)
        mats, nozzle = classify(name, c, n, area)
        for fi, face in enumerate(faces):
            mat = int(mats[fi])
            counts[NAMES[mat]] = counts.get(NAMES[mat], 0) + 1
            ox, oy = cell_origin(mat)
            if mat == EXHAUST and nozzle is not None:
                centre, axis = nozzle
                e1 = np.cross(axis, [0.0, 1.0, 0.0])
                if np.linalg.norm(e1) < 1e-3:
                    e1 = np.cross(axis, [1.0, 0.0, 0.0])
                e1 /= np.linalg.norm(e1)
                e2 = np.cross(axis, e1)
                uvs = [(0.5 + np.dot(V[v[0]] - centre, e1) / 44.0, 0.5 + np.dot(V[v[0]] - centre, e2) / 44.0) for v in face]
            else:
                uvs = [tuple(T[v[1]]) if v[1] >= 0 else (0.5, 0.5) for v in face]
            corners = []
            for (u, w), v in zip(uvs, face):
                u = min(max(u, 0.0), 1.0)
                w = min(max(w, 0.0), 1.0)
                au = (ox + PAD + u * (CELL - 2 * PAD)) / SIZE
                av = (oy + PAD + (1.0 - w) * (CELL - 2 * PAD)) / SIZE
                pos = to_body_space(V[v[0]], top, back)
                vi = key_index(out_v, tuple(np.round(pos, 3)))
                ti = key_index(out_vt, (round(au, 5), round(av, 5)))
                # flat normals, in body space (the axis swap is a proper rotation)
                nb = to_body_space(n[fi], 0.0, 0.0)
                ni = key_index(out_vn, tuple(np.round(nb, 3)))
                corners.append((vi, ti, ni))
            groups["body"].append(corners)
            if mat == EXHAUST:
                groups["glow"].append(corners)

    model_dir = os.path.join(ROOT, "models", "entity")
    tex_dir = os.path.join(ROOT, "textures", "entity")
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    with open(os.path.join(model_dir, "jumpkit.obj"), "w") as f:
        f.write("# Pilot Jumpkit - baked by tools/bake-jumpkit.py\n")
        f.write("# body space: X side, Y down, Z back (player model units before JumpkitModel.SCALE)\n")
        for p in out_v:
            f.write("v %g %g %g\n" % p)
        for t in out_vt:
            f.write("vt %g %g\n" % t)
        for nn in out_vn:
            f.write("vn %g %g %g\n" % nn)
        for g, tris in groups.items():
            f.write("o %s\n" % g)
            for tri in tris:
                f.write("f " + " ".join("%d/%d/%d" % c for c in tri) + "\n")
    paint_base().save(os.path.join(tex_dir, "jumpkit.png"))
    paint_emissive().save(os.path.join(tex_dir, "jumpkit_emissive.png"))
    print("triangles", sum(len(f) for _, f in objects), "glow", len(groups["glow"]), "materials", counts)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
    # Split the thruster flaps out into hinged objects (flap animation), then the two thruster modules into
    # articulated objects with skinned hoses (module animation).
    import runpy
    sys.argv = [sys.argv[0]]
    here = os.path.dirname(os.path.abspath(__file__))
    runpy.run_path(os.path.join(here, "split-jumpkit-flaps.py"), run_name="__main__")
    runpy.run_path(os.path.join(here, "split-jumpkit-pods.py"), run_name="__main__")
    # Paint the thruster interior (exhaust plate, turbine, inner wall) into the emissive atlas.
    runpy.run_path(os.path.join(here, "paint-jumpkit-emissive.py"), run_name="__main__")
