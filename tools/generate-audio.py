#!/usr/bin/env python3
"""Synthesize the mod's movement sounds, modelled on the character of Titanfall 2's pilot jumpkit.

Every sound is built here from oscillators, filtered noise and envelopes - nothing is sampled or derived
from Titanfall 2 or any other game. The aim is the *feel* of the jumpkit: a hard pneumatic "fwump" with
a valve click and a jet hiss for the double jump, quick light footsteps over a thin thruster hiss on a wall
run, a gritty scrape for the slide, and a weighty, rattly landing. The Grapple gets the same treatment: a
pneumatic launch with the line whipping out, a hard metallic bite, a straining winch while it reels the
pilot in, and the quick zip and latch of the hook coming home. And the Stim: the jab and hiss of the
autoinjector and the rush that follows, a racing heart while it lasts, and the comedown.

Usage (from the repository root):

    python3 tools/generate-audio.py            # everything
    python3 tools/generate-audio.py grapple_   # only the sounds whose names start with grapple_ (or stim_, ...)

Requires numpy, scipy and ffmpeg (with libvorbis) on the PATH. Output, as mono 44.1 kHz Ogg Vorbis:

    src/main/resources/assets/titanfall_movement/sounds/movement/<name>.ogg
    src/main/resources/assets/titanfall_movement/sounds/grapple/<name>.ogg   (the grapple_* sounds)
    src/main/resources/assets/titanfall_movement/sounds/stim/<name>.ogg      (the stim_* sounds)

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
}


def write_ogg(name, samples):
    # grapple_* and stim_* sounds go to their own folders without the prefix: grapple_fire_1 -> grapple/fire_1.ogg.
    group = next((g for g in ("grapple", "stim") if name.startswith(g + "_")), None)
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
