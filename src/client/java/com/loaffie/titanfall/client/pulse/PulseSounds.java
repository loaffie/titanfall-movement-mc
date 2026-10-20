package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * Pulse Blade sounds ({@code Pilot_PulseBlade_*}): the unsheath and the throw at the pilot (in the local
 * pilot's own head), the bite and every sonar ping at the kunai, and the alarm a revealed pilot hears.
 */
public final class PulseSounds {
	private PulseSounds() {
	}

	private static boolean enabled() {
		return TitanfallConfig.get().soundsEnabled;
	}

	private static float volume(float base) {
		return base * (float) TitanfallConfig.get().movementVolume;
	}

	static void draw(PlayerEntity player) {
		atPilot(player, TitanfallSounds.PULSE_DRAW, 0.8F);
	}

	public static void toss(PlayerEntity player) {
		atPilot(player, TitanfallSounds.PULSE_THROW, 0.9F);
	}

	static void impact(Entity blade) {
		at(blade, TitanfallSounds.PULSE_IMPACT, 1.0F, 1.0F);
	}

	/** A ping carries a long way: a loud sound fades out over volume x 16 blocks. */
	static void ping(Entity blade) {
		at(blade, TitanfallSounds.PULSE_PING, 2.0F, 1.0F);
	}

	static void detected() {
		if (enabled()) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(TitanfallSounds.PULSE_DETECTED.getId(),
					SoundCategory.PLAYERS, volume(0.8F), 1.0F, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE,
					0.0, 0.0, 0.0, true));
		}
	}

	private static void atPilot(PlayerEntity player, SoundEvent event, float base) {
		if (!enabled()) {
			return;
		}

		float pitch = 0.96F + player.getRandom().nextFloat() * 0.08F;
		if (player == MinecraftClient.getInstance().player) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(event.getId(), SoundCategory.PLAYERS,
					volume(base), pitch, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		} else {
			player.getWorld().playSound(player.getX(), player.getEyeY() - 0.4, player.getZ(), event, SoundCategory.PLAYERS, volume(base), pitch, false);
		}
	}

	private static void at(Entity blade, SoundEvent event, float base, float pitch) {
		if (enabled()) {
			Random random = blade.getWorld().getRandom();
			blade.getWorld().playSound(blade.getX(), blade.getY(), blade.getZ(), event, SoundCategory.PLAYERS, volume(base),
					pitch * (0.98F + random.nextFloat() * 0.04F), false);
		}
	}
}
