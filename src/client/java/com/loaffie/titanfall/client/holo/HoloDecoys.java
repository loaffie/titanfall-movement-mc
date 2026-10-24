package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.client.anim.PilotAnimations;
import com.loaffie.titanfall.client.anim.StandIns;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.holo.HoloDecoyEntity;
import com.loaffie.titanfall.item.PilotGear;
import com.loaffie.titanfall.mixin.client.PlayerEntityHoloAccessor;
import com.loaffie.titanfall.movement.TitanfallSounds;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Every Holo Pilot decoy the client can see, and what it takes to make one look like its pilot.
 *
 * <p>A decoy is drawn as a stand-in player: a client-only copy of the pilot - their profile, so their skin,
 * cape and name; the skin layers they show; their armour, hands and Trinkets gear - that follows the decoy
 * every tick, so the player renderer draws it with everything a pilot is drawn with, the mod's own poses and
 * thruster effects included (the wall run and slide are read from how it moves, as for any other pilot).
 *
 * <p>Titanfall 2's decoys flicker now and then ({@code SetDecoyRandomPulseRateMax( 0.5 )}: at most one pulse
 * every two seconds), which is how a sharp eye tells them. The pilot and their team also see a faint trail
 * off its chest and hear a hum ({@code holopilot_loop}); everyone hears it appear, get hit, and dissolve.
 */
public final class HoloDecoys {
	/** It projects in over this long, ticks. */
	public static final int PROJECT_TICKS = 6;
	/** A pulse lasts this long, ticks, and the next comes 2 to 6 seconds after. */
	private static final int PULSE_TICKS = 8;
	private static final int PULSE_GAP_MIN = 40;
	private static final int PULSE_GAP_MAX = 120;
	/** Stand-in ids count down from here, clear of the server's. */
	private static int nextStandInId = -1_000_000;

	/** One decoy, as the client sees it. */
	public static final class State {
		final HoloDecoyEntity decoy;
		/** Made once the decoy's pilot is known: the spawn arrives a moment before the data saying whose it is. */
		OtherClientPlayerEntity standIn;
		boolean friendly;
		/** It was only just projected as it came into view, so it is announced when its stand-in is made. */
		final boolean fresh;
		int age;
		int nextPulse;
		int pulseAge = -1;
		boolean dissolveSeen;
		/** The decoy's hurt time last tick, to catch each new hit. */
		int lastHurtTime;

		State(HoloDecoyEntity decoy, Random random) {
			this.decoy = decoy;
			this.fresh = decoy.age <= 2;
			this.nextPulse = PULSE_GAP_MIN + random.nextInt(PULSE_GAP_MAX - PULSE_GAP_MIN);
		}

		/** The look-alike drawn for it, or null until its pilot is known. */
		public OtherClientPlayerEntity standIn() {
			return standIn;
		}

		public boolean friendly() {
			return friendly;
		}

		/** How far it has projected in, 0..1. */
		public float projected(float tickDelta) {
			return MathHelper.clamp((age + tickDelta) / PROJECT_TICKS, 0.0F, 1.0F);
		}

		/** How strong a pulse is right now, 0..1: a quick flare and a ragged fall. */
		public float pulse(float tickDelta) {
			if (pulseAge < 0) {
				return 0.0F;
			}

			float t = (pulseAge + tickDelta) / PULSE_TICKS;
			if (t >= 1.0F) {
				return 0.0F;
			}

			float flicker = 0.75F + 0.25F * MathHelper.sin((age + tickDelta) * 3.1F);
			return (t < 0.2F ? t / 0.2F : 1.0F - (t - 0.2F) / 0.8F) * flicker;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private HoloDecoys() {
	}

	public static State of(HoloDecoyEntity decoy) {
		return STATES.get(decoy.getId());
	}

	/** A decoy came into view: its stand-in is made as soon as its pilot is known (see {@link #tick}). */
	public static void loaded(HoloDecoyEntity decoy, ClientWorld world) {
		State state = new State(decoy, decoy.getRandom());
		STATES.put(decoy.getId(), state);
		makeStandIn(state, world);
	}

	/**
	 * The stand-in, once the decoy says whose it is: the pilot's profile (their skin, cape and name) and, if the
	 * pilot is in view, the skin layers they show and the gear in their Trinkets slots. Then, if the decoy has
	 * only just been projected, that is shown and heard; one walking into view mid-run just appears.
	 */
	private static void makeStandIn(State state, ClientWorld world) {
		HoloDecoyEntity decoy = state.decoy;
		UUID owner = decoy.ownerUuid();
		if (state.standIn != null || owner == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity pilot = decoy.owner();
		PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(owner);
		GameProfile profile = entry != null ? entry.getProfile() : pilot != null ? pilot.getGameProfile() : new GameProfile(owner, "Pilot");
		OtherClientPlayerEntity standIn = new OtherClientPlayerEntity(world, profile);
		standIn.setId(nextStandInId--);
		standIn.setSilent(true);
		standIn.refreshPositionAndAngles(decoy.getX(), decoy.getY(), decoy.getZ(), decoy.getYaw(), decoy.getPitch());
		if (pilot != null) {
			byte parts = pilot.getDataTracker().get(PlayerEntityHoloAccessor.titanfall$modelParts());
			standIn.getDataTracker().set(PlayerEntityHoloAccessor.titanfall$modelParts(), parts);
			PilotGear.copyTrinkets(pilot, standIn);
		}

		PlayerEntity local = client.player;
		state.standIn = standIn;
		state.friendly = local != null && (local.getUuid().equals(owner) || (pilot != null && pilot.isTeammate(local)));
		follow(state);
		StandIns.add(standIn);
		if (state.fresh) {
			projected(state, pilot, local);
		}

		if (state.friendly && TitanfallConfig.get().soundsEnabled) {
			client.getSoundManager().play(new HumSound(state));
		}
	}

	public static void unloaded(HoloDecoyEntity decoy) {
		State state = STATES.remove(decoy.getId());
		if (state != null && state.standIn != null) {
			StandIns.remove(state.standIn);
		}
	}

	/** End of every client tick: each stand-in follows its decoy, pulses run, the trail and the dissolve shed light. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			STATES.clear();
			StandIns.clear();
			return;
		}

		Iterator<State> iterator = STATES.values().iterator();
		while (iterator.hasNext()) {
			State state = iterator.next();
			if (state.decoy.isRemoved()) {
				if (state.standIn != null) {
					StandIns.remove(state.standIn);
				}

				iterator.remove();
				continue;
			}

			makeStandIn(state, client.world);
			if (state.standIn == null) {
				continue;
			}

			state.age++;
			follow(state);
			PilotAnimations.tickStandIn(state.standIn);
			Random random = state.decoy.getRandom();
			if (state.pulseAge >= 0 && ++state.pulseAge >= PULSE_TICKS) {
				state.pulseAge = -1;
			}

			if (--state.nextPulse <= 0) {
				state.pulseAge = 0;
				state.nextPulse = PULSE_GAP_MIN + random.nextInt(PULSE_GAP_MAX - PULSE_GAP_MIN);
			}

			if (state.decoy.dissolving() && !state.dissolveSeen) {
				state.dissolveSeen = true;
				HoloEffects.dissolve(state.decoy);
			}

			if (state.friendly && !state.decoy.dissolving()) {
				HoloEffects.trail(state.decoy, state.age);
			}

			// A hit sounds like a hit pilot to everyone (the server plays the player's hurt sound); its own side
			// also hears the projection crackle, so they know their decoy is drawing fire.
			if (state.friendly && state.decoy.hurtTime > state.lastHurtTime && TitanfallConfig.get().soundsEnabled) {
				HoloDecoyEntity decoy = state.decoy;
				decoy.getWorld().playSound(decoy.getX(), decoy.getY() + 1.0, decoy.getZ(), TitanfallSounds.HOLO_HIT, SoundCategory.PLAYERS,
						0.8F * (float) TitanfallConfig.get().movementVolume, 0.95F + random.nextFloat() * 0.1F, false);
			}

			state.lastHurtTime = state.decoy.hurtTime;
		}
	}

	/** Moves the stand-in to where the decoy is, posed and dressed as it is. */
	private static void follow(State state) {
		HoloDecoyEntity decoy = state.decoy;
		OtherClientPlayerEntity p = state.standIn;
		p.prevX = p.getX();
		p.prevY = p.getY();
		p.prevZ = p.getZ();
		p.lastRenderX = p.getX();
		p.lastRenderY = p.getY();
		p.lastRenderZ = p.getZ();
		p.setPosition(decoy.getX(), decoy.getY(), decoy.getZ());
		p.prevYaw = p.getYaw();
		p.prevPitch = p.getPitch();
		p.prevBodyYaw = p.bodyYaw;
		p.prevHeadYaw = p.headYaw;
		p.setYaw(decoy.getYaw());
		p.setPitch(decoy.getPitch());
		p.bodyYaw = decoy.bodyYaw;
		p.headYaw = decoy.headYaw;
		p.setOnGround(decoy.isOnGround());
		// A decoy shot down dissolves standing, rather than falling over as a dying player would.
		p.setPose(decoy.getPose() == EntityPose.DYING ? EntityPose.STANDING : decoy.getPose());
		p.setVelocity(decoy.getPos().subtract(new Vec3d(p.prevX, p.prevY, p.prevZ)));
		p.updateLimbs(false);
		p.age = decoy.age;
		p.hurtTime = decoy.hurtTime;
		p.maxHurtTime = decoy.maxHurtTime;
		p.setMainArm(decoy.getMainArm());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				ItemStack worn = decoy.getEquippedStack(slot);
				if (!ItemStack.areEqual(p.getEquippedStack(slot), worn)) {
					p.equipStack(slot, worn.copy());
				}
			}
		}

		// The cape and the walk's sway, as the player's own tick would update them.
		((PlayerEntityHoloAccessor) p).titanfall$updateCapeAngles();
		p.prevStrideDistance = p.strideDistance;
		float stride = decoy.isOnGround() ? (float) Math.min(0.1, p.getVelocity().horizontalLength()) : 0.0F;
		p.strideDistance += (stride - p.strideDistance) * 0.4F;
	}

	/** The decoy has just been projected: the sound, the scan rings and motes, and its pilot's hand to the projector. */
	private static void projected(State state, PlayerEntity pilot, PlayerEntity local) {
		HoloDecoyEntity decoy = state.decoy;
		boolean own = local != null && pilot == local;
		HoloEffects.projected(decoy);
		if (pilot != null) {
			HoloPoses.tapped(pilot);
		}

		TitanfallConfig config = TitanfallConfig.get();
		if (!config.soundsEnabled) {
			return;
		}

		float volume = 0.9F * (float) config.movementVolume;
		float pitch = 0.97F + decoy.getRandom().nextFloat() * 0.06F;
		MinecraftClient client = MinecraftClient.getInstance();
		if (own) {
			client.getSoundManager().play(new PositionedSoundInstance(TitanfallSounds.HOLO_DEPLOY.getId(), SoundCategory.PLAYERS, volume, pitch,
					SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		} else {
			decoy.getWorld().playSound(decoy.getX(), decoy.getY() + 1.0, decoy.getZ(), TitanfallSounds.HOLO_DEPLOY, SoundCategory.PLAYERS,
					volume * 0.7F, pitch, false);
		}
	}

	/** The decoy's soft hum, heard by its pilot and their team only, while it lasts. */
	private static final class HumSound extends MovingSoundInstance {
		private final State state;

		HumSound(State state) {
			super(TitanfallSounds.HOLO_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.state = state;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = 0.01F;
			this.x = state.decoy.getX();
			this.y = state.decoy.getY() + 1.0;
			this.z = state.decoy.getZ();
		}

		@Override
		public void tick() {
			if (state.decoy.isRemoved() || STATES.get(state.decoy.getId()) != state) {
				this.setDone();
				return;
			}

			this.x = state.decoy.getX();
			this.y = state.decoy.getY() + 1.0;
			this.z = state.decoy.getZ();
			float fade = 1.0F - state.decoy.dissolve(0.0F);
			this.volume = Math.max(0.01F, 0.35F * (float) TitanfallConfig.get().movementVolume * fade * state.projected(0.0F));
		}
	}
}
