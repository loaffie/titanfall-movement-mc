# Movement and pilot ability sounds

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
| `phase_activate` | `phase/activate_1..2.ogg` | the coil charging with a quick whine and crackle, a reversed rush pulling the pilot out, then a hollow "whumm" with a tone falling through it and a ghostly shimmer |
| `phase_loop` | `phase/loop.ogg` | while phased: a low drone beating against itself, hollow air swinging through it and a faint electric fizz (seamless loop; others hear it quieter) |
| `phase_warning` | `phase/warning.ogg` | a tone and rush rising, tremolo speeding up, 1.5 seconds before the way back opens (Titanfall's cue timing) |
| `phase_end` | `phase/end_1..2.ogg` | coming back: a hard reversed suck snapping into an electric crack, a punchy thump, a metallic ring and sparkle |
| `phase_telefrag` | `phase/telefrag.ogg` | a crunching burst with a deep body hit, where someone is telefragged |
| `pulse_draw` | `pulse/draw_1..2.ogg` | the kunai sliding out of its sheath: a bright metal "shhing", a ring and the arming click |
| `pulse_throw` | `pulse/throw_1..2.ogg` | a hard, fast whip of air with the kunai's spin fluttering through it |
| `pulse_impact` | `pulse/impact_1..2.ogg` | the bite: a hard thunk, a metallic twang, grit, and two arming beeps. Played at the kunai |
| `pulse_ping` | `pulse/ping.ogg` | each sonar pulse: a deep round "whoom", a clean high ping and its echo rolling away (heard far off) |
| `pulse_detected` | `pulse/detected.ogg` | two harsh alarm beeps over a rising sweep, when someone's pulse reveals you |
| `cloak_activate` | `cloak/activate_1..2.ogg` | an airy rush drawn in, a glassy shimmer climbing over it, and the field closing with a soft low "thoom" |
| `cloak_loop` | `cloak/loop.ogg` | while cloaked, heard only by the pilot: a faint, airy, slowly beating hum with a far-off shimmer (seamless loop) |
| `cloak_warning` | `cloak/warning.ogg` | three glassy flutters, each lower, 3 seconds before the cloak fails |
| `cloak_end` | `cloak/end_1..2.ogg` | the shimmer falling apart downward, a short static tear and the air rushing back |
| `holo_deploy` | `holo/deploy_1..2.ogg` | a rising digital chirp and bit-clicks resolving into a bright, detuned synthetic chord |
| `holo_loop` | `holo/loop.ogg` | while a decoy runs, heard by its pilot's side only: a low synthetic hum with a faint digital flutter (seamless loop) |
| `holo_hit` | `holo/hit_1..3.ogg` | a decoy hit, heard by its pilot's side only (whoever shot it hears a player's hurt sound): a crushed square burst falling in pitch, and crackle |
| `holo_end` | `holo/end_1..2.ogg` | a decoy dissolving: the tone breaking up and falling away under digital crackle |
| `awall_toss` | `awall/toss_1..2.ogg` | the disc unclipped from the pack and flicked out: a latch clack, a whirr as it spins up, and the whoosh of the throw |
| `awall_deploy` | `awall/deploy_1..2.ogg` | the disc's legs locking with a heavy clunk, the mast's servo whine, and the field rising through a crackling, swelling hum to a bright energy chord |
| `awall_loop` | `awall/loop.ogg` | while a wall stands: a deep, steady electrical hum with a slow beat and a faint fizz (seamless loop) |
| `awall_block` | `awall/block_1..3.ogg` | a shot stopped by the wall: a hard, bright electric snap ringing off into a short falling zap |
| `awall_amp` | `awall/amp_1..2.ogg` | a shot amped going out through the wall: a quick rising electric zing |
| `awall_end` | `awall/end_1..2.ogg` | the wall powering down: the hum dropping away through a stutter of crackle, and a last clunk |

Most one-shots have two or three variants that `sounds.json` picks between at random, and the game adds a ±4%
pitch variation on top, so repeated moves don't sound canned.

## Regenerating

```
python3 tools/generate-audio.py            # everything
python3 tools/generate-audio.py grapple_   # only the Grapple's
```

Requires numpy, scipy and ffmpeg with libvorbis. It writes mono 44.1 kHz Ogg Vorbis into
`src/main/resources/assets/titanfall_movement/sounds/movement/`, `.../sounds/grapple/`, `.../sounds/stim/`, `.../sounds/phase/`, `.../sounds/pulse/`, `.../sounds/cloak/`, `.../sounds/holo/` and `.../sounds/awall/`.

## Using other audio

Any resource pack can replace the files under `assets/titanfall_movement/sounds/movement/` and
`sounds/grapple/`, `sounds/stim/`, `sounds/phase/`, `sounds/pulse/`, `sounds/cloak/`, `sounds/holo/` and `sounds/awall/` with the same names. If you use audio from your own copy of a game, keep it in a local resource pack and don't
redistribute it.

## Adjusting

`config/titanfall_movement.json` (or the Mod Menu config screen) has `soundsEnabled` and `movementVolume`.
