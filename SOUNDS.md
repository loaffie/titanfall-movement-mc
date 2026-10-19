# Movement, Grapple and Stim sounds

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
| `grapple_fire` | `grapple/fire_1..3.ogg` | breech click, pneumatic punch and air burst, a short metallic ring, then the line whirring out of the spool |
| `grapple_impact` | `grapple/impact_1..3.ogg` | the hook biting: a bright metallic strike, a heavy thunk, grit off the surface and the line twanging taut. Played at the hook |
| `grapple_reel` | `grapple/reel_loop.ogg` | the winch: a straining motor whine with gear chatter and the line hissing through (seamless loop; pitch and volume follow how fast you're reeled in) |
| `grapple_release` | `grapple/release_1..2.ogg` | the hook ripping free, the line zipping back in, and the hook seating with a click-clack |
| `grapple_ready` | `grapple/ready.ogg` | soft rising two-note chime when a charge is back |
| `grapple_denied` | `grapple/denied.ogg` | two flat, low beeps when you fire with no charge (also the Stim's) |
| `stim_activate` | `stim/activate_1..2.ogg` | the injector's click and the hiss of the dose going in, then a rush: a deep swell, a tone climbing an octave and a half, and the first hard heartbeat |
| `stim_loop` | `stim/loop.ogg` | a racing heart (150 bpm, lub-dub), blood rushing in the ears and a thin ringing (seamless loop, fades out with the stim) |
| `stim_deactivate` | `stim/deactivate.ogg` | the comedown, two seconds before the stim ends: a tone sliding down, the heart slowing, a long exhale |

Most one-shots have two or three variants that `sounds.json` picks between at random, and the game adds a ±4%
pitch variation on top, so repeated moves don't sound canned.

## Regenerating

```
python3 tools/generate-audio.py            # everything
python3 tools/generate-audio.py grapple_   # only the Grapple's
```

Requires numpy, scipy and ffmpeg with libvorbis. It writes mono 44.1 kHz Ogg Vorbis into
`src/main/resources/assets/titanfall_movement/sounds/movement/`, `.../sounds/grapple/` and `.../sounds/stim/`.

## Using other audio

Any resource pack can replace the files under `assets/titanfall_movement/sounds/movement/` and
`sounds/grapple/` and `sounds/stim/` with the same names. If you use audio from your own copy of a game, keep it in a local resource pack and don't
redistribute it.

## Adjusting

`config/titanfall_movement.json` (or the Mod Menu config screen) has `soundsEnabled` and `movementVolume`.
