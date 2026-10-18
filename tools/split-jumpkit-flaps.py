#!/usr/bin/env python3
"""Split the thruster flaps out of the baked jumpkit OBJ so the renderer can animate them.

Usage (from the repository root, after tools/bake-jumpkit.py):

    python3 tools/split-jumpkit-flaps.py

Each thruster pod ends in a hexagonal nozzle rim with three long plates hanging off alternate sides of it:
the thruster flaps. They are found geometrically around each nozzle's exhaust axis (the same nozzle
constants as JumpkitModel): triangles past the exhaust plate, inside the rim radius, that reach far down
the axis (the rim itself stops ~20 units out, the flaps run to ~49). They are grouped into the three
flaps per pod by angle around the axis and written as objects flap_r0..r2 / flap_l0..l2.

Every flap gets a hinge line:

    # hinge <object> <px> <py> <pz> <ax> <ay> <az>

a pivot on the flap's base edge (where it meets the rim) and the hinge axis along that edge, signed so
that a positive rotation swings the flap tip outward, away from the exhaust axis. JumpkitMesh reads these
and the renderer opens the flaps with the thrust level.

The script is idempotent: flap objects already present are merged back into "body" first.
"""
import os
import sys

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement")
OBJ = os.path.join(ROOT, "models", "entity", "jumpkit.obj")

NOZZLE = np.array([136.2, 124.7, 92.4])
NOZZLE_DIR = np.array([0.16, 0.79, 0.58])
RIM_RADIUS = 50.0
PAST_PLATE = 1.0
FLAP_REACH = 30.0
FLAPS_PER_POD = 3


def read(path):
    header, verts, objects, order = [], [], {}, []
    current = None
    for line in open(path):
        if line.startswith(("# hinge", "# pivot", "# skin")):
            continue
        if line.startswith("#") and current is None:
            header.append(line)
        elif line.startswith(("v ", "vt ", "vn ")):
            verts.append(line)
        elif line.startswith("o "):
            name = line.split()[1]
            if name.startswith(("flap_", "pod_", "hose_")):
                # Merge earlier splits back (re-run split-jumpkit-pods.py afterwards).
                name = "body"
            elif name.startswith("glow_"):
                name = "glow"
            current = name
            if name not in objects:
                objects[name] = []
                order.append(name)
        elif line.startswith("f "):
            objects[current].append(line)
    return header, verts, objects, order


def main(path=OBJ):
    header, verts, objects, order = read(path)
    positions = np.array([[float(x) for x in l.split()[1:4]] for l in verts if l.startswith("v ")])

    def corners(face):
        return positions[[int(t.split("/")[0]) - 1 for t in face.split()[1:]]]

    body = objects["body"]
    taken = set()
    flaps = {}
    hinges = {}
    for side_name, side in (("r", 1.0), ("l", -1.0)):
        nozzle = NOZZLE * np.array([side, 1, 1])
        axis = NOZZLE_DIR * np.array([side, 1, 1])
        axis = axis / np.linalg.norm(axis)
        u = np.cross(axis, [1.0, 0.0, 0.0])
        u /= np.linalg.norm(u)
        w = np.cross(axis, u)
        picked = []
        for i, face in enumerate(body):
            p = corners(face)
            rel = p - nozzle
            a = rel @ axis
            radial = rel - np.outer(a, axis)
            if (np.sign(p[:, 0]) == side).all() and a.min() > PAST_PLATE and np.linalg.norm(radial, axis=1).max() < RIM_RADIUS \
                    and a.max() > FLAP_REACH:
                c = rel.mean(0)
                picked.append((i, np.arctan2(c @ w, c @ u)))

        if not picked:
            sys.exit(f"no flaps found on side {side_name}")

        # Three flaps, 120 degrees apart: find the rotation of that pattern that best fits the triangles.
        angles = np.array([a for _, a in picked])
        best = max(np.linspace(0, 2 * np.pi / 3, 240, endpoint=False),
                   key=lambda off: np.cos(3 * (angles - off)).sum())
        for k in range(FLAPS_PER_POD):
            centre = best + k * 2 * np.pi / 3
            name = f"flap_{side_name}{k}"
            members = [i for i, a in picked if abs((a - centre + np.pi) % (2 * np.pi) - np.pi) < np.pi / 3]
            flaps[name] = [body[i] for i in members]
            taken.update(members)
            pts = np.concatenate([corners(body[i]) for i in members])
            rel = pts - nozzle
            a = rel @ axis
            base = pts[a < a.min() + 4.0]
            pivot = base.mean(0)
            # Hinge axis: the base edge direction, i.e. tangential around the exhaust axis at this flap.
            radial = np.cos(centre) * u + np.sin(centre) * w
            hinge = np.cross(axis, radial)
            if len(base) >= 2:
                spread = base - pivot
                _, _, vt = np.linalg.svd(spread)
                edge = vt[0]
                if abs(edge @ hinge) > 0.5:
                    hinge = edge * np.sign(edge @ hinge)
            hinge = hinge / np.linalg.norm(hinge)
            # Sign: a small positive rotation must move the tip away from the exhaust axis.
            tip = pts[np.argmax(a)]
            r = tip - pivot
            moved = r + 0.01 * np.cross(hinge, r)
            before = np.linalg.norm((tip - nozzle) - ((tip - nozzle) @ axis) * axis)
            after_p = pivot + moved - nozzle
            after = np.linalg.norm(after_p - (after_p @ axis) * axis)
            if after < before:
                hinge = -hinge
            hinges[name] = (pivot, hinge)
            print(f"{name}: {len(members)} triangles, pivot {np.round(pivot, 1)}, axis {np.round(hinge, 3)}")

    objects["body"] = [f for i, f in enumerate(body) if i not in taken]
    with open(path, "w") as out:
        out.writelines(header)
        for name, (pivot, hinge) in hinges.items():
            out.write(f"# hinge {name} {pivot[0]:.4f} {pivot[1]:.4f} {pivot[2]:.4f} {hinge[0]:.5f} {hinge[1]:.5f} {hinge[2]:.5f}\n")
        out.writelines(verts)
        for name in order + sorted(flaps):
            faces = objects.get(name) if name in objects else flaps[name]
            out.write(f"o {name}\n")
            out.writelines(faces)


if __name__ == "__main__":
    main(*sys.argv[1:])
