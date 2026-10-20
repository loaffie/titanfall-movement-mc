package com.loaffie.titanfall.client.cloak;

import com.loaffie.titanfall.cloak.Cloak;
import com.loaffie.titanfall.cloak.CloakSyncPayload;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.TitanfallSounds;
import com.loaffie.titanfall.particle.TitanfallParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every cloak the client knows about, by player entity id: the local pilot's (started by
 * {@code CloakController}) and other pilots' (from the server's relay). It is the client's answer to
 * {@link Cloak#isCloaked}, and drives the fade, the shimmer, the sounds and the glints shed as the cloak
 * closes and falls away. A cloak that has dropped stays a few ticks more while the pilot fades back in.
 */
public final class CloakStates {
	/** The cloak closes over this long, and falls away over the same, ticks. */
	public static final int FADE_TICKS = 8;
	/** The warning that the cloak is about to fail plays this long before it does, ticks. */
	private static final int WARNING_LEAD_TICKS = 60;
	private static final int GLINTS = 22;

	/** One cloak. */
	public static final class State {
		public int age;
		public final int ticks;
		/** Ticks since the cloak dropped, or -1 while it holds. */
		public int dropped = -1;

		State(int ticks) {
			this.ticks = ticks;
		}

		public boolean holding() {
			return dropped < 0;
		}

		/** How fully the pilot is cloaked, 0..1: closing over the first ticks, falling away after it drops. */
		public float amount(float tickDelta) {
			if (!holding()) {
				return Math.max(0.0F, 1.0F - (dropped + tickDelta) / FADE_TICKS);
			}

			return Math.min(1.0F, (age + tickDelta) / FADE_TICKS);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private CloakStates() {
	}

	public static void register() {
		Cloak.setClientLookup(entity -> {
			State state = STATES.get(entity.getId());
			return state != null && state.holding();
		});
	}

	public static State of(Entity entity) {
		return STATES.get(entity.getId());
	}

	/** How fully the entity is cloaked right now, 0..1 (0 for anyone not cloaked or fading). */
	public static float amount(Entity entity, float tickDelta) {
		if (STATES.isEmpty()) {
			return 0.0F;
		}

		State state = STATES.get(entity.getId());
		return state == null ? 0.0F : state.amount(tickDelta);
	}

	public static State start(PlayerEntity player, int ticks) {
		State state = new State(ticks);
		STATES.put(player.getId(), state);
		play(player, TitanfallSounds.CLOAK_ACTIVATE, 0.9F);
		if (isLocal(player) && TitanfallConfig.get().soundsEnabled) {
			MinecraftClient.getInstance().getSoundManager().play(new LoopSound(player, state));
		}

		glints(player);
		return state;
	}

	/** From the server: another pilot cloaked, or a cloak dropped (it ran out, they fired, or it was refused). */
	public static void receive(CloakSyncPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !(client.world.getEntityById(payload.player()) instanceof PlayerEntity player)) {
			return;
		}

		if (payload.ticks() > 0) {
			if (player != client.player) {
				start(player, payload.ticks());
			}
		} else {
			State state = STATES.get(player.getId());
			if (state != null && state.holding()) {
				drop(player, state);
			}
		}
	}

	/** End of every client tick: age every cloak, warn, drop expired ones, forget faded ones. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			STATES.clear();
			return;
		}

		Iterator<Map.Entry<Integer, State>> iterator = STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, State> entry = iterator.next();
			State state = entry.getValue();
			Entity entity = client.world.getEntityById(entry.getKey());
			if (!(entity instanceof PlayerEntity player) || !player.isAlive()) {
				iterator.remove();
				continue;
			}

			state.age++;
			if (state.holding()) {
				if (state.age == state.ticks - WARNING_LEAD_TICKS && isLocal(player)) {
					play(player, TitanfallSounds.CLOAK_WARNING, 0.7F);
				}

				if (state.age >= state.ticks) {
					drop(player, state);
				}
			} else if (++state.dropped >= FADE_TICKS) {
				iterator.remove();
			}
		}
	}

	private static void drop(PlayerEntity player, State state) {
		state.dropped = 0;
		play(player, TitanfallSounds.CLOAK_END, 0.9F);
		glints(player);
	}

	/** Faint glints over the body as the light bends round it. */
	private static void glints(PlayerEntity player) {
		if (!TitanfallConfig.get().thrusterParticles || (isLocal(player) && MinecraftClient.getInstance().options.getPerspective().isFirstPerson())) {
			return;
		}

		Random random = player.getRandom();
		for (int i = 0; i < GLINTS; i++) {
			double a = random.nextDouble() * MathHelper.TAU;
			double r = player.getWidth() * (0.35 + random.nextDouble() * 0.3);
			player.getWorld().addParticle(TitanfallParticles.CLOAK_SHIMMER, player.getX() + Math.cos(a) * r,
					player.getY() + random.nextDouble() * player.getHeight(), player.getZ() + Math.sin(a) * r,
					Math.cos(a) * 0.01, 0.01 + random.nextDouble() * 0.02, Math.sin(a) * 0.01);
		}
	}

	private static boolean isLocal(PlayerEntity player) {
		return player == MinecraftClient.getInstance().player;
	}

	private static void play(PlayerEntity player, SoundEvent event, float base) {
		TitanfallConfig config = TitanfallConfig.get();
		if (!config.soundsEnabled) {
			return;
		}

		float volume = base * (float) config.movementVolume;
		float pitch = 0.97F + player.getRandom().nextFloat() * 0.06F;
		if (isLocal(player)) {
			MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(event.getId(), SoundCategory.PLAYERS,
					volume, pitch, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
		} else {
			player.getWorld().playSound(player.getX(), player.getY() + 1.0, player.getZ(), event, SoundCategory.PLAYERS, volume * 0.7F, pitch, false);
		}
	}

	/** The cloak's faint hum, in the pilot's own head only, while it holds. */
	private static final class LoopSound extends MovingSoundInstance {
		private final PlayerEntity player;
		private final State state;

		LoopSound(PlayerEntity player, State state) {
			super(TitanfallSounds.CLOAK_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
			this.player = player;
			this.state = state;
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = true;
			this.attenuationType = SoundInstance.AttenuationType.NONE;
			this.volume = 0.01F;
		}

		@Override
		public void tick() {
			if (player.isRemoved() || STATES.get(player.getId()) != state) {
				this.setDone();
				return;
			}

			this.volume = Math.max(0.01F, 0.45F * (float) TitanfallConfig.get().movementVolume * state.amount(0.0F));
		}
	}
}
