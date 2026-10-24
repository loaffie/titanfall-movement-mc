#!/usr/bin/env python3
"""Synthesize the mod's movement sounds, modelled on the character of Titanfall 2's pilot jumpkit.

Every sound is built here from oscillators, filtered noise and envelopes - nothing is sampled or derived
from Titanfall 2 or any other game. The aim is the *feel* of the jumpkit: a hard pneumatic "fwump" with
a valve click and a jet hiss for the double jump, quick light footsteps over a thin thruster hiss on a wall
run, a gritty scrape for the slide, and a weighty, rattly landing. The Grapple gets the same treatment: a
pneumatic launch with the line whipping out, a hard metallic bite, a straining winch while it reels the
pilot in, and the quick zip and latch of the hook coming home. The Stim: the jab and hiss of the
autoinjector and the rush that follows, a racing heart while it lasts, and the comedown. And Phase Shift:
the gauntlet's coil charging and the pilot tearing out of the world with a warped, reversed rush, a hollow
humming drone while phased, the rising warning before the way back, and the snap of rematerialising. And
the Pulse Blade: the kunai sliding out of its sheath, the whip of the throw, the bite as it sticks, the
deep sonar ping of each pulse, and the alarm a revealed pilot hears. And the Cloak: a glassy shimmer bending
shut round the pilot, a faint airy hum while it holds, a warning flutter before it fails, and the shimmer
tearing open again. And the Holo Pilot: the projector's digital chirp and the hologram resolving with a
bright synthetic tone, its low flickering hum, the crackle of a hit, and the picture breaking up. And the
A-Wall: the pin pull and throw with the disc arming in flight, the slam of the disc and the field igniting with
a distorted electrical surge, its grainy power buzz, the dull absorbing thump of a shot it stops, a faint sizzle
on a shot it amps, and the field collapsing - gritty and low, as Titanfall's tech sounds, never tuneful.

Usage (from the repository root):

    python3 tools/generate-audio.py            # everything
    python3 tools/generate-audio.py grapple_   # only the sounds whose names start with grapple_ (or stim_, ...)

Requires numpy, scipy and ffmpeg (with libvorbis) on the PATH. Output, as mono 44.1 kHz Ogg Vorbis:

    src/main/resources/assets/titanfall_movement/sounds/movement/<name>.ogg
    src/main/resources/assets/titanfall_movement/sounds/grapple/<name>.ogg   (the grapple_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/stim/<name>.ogg      (the stim_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/phase/<name>.ogg     (the phase_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/pulse/<name>.ogg     (the pulse_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/cloak/<name>.ogg     (the cloak_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/holo/<name>.ogg      (the holo_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/awall/<name>.ogg     (the awall_* sounds)

The one-shots come in variants (jump_1..3, double_jump_1..3, walljump_1..3, land_1..3) that
sounds.json picks between at random, so repeated moves do not sound like a loop of one sample. The
loops (wallrun_loop, slide_loop) are cross-faded at the seam so they loop without a click.
"""
from __future__ import annotations

import os
import pathlib
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

RATE = 44100
SOUNDS_DIR = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/titanfall_movement/sounds"
OUT = SOUNDS_DIR / "movement"


# ------------------------------------------------------------------------------------------------
# building blocks
# ------------------------------------------------------------------------------------------------

def t_axis(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def noise(rng, seconds):
    return rng.normal(0.0, 1.0, int(RATE * seconds))


def bandpass(x, low, high, order=4):
    sos = signal.butter(order, [low, high], btype="bandpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, cutoff, order=4):
    sos = signal.butter(order, cutoff, btype="lowpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, cutoff, order=4):
    sos = signal.butter(order, cutoff, btype="highpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, x)


def sweep_bandpass(x, centres, q=1.6, block=256):
    """Band-pass whose centre frequency follows ``centres`` (sampled per block) - a moving resonance."""
    out = np.zeros_like(x)
    zi = None
    for start in range(0, len(x), block):
        c = float(np.interp(start / max(len(x) - 1, 1), np.linspace(0, 1, len(centres)), centres))
        low, high = c / (1 + 1 / (2 * q)), c * (1 + 1 / (2 * q))
        sos = signal.butter(2, [max(low, 30.0), min(high, RATE / 2 - 100)], btype="bandpass", fs=RATE, output="sos")
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        out[start:start + block], zi = signal.sosfilt(sos, x[start:start + block], zi=zi)
    return out


def env_ad(seconds, attack, decay, curve=1.0):
    t = t_axis(seconds)
    rise = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    fall = np.exp(-np.maximum(t - attack, 0.0) / max(decay, 1e-4)) ** curve
    return rise * fall


def tone(freqs, seconds, wave="sine"):
    """Oscillator following a frequency curve (list of points spread over the duration)."""
    t = t_axis(seconds)
    f = np.interp(t / seconds, np.linspace(0, 1, len(freqs)), freqs)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    if wave == "saw":
        return 2.0 * ((phase / (2 * np.pi)) % 1.0) - 1.0
    return np.sin(phase)


def place(dest, src, at, gain=1.0):
    i = int(at * RATE)
    n = min(len(src), len(dest) - i)
    if n > 0:
        dest[i:i + n] += src[:n] * gain


def normalize(x, peak=0.84):
    x = x - np.mean(x)
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def soft_clip(x, drive=1.4):
    return np.tanh(x * drive) / np.tanh(drive)


def fade_edges(x, fade_in=0.002, fade_out=0.03):
    n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
    x = x.copy()
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def seamless(x, loop_seconds, cross_seconds):
    """Cut a loop of ``loop_seconds`` and cross-fade its tail into its head (equal power)."""
    n, f = int(loop_seconds * RATE), int(cross_seconds * RATE)
    head = x[:n].copy()
    tail = x[n:n + f]
    k = np.linspace(0, np.pi / 2, f)
    head[:f] = head[:f] * np.sin(k) + tail * np.cos(k)
    return head


# ------------------------------------------------------------------------------------------------
# sound parts
# ------------------------------------------------------------------------------------------------

def thud(rng, seconds=0.18, pitch=70.0, weight=1.0):
    """Body / boot impact: a pitched-down sine thump plus a short filtered noise knock."""
    body = tone([pitch * 1.8, pitch, pitch * 0.8], seconds) * env_ad(seconds, 0.002, seconds * 0.22)
    knock = lowpass(noise(rng, seconds), 900) * env_ad(seconds, 0.001, 0.025)
    return body * weight + knock * 0.6


def click(rng, seconds=0.012, bright=6000.0):
    """Mechanical valve / latch click."""
    return highpass(noise(rng, seconds), bright * 0.5) * env_ad(seconds, 0.0003, 0.002)


def rattle(rng, seconds=0.25, hits=7):
    """Gear and buckles settling: a few small metallic ticks, each a ringing high band."""
    out = np.zeros(int(RATE * seconds))
    for _ in range(hits):
        at = rng.uniform(0.0, seconds * 0.6)
        f = rng.uniform(2500, 6500)
        tick = bandpass(noise(rng, 0.04), f * 0.85, f * 1.15, order=2) * env_ad(0.04, 0.0005, rng.uniform(0.004, 0.012))
        place(out, tick, at, rng.uniform(0.25, 0.7))
    return out


def jet(rng, seconds, centres, q=1.3, decay=None, attack=0.004):
    """Thruster jet: noise through a sweeping resonance, the airy core of every jumpkit burst."""
    x = sweep_bandpass(noise(rng, seconds), centres, q=q)
    x += 0.35 * highpass(noise(rng, seconds), 4000) * env_ad(seconds, 0.001, 0.05)
    env = env_ad(seconds, attack, decay if decay is not None else seconds * 0.3)
    return x * env


def whine(seconds, freqs, level=0.12):
    """Faint turbine / jet whine riding the burst."""
    return tone(freqs, seconds) * env_ad(seconds, 0.01, seconds * 0.4) * level


def footstep(rng, hard=1.0):
    """Quick light boot step on a hard surface: small thump, gritty scuff, a tick."""
    step = thud(rng, 0.09, rng.uniform(95, 130), 0.55 * hard)
    scuff = bandpass(noise(rng, 0.09), 1200, 5000) * env_ad(0.09, 0.002, 0.018)
    step[:len(scuff)] += scuff * 0.45
    step[:len(click(rng))] += click(rng) * 0.25
    return step


# ------------------------------------------------------------------------------------------------
# sounds
# ------------------------------------------------------------------------------------------------

def double_jump(seed):
    """The jumpkit's signature: valve click, hard 'fwump' of ignition, jet roar sweeping down, hiss tail."""
    rng = np.random.default_rng(seed)
    d = 0.75
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.93, 1.07)
    place(out, click(rng), 0.0, 0.9)
    # Ignition: low concussive thump.
    place(out, tone([95 * p, 48 * p], 0.22) * env_ad(0.22, 0.002, 0.06), 0.004, 1.1)
    # Jet burst: resonance sweeping 3.4 kHz -> 700 Hz, the 'fwoosh'.
    place(out, jet(rng, 0.55, [3400 * p, 2200 * p, 1100 * p, 700 * p], q=1.2, decay=0.14), 0.006, 1.0)
    # Broad body of the burst under it.
    place(out, lowpass(noise(rng, 0.4), 1400) * env_ad(0.4, 0.003, 0.08), 0.006, 0.7)
    # Whine riding the burst, and a long airy tail.
    place(out, whine(0.5, [2100 * p, 1300 * p], 0.08), 0.01)
    place(out, highpass(noise(rng, 0.6), 2500) * env_ad(0.6, 0.02, 0.18) * 0.12, 0.05)
    return fade_edges(normalize(soft_clip(out, 1.6)))


def jump(seed):
    """A normal jump with the kit: boot push, cloth and gear, a small pneumatic assist puff."""
    rng = np.random.default_rng(seed)
    d = 0.4
    out = np.zeros(int(RATE * d))
    place(out, thud(rng, 0.14, rng.uniform(80, 100), 0.8), 0.0)
    place(out, bandpass(noise(rng, 0.2), 300, 2500) * env_ad(0.2, 0.01, 0.05) * 0.35, 0.0)
    place(out, jet(rng, 0.22, [2600, 1500], q=1.5, decay=0.05), 0.012, 0.45)
    place(out, rattle(rng, 0.25, 4), 0.03, 0.5)
    return fade_edges(normalize(soft_clip(out)))


def walljump(seed):
    """Kick off the wall (impact + scuff) and a sharp thrust burst, shorter than the double jump."""
    rng = np.random.default_rng(seed)
    d = 0.55
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.95, 1.08)
    place(out, thud(rng, 0.16, 85 * p, 1.0), 0.0)
    place(out, bandpass(noise(rng, 0.12), 900, 4500) * env_ad(0.12, 0.002, 0.03) * 0.6, 0.0)
    place(out, click(rng), 0.012, 0.7)
    place(out, jet(rng, 0.4, [3800 * p, 1900 * p, 900 * p], q=1.25, decay=0.09), 0.015, 0.95)
    place(out, tone([80 * p, 45 * p], 0.15) * env_ad(0.15, 0.002, 0.04), 0.016, 0.7)
    return fade_edges(normalize(soft_clip(out, 1.5)))


def land(seed):
    """Heavy landing: a deep boot impact, knee-flex grunt of the frame, and the kit's gear rattling."""
    rng = np.random.default_rng(seed)
    d = 0.6
    out = np.zeros(int(RATE * d))
    place(out, thud(rng, 0.3, rng.uniform(55, 68), 1.3), 0.0)
    place(out, thud(rng, 0.12, rng.uniform(110, 140), 0.5), 0.012)
    place(out, bandpass(noise(rng, 0.25), 200, 1800) * env_ad(0.25, 0.002, 0.05) * 0.5, 0.0)
    place(out, rattle(rng, 0.4, 9), 0.02, 0.8)
    # Pneumatic settle: a soft exhale from the kit as it takes the load.
    place(out, jet(rng, 0.3, [1500, 900], q=1.5, decay=0.08) * 0.35, 0.05)
    return fade_edges(normalize(soft_clip(out, 1.3)))


def wallrun_start(seed=11):
    """Catching the wall: a grip scuff, a boot plant and the thruster lighting up with a rising hiss."""
    rng = np.random.default_rng(seed)
    d = 0.6
    out = np.zeros(int(RATE * d))
    place(out, bandpass(noise(rng, 0.18), 1000, 6000) * env_ad(0.18, 0.003, 0.05) * 0.8, 0.0)
    place(out, footstep(rng, 1.2), 0.01)
    place(out, click(rng), 0.03, 0.6)
    rise = sweep_bandpass(noise(rng, 0.55), [900, 1800, 2400], q=1.4) * env_ad(0.55, 0.12, 0.25)
    place(out, rise, 0.03, 0.8)
    place(out, whine(0.5, [900, 1500], 0.07), 0.04)
    return fade_edges(normalize(soft_clip(out)))


def wallrun_loop(seed=12):
    """Running along the wall: quick light footsteps over a steady, slightly breathing thruster hiss."""
    rng = np.random.default_rng(seed)
    loop, cross = 1.6, 0.12
    d = loop + cross + 0.2
    out = np.zeros(int(RATE * d))
    # Thruster: band-limited noise with a slow breathing swell, plus a faint steady whine.
    hiss = bandpass(noise(rng, d), 1100, 4200, order=3)
    breathe = 0.8 + 0.2 * np.sin(2 * np.pi * t_axis(d) * (2.5 / loop))
    out += hiss * breathe * 0.32
    out += lowpass(noise(rng, d), 300) * 0.25
    out += np.sin(2 * np.pi * 1320.0 * t_axis(d)) * 0.018
    # Footsteps: 10 per loop (6.25 a second), alternating feet, slight timing jitter.
    steps = 10
    for k in range(steps + 2):
        at = k * loop / steps + rng.uniform(-0.006, 0.006)
        if at < d - 0.1:
            place(out, footstep(rng, 1.0 if k % 2 == 0 else 0.8), max(at, 0.0), 0.9)
    return normalize(seamless(soft_clip(out, 1.2), loop, cross), 0.8)


def slide_start(seed=21):
    """Dropping into a slide: a whoosh of air and gear, then the scrape biting in."""
    rng = np.random.default_rng(seed)
    d = 0.7
    out = np.zeros(int(RATE * d))
    place(out, sweep_bandpass(noise(rng, 0.45), [600, 2400, 1500], q=1.1) * env_ad(0.45, 0.04, 0.12) * 0.8, 0.0)
    place(out, thud(rng, 0.16, 70, 0.9), 0.06)
    scrape = bandpass(noise(rng, 0.6), 500, 3500) * env_ad(0.6, 0.02, 0.25)
    grain = 0.6 + 0.4 * np.abs(lowpass(noise(rng, 0.6), 40)) / 1.5
    place(out, scrape * grain * 0.9, 0.06)
    place(out, rattle(rng, 0.3, 5), 0.07, 0.5)
    return fade_edges(normalize(soft_clip(out)))


def slide_loop(seed=22):
    """Sliding: gritty scrape with uneven grain, cloth friction and a low rumble from the ground."""
    rng = np.random.default_rng(seed)
    loop, cross = 2.0, 0.15
    d = loop + cross + 0.1
    scrape = bandpass(noise(rng, d), 450, 3800, order=3)
    grain = 0.55 + 0.45 * np.clip(np.abs(lowpass(noise(rng, d), 35)) / 1.2, 0, 1)
    grit = highpass(noise(rng, d), 5000) * (np.abs(lowpass(noise(rng, d), 80)) > 1.4) * 0.5
    rumble = lowpass(noise(rng, d), 160) * 0.9
    cloth = bandpass(noise(rng, d), 200, 900) * 0.25
    out = scrape * grain * 0.7 + grit + rumble + cloth
    return normalize(seamless(soft_clip(out, 1.2), loop, cross), 0.75)


# ------------------------------------------------------------------------------------------------
# grapple
# ------------------------------------------------------------------------------------------------

def tail(x, fade=0.02):
    """Fades a part's end to silence, so cutting it to length never clicks."""
    return fade_edges(x, 0.0005, fade)


def metal_ring(rng, seconds, partials, decay):
    """A struck metal part: a few inharmonic partials, each decaying at its own rate."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for f, gain, tau in partials:
        f = f * rng.uniform(0.985, 1.015)
        out += np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / (decay * tau)) * gain
    return tail(out, seconds * 0.3)


def zip_line(rng, seconds, start, end, rough=0.5):
    """Cable running through the launcher: a fast whir whose pitch sweeps, with a rough braided buzz."""
    t = t_axis(seconds)
    whir = tone([start, (start + end) / 2, end], seconds, "saw")
    whir = bandpass(whir, 300, 6500, order=2)
    buzz = sweep_bandpass(noise(rng, seconds), [start * 3, end * 3], q=2.5)
    flutter = 0.7 + 0.3 * np.sin(2 * np.pi * np.interp(t, [0, seconds], [start / 6, end / 6]) * t)
    return tail((whir * 0.35 + buzz * rough) * flutter, seconds * 0.25)


def grapple_fire(seed):
    """The launch: a pneumatic punch with a hard mechanical clack, then the line whipping out of the spool."""
    rng = np.random.default_rng(seed)
    d = 0.7
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.95, 1.05)
    place(out, click(rng, 0.015, 7000), 0.0, 1.0)
    # Pneumatic punch: a low thump and a short, sharp air burst.
    place(out, tail(tone([150 * p, 60 * p], 0.16) * env_ad(0.16, 0.001, 0.035)), 0.002, 1.2)
    place(out, jet(rng, 0.18, [5200 * p, 2600 * p], q=1.4, decay=0.03, attack=0.001), 0.002, 0.9)
    # Mechanical clack of the breech and a short metallic ring.
    place(out, metal_ring(rng, 0.25, [(1850 * p, 0.5, 1.0), (3120 * p, 0.35, 0.7), (5230 * p, 0.25, 0.45)], 0.05), 0.004, 0.55)
    # Line paying out: a whir that starts high and drops as the spool winds down.
    place(out, zip_line(rng, 0.42, 900 * p, 380 * p) * env_ad(0.42, 0.01, 0.2), 0.03, 0.8)
    return fade_edges(normalize(soft_clip(out, 1.5)))


def grapple_impact(seed):
    """The hook biting: a bright metallic strike, a heavy thunk and grit from the surface."""
    rng = np.random.default_rng(seed)
    d = 0.6
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.94, 1.06)
    place(out, click(rng, 0.01, 9000), 0.0, 1.0)
    place(out, metal_ring(rng, 0.5, [(1420 * p, 0.6, 1.0), (2380 * p, 0.45, 0.8), (3990 * p, 0.35, 0.55), (6100 * p, 0.2, 0.35)], 0.09), 0.0, 0.7)
    place(out, tail(thud(rng, 0.22, 85 * p, 1.1)), 0.0)
    place(out, bandpass(noise(rng, 0.2), 1500, 7000) * env_ad(0.2, 0.001, 0.03), 0.002, 0.6)
    place(out, rattle(rng, 0.3, 6), 0.02, 0.45)
    # The line snapping taut behind the hook: a short, low twang.
    place(out, tone([210 * p, 160 * p], 0.25, "saw") * env_ad(0.25, 0.004, 0.06), 0.03, 0.18)
    return fade_edges(normalize(soft_clip(out, 1.4)))


def grapple_reel_loop(seed=61):
    """The winch reeling the pilot in: a straining motor whine with gear chatter and the line hissing through."""
    rng = np.random.default_rng(seed)
    loop, cross = 1.2, 0.1
    d = loop + cross + 0.1
    t = t_axis(d)
    # Motor: fundamental and harmonics, with a slow wobble so it strains rather than drones.
    wobble = 1.0 + 0.012 * np.sin(2 * np.pi * (2.5 / loop) * t)
    motor = sum(np.sin(2 * np.pi * f * wobble * t) * g for f, g in ((235, 0.5), (470, 0.35), (705, 0.18), (1410, 0.1)))
    # Gear chatter: a fast train of small ticks (an exact number per loop, so it loops cleanly).
    chatter = np.zeros_like(t)
    rate = round(36 * loop) / loop
    for k in range(int(d * rate) + 1):
        place(chatter, bandpass(noise(rng, 0.01), 2500, 6000, order=2) * env_ad(0.01, 0.0003, 0.002), k / rate, rng.uniform(0.5, 1.0))
    hiss = bandpass(noise(rng, d), 2000, 7000, order=3) * (0.75 + 0.25 * np.sin(2 * np.pi * (5 / loop) * t))
    rumble = lowpass(noise(rng, d), 180)
    out = motor * 0.32 + chatter * 0.3 + hiss * 0.22 + rumble * 0.25
    return normalize(seamless(soft_clip(out, 1.3), loop, cross), 0.78)


def grapple_release(seed):
    """Letting go: the hook ripping free, the line zipping back in and the hook seating with a latch."""
    rng = np.random.default_rng(seed)
    d = 0.5
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.95, 1.05)
    place(out, bandpass(noise(rng, 0.08), 1200, 6000) * env_ad(0.08, 0.001, 0.015), 0.0, 0.5)
    place(out, zip_line(rng, 0.26, 420 * p, 1300 * p, 0.6) * env_ad(0.26, 0.02, 0.12), 0.0, 0.8)
    # Hook seating in the muzzle: click-clack.
    place(out, click(rng, 0.012, 6500), 0.27, 0.9)
    place(out, metal_ring(rng, 0.15, [(2600 * p, 0.4, 1.0), (4400 * p, 0.25, 0.6)], 0.03), 0.27, 0.5)
    place(out, click(rng, 0.012, 5000), 0.31, 0.6)
    place(out, thud(rng, 0.08, 140 * p, 0.35), 0.27)
    return fade_edges(normalize(soft_clip(out, 1.3)))


def grapple_ready():
    """Meter refilled: a soft rising two-note chime with a little digital shimmer."""
    d = 0.55
    out = np.zeros(int(RATE * d))
    for at, f in ((0.0, 1318.5), (0.085, 1975.5)):
        note = tone([f, f], 0.4) * env_ad(0.4, 0.004, 0.12)
        note += tone([f * 2, f * 2], 0.4) * env_ad(0.4, 0.002, 0.05) * 0.25
        place(out, tail(note, 0.1), at, 0.5)
    t = t_axis(d)
    out += np.sin(2 * np.pi * 3950 * t) * env_ad(d, 0.1, 0.1) * 0.04
    return fade_edges(normalize(out, 0.6), 0.002, 0.08)


def grapple_denied():
    """Key pressed with no charge: two short, flat low beeps."""
    d = 0.32
    out = np.zeros(int(RATE * d))
    for at in (0.0, 0.14):
        beep = lowpass(np.sign(tone([330, 330], 0.1)), 2400) * env_ad(0.1, 0.003, 0.05)
        place(out, tail(beep, 0.02), at, 0.5)
    return fade_edges(normalize(out, 0.55))


# ------------------------------------------------------------------------------------------------
# stim
# ------------------------------------------------------------------------------------------------

def heartbeat(rng, strength=1.0):
    """One heartbeat, lub-dub: two low, chesty thumps 0.13 s apart, the second lighter."""
    d = 0.4
    out = np.zeros(int(RATE * d))
    for at, gain, pitch in ((0.0, 1.0, 58.0), (0.13, 0.7, 66.0)):
        thump = tone([pitch * 1.4, pitch, pitch * 0.8], 0.16) * env_ad(0.16, 0.004, 0.045)
        thump += lowpass(noise(rng, 0.16), 220) * env_ad(0.16, 0.002, 0.03) * 0.5
        place(out, tail(thump), at, gain * strength)
    return out


def stim_activate(seed):
    """The jab: the autoinjector's click, the hiss of the dose going in, then a rising rush of adrenaline -
    a deep swell, a climbing synth tone and the first hard heartbeat."""
    rng = np.random.default_rng(seed)
    d = 1.3
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    place(out, click(rng, 0.012, 8000), 0.0, 1.0)
    place(out, metal_ring(rng, 0.12, [(3300 * p, 0.4, 1.0), (5200 * p, 0.25, 0.6)], 0.025), 0.0, 0.5)
    # The injection: a short, hard pneumatic hiss.
    hiss = bandpass(noise(rng, 0.32), 2500, 9000, order=3) * env_ad(0.32, 0.004, 0.09)
    place(out, tail(hiss), 0.01, 0.9)
    # The rush: a low swell and a tone climbing an octave and a half, with a shimmer on top.
    t = t_axis(1.1)
    swell = np.clip(t / 0.35, 0, 1) * np.exp(-np.maximum(t - 0.45, 0) / 0.3)
    rush = sweep_bandpass(noise(rng, 1.1), [180 * p, 900 * p, 1600 * p], q=1.3) * swell
    climb = tone([220 * p, 420 * p, 640 * p], 1.1, "saw")
    climb = lowpass(climb, 2400) * swell * 0.22
    shimmer = tone([2600 * p, 3900 * p], 1.1) * swell * 0.05
    place(out, tail(rush * 0.9 + climb + shimmer, 0.2), 0.08)
    place(out, heartbeat(rng, 1.2), 0.18, 1.0)
    return fade_edges(normalize(soft_clip(out, 1.4)))


def stim_loop(seed=81):
    """While stimmed: a racing heart (150 bpm), blood rushing in the ears and a thin ringing over it all."""
    rng = np.random.default_rng(seed)
    loop, cross = 1.6, 0.12
    d = loop + cross + 0.4
    t = t_axis(d)
    out = np.zeros(len(t))
    beats = 4  # 4 beats per 1.6 s loop = 150 bpm
    for k in range(beats + 2):
        place(out, heartbeat(rng, 0.9), k * loop / beats, 1.0)
    # Blood rush: low filtered noise swelling with each beat.
    pulse = 0.6 + 0.4 * np.cos(2 * np.pi * (beats / loop) * t) ** 2
    out += lowpass(noise(rng, d), 350) * pulse * 0.35
    out += bandpass(noise(rng, d), 600, 2200, order=2) * pulse * 0.06
    # Tinnitus-like ringing, wavering slightly.
    out += np.sin(2 * np.pi * 5200 * t + 0.6 * np.sin(2 * np.pi * (2 / loop) * t)) * 0.012
    return normalize(seamless(soft_clip(out, 1.2), loop, cross), 0.75)


def stim_deactivate(seed=91):
    """Coming down: the rush draining away - a tone sliding down, the heart slowing, a long exhale."""
    rng = np.random.default_rng(seed)
    d = 1.6
    out = np.zeros(int(RATE * d))
    t = t_axis(1.4)
    fall = np.exp(-t / 0.6)
    slide = lowpass(tone([640, 380, 160], 1.4, "saw"), 1800) * fall * 0.25
    whoosh = sweep_bandpass(noise(rng, 1.4), [1500, 700, 250], q=1.3) * fall * 0.6
    place(out, tail(slide + whoosh, 0.3), 0.0)
    place(out, heartbeat(rng, 0.8), 0.05, 1.0)
    place(out, heartbeat(rng, 0.6), 0.6, 1.0)
    breath = bandpass(noise(rng, 0.9), 400, 2500, order=2) * env_ad(0.9, 0.15, 0.3)
    place(out, tail(breath, 0.2), 0.45, 0.35)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.7), 0.002, 0.15)


def electric(rng, seconds, density=60.0, bright=4500.0):
    """Electric crackle: a sparse train of tiny sharp clicks, each a burst of bright noise, at random spacing."""
    out = np.zeros(int(RATE * seconds))
    n = int(density * seconds)
    for _ in range(n):
        at = rng.uniform(0, seconds - 0.01)
        length = rng.uniform(0.002, 0.008)
        burst = highpass(noise(rng, length), bright, order=2) * env_ad(length, 0.0005, length * 0.4)
        place(out, burst, at, rng.uniform(0.2, 1.0))
    return out


def reverse_swell(rng, seconds, centres, q=1.4):
    """A swell that builds to a hard stop (a reversed decay): the rush of something being pulled away."""
    t = t_axis(seconds)
    env = (t / seconds) ** 2.6
    return sweep_bandpass(noise(rng, seconds), centres, q=q) * env


def phase_activate(seed):
    """Phasing out: the coil charging with a quick electric whine and crackle, a reversed rush sucking the
    pilot out of the world, a hollow 'whumm' with a pitch-bent tone falling through it, and a ghostly tail."""
    rng = np.random.default_rng(seed)
    d = 1.5
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    # Coil charge: a whine sweeping up over 0.16 s with crackle on it.
    charge = tone([900 * p, 2600 * p, 5200 * p], 0.18, "saw")
    charge = bandpass(charge, 800, 7000, order=2) * np.linspace(0.2, 1.0, len(charge)) * 0.25
    place(out, tail(charge, 0.01), 0.0)
    place(out, electric(rng, 0.2, 140, 3500), 0.0, 0.45)
    # The pull: a reversed swell into the moment of the shift.
    place(out, reverse_swell(rng, 0.2, [400 * p, 1800 * p, 6000 * p]), 0.0, 0.9)
    # The shift: a deep hollow 'whumm' (detuned, ring-modulated) with a falling tone through it.
    t = t_axis(1.25)
    body = np.sin(2 * np.pi * 52 * p * t + 3.0 * np.sin(2 * np.pi * 7 * t)) * np.exp(-t / 0.35)
    fall = tone([1400 * p, 600 * p, 180 * p], 1.25, "saw")
    fall = lowpass(fall * np.sin(2 * np.pi * 31 * t), 2600) * np.exp(-t / 0.4) * 0.35
    rush = sweep_bandpass(noise(rng, 1.25), [5000 * p, 1500 * p, 300 * p], q=1.2) * np.exp(-t / 0.3)
    place(out, tail(body * 0.9 + fall + rush * 0.7, 0.3), 0.2)
    place(out, thud(rng, 0.25, 48, 1.0), 0.2, 0.7)
    # A ghostly, chorused shimmer hanging after.
    shimmer = (tone([2100 * p, 1700 * p], 1.25) + tone([2113 * p, 1690 * p], 1.25)) * np.exp(-t / 0.5) * 0.05
    place(out, tail(shimmer, 0.3), 0.22)
    return fade_edges(normalize(soft_clip(out, 1.5)))


def phase_loop(seed=1011):
    """While phased: a hollow, beating drone, a slow airy whoosh moving through it and a faint crackle -
    the world heard from outside it."""
    rng = np.random.default_rng(seed)
    loop, cross = 2.0, 0.2
    d = loop + cross + 0.4
    t = t_axis(d)
    # Drone: two close low tones beating against each other, with a quiet fifth above.
    drone = np.sin(2 * np.pi * 73.4 * t) + np.sin(2 * np.pi * 74.9 * t) + 0.35 * np.sin(2 * np.pi * 110.2 * t)
    drone = drone * (0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t))
    # Hollow air: noise through a slowly swinging resonance.
    air = sweep_bandpass(noise(rng, d), [500, 1300, 700, 1600, 600], q=2.2) * 0.5
    # Faint electric fizz.
    fizz = electric(rng, d, 25, 5000) * 0.25
    out = drone * 0.22 + air + fizz
    return normalize(seamless(soft_clip(out, 1.2), loop, cross), 0.7)


def phase_warning(seed=1021):
    """The way back opening: a tone and a rush rising over a second and a half, tightening toward the exit."""
    rng = np.random.default_rng(seed)
    d = 1.5
    t = t_axis(d)
    env = np.clip(t / 0.15, 0, 1) * (0.35 + 0.65 * (t / d) ** 1.5)
    rise = tone([180, 420, 1100], d, "saw")
    rise = lowpass(rise, 3000) * env * 0.3
    swell = sweep_bandpass(noise(rng, d), [300, 1200, 4500], q=1.6) * env * 0.6
    # A tremolo that speeds up toward the end.
    tremolo = 0.65 + 0.35 * np.sin(2 * np.pi * np.cumsum(np.linspace(6, 22, len(t))) / RATE)
    out = (rise + swell) * tremolo
    return fade_edges(normalize(soft_clip(out, 1.2), 0.75), 0.01, 0.02)


def phase_end(seed):
    """Rematerialising: a hard reversed suck snapping into a bright electric crack, a punchy low impact and
    a quick sparkling decay as the pilot drops back into the world."""
    rng = np.random.default_rng(seed)
    d = 1.0
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    place(out, reverse_swell(rng, 0.12, [6000 * p, 1500 * p, 400 * p], q=1.2), 0.0, 1.0)
    crack = highpass(noise(rng, 0.05), 1800, order=2) * env_ad(0.05, 0.0005, 0.012)
    place(out, crack, 0.12, 1.0)
    place(out, thud(rng, 0.3, 62 * p, 1.2), 0.12, 0.9)
    place(out, metal_ring(rng, 0.5, [(1900 * p, 0.25, 1.0), (3050 * p, 0.15, 0.7), (4700 * p, 0.08, 0.5)], 0.08), 0.12, 0.35)
    place(out, electric(rng, 0.45, 90, 4000) * np.exp(-t_axis(0.45) / 0.15), 0.13, 0.6)
    return fade_edges(normalize(soft_clip(out, 1.5)))


def telefrag(seed=1041):
    """A telefrag: a wet, crunching burst with a deep body hit under it."""
    rng = np.random.default_rng(seed)
    d = 0.7
    out = np.zeros(int(RATE * d))
    crunch = bandpass(noise(rng, 0.25), 300, 4000, order=2) * env_ad(0.25, 0.001, 0.06)
    crunch *= 0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 37 * t_axis(0.25)))
    place(out, crunch, 0.0, 1.0)
    place(out, thud(rng, 0.4, 45, 1.4), 0.0, 1.0)
    squelch = lowpass(noise(rng, 0.3), 700) * env_ad(0.3, 0.01, 0.08)
    place(out, squelch, 0.05, 0.6)
    return fade_edges(normalize(soft_clip(out, 1.8)))


def pulse_draw(seed):
    """Unsheathing: a quick bright metal slide ('shhing') off the sheath's lip and the kunai's arming click."""
    rng = np.random.default_rng(seed)
    d = 0.5
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    slide = sweep_bandpass(noise(rng, 0.22), [2500 * p, 5500 * p, 8000 * p], q=3.0) * env_ad(0.22, 0.03, 0.08)
    place(out, tail(slide), 0.0, 0.8)
    place(out, metal_ring(rng, 0.35, [(3900 * p, 0.18, 1.0), (6100 * p, 0.1, 0.6), (8300 * p, 0.06, 0.3)], 0.1), 0.17, 0.45)
    place(out, click(rng, 0.01, 7000), 0.3, 0.7)
    place(out, tone([2200 * p, 2200 * p], 0.05) * env_ad(0.05, 0.002, 0.02) * 0.25, 0.32)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.75))


def pulse_throw(seed):
    """The throw: a hard, fast whip of air with the kunai's spin fluttering through it."""
    rng = np.random.default_rng(seed)
    d = 0.5
    t = t_axis(d)
    p = rng.uniform(0.95, 1.05)
    env = env_ad(d, 0.02, 0.1)
    whip = sweep_bandpass(noise(rng, d), [900 * p, 3800 * p, 1500 * p], q=1.8) * env
    flutter = 0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 38 * t))
    out = whip * flutter
    out += bandpass(noise(rng, d), 150, 500, order=2) * env_ad(d, 0.005, 0.05) * 0.4
    return fade_edges(normalize(soft_clip(out, 1.3), 0.8))


def pulse_impact(seed):
    """The kunai biting in: a hard thunk, a short metallic twang of the blade, grit, and the emitter arming."""
    rng = np.random.default_rng(seed)
    d = 0.8
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    place(out, thud(rng, 0.2, 95 * p, 1.0), 0.0, 1.0)
    place(out, bandpass(noise(rng, 0.05), 1500, 6000, order=2) * env_ad(0.05, 0.001, 0.012), 0.0, 0.9)
    twang = metal_ring(rng, 0.45, [(1250 * p, 0.25, 1.0), (2780 * p, 0.15, 0.5), (4100 * p, 0.08, 0.3)], 0.08)
    place(out, twang * (1.0 + 0.4 * np.sin(2 * np.pi * 26 * t_axis(0.45))), 0.005, 0.45)
    place(out, bandpass(noise(rng, 0.15), 300, 3000, order=2) * env_ad(0.15, 0.005, 0.05), 0.01, 0.35)
    for k in range(2):
        beep = tone([1750, 1750], 0.06) * env_ad(0.06, 0.003, 0.025)
        place(out, beep, 0.2 + k * 0.1, 0.22)
    return fade_edges(normalize(soft_clip(out, 1.4)))


def pulse_ping(seed=1201):
    """A sonar pulse: a deep, round 'whoom' swelling out, a clean high ping on top, and its echo rolling away."""
    rng = np.random.default_rng(seed)
    d = 1.6
    out = np.zeros(int(RATE * d))
    t = t_axis(1.5)
    boom = np.sin(2 * np.pi * (70 * t + 40 * (1 - np.exp(-t / 0.08)) * 0.08)) * np.exp(-t / 0.35)
    boom *= np.clip(t / 0.01, 0, 1)
    swell = sweep_bandpass(noise(rng, 1.5), [200, 700, 1800, 600], q=2.0) * np.exp(-t / 0.3) * 0.6
    ping = (np.sin(2 * np.pi * 1480 * t) + 0.35 * np.sin(2 * np.pi * 2960 * t)) * np.exp(-t / 0.18) * 0.4
    place(out, tail(boom + swell + ping, 0.3), 0.0)
    # The echo: the ping again, softer and darker, twice.
    for k, (delay, gain) in enumerate(((0.32, 0.35), (0.66, 0.15))):
        place(out, tail(lowpass(ping, 2500 - 800 * k), 0.3), delay, gain)
    return fade_edges(normalize(soft_clip(out, 1.3), 0.85), 0.002, 0.1)


def pulse_detected(seed=1211):
    """Revealed: two sharp, harsh alarm beeps under a rising sweep - someone's sonar has you."""
    rng = np.random.default_rng(seed)
    d = 0.6
    out = np.zeros(int(RATE * d))
    for k in range(2):
        beep = tone([1320, 1320], 0.09, "saw")
        beep = lowpass(beep, 5000) * env_ad(0.09, 0.003, 0.04) * 0.6
        place(out, beep, 0.02 + k * 0.16, 1.0)
    sweep = tone([400, 1100], 0.45) * env_ad(0.45, 0.05, 0.15) * 0.15
    place(out, sweep, 0.0, 1.0)
    place(out, bandpass(noise(rng, 0.4), 2000, 6000, order=2) * env_ad(0.4, 0.01, 0.1) * 0.1, 0.0)
    return fade_edges(normalize(soft_clip(out, 1.3), 0.75))


def shimmer(rng, seconds, low, high, voices=6, rise=True):
    """Glassy shimmer: a cluster of detuned high partials, each sliding a little and trembling."""
    t = t_axis(seconds)
    out = np.zeros(len(t))
    for _ in range(voices):
        f0 = rng.uniform(low, high)
        f1 = f0 * (rng.uniform(1.05, 1.25) if rise else rng.uniform(0.8, 0.95))
        f = np.linspace(f0, f1, len(t))
        trem = 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(9, 17) * t + rng.uniform(0, 6.28))
        out += np.sin(2 * np.pi * np.cumsum(f) / RATE + rng.uniform(0, 6.28)) * trem
    return out / voices


def cloak_activate(seed):
    """Cloaking: an airy rush drawn in, a glassy shimmer climbing over it, and the field closing with a soft
    low 'thoom' and a fading crystalline tail."""
    rng = np.random.default_rng(seed)
    d = 1.4
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    place(out, reverse_swell(rng, 0.3, [600 * p, 2400 * p, 7000 * p], q=1.6), 0.0, 0.7)
    t = t_axis(1.1)
    env = np.clip(t / 0.25, 0, 1) * np.exp(-np.maximum(t - 0.3, 0) / 0.35)
    place(out, tail(shimmer(rng, 1.1, 2200 * p, 4200 * p) * env * 0.6, 0.2), 0.05)
    thoom = tone([140 * p, 70 * p], 0.5) * env_ad(0.5, 0.01, 0.18) * 0.6
    place(out, thoom, 0.28, 1.0)
    air = bandpass(noise(rng, 1.0), 3000, 9000, order=2) * np.exp(-t_axis(1.0) / 0.3) * 0.15
    place(out, tail(air, 0.2), 0.3)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.75))


def cloak_loop(seed=1311):
    """Cloaked: a faint, airy, slowly beating hum with a far-off glassy shimmer - heard only by the pilot."""
    rng = np.random.default_rng(seed)
    loop, cross = 3.0, 0.3
    d = loop + cross + 0.4
    t = t_axis(d)
    hum = (np.sin(2 * np.pi * 220 * t) + np.sin(2 * np.pi * 221.3 * t)) * 0.12
    air = sweep_bandpass(noise(rng, d), [1500, 3200, 2000, 3600, 1800], q=3.0) * 0.4
    glass = shimmer(rng, d, 3000, 5200, voices=4) * 0.08
    out = (hum + air + glass) * (0.8 + 0.2 * np.sin(2 * np.pi * (1 / loop) * t))
    return normalize(seamless(soft_clip(out, 1.1), loop, cross), 0.6)


def cloak_warning(seed=1321):
    """Running out: the shimmer stuttering - three quick glassy flutters, each a little lower."""
    rng = np.random.default_rng(seed)
    d = 1.0
    out = np.zeros(int(RATE * d))
    for k in range(3):
        seg = shimmer(rng, 0.18, 2600 - 300 * k, 3800 - 400 * k, voices=4) * env_ad(0.18, 0.01, 0.06)
        place(out, seg, 0.05 + k * 0.22, 1.0 - 0.15 * k)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.7))


def cloak_end(seed):
    """Decloaking: the shimmer falling apart downward, a short static tear and the air rushing back in."""
    rng = np.random.default_rng(seed)
    d = 1.0
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    t = t_axis(0.8)
    place(out, tail(shimmer(rng, 0.8, 2600 * p, 4400 * p, rise=False) * np.exp(-t / 0.25) * 0.6, 0.2), 0.0)
    tear = bandpass(noise(rng, 0.12), 1500, 8000, order=2) * env_ad(0.12, 0.002, 0.04)
    tear *= 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 60 * t_axis(0.12)))
    place(out, tear, 0.0, 0.7)
    place(out, tail(sweep_bandpass(noise(rng, 0.6), [6000 * p, 1500 * p, 500 * p], q=1.4) * np.exp(-t_axis(0.6) / 0.2)), 0.02, 0.6)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.75))


def bitcrush(x, levels=16, hold=6):
    """Digital grit: the signal held for ``hold`` samples at a time and rounded to ``levels`` steps."""
    held = np.repeat(x[::hold], hold)[:len(x)]
    return np.round(held * levels) / levels


def glitch_ticks(rng, seconds, rate, bright=5000.0):
    """A run of tiny digital clicks at about ``rate`` a second, each a few samples of filtered noise."""
    n = int(RATE * seconds)
    out = np.zeros(n)
    t = 0.0
    while True:
        t += rng.exponential(1.0 / rate)
        i = int(t * RATE)
        if i >= n - 200:
            break
        burst = rng.standard_normal(rng.integers(30, 160)) * rng.uniform(0.3, 1.0)
        out[i:i + len(burst)] += burst
    return highpass(out, bright, order=2)


def holo_deploy(seed):
    """Projecting the hologram: a quick rising digital chirp with a scatter of bit-clicks, resolving into a
    bright, slightly detuned synthetic chord that settles with a soft shimmer."""
    rng = np.random.default_rng(seed)
    d = 1.1
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    t = t_axis(0.22)
    chirp = np.sign(np.sin(2 * np.pi * np.cumsum(np.linspace(300 * p, 2600 * p, len(t))) / RATE))
    place(out, lowpass(bitcrush(chirp * env_ad(0.22, 0.01, 0.08), 8, 4), 7000) * 0.35, 0.0)
    place(out, glitch_ticks(rng, 0.35, 70) * np.exp(-t_axis(0.35) / 0.15) * 0.4, 0.0)
    t = t_axis(0.85)
    chord = sum(np.sin(2 * np.pi * f * p * t + rng.uniform(0, 6.28)) for f in (660, 663.5, 990, 1320)) / 4
    chord *= np.clip(t / 0.03, 0, 1) * np.exp(-t / 0.3)
    place(out, tail(chord * 0.55, 0.15), 0.16)
    place(out, tail(shimmer(rng, 0.7, 3000 * p, 5000 * p, voices=4) * np.exp(-t_axis(0.7) / 0.25) * 0.18, 0.15), 0.18)
    place(out, tone([180 * p, 120 * p], 0.3) * env_ad(0.3, 0.005, 0.1) * 0.35, 0.15)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.78))


def holo_loop(seed=1411):
    """A decoy running: a low synthetic hum with a faint digital flutter - heard by its pilot's side only."""
    rng = np.random.default_rng(seed)
    loop, cross = 2.4, 0.3
    d = loop + cross + 0.4
    t = t_axis(d)
    hum = (np.sin(2 * np.pi * 110 * t) + 0.5 * np.sin(2 * np.pi * 220.7 * t) + 0.25 * np.sin(2 * np.pi * 331 * t)) * 0.2
    flutter = 0.75 + 0.25 * np.sign(np.sin(2 * np.pi * 12.5 * t))
    hiss = bandpass(noise(rng, d), 2500, 7000, order=2) * 0.05
    ticks = glitch_ticks(rng, d, 9) * 0.05
    out = bitcrush(hum * flutter, 64, 3) + hiss + ticks
    return normalize(seamless(soft_clip(out, 1.1), loop, cross), 0.55)


def holo_hit(seed):
    """A decoy hit: the picture tearing for an instant - a crushed square burst falling in pitch, and crackle."""
    rng = np.random.default_rng(seed)
    d = 0.3
    out = np.zeros(int(RATE * d))
    t = t_axis(0.16)
    f = np.linspace(rng.uniform(1300, 1700), rng.uniform(300, 450), len(t))
    zap = np.sign(np.sin(2 * np.pi * np.cumsum(f) / RATE)) * env_ad(0.16, 0.002, 0.05)
    place(out, lowpass(bitcrush(zap, 6, 5), 6000) * 0.5, 0.0)
    place(out, glitch_ticks(rng, 0.2, 140) * np.exp(-t_axis(0.2) / 0.06) * 0.6, 0.0)
    return fade_edges(normalize(soft_clip(out, 1.3), 0.72))


def holo_end(seed):
    """A decoy dissolving: the tone breaking up and falling away under a spray of digital crackle, then gone."""
    rng = np.random.default_rng(seed)
    d = 1.0
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    t = t_axis(0.7)
    f = np.linspace(990 * p, 260 * p, len(t))
    fall = (np.sin(2 * np.pi * np.cumsum(f) / RATE) + 0.5 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / RATE)) * np.exp(-t / 0.22)
    gate = 0.6 + 0.4 * np.sign(np.sin(2 * np.pi * np.linspace(18, 40, len(t)) * t))
    place(out, tail(bitcrush(fall * gate, 24, 4) * 0.5, 0.15), 0.0)
    place(out, tail(glitch_ticks(rng, 0.6, 120) * np.exp(-t_axis(0.6) / 0.2) * 0.5, 0.1), 0.02)
    place(out, tail(shimmer(rng, 0.6, 2800 * p, 4600 * p, rise=False) * np.exp(-t_axis(0.6) / 0.2) * 0.25, 0.15), 0.0)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.75))


def buzz(rng, seconds, freqs, cutoffs, drive=2.5):
    """Mains-like electrical buzz: a sawtooth whose pitch wanders a little about ``freqs``, through a low-pass
    following ``cutoffs`` and driven into saturation - the grainy power hum of the field, not a musical note."""
    wander = lowpass(noise(rng, seconds), 4.0, order=1)
    wander *= 0.02 / max(np.std(wander), 1e-9)
    t = t_axis(seconds)
    f = np.interp(t / seconds, np.linspace(0, 1, len(freqs)), freqs) * (1.0 + wander)
    # Band-limited (summed harmonics up to 5 kHz): a naive sawtooth folds its upper harmonics back as whistles.
    phase = 2 * np.pi * np.cumsum(f) / RATE
    saw = np.zeros_like(t)
    for k in range(1, int(5000 / max(np.min(f), 1.0)) + 1):
        saw += np.where(k * f < 5000, np.sin(k * phase) / k, 0.0)
    saw *= 2 / np.pi
    # A plain low-pass, swept a block at a time: a resonant band driven into saturation would whistle.
    out = np.zeros_like(saw)
    zi = np.zeros((1, 2))
    for start in range(0, len(saw), 256):
        c = float(np.interp(start / max(len(saw) - 1, 1), np.linspace(0, 1, len(cutoffs)), cutoffs))
        sos = signal.butter(2, c, btype="lowpass", fs=RATE, output="sos")
        out[start:start + 256], zi = signal.sosfilt(sos, saw[start:start + 256], zi=zi)
    return soft_clip(out * drive + 0.15 * lowpass(noise(rng, seconds), 200), 1.5)


def rumble(rng, seconds, cutoff=140.0):
    """Sub-bass rumble: low-passed noise, the weight under the field."""
    x = lowpass(noise(rng, seconds), cutoff, order=2)
    return x / max(np.std(x), 1e-9) * 0.3


def awall_toss(seed):
    """Pin pull and throw: the disc unlatched from the pack with a hard metal clack, the swing of the arm, and
    the disc arming in flight with a low, gritty electrical whir rising under it."""
    rng = np.random.default_rng(seed)
    d = 0.8
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.96, 1.04)
    place(out, click(rng, 0.014, 2400) * 0.8, 0.0)
    place(out, metal_ring(rng, 0.09, [(1850 * p, 1.0, 1.0), (2930 * p, 0.6, 0.7), (4380 * p, 0.4, 0.5)], 0.02) * 0.3, 0.0)
    place(out, click(rng, 0.01, 3500) * 0.45, 0.045)
    place(out, thud(rng, 0.06, 160 * p, 0.3) * 0.3, 0.045)
    place(out, jet(rng, 0.3, (450, 1300, 650), q=0.9, decay=0.09, attack=0.03) * 0.55, 0.09)
    t = t_axis(0.55)
    arm = buzz(rng, 0.55, [70 * p, 120 * p], [400, 1600]) * np.clip(t / 0.35, 0, 1) ** 2 * np.exp(-np.clip(t - 0.4, 0, None) / 0.06)
    place(out, tail(arm * 0.22, 0.08), 0.18)
    place(out, tail(electric(rng, 0.5, density=30, bright=3000) * np.clip(t_axis(0.5) / 0.4, 0, 1) * 0.25, 0.08), 0.2)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.72))


def awall_deploy(seed):
    """The disc slamming down and the field igniting: a heavy thump and clank as it bites and its legs lock,
    then a deep, distorted electrical surge with a burst of static and crackle that settles into the field's
    buzzing hum."""
    rng = np.random.default_rng(seed)
    d = 1.7
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    place(out, thud(rng, 0.3, 58 * p, 1.3), 0.0)
    place(out, metal_ring(rng, 0.25, [(410 * p, 1.0, 1.0), (1130 * p, 0.6, 0.6), (2240 * p, 0.35, 0.4)], 0.06) * 0.3, 0.0)
    place(out, rattle(rng, 0.16, 5) * 0.3, 0.01)
    place(out, click(rng, 0.012, 3000) * 0.5, 0.07)
    place(out, click(rng, 0.012, 3000) * 0.4, 0.1)
    place(out, tail(sweep_bandpass(noise(rng, 0.14), (700, 1500), q=2.0) * env_ad(0.14, 0.02, 0.05) * 0.25, 0.03), 0.05)
    s = 1.55
    t = t_axis(s)
    surge = np.clip(t / 0.07, 0, 1) * (0.4 + 0.6 * np.exp(-t / 0.3))
    place(out, tail(buzz(rng, s, [32 * p, 52 * p, 50 * p, 50 * p], [180, 1400, 700, 500]) * surge * 0.6, 0.35), 0.12)
    place(out, tail(rumble(rng, s) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.4) * 1.6, 0.3), 0.12)
    place(out, tone([75 * p, 38 * p], 0.5) * env_ad(0.5, 0.01, 0.16) * 0.7, 0.12)
    static = sweep_bandpass(noise(rng, 0.8), (500, 2800, 1500), q=0.8) * env_ad(0.8, 0.025, 0.22)
    place(out, tail(static * 0.5, 0.2), 0.12)
    place(out, tail(electric(rng, 1.0, density=170, bright=2200) * np.exp(-t_axis(1.0) / 0.3) * 0.55, 0.2), 0.12)
    out *= np.minimum(1.0, (d - t_axis(d)) / 0.3)
    return fade_edges(normalize(soft_clip(out, 1.3), 0.78))


def awall_loop(seed=1511):
    """A standing wall: the field's low, grainy electrical buzz over a sub rumble, with fine static and the odd
    crackle running through it."""
    rng = np.random.default_rng(seed)
    loop, cross = 3.0, 0.4
    d = loop + cross + 0.4
    t = t_axis(d)
    hum = buzz(rng, d, [50, 50], [520, 520], drive=2.0) * (0.9 + 0.1 * np.sin(2 * np.pi * 0.7 * t))
    grain = bandpass(noise(rng, d), 300, 1400, order=2) * np.abs(np.sin(2 * np.pi * 50 * t)) ** 3
    sizzle = bandpass(noise(rng, d), 3000, 8000, order=2) * 0.03
    fizz = electric(rng, d, density=16, bright=3500) * 0.12
    return normalize(seamless(hum * 0.5 + rumble(rng, d) * 0.6 + grain * 0.12 + sizzle + fizz, loop, cross), 0.55)


def awall_block(seed):
    """A shot soaked up by the field: a dull, soft energy thump, a short hiss of static and a spit of crackle -
    the sound of the shot being absorbed, not ricocheting."""
    rng = np.random.default_rng(seed)
    d = 0.4
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.92, 1.08)
    place(out, tone([190 * p, 70 * p], 0.14) * env_ad(0.14, 0.002, 0.035) * 0.8, 0.0)
    place(out, lowpass(noise(rng, 0.06), 1200) * env_ad(0.06, 0.0008, 0.012) * 0.5, 0.0)
    hiss = sweep_bandpass(noise(rng, 0.25), (2600 * p, 900 * p), q=1.0) * env_ad(0.25, 0.001, 0.05)
    place(out, tail(hiss * 0.6, 0.05), 0.002)
    place(out, tail(electric(rng, 0.12, density=450, bright=2000) * np.exp(-t_axis(0.12) / 0.03) * 0.45, 0.03), 0.0)
    place(out, tail(buzz(rng, 0.2, [70 * p, 55 * p], [900, 300]) * np.exp(-t_axis(0.2) / 0.05) * 0.35, 0.04), 0.0)
    return fade_edges(normalize(soft_clip(out, 1.5), 0.8))


def awall_amp(seed):
    """A shot going out through the wall and picking up its charge: just a quick, faint electrical sizzle on
    the shot - the game gives it no sound of its own beyond the shot."""
    rng = np.random.default_rng(seed)
    d = 0.22
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.94, 1.06)
    sizzle = sweep_bandpass(noise(rng, 0.15), (2500 * p, 5500 * p), q=1.2) * env_ad(0.15, 0.002, 0.035)
    place(out, tail(sizzle * 0.6, 0.03), 0.0)
    place(out, tail(electric(rng, 0.08, density=300, bright=3000) * np.exp(-t_axis(0.08) / 0.025) * 0.4, 0.02), 0.0)
    place(out, tail(buzz(rng, 0.12, [90 * p, 90 * p], [800, 400]) * np.exp(-t_axis(0.12) / 0.03) * 0.2, 0.03), 0.0)
    return fade_edges(normalize(soft_clip(out, 1.3), 0.6))


def awall_end(seed):
    """The field shutting off: its buzz collapsing and dropping away in pitch through a falling rush of
    static and a sputter of crackle, then the dull pop of the spent disc."""
    rng = np.random.default_rng(seed)
    d = 1.2
    out = np.zeros(int(RATE * d))
    p = rng.uniform(0.97, 1.03)
    s = 0.75
    t = t_axis(s)
    place(out, tail(buzz(rng, s, [52 * p, 40 * p, 22 * p], [900, 400, 120]) * np.exp(-t / 0.28) * 0.7, 0.15), 0.0)
    place(out, tail(rumble(rng, s) * np.exp(-t / 0.2) * 1.2, 0.15), 0.0)
    place(out, tail(sweep_bandpass(noise(rng, s), (2600, 900, 300), q=0.9) * env_ad(s, 0.01, 0.2) * 0.45, 0.15), 0.0)
    sputter = electric(rng, 0.6, density=140, bright=2200) * np.exp(-t_axis(0.6) / 0.18)
    place(out, tail(sputter * 0.5, 0.1), 0.02)
    place(out, thud(rng, 0.18, 80 * p, 0.8) * 0.55, 0.62)
    place(out, tail(lowpass(noise(rng, 0.12), 1800) * env_ad(0.12, 0.001, 0.025) * 0.5, 0.03), 0.62)
    place(out, click(rng, 0.01, 3000) * 0.3, 0.63)
    return fade_edges(normalize(soft_clip(out, 1.2), 0.78))


SOUNDS = {
    "double_jump_1": lambda: double_jump(101),
    "double_jump_2": lambda: double_jump(102),
    "double_jump_3": lambda: double_jump(103),
    "jump_1": lambda: jump(201),
    "jump_2": lambda: jump(202),
    "jump_3": lambda: jump(203),
    "walljump_1": lambda: walljump(301),
    "walljump_2": lambda: walljump(302),
    "walljump_3": lambda: walljump(303),
    "land_1": lambda: land(401),
    "land_2": lambda: land(402),
    "land_3": lambda: land(403),
    "wallrun_start": wallrun_start,
    "wallrun_loop": wallrun_loop,
    "slide_start": slide_start,
    "slide_loop": slide_loop,
    "grapple_fire_1": lambda: grapple_fire(501),
    "grapple_fire_2": lambda: grapple_fire(502),
    "grapple_fire_3": lambda: grapple_fire(503),
    "grapple_impact_1": lambda: grapple_impact(601),
    "grapple_impact_2": lambda: grapple_impact(602),
    "grapple_impact_3": lambda: grapple_impact(603),
    "grapple_reel_loop": grapple_reel_loop,
    "grapple_release_1": lambda: grapple_release(701),
    "grapple_release_2": lambda: grapple_release(702),
    "grapple_ready": grapple_ready,
    "grapple_denied": grapple_denied,
    "stim_activate_1": lambda: stim_activate(801),
    "stim_activate_2": lambda: stim_activate(802),
    "stim_loop": stim_loop,
    "stim_deactivate": stim_deactivate,
    "phase_activate_1": lambda: phase_activate(1001),
    "phase_activate_2": lambda: phase_activate(1002),
    "phase_loop": phase_loop,
    "phase_warning": phase_warning,
    "phase_end_1": lambda: phase_end(1031),
    "phase_end_2": lambda: phase_end(1032),
    "phase_telefrag": telefrag,
    "pulse_draw_1": lambda: pulse_draw(1101),
    "pulse_draw_2": lambda: pulse_draw(1102),
    "pulse_throw_1": lambda: pulse_throw(1111),
    "pulse_throw_2": lambda: pulse_throw(1112),
    "pulse_impact_1": lambda: pulse_impact(1121),
    "pulse_impact_2": lambda: pulse_impact(1122),
    "pulse_ping": pulse_ping,
    "pulse_detected": pulse_detected,
    "cloak_activate_1": lambda: cloak_activate(1301),
    "cloak_activate_2": lambda: cloak_activate(1302),
    "cloak_loop": cloak_loop,
    "cloak_warning": cloak_warning,
    "cloak_end_1": lambda: cloak_end(1331),
    "cloak_end_2": lambda: cloak_end(1332),
    "holo_deploy_1": lambda: holo_deploy(1401),
    "holo_deploy_2": lambda: holo_deploy(1402),
    "holo_loop": holo_loop,
    "holo_hit_1": lambda: holo_hit(1421),
    "holo_hit_2": lambda: holo_hit(1422),
    "holo_hit_3": lambda: holo_hit(1423),
    "holo_end_1": lambda: holo_end(1431),
    "holo_end_2": lambda: holo_end(1432),
    "awall_toss_1": lambda: awall_toss(1501),
    "awall_toss_2": lambda: awall_toss(1502),
    "awall_deploy_1": lambda: awall_deploy(1503),
    "awall_deploy_2": lambda: awall_deploy(1504),
    "awall_loop": awall_loop,
    "awall_block_1": lambda: awall_block(1521),
    "awall_block_2": lambda: awall_block(1522),
    "awall_block_3": lambda: awall_block(1523),
    "awall_amp_1": lambda: awall_amp(1531),
    "awall_amp_2": lambda: awall_amp(1532),
    "awall_end_1": lambda: awall_end(1541),
    "awall_end_2": lambda: awall_end(1542),
}


def write_ogg(name, samples):
    # grapple_*, stim_*, phase_*, pulse_*, cloak_*, holo_* and awall_* sounds go to their own folders without the prefix: grapple_fire_1 -> grapple/fire_1.ogg.
    group = next((g for g in ("grapple", "stim", "phase", "pulse", "cloak", "holo", "awall") if name.startswith(g + "_")), None)
    out_dir = SOUNDS_DIR / group if group else OUT
    file = name[len(group) + 1:] if group else name
    out_dir.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        wavfile.write(wav, RATE, pcm)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "6",
                        str(out_dir / (file + ".ogg"))], check=True)


def main():
    prefix = sys.argv[1] if len(sys.argv) > 1 else ""
    # Old single-file names are replaced by the variants above.
    for old in ("jump", "double_jump", "walljump", "land"):
        path = OUT / (old + ".ogg")
        if path.exists():
            path.unlink()
    for name, make in SOUNDS.items():
        if not name.startswith(prefix):
            continue
        samples = make()
        write_ogg(name, samples)
        print(f"{name}.ogg  {len(samples) / RATE:.2f} s")


if __name__ == "__main__":
    main()
