package com.loaffie.titanfall.client.awall;

import com.loaffie.titanfall.awall.AWall;
import com.loaffie.titanfall.awall.AWallEntity;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * Each A-Wall as the client sees it: how far the wall has risen since it was seen to plant (one that was
 * already up when it came into view is drawn up), how far it has come down, the flash as it stops a shot, and
 * its hum. Also who has a disc out, for the packs ({@link AWallFeatureRenderer}).
 */
public final class AWallStates {
	/** The wall rises over this many ticks as it goes up. */
	public static final int RISE_TICKS = 7;
	/** A stopped shot lights the wall up for this many ticks. */
	private static final int FLASH_TICKS = 6;
	/** In its last ticks the wall starts to stutter, a warning it is about to drop. */
	private static final int WARNING_TICKS = 50;

	private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

	private AWallStates() {
	}

	public static final class State {
		final AWallEntity wall;
		/** Ticks since the wall was seen to go up; starts at its full rise if it was already up. */
		int standing = -1;
		/** Ticks since it was seen to start coming down, or -1. */
		int collapsing = -1;
		int flash;
		float lastHealth = 1.0F;
		boolean humming;

		State(AWallEntity wall) {
			this.wall = wall;
			if (wall.planted()) {
				standing = RISE_TICKS;
			}
		}

		/** How far the wall is up, 0..1: rising, standing, coming down. */
		public float raised(float tickDelta) {
			if (standing < 0) {
				return 0.0F;
			}

			float rise = MathHelper.clamp((standing + tickDelta) / RISE_TICKS, 0.0F, 1.0F);
			if (collapsing >= 0) {
				rise *= 1.0F - MathHelper.clamp((collapsing + tickDelta) / AWall.COLLAPSE_TICKS, 0.0F, 1.0F);
			}

			return rise;
		}

		/** The disc's legs and mast: out as the wall rises, and staying out while it comes down. */
		public float unfold(float tickDelta) {
			return standing < 0 ? 0.0F : MathHelper.clamp((standing + tickDelta) / (RISE_TICKS * 0.6F), 0.0F, 1.0F);
		}

		/** The flash of a stopped shot, 0..1. */
		public float flash(float tickDelta) {
			return flash <= 0 ? 0.0F : MathHelper.clamp((flash - tickDelta) / FLASH_TICKS, 0.0F, 1.0F);
		}

		/** The end is near: 0 until the warning, then 0..1 to the end. */
		public float warning() {
			int left = AWall.durationTicks(TitanfallConfig.get()) - standing;
			return collapsing >= 0 || standing < 0 ? 0.0F : MathHelper.clamp(1.0F - left / (float) WARNING_TICKS, 0.0F, 1.0F);
		}

		public int age() {
			return Math.max(0, standing);
		}
	}

	public static State of(AWallEntity wall) {
		return STATES.get(wall.getId());
	}

	public static void loaded(AWallEntity wall, ClientWorld world) {
		STATES.put(wall.getId(), new State(wall));
		if (!wall.planted() && wall.getOwner() instanceof PlayerEntity owner && owner != MinecraftClient.getInstance().player) {
			AWallAnimation.released(owner);
		}
	}

	public static void unloaded(AWallEntity wall) {
		STATES.remove(wall.getId());
	}

	/** True if the player has a disc in flight or a wall up that we can see. */
	public static boolean hasWallOut(PlayerEntity player) {
		for (State state : STATES.values()) {
			Entity owner = state.wall.getOwner();
			if (owner == player && !state.wall.isRemoved()) {
				return true;
			}
		}

		return false;
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.isPaused()) {
			if (client.world == null) {
				STATES.clear();
			}

			return;
		}

		for (State state : STATES.values()) {
			AWallEntity wall = state.wall;
			if (wall.planted() && state.standing < 0) {
				state.standing = 0;
				AWallEffects.planted(wall);
			} else if (state.standing >= 0) {
				state.standing++;
			}

			if (wall.state() == AWallEntity.COLLAPSING && state.collapsing < 0) {
				state.collapsing = 0;
				AWallEffects.collapsed(wall, state.lastHealth <= 0.0F || wall.healthFraction() <= 0.0F);
			} else if (state.collapsing >= 0) {
				state.collapsing++;
			}

			float health = wall.healthFraction();
			if (health < state.lastHealth - 1.0E-4F) {
				state.flash = FLASH_TICKS;
			} else if (state.flash > 0) {
				state.flash--;
			}

			state.lastHealth = health;
			if (wall.standing()) {
				AWallEffects.idle(wall, state);
			}

			if (!state.humming && wall.standing() && TitanfallConfig.get().soundsEnabled) {
				state.humming = true;
				client.getSoundManager().play(new HumSound(state));
			}
		}
	}

	/** The wall's low hum, heard nearby while it stands. */
	private static final class HumSound extends MovingSoundInstance {
		private final State state;

		HumSound(State state) {
			super(TitanfallSounds.AWALL_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.state = state;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = 0.01F;
			this.x = state.wall.getX();
			this.y = state.wall.getY() + 1.0;
			this.z = state.wall.getZ();
		}

		@Override
		public void tick() {
			if (state.wall.isRemoved() || STATES.get(state.wall.getId()) != state) {
				this.setDone();
				return;
			}

			this.volume = Math.max(0.01F, 0.5F * (float) TitanfallConfig.get().movementVolume * state.raised(0.0F));
			this.pitch = 1.0F + 0.15F * state.flash(0.0F) - 0.1F * state.warning();
		}
	}
}
