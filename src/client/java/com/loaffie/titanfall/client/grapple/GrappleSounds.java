package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Grapple sounds. The local pilot hears the launcher, the reel and the meter in their own ears (relative,
 * like the jumpkit's sounds); other pilots' grapples are heard where they are. The hook's bite is always
 * placed at the hook, so a long shot is heard striking in the distance.
 */
public final class GrappleSounds {
	/** Reel loops by player: the local one and any other pilot being reeled in. */
	private static final Map<PlayerEntity, ReelSound> REELS = new WeakHashMap<>();

	private GrappleSounds() {
	}

	public static void fire(PlayerEntity player) {
		at(player, TitanfallSounds.GRAPPLE_FIRE, 1.0F);
	}

	public static void release(PlayerEntity player) {
		stopReel(player);
		at(player, TitanfallSounds.GRAPPLE_RELEASE, 0.85F);
	}

	public static void impact(PlayerEntity player, Vec3d at) {
		if (!enabled()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		float pitch = 0.95F + player.getRandom().nextFloat() * 0.1F;
		client.getSoundManager().play(new PositionedSoundInstance(TitanfallSounds.GRAPPLE_IMPACT, SoundCategory.PLAYERS, volume(1.0F), pitch,
				SoundInstance.createRandom(), at.x, at.y, at.z));
	}

	/** Meter refilled: the HUD chime. */
	public static void ready() {
		local(TitanfallSounds.GRAPPLE_READY, 0.7F);
	}

	/** Key pressed with the meter empty. */
	public static void denied() {
		local(TitanfallSounds.GRAPPLE_DENIED, 0.6F);
	}

	public static void startReel(PlayerEntity player, GrappleLine line) {
		stopReel(player);
		if (!enabled()) {
			return;
		}

		ReelSound sound = new ReelSound(player, line, player == MinecraftClient.getInstance().player);
		REELS.put(player, sound);
		MinecraftClient.getInstance().getSoundManager().play(sound);
	}

	public static void stopReel() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			stopReel(client.player);
		}
	}

	private static void stopReel(PlayerEntity player) {
		ReelSound sound = REELS.remove(player);
		if (sound != null) {
			MinecraftClient.getInstance().getSoundManager().stop(sound);
		}
	}

	private static boolean enabled() {
		return TitanfallConfig.get().soundsEnabled;
	}

	private static float volume(float base) {
		return base * (float) TitanfallConfig.get().movementVolume;
	}

	/** In the local pilot's ears, or at another pilot. */
	private static void at(PlayerEntity player, SoundEvent event, float base) {
		if (!enabled()) {
			return;
		}

		float pitch = 0.96F + player.getRandom().nextFloat() * 0.08F;
		if (player == MinecraftClient.getInstance().player) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(event.getId(), SoundCategory.PLAYERS,
					volume(base), pitch, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		} else {
			player.getWorld().playSound(player.getX(), player.getY() + 1.2, player.getZ(), event, SoundCategory.PLAYERS, volume(base), pitch, false);
		}
	}

	private static void local(SoundEvent event, float base) {
		if (enabled()) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(event.getId(), SoundCategory.PLAYERS,
					volume(base), 1.0F, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		}
	}

	/**
	 * The winch while the line is reeling. It follows its pilot, and its pitch and volume ride the closing
	 * speed: a strained, low whir when the line first takes the weight, climbing as the pilot picks up
	 * speed. Ends itself when the line lets go.
	 */
	private static final class ReelSound extends MovingSoundInstance {
		private final PlayerEntity player;
		private final GrappleLine line;
		private float level;

		ReelSound(PlayerEntity player, GrappleLine line, boolean local) {
			super(TitanfallSounds.GRAPPLE_REEL, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.player = player;
			this.line = line;
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = local;
			this.attenuationType = local ? SoundInstance.AttenuationType.NONE : SoundInstance.AttenuationType.LINEAR;
			this.volume = 0.01F;
			this.pitch = 0.8F;
			updatePosition();
		}

		@Override
		public void tick() {
			if (player.isRemoved() || line.phase != GrappleLine.Phase.ATTACHED) {
				this.setDone();
				return;
			}

			// Remote pilots: their client's pull is not sent, so estimate it from how fast they close on the hook.
			float pull = line.pull;
			if (!this.relative) {
				Vec3d motion = player.getPos().subtract(player.prevX, player.prevY, player.prevZ).multiply(20.0);
				Vec3d toward = line.position(player.getWorld(), 1.0F).subtract(player.getPos()).normalize();
				pull = (float) motion.dotProduct(toward);
			}

			float target = MathHelper.clamp(pull / 12.0F, 0.0F, 1.0F);
			level += (target - level) * 0.35F;
			this.pitch = 0.78F + 0.42F * level;
			this.volume = volume(0.35F + 0.5F * level) * Math.min(1.0F, (line.age + 1) / 3.0F);
			updatePosition();
		}

		private void updatePosition() {
			if (!this.relative) {
				this.x = player.getX();
				this.y = player.getY() + 1.2;
				this.z = player.getZ();
			}
		}
	}
}
