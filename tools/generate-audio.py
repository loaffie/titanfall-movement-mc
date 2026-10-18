#!/usr/bin/env python3
"""Synthesize the mod's movement sounds, modelled on the character of Titanfall 2's pilot jumpkit.

Every sound is built here from oscillators, filtered noise and envelopes - nothing is sampled or derived
from Titanfall 2 or any other game. The aim is the *feel* of the jumpkit: a hard pneumatic "fwump" with
a valve click and a jet hiss for the double jump, quick light footsteps over a thin thruster hiss on a wall
run, a gritty scrape for the slide, and a weighty, rattly landing.

Usage (from the repository root):

    python3 tools/generate-audio.py

Requires numpy, scipy and ffmpeg (with libvorbis) on the PATH. Output, as mono 44.1 kHz Ogg Vorbis:

    src/main/resources/assets/titanfall_movement/sounds/movement/<name>.ogg

The one-shots come in variants (jump_1..3, double_jump_1..3, walljump_1..3, land_1..3) that
sounds.json picks between at random, so repeated moves do not sound like a loop of one sample. The
loops (wallrun_loop, slide_loop) are cross-faded at the seam so they loop without a click.
"""
from __future__ import annotations

import os
import pathlib
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

RATE = 44100
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/titanfall_movement/sounds/movement"


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
}


def write_ogg(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        wavfile.write(wav, RATE, pcm)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "6",
                        str(OUT / (name + ".ogg"))], check=True)


def main():
    # Old single-file names are replaced by the variants above.
    for old in ("jump", "double_jump", "walljump", "land"):
        path = OUT / (old + ".ogg")
        if path.exists():
            path.unlink()
    for name, make in SOUNDS.items():
        samples = make()
        write_ogg(name, samples)
        print(f"{name}.ogg  {len(samples) / RATE:.2f} s")


if __name__ == "__main__":
    main()
