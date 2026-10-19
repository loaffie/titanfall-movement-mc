package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.phase.PhaseShift;
import com.loaffie.titanfall.phase.PhaseSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every phase the client knows about, by player entity id: the local pilot's (started by
 * {@code PhaseController}) and other pilots' (from the server's relay). It is the client's answer to
 * {@link PhaseShift#isPhased}, and drives the sounds, the ghost and the flashes, the gauntlet's glow, the
 * activation animation and, for the local pilot, the phase world on screen.
 */
public final class PhaseStates {
	/** Length of the activation animation, seconds: the hand comes up, the coil fires, the hand drops. */
	public static final float ACTIVATE_SECONDS = 0.9F;
	/** The warning that the way back is opening plays this long before the end ({@code SHIFTER_OUTRO_TIME_BEFORE_END}). */
	private static final int WARNING_LEAD_TICKS = 30;
	/** After a phase ends the gauntlet's coil keeps glowing, fading out over this long, ticks. */
	public static final int COOL_TICKS = 12;

	/** One phase. */
	public static final class State {
		/** Ticks since the shift, and the phase's length. */
		public int age;
		public final int ticks;
		/** Ticks since the pilot came back, or -1 while still phased. */
		public int cooling = -1;

		State(int ticks) {
			this.ticks = ticks;
		}

		public boolean phased() {
			return cooling < 0;
		}

		/** Seconds since the shift, for the activation animation. */
		public float seconds(float tickDelta) {
			return (age + tickDelta) / 20.0F;
		}

		/** The gauntlet's glow 0..1: up at once, held while phased, fading out after. */
		public float glow(float tickDelta) {
			if (phased()) {
				return 1.0F;
			}

			return Math.max(0.0F, 1.0F - (cooling + tickDelta) / COOL_TICKS);
		}

		/**
		 * How deep into the phase world the view is, 0..1: in within a few frames of the shift, out within a
		 * few of the return.
		 */
		public float depth(float tickDelta) {
			if (!phased()) {
				return Math.max(0.0F, 1.0F - (cooling + tickDelta) / 3.0F);
			}

			return Math.min(1.0F, (age + tickDelta) / 3.0F);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private PhaseStates() {
	}

	public static void register() {
		PhaseShift.setClientLookup(entity -> {
			State state = STATES.get(entity.getId());
			return state != null && state.phased();
		});
	}

	/** {@code player} phases out now, for {@code ticks}. */
	public static State start(PlayerEntity player, int ticks) {
		State state = new State(ticks);
		STATES.put(player.getId(), state);
		PhaseSounds.activate(player, state);
		PhaseEffects.shiftOut(player);
		return state;
	}

	/** The player's phase - running, or just over and cooling down - or null. */
	public static State of(PlayerEntity player) {
		return STATES.get(player.getId());
	}

	/** True while the player is phased. */
	public static boolean phased(Entity entity) {
		State state = STATES.get(entity.getId());
		return state != null && state.phased();
	}

	/**
	 * True when {@code entity} can't be seen from where the local pilot is: a phased pilot from the world, and
	 * - while the local pilot is phased - everything in the world from them, but other phased pilots.
	 */
	public static boolean hidden(Entity entity) {
		ClientPlayerEntity local = MinecraftClient.getInstance().player;
		if (local == null || entity == local || STATES.isEmpty()) {
			return false;
		}

		return phased(local) != phased(entity);
	}

	/** From the server: another pilot shifted, or a phase ended early (or a local shift was refused). */
	public static void receive(PhaseSyncPayload payload) {
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
			if (state != null && state.phased()) {
				end(player, state);
			}
		}
	}

	/** End of every client tick: age every phase, cue the warning, bring pilots back, drop cooled phases. */
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
			if (state.phased()) {
				if (state.age == state.ticks - WARNING_LEAD_TICKS) {
					PhaseSounds.warning(player);
				}

				if (state.age >= state.ticks) {
					end(player, state);
				} else {
					PhaseEffects.phasedTick(player, state);
				}
			} else if (++state.cooling >= COOL_TICKS && state.age > ACTIVATE_SECONDS * 20.0F) {
				iterator.remove();
			}
		}
	}

	private static void end(PlayerEntity player, State state) {
		state.cooling = 0;
		PhaseSounds.end(player);
		PhaseEffects.shiftIn(player);
	}
}
