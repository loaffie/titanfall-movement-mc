#!/usr/bin/env python3
"""Split the two thruster modules out of the baked jumpkit OBJ so the renderer can articulate them.

Usage (from the repository root, after tools/bake-jumpkit.py and tools/split-jumpkit-flaps.py; the bake
script runs both automatically):

    python3 tools/split-jumpkit-pods.py

Each thruster module is a pod (the big housing with the nozzle, one connected mesh), the capsule tank
that joins it to the pack (its root sits in the side hub, which stays on the pack as the joint), and the
ends of the hoses running from the central tank into the pod. They are written as:

    pod_r / pod_l     pod housing + capsule, moved rigidly about the hub
    glow_r / glow_l   that pod's exhaust plate (emissive)
    hose_r / hose_l   hose ends: skinned, blending from the pack (weight 0) to the pod (weight 1)

with metadata lines

    # pivot <pod> <x> <y> <z>       the joint: centre of the side hub
    # skin <hose> <x0> <x1>         weight = smoothstep(x0, x1, |x|) per vertex

"_r" is the +X side of the mesh, matching the flap names. Flap objects are left alone (they belong to the
pod on their side). The script is idempotent: earlier pod/hose/glow splits are merged back first.
"""
import os
import sys

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement")
OBJ = os.path.join(ROOT, "models", "entity", "jumpkit.obj")

# Hose skinning: fully on the pack inside |x| < 58 (the central tank reaches ~55), fully on the pod past 72.
# Nozzles (as in JumpkitModel / split-jumpkit-flaps.py) and the inner-wall glow: atlas cell, rim depth.
NOZZLE = np.array([136.2, 124.7, 92.4])
NOZZLE_DIR = np.array([0.16, 0.79, 0.58])
WALL_CELL = 11
RIM_DEPTH = 17.5

SKIN_X0 = 50.0
SKIN_X1 = 88.0
# Candidate hose triangles start past this |x| (the tank's side dome reaches ~69, the hose tubes ~90).
HOSE_START = 52.0
# Of the candidates, only the pieces that reach this far out are hose tubes running into the pod; the
# rest is the tank's side dome (the green pouch), which stays on the pack so it never stretches.
HOSE_REACH = 80.0


def read(path):
    header, meta, verts, objects, order = [], [], [], {}, []
    current = None
    for line in open(path):
        if line.startswith(("# pivot", "# skin")):
            continue
        if line.startswith("# hinge"):
            meta.append(line)
            continue
        if line.startswith("#") and current is None:
            header.append(line)
        elif line.startswith(("v ", "vt ", "vn ")):
            verts.append(line)
        elif line.startswith("o "):
            name = line.split()[1]
            if name.startswith(("pod_", "hose_")):
                name = "body"
            elif name.startswith("glowin_"):
                name = None  # regenerated below
            elif name.startswith("glow_"):
                name = "glow"
            current = name
            if name is None:
                continue
            if name not in objects:
                objects[name] = []
                order.append(name)
        elif line.startswith("f ") and current is not None:
            objects[current].append(line)
    return header, meta, [l for l in verts if not l.startswith("vt ") or not l.rstrip().endswith(" #glowin")], objects, order


def components(faces, index_of):
    parent = list(range(len(faces)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    first = {}
    for i, face in enumerate(faces):
        for key in index_of(face):
            if key in first:
                ra, rb = find(i), find(first[key])
                if ra != rb:
                    parent[ra] = rb
            else:
                first[key] = i
    groups = {}
    for i in range(len(faces)):
        groups.setdefault(find(i), []).append(i)
    return list(groups.values())


def main(path=OBJ):
    header, meta, verts, objects, order = read(path)
    positions = np.array([[float(x) for x in l.split()[1:4]] for l in verts if l.startswith("v ")])

    def ids(face):
        return [int(t.split("/")[0]) - 1 for t in face.split()[1:]]

    def keys(face):
        return [tuple(np.round(positions[i], 3)) for i in ids(face)]

    body = objects["body"]
    comps = components(body, keys)
    info = []
    for c in comps:
        pts = positions[sorted({v for i in c for v in ids(body[i])})]
        info.append((c, pts))
    info.sort(key=lambda item: -len(item[0]))

    out = {"body": [], "glow": []}
    pivots = {}
    assigned = set()
    for side_name, side in (("r", 1.0), ("l", -1.0)):
        on_side = [(c, p) for c, p in info if (np.sign(p[:, 0]) == side).all()]
        # Pod: the largest one-sided component. Capsule: the one-sided component spanning the gap from the
        # hub into the pod (reaches past the pod's inner face). Hub: the one-sided component nearest the
        # centre line that the capsule root sits in.
        pod_c, pod_p = on_side[0]
        inner = np.abs(pod_p[:, 0]).min()
        capsule = [(c, p) for c, p in on_side[1:] if np.abs(p[:, 0]).max() > inner + 20 and np.abs(p[:, 0]).min() < inner]
        hub = [(c, p) for c, p in on_side[1:] if np.abs(p[:, 0]).max() < inner + 20]
        if not capsule or not hub:
            sys.exit(f"could not identify the capsule or hub on side {side_name}")
        cap_c, _ = capsule[0]
        hub_c, hub_p = hub[0]
        pivot = (hub_p.min(0) + hub_p.max(0)) / 2.0
        pivots[f"pod_{side_name}"] = pivot
        out[f"pod_{side_name}"] = [body[i] for i in pod_c + cap_c]
        assigned.update(pod_c + cap_c)
        print(f"pod_{side_name}: pod {len(pod_c)} + capsule {len(cap_c)} triangles, pivot {np.round(pivot, 2)}")

    # Hoses: the central tank's hose runs (the two-sided component reaching furthest out, into the pods).
    for side_name, side in (("r", 1.0), ("l", -1.0)):
        out[f"hose_{side_name}"] = []
    two_sided = [(c, p) for c, p in info if p[:, 0].min() < 0 < p[:, 0].max() and not assigned.intersection(c)]
    tank_c, _ = max(two_sided, key=lambda item: np.abs(item[1][:, 0]).max())
    tank = set(tank_c)
    candidates = {"r": [], "l": []}
    for i, face in enumerate(body):
        if i in assigned:
            continue
        p = positions[ids(face)]
        if i in tank and p[:, 0].min() * p[:, 0].max() > 0 and np.abs(p[:, 0]).max() > HOSE_START \
                and np.abs(p[:, 0]).mean() > HOSE_START - 6:
            candidates["r" if p[:, 0].mean() > 0 else "l"].append(i)
    for side_name, picked in candidates.items():
        faces = [body[i] for i in picked]
        for piece in components(faces, keys):
            reach = max(np.abs(positions[ids(faces[k])][:, 0]).max() for k in piece)
            if reach >= HOSE_REACH:
                out[f"hose_{side_name}"].extend(faces[k] for k in piece)
                assigned.update(picked[k] for k in piece)
    for i, face in enumerate(body):
        if i not in assigned:
            out["body"].append(face)
    for side_name in ("r", "l"):
        print(f"hose_{side_name}: {len(out['hose_' + side_name])} triangles")
        out[f"glow_{side_name}"] = []

    for face in objects.get("glow", []):
        side_name = "r" if positions[ids(face)][:, 0].mean() > 0 else "l"
        out[f"glow_{side_name}"].append(face)
    del out["glow"]

    # Nozzle interiors: the faces of each pod's nozzle rim that look in at the exhaust axis, duplicated
    # for the emissive pass with UVs into the emissive atlas's "inner wall" cell (11): u runs round the
    # axis, v from the exhaust plate (bright) to the rim's mouth (dark).
    normals = np.array([[float(x) for x in l.split()[1:4]] for l in verts if l.startswith("vn ")])
    vt_count = sum(1 for l in verts if l.startswith("vt "))
    new_vt = []
    cell, grid, size, pad = 64, 4, 256, 3
    ox, oy = (WALL_CELL % grid) * cell, (WALL_CELL // grid) * cell
    for side_name, side in (("r", 1.0), ("l", -1.0)):
        nozzle = NOZZLE * np.array([side, 1, 1])
        axis = NOZZLE_DIR * np.array([side, 1, 1])
        axis = axis / np.linalg.norm(axis)
        e1 = np.cross(axis, [1.0, 0.0, 0.0])
        e1 /= np.linalg.norm(e1)
        e2 = np.cross(axis, e1)
        faces_in = []
        for face in out[f"pod_{side_name}"]:
            tokens = face.split()[1:]
            vi = [int(t.split("/")[0]) - 1 for t in tokens]
            ni = int(tokens[0].split("/")[2]) - 1
            p = positions[vi]
            rel = p - nozzle
            a = rel @ axis
            radial = rel - np.outer(a, axis)
            r = np.linalg.norm(radial, axis=1)
            c = radial.mean(0)
            inward = -c / (np.linalg.norm(c) + 1e-9)
            if a.min() > -1.0 and a.max() < RIM_DEPTH + 7.0 and r.max() < 34.0 and normals[ni] @ inward > 0.4:
                corners = []
                for k, t in enumerate(tokens):
                    angle = np.arctan2(radial[k] @ e2, radial[k] @ e1)
                    u = (angle / (2 * np.pi)) % 1.0
                    v = min(max(a[k] / RIM_DEPTH, 0.0), 1.0)
                    au = (ox + pad + u * (cell - 2 * pad)) / size
                    av = (oy + pad + v * (cell - 2 * pad)) / size
                    new_vt.append(f"vt {au:.5f} {av:.5f} #glowin\n")
                    parts = t.split("/")
                    corners.append(f"{parts[0]}/{vt_count + len(new_vt)}/{parts[2]}")
                faces_in.append("f " + " ".join(corners) + "\n")
        out[f"glowin_{side_name}"] = faces_in
        print(f"glowin_{side_name}: {len(faces_in)} triangles")
    verts = verts + new_vt

    flaps = sorted(name for name in order if name.startswith("flap_"))
    names = ["body", "pod_r", "pod_l", "hose_r", "hose_l", "glow_r", "glow_l", "glowin_r", "glowin_l"] + flaps
    with open(path, "w") as f:
        f.writelines(header)
        f.writelines(meta)
        for name, pivot in pivots.items():
            f.write(f"# pivot {name} {pivot[0]:.4f} {pivot[1]:.4f} {pivot[2]:.4f}\n")
        for side_name in ("r", "l"):
            f.write(f"# skin hose_{side_name} {SKIN_X0:g} {SKIN_X1:g}\n")
        f.writelines(verts)
        for name in names:
            faces = out.get(name, objects.get(name, []))
            f.write(f"o {name}\n")
            f.writelines(faces)


if __name__ == "__main__":
    main(*sys.argv[1:])
