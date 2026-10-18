# Movement sounds

The mod ships its own movement sounds, synthesised by `tools/generate-audio.py`. They are modelled on the
*character* of Titanfall 2's pilot jumpkit, but every one is built from oscillators, filtered noise and
envelopes. Nothing is sampled from Titanfall 2 or any other game.

| Event | Files | What it sounds like |
| --- | --- | --- |
| `double_jump` | `double_jump_1..3.ogg` | valve click, a hard low "fwump" of ignition, a jet roar sweeping from 3.4 kHz down to 700 Hz with a faint turbine whine, and an airy hiss tail |
| `jump` | `jump_1..3.ogg` | boot push, cloth and gear, a small pneumatic assist puff |
| `walljump` | `walljump_1..3.ogg` | boot kick and scuff off the wall, then a sharp thrust burst |
| `land` | `land_1..3.ogg` | deep boot impact, the kit's gear rattling, a soft pneumatic exhale |
| `wallrun_start` | `wallrun_start.ogg` | grip scuff, boot plant, thruster lighting with a rising hiss |
| `wallrun_loop` | `wallrun_loop.ogg` | quick light footsteps (6.25 a second) over a steady, breathing thruster hiss (seamless loop) |
| `slide_start` | `slide_start.ogg` | a whoosh of air and gear, then the scrape biting in |
| `slide_loop` | `slide_loop.ogg` | gritty, uneven scrape with cloth friction and a low ground rumble (seamless loop) |

Each one-shot has three variants that `sounds.json` picks between at random, and the game adds a ±4%
pitch variation on top, so repeated moves don't sound canned.

## Regenerating

```
python3 tools/generate-audio.py
```

Requires numpy, scipy and ffmpeg with libvorbis. It writes mono 44.1 kHz Ogg Vorbis into
`src/main/resources/assets/titanfall_movement/sounds/movement/`.

## Using other audio

Any resource pack can replace the files under `assets/titanfall_movement/sounds/movement/` with the same
names. If you use audio from your own copy of a game, keep it in a local resource pack and don't
redistribute it.

## Adjusting

`config/titanfall_movement.json` (or the Mod Menu config screen) has `soundsEnabled` and `movementVolume`.
