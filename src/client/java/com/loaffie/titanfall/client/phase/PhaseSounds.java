package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Phase Shift sounds, cued as Titanfall 2 cues them ({@code Pilot_PhaseShift_*}): the shift out, a drone for
 * as long as the pilot is phased, the warning a second and a half before the way back opens, and the
 * return. The local pilot hears them in their own head; other pilots are heard where they are - a phased
 * pilot can't be seen, but the hum gives them away to anyone close.
 */
public final class PhaseSounds {
	private PhaseSounds() {
	}

	static void activate(PlayerEntity player, PhaseStates.State state) {
		if (!TitanfallConfig.get().soundsEnabled) {
			return;
		}

		play(player, TitanfallSounds.PHASE_ACTIVATE, 1.0F);
		MinecraftClient.getInstance().getSoundManager().play(new LoopSound(player, state, isLocal(player)));
	}

	static void warning(PlayerEntity player) {
		if (TitanfallConfig.get().soundsEnabled) {
			play(player, TitanfallSounds.PHASE_WARNING, isLocal(player) ? 0.7F : 0.45F);
		}
	}

	static void end(PlayerEntity player) {
		if (TitanfallConfig.get().soundsEnabled) {
			play(player, TitanfallSounds.PHASE_END, 1.0F);
		}
	}

	private static boolean isLocal(PlayerEntity player) {
		return player == MinecraftClient.getInstance().player;
	}

	private static float volume(float base) {
		return base * (float) TitanfallConfig.get().movementVolume;
	}

	private static void play(PlayerEntity player, SoundEvent event, float base) {
		float pitch = 0.97F + player.getRandom().nextFloat() * 0.06F;
		if (isLocal(player)) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(event.getId(), SoundCategory.PLAYERS,
					volume(base), pitch, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		} else {
			player.getWorld().playSound(player.getX(), player.getY() + 1.0, player.getZ(), event, SoundCategory.PLAYERS, volume(base), pitch, false);
		}
	}

	/** The phase drone: follows its pilot while they are phased and fades out as they come back. */
	private static final class LoopSound extends MovingSoundInstance {
		private final PlayerEntity player;
		private final PhaseStates.State state;
		private final float base;

		LoopSound(PlayerEntity player, PhaseStates.State state, boolean local) {
			super(TitanfallSounds.PHASE_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.player = player;
			this.state = state;
			this.base = local ? 0.75F : 0.4F;
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = local;
			this.attenuationType = local ? SoundInstance.AttenuationType.NONE : SoundInstance.AttenuationType.LINEAR;
			this.volume = 0.01F;
			follow();
		}

		@Override
		public void tick() {
			if (player.isRemoved() || PhaseStates.of(player) != state || (!state.phased() && state.cooling >= 4)) {
				this.setDone();
				return;
			}

			float fade = state.phased() ? Math.min(1.0F, state.age / 4.0F) : 1.0F - state.cooling / 4.0F;
			this.volume = volume(base) * Math.max(0.01F, fade);
			follow();
		}

		private void follow() {
			if (!this.relative) {
				this.x = player.getX();
				this.y = player.getY() + 1.0;
				this.z = player.getZ();
			}
		}
	}
}
