package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.PulseController;
import com.loaffie.titanfall.pulse.PulseSyncPayload;
import com.loaffie.titanfall.pulse.PulseThrowPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every Pulse Blade throw in progress that the client knows of, by player entity id: the local pilot's
 * (driven by {@code PulseController}) and other pilots' (from the server's relay). Drives the arm in first
 * and third person and the kunai in the hand. Timings from Titanfall's {@code mp_weapon_grenade_sonar}:
 * {@code toss_pullout_time} 0.18 s to draw, {@code toss_time} 0.385 s to throw.
 */
public final class PulseStates {
	/** The draw, ticks: the hand comes up with the kunai. */
	public static final int DRAW_TICKS = 4;
	/** The throw, ticks; the kunai leaves the hand at {@link #RELEASE_TICK}. */
	public static final int THROW_TICKS = 8;
	public static final int RELEASE_TICK = 3;
	/** The swing is done (the arm across the body) this far into the throw. */
	private static final float SWING_END = 0.45F;

	public enum Stage { DRAW, AIM, THROW }

	/** One throw in progress. */
	public static final class State {
		public Stage stage = Stage.DRAW;
		/** Ticks into the current stage. */
		public int ticks;

		/** How far the arm is up out of its rest pose, 0..1. */
		public float raise(float tickDelta) {
			float t = ticks + tickDelta;
			return switch (stage) {
				case DRAW -> smooth(t / DRAW_TICKS);
				case AIM -> 1.0F;
				case THROW -> 1.0F - smooth((t / THROW_TICKS - SWING_END) / (1.0F - SWING_END));
			};
		}

		/** How far through the throwing swing the arm is: 0 cocked back, 1 followed through. */
		public float swing(float tickDelta) {
			return stage == Stage.THROW ? smooth((ticks + tickDelta) / THROW_TICKS / SWING_END) : 0.0F;
		}

		/** Whether the kunai is still in the hand. */
		public boolean inHand() {
			return stage != Stage.THROW || ticks < RELEASE_TICK;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	/** The world tick each pilot last threw, so their sheath shows empty while the next kunai charges. */
	private static final Map<Integer, Long> LAST_THROW = new HashMap<>();

	private PulseStates() {
	}

	static float smooth(float x) {
		float k = MathHelper.clamp(x, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}

	public static State of(PlayerEntity player) {
		return STATES.get(player.getId());
	}

	/** {@code player} draws the kunai to aim. */
	public static State draw(PlayerEntity player) {
		State state = new State();
		STATES.put(player.getId(), state);
		PulseSounds.draw(player);
		return state;
	}

	/** {@code player} starts the throwing swing, {@code ticks} into it. */
	public static void swing(PlayerEntity player, int ticks) {
		State state = STATES.computeIfAbsent(player.getId(), id -> new State());
		state.stage = Stage.THROW;
		state.ticks = ticks;
		LAST_THROW.put(player.getId(), player.getWorld().getTime() - ticks);
	}

	/** Whether {@code player}'s next kunai is in its sheath: not in the hand, and charged since the last throw. */
	public static boolean sheathed(PlayerEntity player) {
		State state = STATES.get(player.getId());
		if (state != null && state.inHand()) {
			return false;
		}

		if (player == MinecraftClient.getInstance().player) {
			return PulseController.ready();
		}

		Long last = LAST_THROW.get(player.getId());
		return last == null || player.getWorld().getTime() - last >= TitanfallConfig.get().pulseCooldownSeconds * 20.0;
	}

	public static void cancel(PlayerEntity player) {
		STATES.remove(player.getId());
	}

	/** Another pilot drew, threw or put away their kunai. */
	public static void receive(PulseSyncPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !(client.world.getEntityById(payload.player()) instanceof PlayerEntity player) || player == client.player) {
			return;
		}

		switch (payload.stage()) {
			case PulseThrowPayload.AIM -> draw(player);
			case PulseThrowPayload.THROW -> {
				swing(player, RELEASE_TICK);
				PulseSounds.toss(player);
			}
			default -> cancel(player);
		}
	}

	/** End of every client tick: draws become aims, finished throws are dropped. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			STATES.clear();
			LAST_THROW.clear();
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

			state.ticks++;
			if (state.stage == Stage.DRAW && state.ticks >= DRAW_TICKS) {
				state.stage = Stage.AIM;
				state.ticks = 0;
			} else if (state.stage == Stage.THROW && state.ticks >= THROW_TICKS) {
				iterator.remove();
			}
		}
	}
}
