package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.stim.StimSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every running stim the client knows about, by player entity id: the local pilot's (started by
 * {@code StimController}) and other pilots' (from the server's relay). Drives the trail, the sounds, the
 * injection animation and, for the local pilot, the screen effects.
 */
public final class StimStates {
	/** Length of the injection animation, seconds: reach for the hip, jab, and put it back. */
	public static final float INJECT_SECONDS = 0.6F;
	/** The end-of-stim sound plays this long before the stim ends (as Titanfall's deactivate cue does). */
	private static final int DEACTIVATE_LEAD_TICKS = 40;
	/** The tick of the injection animation the needle goes in (40% through it): the flare. */
	private static final int JAB_TICK = Math.round(INJECT_SECONDS * 20.0F * 0.4F);

	/** One running stim. */
	public static final class State {
		/** Ticks since injection, and the length of the speed boost and of the stim itself. */
		public int age;
		final int boostTicks;
		final int durationTicks;
		State(int boostTicks, int durationTicks) {
			this.boostTicks = boostTicks;
			this.durationTicks = durationTicks;
		}

		/** Speed boost level 0..1: full, easing out over the boost's last quarter second. */
		public float boost(float tickDelta) {
			float left = boostTicks - (age + tickDelta);
			return left <= 0.0F ? 0.0F : Math.min(1.0F, left / 5.0F);
		}

		/** The stim's visual intensity 0..1: up within a few frames, down over its last half second. */
		public float intensity(float tickDelta) {
			float t = age + tickDelta;
			float in = Math.min(1.0F, t / 3.0F);
			float out = Math.min(1.0F, Math.max(0.0F, (durationTicks + 10 - t) / 10.0F));
			return in * out;
		}

		/** Injection animation progress 0..1, or 1 once it is over. */
		public float inject(float tickDelta) {
			return Math.min(1.0F, (age + tickDelta) / (INJECT_SECONDS * 20.0F));
		}

		boolean over() {
			return age >= Math.max(boostTicks, durationTicks + 10);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private StimStates() {
	}

	/** A stim starts on {@code player} now. */
	public static State start(PlayerEntity player, int boostTicks) {
		int duration = (int) Math.round(TitanfallConfig.get().stimDurationSeconds * 20.0);
		State state = new State(boostTicks, Math.min(duration, boostTicks));
		STATES.put(player.getId(), state);
		StimSounds.activate(player, state);
		return state;
	}

	/** The player's running stim, or null. */
	public static State of(PlayerEntity player) {
		return STATES.get(player.getId());
	}

	/** Another pilot's stim, relayed by the server. */
	public static void receive(StimSyncPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && client.world.getEntityById(payload.player()) instanceof PlayerEntity player && player != client.player) {
			start(player, payload.ticks());
		}
	}

	/** End of every client tick: age every stim, cue the end sound, lay the trails, drop finished stims. */
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
			if (state.age == JAB_TICK) {
				StimEffects.inject(player);
			}

			if (state.age == state.durationTicks - DEACTIVATE_LEAD_TICKS) {
				StimSounds.deactivate(player);
			}

			if (state.age < state.durationTicks) {
				StimEffects.trail(player, state);
			}

			if (state.over()) {
				iterator.remove();
			}
		}
	}
}
