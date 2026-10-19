# Titanfall Movement 2.2.1 (Fabric 1.21.1)

Titanfall 2 pilot movement for Minecraft Java 1.21.1, built on Source-engine movement math and
powered by a wearable **Pilot Jumpkit**, plus two pilot tactical abilities: the **Grapple** and the
**Stim**. Install it on **both client and server**: the jumpkit, Grapple and Stim are registered items,
so a server without the mod would reject them.

## Pilot Jumpkit

- Item id `titanfall_movement:pilot_jumpkit`. It is **craftable** (shaped recipe below), sits in
  the Combat creative tab, and the recipe unlocks in the recipe book once you pick up an elytra or
  netherite scrap.

  ```
  Iron Ingot   Netherite Scrap   Iron Ingot
  Redstone     Elytra            Redstone
  Firework     Copper Ingot      Firework
  ```
- It goes in the **chest slot**: shift-click it, drag it in, right-click it in hand (swaps with
  your chestplate), or let a dispenser put it on you. It is an `Equipment` item, the same way the
  elytra works, so no chestplate shell is drawn over the player.
- It is drawn from an OBJ model (`models/entity/jumpkit.obj`) in Titanfall 2 Grapple-pilot colours: a charcoal chassis, olive and navy webbing, hazard-stripe and orange accents, dark-steel mechanicals and a weathered steel frame. As in Titanfall 2, it's secured at the waistline: the pack rides on the belt at the lower back, with a thruster pod at each hip. It follows the body through every pose. The exhaust ports glow electric cyan through a separate emissive map at full brightness, so they stay lit in pitch darkness and burn brighter in the air.
  Every player can see it on others, because equipment is synced.
- The **item itself uses the same 3D model**: inventory slots, the hotbar, your hands, dropped items and
  item frames all show the textured jumpkit with glowing exhausts. Angles and sizes per context are in
  `models/item/pilot_jumpkit.json` (`display`).
- **All pilot movement needs the kit**: air strafing, sliding, slide hopping, wall running, wall
  jumping, double jumping and lurch. If you take the kit off, even mid-air, you go straight back
  to vanilla movement.
- While you wear it, fall damage is cancelled. This happens on the server.
- **Titanfall gravity.** The kit lowers your gravity to Titanfall 2's: 750 u/s² × the pilot's 0.8
  scale = 15.24 blocks/s², against Minecraft's 32. It's an attribute modifier on the chest slot
  (Gravity ×0.476, Jump Strength 0.337), so it applies on the server and every client, in every
  state, and shows in the item tooltip. The jump is retuned to Titanfall's 60-unit (1.52-block) jump
  height, so you hang in the air about 50% longer (18 ticks against vanilla's 12), and falls are slower.
  The double jump uses the same impulse. Titanfall's datamined wall-jump speeds now give their real
  heights, because the gravity matches.

## Grapple

Titanfall 2's Grapple tactical: a hook fired from a launcher built into the forearm of the pilot's
gauntlet, which reels you in.

- Item id `titanfall_movement:pilot_grapple`, in the Combat creative tab. Craftable; the recipe unlocks
  once you pick up a tripwire hook or a sticky piston.

  ```
  Iron Ingot   Tripwire Hook   Iron Ingot
  Chain        Sticky Piston   Chain
  Leather      Redstone        Leather
  ```
- **Where it goes.** With [Trinkets](https://modrinth.com/mod/trinkets) installed it is worn in a
  **glove slot**: the off-hand glove (Titanfall's pilots wear it on the off-hand forearm, keeping the
  weapon hand free) or the main-hand glove. Right-click it to equip it. Without Trinkets it is worn by
  holding it in the **off hand** (right-clicking it in the main hand moves it there). Trinkets is optional.

  Curios API, which was the first choice, only exists for NeoForge/Forge on 1.21.1; Trinkets is
  the Fabric equivalent, and the Accessories mod also loads Trinkets items through its compatibility layer.
- **Drawn on the forearm** that wears it, on every player: an armoured bracer with the launcher along the
  top of the forearm, the barrel over the back of the hand, a cable spool on the side and the
  three-clawed hook seated in the muzzle. A status light on the launcher is cyan when a charge is ready,
  pulses amber while it refills and burns white while the line is out. In the inventory and on the
  ground the item is the same 3D model; held in a hand it is drawn on the arm instead of as an item.
- **Using it.** Press **Tactical Ability** (default `G`; rebind it under Controls > Titanfall Movement;
  Titanfall's own key, Q, is Minecraft's drop key). Press it again, or jump, to let go. It works with or
  without the jumpkit.
- **Works like Titanfall 2's** (values from the game's `mp_ability_grapple.txt` and `pilot_base.set`):
  - The hook flies at 4400 u/s and reaches its full range of 1100 u (**28 blocks**) in 0.25 s. A shot
    that hits nothing reels back in and **costs nothing**.
  - When it bites, a charge is spent, you're kicked upward (300 u/s, so it pops you off the ground) and
    the line reels you in at about 40-45 km/h (**11-12.5 b/s**, faster for longer shots). Speed you
    already have is kept, and the line never pays out.
  - While hooked you have strong air control (`grapple_airSpeedMax` 420, `grapple_airAccel` 650), so you
    can swing round the hook. The line carries part of your weight: gravity is ×0.7 with the hook level,
    down to ×0.25 with it overhead.
  - It lets go when you arrive (33-50 u), jump, or press the key again. All of these give the release kick:
    a 200 u/s upward boost that pops you over the ledge, and the speed trimmed by 300 u/s (never below
    460). It also lets go, with no kick, if you lose sight of the hook, are flung away from it faster
    than 500 u/s, or stay slower than 250 u/s for 1.5 s in the air (1.2 s on a wall, 0.7 s on the
    ground).
  - **One charge, 10 s to recharge**, starting 3 s after the line lets go: the October 2017 update's
    numbers. The game launched with two charges; `grappleCharges` brings that back.
  - **Hooking a creature or player** reels you to them. If they're in mid-air, the server reels them
    toward you at the same time. You can't use items such as bows while being pulled, but you can melee
    (`grappleBlocksItemUse`).
  - A fall caught by the line doesn't count as a fall (server side).
  - Barriers can't be hooked; add blocks to the `titanfall_movement:grapple_disallowed` block tag to
    rule out more.
- **HUD**: an arc under the crosshair shows the charge, amber while refilling and cyan when ready. Brackets
  either side of the crosshair light up when something hookable is in range (`grappleHud`).
- **Looks and sound.** In first person the launcher arm comes up when you fire, stays raised toward the
  hook while you're pulled, and drops away once the hook is home. In third person the arm points at the
  hook. The cable whips out in flight, snaps taut when the hook bites, hums under load and snakes home
  behind the hook when it reels in. Effects:
  - a muzzle flash and sparks when it fires;
  - a tracer behind the hook in flight;
  - where it bites, a flash, a ring, chips of the block it hit and sparks (crit sparks on a creature).
  Sounds: the pneumatic launch with the line paying out, the metallic bite, a winch whose pitch climbs
  with your speed, the zip and latch of the hook coming home, the charge-ready chime and an empty-meter
  beep. Other players see and hear all of it.

## Stim

Titanfall 2's Stim tactical: an autoinjector that floods the pilot's system for a burst of speed and fast
healing.

- Item id `titanfall_movement:pilot_stim`, in the Combat creative tab. Craftable; the recipe unlocks
  once you pick up sugar or a glistering melon slice.

  ```
  Iron Nugget   Sugar                    Iron Nugget
  Redstone      Glistering Melon Slice   Redstone
  Iron Nugget   Glass Bottle             Iron Nugget
  ```
- **Worn on the right hip.** With Trinkets it goes in the **belt** slot (right-click it to equip). Without
  Trinkets, hold it in the off hand. Either way it's drawn in a holster on the front of the right hip.
- **Using it.** Press **Tactical Ability** (`G`). If you wear the Grapple as well, the key fires the
  Grapple, and the Stim has its own **Stim** key (unbound by default) under Controls > Titanfall Movement.
- **Works like Titanfall 2's** (values from the game's `mp_ability_heal.txt`, `sh_stim.gnut`,
  `_health_regen.gnut` and `sh_consts.gnut`):
  - **+40% speed for 5 seconds** (Titanfall's stim lasts 3; `stimLengthSeconds`), fading out over half a
    second after that. Walking and sprinting get
    it on the server too, as a movement-speed attribute modifier. The pilot moves the mod runs itself also
    get it: the sprint take-off, the wall run's top speed and entry speed, and the skid hand-off.
  - **Fast healing while it runs, and for 2 seconds after it ends** (`stimHealLingerSeconds`): double the
    pilot's regen rate, starting 2 seconds after your last hit instead of 5. That's enough to bring a hurt
    pilot back to full in about a second once it kicks in.
  - **25-second recharge**, starting the moment you use it. The meter refills steadily, as in the game.
  - The view widens a little while it runs. Vanilla's speed-based FOV jump is taken back out so the
    widening stays subtle.
- **Looks.**
  - **Model:** a gunmetal autoinjector with grip rings, an amber plunger cap, a steel needle and a caged
    glass vial of glowing blue stim, in an olive holster with clamp bands.
  - **The vial is a charge gauge:** it empties as the plunger goes down and refills as the stim recharges.
  - **First person:** whatever the right hand holds drops out of view. The right forearm rises into the
    right of the screen with the injector in the fist, needle toward the chest, and stabs it in, the vial
    draining as it goes. The item comes back afterwards.
  - **Third person:** the right hand reaches to the hip and draws the injector, then the arm becomes a
    forearm. Its joint moves from the shoulder to an elbow in front of the waist, and the fist stabs the
    injector straight into the right side of the chest with a blue flare, then puts it back.
  - **While it runs:**
    - glowing blue trails stream from the pilot's shoulders and hips and hang in the air behind them;
    - the screen edges glow blue and pulse like a racing heartbeat;
    - speed lines stream out from the middle of the view, more of them the faster you move.
  - Sounds: the injector's click and hiss with a rush of adrenaline, a racing heartbeat while it lasts,
    and the comedown two seconds before it ends (Titanfall's own cue timing).
  - Other players see the trail and the injection, and hear it.
- **HUD**: a charge arc round the crosshair, under it or over it when the Grapple's arc is there too.

## Movement

| Move | How |
| --- | --- |
| Air strafe | In the air, hold `A` or `D` and turn the camera smoothly the same way. Speed builds without limit. Turning faster than your speed allows loses speed, as in Source. |
| Slide | Press crouch while sprinting. You get a +3.8 b/s boost up to 10.2 b/s, at most once every 2 s. While sliding, your hitbox is 0.8 blocks tall, so you fit through 1-block gaps. Under a low ceiling the slide won't stand you up and keeps at least 3 b/s to carry you through; if something stops you dead inside, you drop into vanilla's crawl instead of suffocating. |
| Slide hop / bunny hop | Jump on the tick you land, or jump out of a slide. Ground friction never touches you, so you keep all your speed. In a slide-hop chain (or while crouching) you can simply hold jump: you hop again on the exact tick you land, without vanilla's 10-tick jump cooldown. |
| Skid | If you land without crouching or jumping, Source ground friction brings you back to normal speed in about 4 ticks. |
| Wall run | Get near a wall after a few ticks in the air while moving along it, or while holding a key along it and pressing into it. Which way you face doesn't matter: you can look along the wall, into it, or have it at your back. W, A, S and D all drive the run, whichever key points along the wall. Press the opposite key to slow down through zero and run back the other way without dropping off. It works on diagonal walls too - blocks set corner to corner, 2:1 and other staircases, rows of cobblestone wall or fence posts - exactly like a flat wall, and it carries on smoothly from a straight wall onto a diagonal one and back. The run wraps round outside corners. Running on walls you've already used since landing doesn't give the double jump back, and each extra wall weakens the wall jump's upward kick, so you can't climb shafts forever. It lasts up to 1.75 s; gravity ramps in, and you slip after 1.5 s. Crouch drops you off. Holding straight away from the wall for 0.3 s peels you off. |
| Wall jump | Press jump while wall running. You launch outward and upward and keep the run's speed. Your held direction adds a small push. |
| Double jump | Press jump once in the air. Your vertical speed resets to a full jump, and part of your momentum turns toward the held direction. Wall runs and wall bumps (crouch against a wall mid-air) give it back. |
| Lurch | Within about 0.4 s of any jump, pressing a new direction key swings your velocity toward it. Sharper lurches cost more speed. |

## Camera and animation

- **Wall-run roll.** The view banks up to 12° outward, away from the wall. The roll follows how side-on the wall is to your view, so it fades as you turn to face the wall. Set **Cam Wall Roll** negative to bank toward the wall instead.
- **Wall-jump kick.** A wall jump kicks the view away from the wall, then springs back.
- **Slide camera.** The slide pose drops your eyes to 0.6 blocks, and vanilla eases the height change smoothly. Slides also widen the FOV by 8% (scaled by the FOV Effects setting) and tilt the view slightly into the slide's sideways drift.
- **No stutter at any frame rate.** Every camera value moves each frame with `lerp(value, target, 1 - exp(-rate * frameSeconds))`, a lerp weighted by real frame time. Targets are read from the movement state of the current tick.
- **Player animations in third person.**
  - *Wall run:* feet toward the wall and upper body leaning straight out from it, whichever way you face. The arm away from the wall is held out about 57° for balance. Both arms keep the same pose whether you run forward or backward along the wall. The wall-side arm lies flat against the wall, angled down and along the run, and slides over the surface. Its pose is solved each frame from the wall's real position (measured with a raycast), so the arm never sinks into the block or floats off it. If the lean would push a foot into the wall, that leg swings back just enough to rest on the surface. You lean into the run going forward and lean back going backward, and reversing blends between the two.
  - *Wall jump:* a push-off tilt with arms flung up, played as a smooth rise-and-settle.
  - *Slide:* the hips sit on the ground and the torso reclines 55°. Both legs lie flat along the ground, the lead hand reaches forward and the trailing hand is planted behind.
  - *Slide hop:* every hop plays a short push-off (legs drive down, arms swing up), then draws into a coil. The tops of the legs act as knees: they lift to chest height out in front, with the shins hanging below, and the hands clasp the knees together. Held-jump chains replay push-off → coil on every jump, and slide → hop blends smoothly.
- **Other players.** Their moves are recognised client-side from their motion and nearby walls, so the animations work for them too, with nothing sent over the network.

## How it works

### Wall detection

`WallProbe` gathers the block collision boxes beside the player and flattens them to plan view at
hip and head height. Wall is wherever there's collision at both heights, which rules out slabs,
carpets and short posts. `WallFit` then treats the corners of those boxes as an outline and runs
along its edge nearest the player:

- **Direction:** the wall's normal is the direction in which the player stands furthest out from the
  outline. In front of a face that's exactly the face's normal; in front of a staircase it's exactly
  the staircase's slope. At the corner where two walls meet it turns smoothly from one to the other.
- **Clearance:** the edge runs through the outermost block corners, so a hitbox held 0.03 blocks in
  front of it never catches a step.
- **Validity:** a depth profile along a 3.2-block window checks there really is wall there. A gap
  narrower than the hitbox, such as between wall posts, still counts.
- **Collision:** Minecraft resolves movement one axis at a time, larger axis first. On a diagonal
  wall that can test the hitbox half a step inside a block and stop the run dead. So on a diagonal
  wall the controller moves the axis heading away from the wall first, itself, and vanilla moves
  the other one.
- **Tested** headlessly against Minecraft-style axis-by-axis collision, at 7, 10 and 14 b/s: flat
  walls, 45°, 2:1 and 1:2 staircases, diagonal wall posts, straight-to-diagonal and back, a
  one-block corridor, and outside corners. No collisions, and the run keeps its full speed. Without
  the axis ordering the same runs hit the steps and lose 50–80% of their speed.
  `tools/wallfit-check/WallSim.java` reproduces these runs.

### Movement core

The physics core (`movement/physics/SourceMovement.java`) is a direct port of the Source
`gamemovement.cpp` primitives, working in blocks per second:

```
air:    vProj = dot(v, wishDir); add = airSpeedCap - vProj; v += wishDir * min(add, airAccel * dt)
ground: drop = max(speed, stopSpeed) * friction * dt          (Friction)
        add = wishSpeed - dot(v, wishDir); v += wishDir * min(add, accel * wishSpeed * dt)
```

`TitanfallController` runs inside `LivingEntity.travel` for the local player:

1. **`travel` HEAD** (`LivingEntityTravelMixin`). This tick's input has been read and vanilla has
   already done its ground jump, but the player has not moved yet. The controller checks
   `Jumpkit.abilitiesActive(player)` and then runs the state machine
   (`NONE / AIR / WALL / SLIDE / SKID`). It writes the velocity for this tick and returns a zero
   movement input while it owns the player. That means no vanilla WASD acceleration, no vanilla
   friction on the modelled speed, and no tick of input latency.
2. **`travel` TAIL**. Any axis the move collided on loses its momentum, like Source's
   `ClipVelocity`. The velocity vanilla is left holding is recorded, so knockback and explosions
   can be adopted next tick as external impulses.
3. **`jump` HEAD/TAIL** (`LivingEntityJumpMixin`). Vanilla's +0.2 b/t sprint-jump kick is removed,
   because it would stack on every hop in a model without drag. The controller is also told that
   a ground jump happened. That is what lets a hop keep its momentum.
4. **`adjustMovementForSneaking`** (`PlayerEntityLedgeMixin`, common). For a jumpkit pilot moving
   faster than crouch-walking, vanilla's crouch edge-clamp is skipped, so slides carry off ledges.
   This runs on the client and on the server. The server replays every move, and a one-sided bypass
   would rubber-band you back to the ledge.
5. **Camera** (`CameraMixin` on `Camera.update`, `GameRendererMixin` on `tiltViewWhenHurt`
   and `getFov`), plus **animation** (`PlayerEntityRendererMixin` on `setupTransforms`,
   `PlayerEntityModelMixin` on `setAngles`).
6. **`setVelocityClient`** (`EntityServerVelocityMixin`). A server velocity packet replaces the
   pilot's momentum, because knockback packets set velocity rather than add to it.
7. **`handleFallDamage`** (`PlayerEntityFallDamageMixin`, common/server). Returns false when the kit
   is worn.

Air acceleration is sub-stepped three times per tick, with the camera yaw interpolated between
ticks. A smooth mouse turn is integrated the way Source's 60+ Hz movement sees it, not in 50 ms
jumps.

## Tuning

All values are in `config/titanfall_movement.json` and are read live. With
[Mod Menu](https://modrinth.com/mod/modmenu) installed, the same options appear as sliders. Most
defaults come from Titanfall 2's own player settings files (`pilot_base.set`, `pilot_mp.set`). One
Titanfall unit is one inch, so 1 unit = 0.0254 blocks. Values that were not datamined are marked
*estimated* in `TitanfallConfig.java`.

An older config file is backed up as `titanfall_movement.json.v<N>.bak`, and new
defaults are written in its place.

## Building

Requires JDK 25 to run Gradle (the mod itself targets Java 21):

```
./gradlew build
```

The jar ends up in `build/libs/`. The included GitHub Actions workflow builds the jar on every push.

Versions: Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric Loader 0.19.5 (the mod runs on 0.16+),
Fabric API 0.116.17+1.21.1, Loom 1.18, Mod Menu 11.0.3 (optional), Trinkets 3.10.0 (optional).

## Sounds and textures

The movement, Grapple and Stim sounds are synthesised by `tools/generate-audio.py` in the style of Titanfall 2's jumpkit:
a pneumatic "fwump" and jet roar for the double jump, quick footsteps over a thruster hiss on the wall,
a gritty slide scrape and a rattly landing. Each one-shot has three variants. They contain no game
audio. See [SOUNDS.md](SOUNDS.md) for the full list and how to replace them, or use a
resource pack. The jumpkit textures are generated by `tools/generate-jumpkit-textures.py`. The
Grapple's and the Stim's meshes, texture atlases and item sprites are generated by
`tools/generate-grapple-model.py` and `tools/generate-stim-model.py` (shared parts in `tools/meshkit.py`).

## License

CC0-1.0.

## Jumpkit model and textures

`tools/bake-jumpkit.py <source.obj>` turns the untextured model into the files the mod loads:
- **`models/entity/jumpkit.obj`**: the model converted to the player body's axes, split into a `body`
  object and a `glow` object (the exhaust plates).
- **`textures/entity/jumpkit.png`**: a 4×4 atlas of 64 px material cells.
- **`textures/entity/jumpkit_emissive.png`**: black everywhere except a cyan radial glow in the exhaust
  cell.

**How faces are coloured.** The script assigns every triangle a material from its mesh, position and
normal, and squeezes its UVs into that material's atlas cell. Exhaust plates instead get UVs centred on
the nozzle axis, so the glow sits in the middle of each port. Re-run the script after editing the model.
Placement on the body is set by `JumpkitModel.OBJ_SCALE` and `OBJ_OFFSET_*`.

**Atlas layout** (cell index = row × 4 + column):

| Cell | Material | Used on |
| --- | --- | --- |
| 0 | charcoal | pack box, pod shells |
| 1 | dark charcoal | tank |
| 2 | olive webbing | hoses, arms, pod side panels |
| 3 | navy webbing | arm undersides |
| 4 | hazard stripes | top edges, pod bands |
| 5 | orange accent | pack sides |
| 6 | dark steel | joints, fins, side hubs |
| 7 | weathered steel trim | frame, panel rims |
| 8 | vents | radiator slats |
| 9 | exhaust | thruster ports (glow) |

## Thruster effects

Custom particles, drawn with an additive glow sheet so overlapping light blooms, and full-bright so
they glow in the dark. Every burst leaves the model's actual exhaust ports. The renderer records where the ports are drawn each frame, including through crouch, wall-run lean, slide recline and the coil. Turn them off with
**Thruster FX** in the config.

- **Airborne idle flame:** whenever you're in the air (a jump, after a double jump, or just falling),
  both thrusters burn a small, steady blue stream. It fills the whole nozzle opening, swells as the
  exhaust expands and thins toward the tip. It's drawn as glow discs across the opening, a soft
  outer haze, a broad body, glow puffs and a hot core. Each thruster has its own burn level, so the
  carrying module on a wall run burns harder than the one against the wall. It grows on a wall run and surges on a double jump.
  The flame is one long-lived particle per nozzle that is redrawn on the port every frame, so it never
  lags or flickers. It isn't drawn for your own pilot in first person, where the kit isn't drawn
  either.
- **Articulated thruster modules:** the two thruster modules (pod, capsule arm and hose ends) are
  separate parts of the model, jointed at the side hubs of the pack. Only the thin hose tubes flex
  between the pack and each pod; the tank and its side domes stay rigid on the pack. They stay still on the ground and move once you slide, go
  airborne, wall-run or slide-hop:
  - in free air they splay slightly and angle the nozzles back;
  - a double jump kicks them down and out;
  - the slide-hop coil tucks them back;
  - in a slide they swing round so both nozzles point straight back against your direction of
    travel, with a strong steady burn, as if the thrusters were driving the slide (it's only visual).

  On top of that, each frame the kit is checked against the world using the exact matrix it is drawn
  with:
  - On a wall run, the module on the wall side swings back about its hub just far enough to clear
    the wall. If the wall is behind you, it swings forward instead, and the pack tucks in.
  - On a wall run, both modules' jets lean back against the direction you're running, so they look
    as if they drive the run forward or backward (purely visual).
  - Also on a wall run, the module away from the wall swings out level with the ground, as if
    carrying you along the wall. It fires a stronger controlled jet down and out; the wall-side
    module only idles. Your arm on that side is held out for balance, and the carrying module's pose
    is solved each frame around both arms' real positions, so an arm never passes through it.
  - In a slide, the whole kit rides up your back just enough to clear the floor.

  Contact moves follow critically damped springs, so they ease in quickly and relax back smoothly, with
  no snap at either end. The wall is remembered as a fixed plane in the world, so after a wall jump the
  module swings home exactly as the wall falls away behind you. Particles and the steady flames follow
  each module's real nozzle position.
- **Thruster interiors:** looking into a nozzle you see:
  - a white-hot core;
  - a ring of injector ports;
  - a honeycomb grille between glowing rings, crossed by six struts;
  - a turbine disc of swept vanes that idles round and spins up with the burn;
  - an inner wall that glows blue from the plate outward, brighter when the thruster fires.

  The textures are painted by `tools/paint-jumpkit-emissive.py`.
- **Animated thruster flaps:** each pod's nozzle has three flaps around it. They're split out of the
  model (by `tools/split-jumpkit-flaps.py`) and hinged on the nozzle rim. They stay closed on the
  ground, open a little in free air, open wide on a wall run and fling open on a double jump. While
  burning they flutter, each on its own phase. The flaps and the flame read the same smoothed thrust
  level, so they always match.
- **Double jump:**
  - Both pods flash on ignition, and the steady flames surge.
  - Each pod fires a stretched plasma jet (white-hot → electric cyan → deep blue) with sparks and
    lingering afterglow.
  - Each nozzle blows a train of three exhaust rings straight down its own exhaust axis. They start
    nozzle-sized at the port, face along the jet and widen as they travel, like shock diamonds, so they
    show exactly where and which way each thruster fires.
- **Wall run:**
  - The steady flame burns at full size: a bright core along the exhaust axis that tapers and cools
    to deep blue.
  - Your boots strike orange sparks off the wall, flung back against the run.
- **Wall jump:** a ring flashes flat on the wall where you push off, the jets fire into it, and
  sparks burst off the surface.
- **Slide:** ground sparks behind you and a glowing trail from both pods.
- **Slide hop:** a paired thruster puff and a small ground ring.

Other players' effects are triggered from the moves the animation system recognises, so you see
their bursts too.
