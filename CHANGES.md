# 2.0 (Minecraft 1.21.1)

## Wall running on diagonal walls

- **Any straight wall, not just block faces.** Blocks set corner to corner, 2:1, 1:2 and other
  staircases, and diagonal rows of cobblestone wall, fence posts or panes run exactly like a flat wall.
  The run carries on smoothly from a straight wall onto a diagonal one and back.
- **New wall detection.**
  - `WallProbe` no longer casts rays along the four axes. It gathers the block collision boxes beside
    the player and flattens them to plan view; wall is collision at both hip and head height.
  - New `WallFit` (plain 2D geometry, no game classes) treats the box corners on one side of the player
    as an outline. The wall's normal is the direction in which the player stands furthest out from that
    outline, found by a coarse sweep and then a golden-section search.
  - In front of a face or a staircase that direction is exact and steady. At the corner where two walls
    meet it turns continuously from one to the other.
  - The wall line runs through the outermost corners, so holding the hitbox 0.03 blocks in front of it
    never catches a step.
  - A depth profile over ±1.6 blocks confirms there is real wall; gaps narrower than the hitbox (between
    posts) still count.
  - A wall is now a normal plus a line (`Wall(normal, plane, gap)`) instead of a block face.
- **The run follows the wall.** Each tick the wall is re-fitted around its current direction (±70°), and
  the run turns with it at up to 20° a tick, keeping its speed and direction. Round a corner too sharp to
  follow, it transfers to the next wall as before. Clearance is now held both ways: pulled in when
  there's a gap, pushed out if a corner of the wall sticks into the line ahead.
- **Axis-ordered moves on diagonal walls.**
  - Minecraft resolves movement one axis at a time, larger axis first. Along a diagonal wall that can
    test the hitbox half a step inside a block and stop the run dead.
  - When one axis of the move heads into the wall and the other away from it, the controller moves the
    outward axis first itself (`Entity.move`) and leaves the other to vanilla, so every intermediate
    position is in the open.
- **Tested headlessly** (Minecraft-style axis-by-axis collision, at 7, 10 and 14 b/s):
  - flat walls, 45°, 2:1 and 1:2 staircases, diagonal wall posts, straight-to-diagonal and
    diagonal-to-straight, a one-block corridor, and outside corners;
  - no collisions, and the run keeps its full speed in all of them;
  - with vanilla's axis order instead, the same diagonal runs hit steps and lose 50–80% of their speed.
- Animations and effects use the fitted wall line: the wall-side arm lies along a diagonal wall's
  outer corners, and wall-run and wall-jump sparks start where the hitbox's corner meets a diagonal
  wall.

## Optimisation and clean-up

- The wall-run carry-pose search, the costliest part of the jumpkit poser, now runs once per game tick
  instead of every frame (its springs glide between results). Its inner loop reuses scratch matrices
  and vectors instead of allocating per candidate pose.
- Wall detection makes one collision-box query per tick and reuses it for every fit, instead of several
  raycasts per probe.
- Removed unused imports and the raycast-based wall distance in the animation code, which now uses the
  same wall fit as the run.
- Version `2.0`.

# 1.7.5 (Minecraft 1.21.1)

- **The green pouch no longer stretches.** The skinned "hose" objects were picking up the side dome of
  the central tank (the green pouch) as well as the thin hose tubes, so the dome was dragged out toward
  the carrying module.
  - The split now separates the candidate pieces and only skins the ones that actually run into the
    pod (reaching past |x| = 80): 88 and 106 hose-tube triangles, down from 216 and 231. The dome stays
    rigid on the pack.
  - The tubes' skin weights spread over their full length (|x| 50 → 88) so their flex is even.
  - Skinned corners now turn about the hub by a slerp of the module's rotation instead of blending
    positions.
- **Arms keep the same pose running backward on a wall.**
  - The wall arm now reaches the way the body *faces* along the wall, not the way it runs, so
    reversing no longer wrenches it round behind the body.
  - The off-wall arm stays at its forward balance angle instead of swinging back.
  - Checked: the wall arm still sits exactly on the wall (0.3000 vs 0.300) running forward, backward
    and at 45°.
- **Jets lean against the run direction on a wall run** (cosmetic). Running forward, both nozzles point
  back; running backward, they point forward.
  - The carrying module's aim adds a component against the run.
  - The wall-side module's fold is aimed back along the run on a spring (fold range ±100°), and its
    wall-clearance swing is solved after that.
  - Checked: nozzle directions flip correctly between forward and backward runs, side walls are still
    cleared (0.295 / 0.294), and the arm-overlap check stays at 0 corners both ways.
- Version `1.7.5`.

# 1.7.4 (Minecraft 1.21.1)

- **The arm no longer passes through the "carrying" thruster module on a wall run.**
  - The cause: the off-wall module swings out level at hip height, right where the off-wall arm hangs.
    Measured: 714 of the module's 6465 corners were inside that arm.
  - The off-wall arm is now held steady about 57° out from the body for balance, slightly forward
    (back when running backward), instead of pumping.
  - The jumpkit renderer hands both arms' real boxes to the poser each frame: the arm's render
    transform, inverted, and its cuboid plus a 0.75 px margin with the sleeve included.
  - The carry-pose search rejects any lift/fold that puts module surface points inside an arm. It tests
    a dense ~250-point surface sample per module, so an arm through the middle of the module is caught
    too.
  - Checked against every module corner with the arm down, raised 57°, 86°, 106° and 126°: 0 corners
    inside the arm in all cases (with the arm hanging down, the module lifts over it instead).
- The carry pose's lift and fold now glide on springs, like the other module moves, so the module
  can't jump between poses as the arm moves.
- Re-checked: side walls are still cleared to within 0.005 blocks, slides still clear the floor, and the
  wall-jump return is unchanged.
- Version `1.7.4`.

# 1.7.3 (Minecraft 1.21.1)

- **The thrust stream fills the whole nozzle.** The steady flame (air, wall run, slide) now starts at the
  full bore of the opening: 0.062-block radius, puffs drawn about twice that because the glow sprite
  fades inside its quad. The body ribbon runs from 2.3× to 3.6× the bore. Two glow discs face along the
  exhaust, one filling the bore just off the plate and a wider, fainter one at the flap mouth, so
  looking into the nozzle the whole opening is lit. The ribbons' texture now runs from its bright centre
  at the nozzle to its dark edge at the tip, so the stream is full-strength right at the opening.
- **Cooler thruster interior** (new `tools/paint-jumpkit-emissive.py`, which `bake-jumpkit.py` runs):
  - Exhaust plate: a white-hot core, eight injector ports, a honeycomb grille between three glowing
    rings, six dark struts with lit edges, and a deep-blue rim.
  - Turbine: a disc of twelve swept vanes drawn just in front of the plate. It idles round (0.6 rev/s)
    and spins up with each thruster's burn (up to about 5 rev/s).
  - Inner wall: the nozzle rim's inward faces (84 per pod, new objects `glowin_r`/`glowin_l`) are
    drawn again in the emissive pass with a gradient from bright at the plate to dark at the mouth,
    with cooling bands and heat streaks. They're faint when idle and lit when firing.
- **Wall run "carry" pose.** The module away from the wall swings out level with the ground and aims
  its jet down and away from the wall. The pose is solved per frame from the render matrix: a lift and
  fold grid that balances keeping the module level against aiming the jet, and it eases in and out on a
  spring. That module fires a stronger controlled jet (`WALL_CARRY` 0.95) while the one against the wall
  idles (`WALL_SIDE` 0.45).
  - Thrusters now have **per-port burn levels**: flames, flaps, plate and inner-wall brightness and
    turbine speed all follow their own thruster.
  - Which module is "away" comes from the geometry, so it's right whichever way you face.
  - Re-checked: the wall-side module still clears side walls to within 0.005 blocks with the carry
    pose active.
- **New sounds** (new `tools/generate-audio.py`, replacing the placeholder generator). All are
  synthesised in the style of Titanfall 2's jumpkit:
  - a pneumatic double-jump "fwump" with a valve click, a sweeping jet roar and a hiss tail;
  - a wall run of quick footsteps over a breathing thruster hiss;
  - a wall kick with a thrust burst;
  - a gritty slide scrape;
  - a weighty, rattly landing.

  Jump, double jump, wall jump and landing each have three variants, and playback adds ±4% pitch.
  Loops are cross-faded so the seam doesn't click. No game audio is used. See `SOUNDS.md`.
- Version `1.7.3`.

# 1.7.2 (Minecraft 1.21.1)

- **Thruster modules return smoothly after a wall jump.** Two things made the return look wrong:
  - The wall was tracked as a distance from the player, so it moved with you as you jumped away. The
    module stayed fully swung for about 0.4 s, then let go in one go.
  - The easing was an exponential approach, which starts at full speed and reads as a snap.

  Now the wall is stored as a fixed plane in the world (`wallPlane`), so the module swings home exactly
  as the wall really falls away. All the module moves (swing, raise, tuck, slide aim) follow critically
  damped springs, which ease in and out with no jolt; they're quick when the module must get clear and
  gentle when it relaxes. Checked on a simulated wall jump at 60 fps: 54° → 0 with a soft start and a
  soft landing.
- **Slide boost look.** In a slide the modules swing round (about 116° about the hub on flat ground) so
  both nozzles point straight back against your direction of travel, tipped 8° toward the ground. The
  angle is solved from the actual render matrix. The thrusters hold a strong steady burn
  (`PilotThrusters.SLIDE`), so the slide looks thruster-driven, though it's only visual. The direction
  comes from the last tick's movement, so it's right for other players too. The floor contact still
  rides the kit up just enough.
- **Wider thrust stream.** The steady flame (air, wall run, slide) is now a plume rather than a thin
  jet: it starts at the nozzle's own width (0.055 blocks), swells to about twice that and thins toward
  the tip. It's drawn in four soft additive layers: outer haze, a broad tapered body ribbon, glow puffs
  and a hot core.
- **Particle fixes:**
  - Effects for other players used `getVelocity()`, which isn't kept up to date for them, so their
    double-jump jets and slide sparks ignored their motion. They now use the movement over the last tick.
  - The slide's afterglow trail is replaced by the steady boost flame, so the two no longer pile up.
- Version `1.7.2`.

# 1.7.1 (Minecraft 1.21.1)

- **No more jumpkit clipping on wall runs and slides: the thruster modules are articulated.**
  - New `tools/split-jumpkit-pods.py`, which `bake-jumpkit.py` runs after the flap split, splits each
    thruster module into its own objects: `pod_r`/`pod_l` (pod housing + capsule arm, jointed at the
    side hub, `# pivot`), `hose_r`/`hose_l` (hose ends, `# skin` weights 58→72 units, blended per
    vertex between pack and pod so the hoses bend instead of tearing) and `glow_r`/`glow_l`.
  - New `JumpkitPoser` works out each frame where the modules and the pack go.
    - **Style:** nothing moves on the ground. In free air the modules splay slightly and angle the
      nozzles back. A double jump kicks them down and out, the slide-hop coil tucks them back, and a
      slide folds them forward with the pack rolled in.
    - **Contact:** the pose is tested against the world through the kit's exact render matrix, using a
      small precomputed hull of support points per part.
      - On a wall run, a module that would enter the wall swings back about its hub just far enough;
        the solve picks a forward swing when that's shorter, and the pack tucks into the back if the
        wall is behind you.
      - In a slide, the whole kit rides up the spine just far enough to clear the floor.
    - Contact results ease in quickly (35/s) and relax back slowly (9/s).
    - Checked numerically against every vertex:
      - the slide's lowest point is now at the floor; it was 0.21 blocks under it;
      - side walls on either side are cleared to within 0.005 blocks; the module used to go 0.19 in;
      - also cleared: a wall in front, a wall at 45° in front, and a wall at 45° behind (swing
        forward plus tuck).
  - The thruster ports are recorded from each module's own matrix, so jets, rings and the steady
    flames leave the nozzles wherever the modules have swung.
- **Fixed a potential render crash:** the base and emissive layers' vertex buffers are now fetched
  right before each is filled and never held across layers, because fetching one layer's buffer can
  finish another's.
- Older single-piece jumpkit models (for example from resource packs) still load and draw unarticulated.
- `split-jumpkit-flaps.py` can be re-run on an already-split model.
- Version `1.7.1`.

# 1.7 (Minecraft 1.21.1)

- **Titanfall 2 gravity while wearing the jumpkit.**
  - The kit now carries attribute modifiers on the chest slot: Gravity ×0.476 (0.08 → 0.0381
    blocks/tick², i.e. Titanfall's 600 u/s² = 15.24 blocks/s²) and Jump Strength 0.42 → 0.337
    (Titanfall's 1.52-block jump height under that gravity).
  - Air time per jump goes from 12 to 18 ticks.
  - Because it's an attribute, it applies on the server and on every client, in every movement state
    (walking jumps and plain falls included), and it shows in the tooltip.
  - The old client-only `airGravityScale` correction (and its "Air Gravity" slider) is gone.
  - `doubleJumpVelocity` now defaults to 0.337, so a double jump matches the kit's ground jump.
  - Config version 7: an older config is backed up and defaults are written.
- **Small blue flame whenever airborne.** Both thrusters burn a small steady flame in free air: jumps,
  double jumps and falls. It doesn't show while swimming, in lava, flying, gliding, climbing, riding
  or spectating, and it waits two ticks so stepping off a block edge doesn't blip it. It grows to the
  full flame on a wall run and surges on a double jump. The flame particle and the new flap animation
  share one smoothed thrust level (`PilotThrusters`).
- **Animated thruster flaps.** `tools/split-jumpkit-flaps.py` finds the three flaps around each nozzle
  in the baked OBJ (geometry past the exhaust plate that reaches far down the exhaust axis), splits
  them into objects `flap_r0..r2` / `flap_l0..l2`, and writes a hinge line for each: a pivot on the
  base edge, and an axis signed so a positive angle opens outward. `bake-jumpkit.py` now runs it
  automatically. The renderer swings each flap about its hinge: closed on the ground, about 10° in
  free air, 22° on a wall run, +17° flung open by a double jump, with a light per-flap flutter while
  burning. The item model shows them closed.
- **Particle fixes:**
  - The inventory screen's player doll is drawn through the same jumpkit renderer, in GUI space. It
    was overwriting the recorded thruster positions, so effects could spawn in the wrong place while
    the inventory was open. Recorded ports that aren't near the player are now ignored.
  - The steady flame is no longer drawn on invisible players, nor on your own pilot in first person,
    where it floated at the standing-pose hips.
  - The flame's lifetime is now tick-based, so it always dies down after the burn ends, even if it
    wasn't being drawn.
- Version `1.7`.

# 1.6.9 (Minecraft 1.21.1)

- **Double-jump rings come out of the thrusters.** The two rings at the feet and hips are replaced by a
  train of three exhaust rings per nozzle. Each one starts at the visible port, faces along that
  nozzle's exhaust direction and travels down it, widening as it goes. Rings no longer get a random
  spin. New particle type `exhaust_ring`.
- **Steady wall-run flame.** The short jets and wisps during a wall run are replaced by a continuous
  blue flame on both thrusters. It's one long-lived particle per nozzle (`thruster_flame`) that is
  redrawn on the port every frame, from where the kit was just drawn, so it never lags or flickers. It
  fades in when the run starts and out when it ends. Boot sparks on the wall are unchanged.
- **Wall-run arm no longer clips into the block.** The wall-side arm now lies flat in the wall plane,
  angled down and along the run, with its outer face resting on the surface.
  - The rotation is solved exactly from the wall's direction in model space (after the body yaw, lean
    and pitch). The shoulder joint slides along the wall normal by however much the face needs to
    touch the wall. That's under a pixel when the wall is side-on.
  - The wall distance comes from a raycast into the wall at hip and chest height, smoothed per frame.
  - Checked numerically: the arm's outer face sits at the wall surface (±0.0001 blocks) for walls on
    either side, running forward or backward, and with the wall at 45° to the body.
- The wall-run lean now tilts straight away from the wall's real normal, not the body's left or right.
  It matters when you face into or away from the wall.
- Feet no longer sink into the wall. If the lean would push a foot past the surface, that leg swings
  back about the along-wall axis just far enough to rest on it.
- Version `1.6.9`.

# 1.6.8 (Minecraft 1.21.1)

- **The item now looks like the worn jumpkit.** The inventory icon, hotbar, both hands (first and
  third person), dropped items, item frames and armor stands' heads all draw the same OBJ model and
  Grapple-pilot textures as the kit on your waist, instead of a flat sprite.
  - New `JumpkitItemRenderer` (Fabric `BuiltinItemRendererRegistry`): centres the mesh on its
    bounding box and scales it to fill the slot, so it stays framed if a resource pack swaps the model.
  - The thruster exhausts glow through the same emissive map, full-bright, in the inventory too.
  - If the OBJ can't load, the cuboid fallback kit is drawn instead.
- `models/item/pilot_jumpkit.json` now uses `minecraft:builtin/entity`; its `display` block sets the
  angle and size per context (GUI, hands, ground, frame) and can be tweaked by resource packs.
- `JumpkitMesh` now exposes the mesh bounding box.
- Version `1.6.8`.

# 1.6.7 (Minecraft 1.21.1)

- **Particles stay on the thrusters in every pose.**
  - Before, particle origins were computed from the standing pose. That is exact while upright, but
    drifted off the nozzles during crouch, the wall-run lean, the slide recline and the coil.
  - Now the jumpkit renderer records where each exhaust port is actually drawn each frame, and the
    direction it points, using the same transform as the mesh. Effects spawn from there.
  - The standing-pose calculation is kept as a fallback for first person, where the kit isn't drawn.
- Checked: the nozzle constants match the baked exhaust plates to within 1.3 mesh units (about
  0.05 px).
- Version `1.6.7`.

# 1.6.6 (Minecraft 1.21.1)

- **Smaller jumpkit.** It's 20% smaller: `OBJ_SCALE` went from 0.049 to 0.039, making the kit about
  15 px across. It's re-centred on the belt line (`OBJ_OFFSET_Y` 5.0 → 6.0), so the pods still sit at
  the hips. The thruster particles follow automatically because they use the same constants.
- Version `1.6.6`.

# 1.6.5 (Minecraft 1.21.1)

- **The jumpkit is worn at the waistline, like Titanfall 2.** The pack sits on the belt at the lower
  back, with the thruster pods at the hips. It's also 12.5% smaller: `OBJ_SCALE` went from 0.056 to
  0.049, and `OBJ_OFFSET_Y` from 1.2 to 5.0.
- **New thruster particle system.** Five custom particle types (`thruster_jet`, `thruster_spark`,
  `shockwave`, `afterglow`, `ignition`) replace the vanilla cloud and flame puffs.
  - They're drawn on a new additive glow sheet at full brightness. `ParticleManagerMixin` adds the
    sheet to the engine's render order and restores the blend state afterwards.
  - Colour, alpha and size are animated over each particle's life with eased three-stop ramps.
  - Jets and sparks are drawn as streaks stretched along their velocity. Shockwaves are flat rings
    on the ground or the wall.
- **Effects are emitted from the model's exhaust ports**, using the nozzle position and direction
  measured from the baked mesh:
  - **Double jump:** ignition, jets, sparks, afterglow and two shockwave rings.
  - **Wall run:** wall-side jets and wisps, plus boot sparks off the wall.
  - **Wall jump:** a wall ring, jets and a spark burst.
  - **Slide:** ground sparks and pod trails.
  - **Slide hop:** a puff and a ring.
  - Other players are covered too: their double jumps are detected as a mid-air upward kick.
- New sprite textures (`glow`, `spark`, `ring`, `flare`) come from
  `tools/generate-particle-textures.py`.
- Version `1.6.5`.

# 1.6.4 (Minecraft 1.21.1)

- **OBJ jumpkit model.** Your converted model now replaces the cuboid kit. The cuboid kit stays in as
  a fallback if the OBJ can't be loaded.
  - It's mounted on the back of the body part, so it follows wall-run leans, slides and the coil.
  - It's loaded from the client resources and reloads with resource packs.
- **Grapple-pilot colour scheme.** A generated 256 px atlas: a matte charcoal chassis, olive drab and
  navy webbing, hazard-stripe and orange accents, dark-steel bolts, hinges and joints, and a
  weathered steel frame.
- **Emissive thrusters.** `jumpkit_emissive.png` is drawn over the exhaust plates with the eyes render
  layer (additive, full bright). The exhausts glow electric cyan regardless of light level, at 70%
  on the ground and 100% in the air.
- New `tools/bake-jumpkit.py` regenerates the model and both textures from the source OBJ.
- Version `1.6.4`.

# 1.6.3 (Minecraft 1.21.1)

- **Coil reworked in the Roblox-parkour style.** The top of each leg is treated as the knee:
  - The leg joint lifts from the hip to chest height and moves out in front of the torso. The leg
    hangs down from there as the shin, tipped slightly back.
  - The knees are drawn together, and the arms reach forward, down and in so the hands clasp the
    knees.
  - The body leans 10° forward with the gaze kept level.
- **A hop before the tuck.** Every slide hop now starts with a short push-off, timed from the jump
  itself: legs drive down, arms swing up and the body drives forward slightly. It peaks at about 80 ms and
  is gone by 200 ms. The coil only starts drawing in at 90 ms, so the sequence reads as hop into
  tuck.
- **Loops on held jump.**
  - Held-jump chain hops now count as hop events too (both your own and other players'), so
    push-off → coil replays on every jump.
  - When a new hop starts, the coil lets go quickly (22/s) so the legs can drive down again. It
    then draws back in.
- **Smooth slide → hop.** The slide pose blends out while the push-off rises, so no frame snaps.
- Fix: the leg joints' sideways position is now reset every frame, so the coil can't stick or spill
  onto other players drawn with the same model.
- Version `1.6.3`.

# 1.6.2 (Minecraft 1.21.1)

- **Slide-hop coil animation.** The air tuck is now a coiled tuck-jump pose:
  - The hip joints move up and forward, both legs swing forward past horizontal, and the whole body
    leans 22° forward over them. That brings the knees up to the chest, since player legs have no
    knee joint.
  - The arms reach forward and inward so the hands meet in front of the shins, wrapping the legs.
  - The head is counter-tilted so the gaze stays level.
  - The coil weight is smoothstep-eased on top of the per-frame blend, so the tuck accelerates in
    and settles out smoothly. Held-jump chains still loop between coil and slide without passing
    through the standing pose.
- Version `1.6.2`.

# 1.6.1 (Minecraft 1.21.1)

- **Wall-run camera banks outward.** The view now tilts away from the wall instead of toward it.
  `Cam Wall Roll` keeps its 12° default, which now means outward; a negative value banks toward the
  wall. The wall-jump kick still pushes the view away from the wall.
- **Held-jump slide hopping.**
  - In a slide-hop chain, or while crouching, holding jump hops on the exact tick you land. Before,
    you sat out vanilla's 10-tick held-jump cooldown, lost speed to friction and briefly dropped to
    the standing pose.
  - A landing hop counts as a slide hop when it comes from a slide, from a running chain, or with
    crouch held. Before, only a jump out of an actual ground slide counted, so a held-jump chain lost
    its tuck animation after the first hop.
  - Other players: a touchdown of up to 2 ticks no longer ends their tuck, so their held-jump chains
    animate smoothly too.
- Version `1.6.1`.

# 1.6 (Minecraft 1.21.1)

## Slide hitbox
- **0.8-block slide hitbox.** While sliding, players use the vanilla `SLIDING` pose, which players
  never use in vanilla. Its dimensions are now 0.6 × 0.8 blocks, with the eyes at 0.6.
  - The client tells the server when a slide starts and ends (one-boolean `titanfall_movement:slide`
    packet, sent only on change).
  - The server applies the same pose. Its collision, its move validation and the suffocation check
    therefore all agree with the client.
  - The pose syncs to other players, so they see the low hitbox too.
- **1-block gaps.** You can slide through them without taking suffocation damage. If 0.8 blocks
  doesn't fit, the pose falls back to vanilla's 0.6-block crawl.
- **Un-crouch check.** Under anything lower than 1.5 blocks:
  - Releasing crouch doesn't end the slide.
  - The slide can't slow below `slideCeilingSpeed` (3 b/s).
  - If you're stopped dead, vanilla keeps you crawling rather than standing you up into the
    ceiling.

## Animation
- **Slide pose aligned to the ground.**
  - The torso reclines about the real hip joint (0.703 blocks), and the hips are lowered until the
    legs rest on the ground.
  - Leg pitch is now `-(90° − recline)`, so legs lie flat. The old fixed −1.35 rad pointed them into
    the air.
  - The default recline is now 55°, which keeps the head inside the 0.8-block box.
- **Continuous slide-hop loop.** Slide and air tuck are now both held states with their own targets,
  not a slide plus a one-shot hop envelope. Every take-off and landing in a chain swaps the targets.
  The pose blends from wherever it currently is (tuck in at 18/s, out at 10/s, slide at 14/s) and
  never resets or passes through standing.
- **Other players.** Their slides are read from the synced `SLIDING` pose.
- **Wall-jump animation.** A retriggered animation resumes from its current height instead of
  snapping to zero.

## Other
- `slideCameraDrop` now defaults to 0, because the pose already lowers the eyes.
- Config version 6.
- Version `1.6`.

# 1.5 (Minecraft 1.21.1)

## New
- **The Pilot Jumpkit is craftable.** Recipe `data/titanfall_movement/recipe/pilot_jumpkit.json`:
  iron, netherite scrap, iron / redstone, elytra, redstone / firework, copper, firework. A recipe-book
  unlock (`advancement/recipes/combat/pilot_jumpkit.json`) triggers when you pick up an elytra or
  netherite scrap.
- **Omnidirectional wall running:**
  - No facing or approach-angle requirement. A wall in reach latches when your momentum or any held
    movement key has a component along it. That includes hugging the wall, or running with the wall
    at your side or back.
  - Run speed is a signed value along the wall. Keys along the wall drive it either way, and the
    opposite key decelerates through zero and reverses without dropping the run.
  - The run follows outside corners onto the next face at full speed.
  - Holding straight away from the wall only peels you off after 6 ticks. Crouch still drops you
    instantly.
  - Wall reach went from 0.3 to 0.4 blocks.
  - A wall can only be latched after 3 ticks in the air, so stepping off stairs or doing a standing
    jump beside a wall doesn't start a run.
  - Climb limits:
    - The double jump comes back only from a wall not yet used since landing.
    - The wall jump's upward kick shrinks by 15% for each further wall, down to a minimum of 40%.
- **Camera:**
  - Smooth roll toward the wall while wall running, scaled by how side-on the wall is.
  - A roll kick away from the wall on wall jumps.
  - Slides lower the camera, widen the FOV and add a slight drift tilt.
  - Everything is smoothed per frame with a frame-time-weighted lerp.
- **Animations** (third person, all players):
  - Wall-run lean with a reaching wall-side arm. Forward and backward runs lean differently, with a
    smooth blend when you change direction.
  - Wall-jump push-off.
  - Slide recline with an extended leg, and a slide-hop tuck.
- Config version 5, with new camera, animation and wall-run options. All are on the Mod Menu screen.

## Changed
- Version scheme: `1.5` (set in `gradle.properties`, expanded into `fabric.mod.json`).

# 1.4 (Minecraft 1.21.1 port, previously labelled 2.0.0)

## New
- **Pilot Jumpkit** item (`titanfall_movement:pilot_jumpkit`), worn in the chest slot. It renders as a hip belt with side thrusters and glowing exhausts.
- All pilot movement is gated on the jumpkit (`Jumpkit.abilitiesActive`).
- Wearing the jumpkit cancels fall damage. This happens on the server, so the mod is now installed
  on both client and server.
- Lurch now follows Titanfall's documented behaviour: a new key pressed shortly after a jump
  redirects your velocity and costs speed.
- Double jump turns your momentum toward the held, camera-relative direction and gives a small push.
- A skid state: landing faster than normal speed without sliding bleeds speed through Source ground
  friction instead of stopping you dead.
- Thruster particles on double jumps and wall jumps.
- Slides now run long on ice and slime and short on soul sand and honey. Ordinary blocks use the
  slide friction unchanged.

## Physics fixes
- **One tick of input latency removed.** The controller ran at `END_CLIENT_TICK`, so this tick's
  keys only moved you on the next tick. It now runs inside `travel`, before the move.
- **Air strafing was capped** at `maxHorizontalSpeed` (13.4 b/s). The accelerator now caps only the
  velocity projection, as Source does. There is only a 60 b/s safety limit.
- **Wall runs drifted upward.** The vertical velocity was pre-compensated for gravity
  (`v / 0.98 + 0.08`), but the next move uses the stored value before gravity is applied. That
  added about 1.6 b/s of lift to every run. Wall vertical motion is now modelled explicitly from
  Titanfall's `wallrunAccelerateVertical`, `wallrun_gravityRampUpTime` and
  `wallrun_slipstarttime`.
- **Hitting a wall stored speed.** Collisions never removed momentum, so you were held against a
  block at full speed and shot off when you cleared it. Blocked axes now lose their momentum
  (`ClipVelocity`).
- **Lurch farmed speed.** Every jump added +2 b/s in the held direction.
- **Fall damage built up** across wall runs and double jumps, and you took it on landing.
- Wall detection now uses raycasts against block collision shapes at hip and head height. Before,
  it used block-column checks at the feet. Same-wall re-latching is rate-limited until you land.
- Server knockback packets replace your momentum. Explosions, wind charges and entity pushing are
  added as impulses. Cobwebs, berry bushes and powder snow hand control back to vanilla.
- The ledge-slide bypass now also runs on the server. With a client-only bypass, the server
  teleported you back to the ledge.
- Exploit guards: a wall bump returns the double jump only once per wall until you land, and the
  wall-jump input push can no longer raise your speed along the wall (corridor hopping used to gain
  speed without limit).

## Port 1.21.11 → 1.21.1
- `isGliding()` → `isFallFlying()`. `Identifier.of`, the `Equipment` interface,
  `TypedActionResult` and the `ModelPart.render(..., int color)` signature were all checked
  against Yarn 1.21.1+build.3.
- The Gradle setup is Loom 1.18 (`fabric-loom-remap`) with Yarn 1.21.1+build.3,
  Fabric API 0.116.17+1.21.1 and Mod Menu 11.0.3.
- Config version is now 4. An old file is backed up and replaced with new defaults, which come
  from Titanfall 2's player settings files.
