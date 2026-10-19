package com.loaffie.titanfall.client.stim;

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
 * Stim sounds, as Titanfall 2 cues them: the injection when it fires, a loop for as long as it runs (the
 * pilot's racing heart), and the comedown two seconds before it ends. The local pilot hears them in their
 * own head; other pilots' stims are heard where they are.
 */
public final class StimSounds {
	private StimSounds() {
	}

	static void activate(PlayerEntity player, StimStates.State state) {
		if (!TitanfallConfig.get().soundsEnabled) {
			return;
		}

		play(player, TitanfallSounds.STIM_ACTIVATE, 1.0F);
		MinecraftClient.getInstance().getSoundManager().play(new LoopSound(player, state, isLocal(player)));
	}

	static void deactivate(PlayerEntity player) {
		if (TitanfallConfig.get().soundsEnabled) {
			play(player, TitanfallSounds.STIM_DEACTIVATE, 0.9F);
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

	/** The stim loop: follows its pilot, fades in with the stim and out as it wears off. */
	private static final class LoopSound extends MovingSoundInstance {
		private final PlayerEntity player;
		private final StimStates.State state;

		LoopSound(PlayerEntity player, StimStates.State state, boolean local) {
			super(TitanfallSounds.STIM_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.player = player;
			this.state = state;
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = local;
			this.attenuationType = local ? SoundInstance.AttenuationType.NONE : SoundInstance.AttenuationType.LINEAR;
			this.volume = 0.01F;
			follow();
		}

		@Override
		public void tick() {
			if (player.isRemoved() || StimStates.of(player) != state) {
				this.setDone();
				return;
			}

			this.volume = volume(0.8F) * Math.max(0.01F, state.intensity(0.0F));
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
