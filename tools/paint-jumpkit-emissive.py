#!/usr/bin/env python3
"""Paint the jumpkit's thruster interior into the emissive atlas (textures/entity/jumpkit_emissive.png).

Usage (from the repository root; tools/bake-jumpkit.py runs it automatically):

    python3 tools/paint-jumpkit-emissive.py

The emissive map is drawn additively at full brightness (black = no glow), so everything here is light.
Three 64 px cells of the 4x4 atlas:

    9   exhaust plate - what you see looking into the nozzle: a white-hot core, a ring of injector ports,
        a honeycomb grille between glowing rings, six dark support struts, and a deep-blue outer rim
    10  turbine - twelve swept vanes round a hub, drawn on a disc the renderer spins inside the nozzle
        (faster the harder the thruster burns)
    11  inner wall - the nozzle rim's inner faces: u round the axis, v from the plate (bright) to the
        mouth (dark), with cooling bands and faint heat streaks

Everything is rendered 4x supersampled and box-filtered down, so the fine lines stay smooth. Requires
numpy and Pillow.
"""
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "titanfall_movement")
PATH = os.path.join(ROOT, "textures", "entity", "jumpkit_emissive.png")
CELL = 64
GRID = 4
SS = 4

WHITE = np.array([1.0, 1.0, 1.0])
CYAN = np.array([0.35, 0.92, 1.0])
BLUE = np.array([0.08, 0.38, 1.0])
DEEP = np.array([0.02, 0.10, 0.55])


def grid(n):
    """Pixel-centre coordinates in -1..1 for an n x n supersampled cell."""
    c = (np.arange(n) + 0.5) / n * 2.0 - 1.0
    return np.meshgrid(c, c)


def smooth(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def line(distance, width):
    """Soft line intensity from a distance field."""
    return 1.0 - smooth(0.0, width, np.abs(distance))


def hexgrid_distance(x, y, scale):
    """Distance to the edges of a hexagonal honeycomb (pointy-top), in cell units."""
    q = np.stack([x, y], -1) * scale
    r = np.array([1.0, math.sqrt(3.0)])
    h = r * 0.5
    a = np.mod(q, r) - h
    b = np.mod(q - h, r) - h
    g = np.where((a * a).sum(-1, keepdims=True) < (b * b).sum(-1, keepdims=True), a, b)
    # distance from the hex centre to its edge, in a hexagon of inradius 0.5
    gx, gy = np.abs(g[..., 0]), np.abs(g[..., 1])
    d = np.maximum(gx * 0.5 + gy * math.sqrt(3.0) / 2.0, gx)
    return (0.5 - d) / scale


def colour(stops, t):
    """Piecewise-linear colour ramp; stops = [(t, rgb), ...]."""
    t = np.clip(t, 0.0, 1.0)
    out = np.zeros(t.shape + (3,))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = (t >= t0) & (t <= t1)
        k = ((t - t0) / max(t1 - t0, 1e-6))[m][:, None]
        out[m] = c0 + (c1 - c0) * k
    return out


def plate(n):
    x, y = grid(n)
    r = np.hypot(x, y)
    theta = np.arctan2(y, x)
    light = np.zeros((n, n, 3))
    # Core: white-hot centre bleeding into cyan.
    light += colour([(0.0, WHITE * 1.1), (0.16, WHITE), (0.3, CYAN * 0.9), (0.45, BLUE * 0.3), (1.0, BLUE * 0.0)], r)
    # Injector ring: eight bright ports round the core.
    ports = np.zeros_like(r)
    for k in range(8):
        a = k * math.pi / 4.0
        px, py = 0.38 * math.cos(a), 0.38 * math.sin(a)
        ports = np.maximum(ports, 1.0 - smooth(0.02, 0.075, np.hypot(x - px, y - py)))
    light += ports[..., None] * CYAN * 1.1
    # Honeycomb grille between the rings, dimmer toward the rim.
    honey = line(hexgrid_distance(x, y, 9.0), 0.012) * smooth(0.46, 0.5, r) * (1.0 - smooth(0.8, 0.86, r))
    light += honey[..., None] * (BLUE * 0.9 + CYAN * 0.25) * (1.15 - r)[..., None]
    # Glowing rings.
    for radius, width, col in ((0.47, 0.018, CYAN), (0.68, 0.012, CYAN * 0.7), (0.88, 0.02, BLUE)):
        light += line(r - radius, width)[..., None] * col
    # Outer rim: deep-blue glow fading out to the plate's edge.
    light += (smooth(0.8, 0.95, r) * (1.0 - smooth(0.95, 1.0, r)))[..., None] * DEEP * 1.4
    # Six struts cut across, dark with faint lit edges.
    strut = np.zeros_like(r)
    edge = np.zeros_like(r)
    for k in range(6):
        a = k * math.pi / 3.0 + math.pi / 6.0
        d = np.abs(-math.sin(a) * x + math.cos(a) * y)
        along = math.cos(a) * x + math.sin(a) * y
        on = (along > 0.2) & (r < 0.92)
        strut = np.maximum(strut, np.where(on, 1.0 - smooth(0.025, 0.04, d), 0.0))
        edge = np.maximum(edge, np.where(on, line(d - 0.045, 0.01), 0.0))
    light *= (1.0 - 0.9 * strut)[..., None]
    light += edge[..., None] * CYAN * 0.5
    light *= (r < 1.0)[..., None]
    return light


def turbine(n):
    x, y = grid(n)
    r = np.hypot(x, y)
    theta = np.arctan2(y, x)
    light = np.zeros((n, n, 3))
    blades = 12
    # Swept vanes: the vane angle drifts with radius, like a compressor stage seen end-on.
    swept = theta - 1.1 * r
    phase = np.mod(swept * blades / (2 * math.pi), 1.0)
    vane = line(phase - 0.5, 0.06) * smooth(0.2, 0.26, r) * (1.0 - smooth(0.86, 0.95, r))
    light += vane[..., None] * (CYAN * (1.1 - 0.7 * r)[..., None] + BLUE * 0.4)
    # Leading-edge glints.
    glint = line(phase - 0.44, 0.015) * smooth(0.3, 0.4, r) * (1.0 - smooth(0.8, 0.9, r))
    light += glint[..., None] * WHITE * 0.6
    # Hub: bright ring with a dark cap.
    light += line(r - 0.22, 0.025)[..., None] * WHITE * 0.9
    light += (1.0 - smooth(0.0, 0.18, r))[..., None] * CYAN * 0.25
    light *= 1.5 * (r < 1.0)[..., None]
    return light


def inner_wall(n):
    u = (np.arange(n) + 0.5) / n
    v = (np.arange(n) + 0.5) / n
    uu, vv = np.meshgrid(u, v)
    # Bright at the plate (v = 0), dark at the mouth.
    base = colour([(0.0, CYAN * 1.0), (0.25, BLUE * 0.8), (0.7, DEEP * 0.6), (1.0, DEEP * 0.0)], vv)
    # Cooling bands round the bell.
    bands = 0.75 + 0.25 * np.cos(vv * 2 * math.pi * 5.0)
    # Faint heat streaks running out along the wall.
    rng = np.random.default_rng(1609)
    streak = np.zeros_like(uu)
    for _ in range(14):
        c = rng.uniform(0, 1)
        streak = np.maximum(streak, line(np.mod(uu - c + 0.5, 1.0) - 0.5, 0.012) * rng.uniform(0.3, 0.8))
    return base * bands[..., None] * (1.0 + 0.6 * streak[..., None])


def paint(img, index, light):
    small = light.reshape(CELL, SS, CELL, SS, 3).mean(axis=(1, 3))
    rgb = (np.clip(small, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)
    ox, oy = (index % GRID) * CELL, (index // GRID) * CELL
    arr = np.array(img)
    arr[oy:oy + CELL, ox:ox + CELL, :3] = rgb
    arr[oy:oy + CELL, ox:ox + CELL, 3] = 255
    return Image.fromarray(arr, "RGBA")


def main():
    img = Image.open(PATH).convert("RGBA")
    n = CELL * SS
    img = paint(img, 9, plate(n))
    img = paint(img, 10, turbine(n))
    img = paint(img, 11, inner_wall(n))
    img.save(PATH)
    print("painted exhaust plate, turbine and inner wall into", os.path.relpath(PATH))


if __name__ == "__main__":
    main()
