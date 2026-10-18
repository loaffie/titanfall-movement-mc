package com.loaffie.titanfall.client.particle;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How hard each pilot's thrusters are burning, shared by the steady flames and the animated flaps so the
 * two always agree.
 *
 * <p>Movement code <em>fuels</em> the thrusters every tick with a level - a small idle burn while airborne,
 * a full burn on a wall run - and a double jump adds a short <em>flare</em> on top. Renderers read a level
 * smoothed per frame from frame time, so the flame grows and shrinks and the flaps open and close
 * smoothly at any frame rate.
 */
public final class PilotThrusters {
	/** Small blue idle burn while airborne: jumping, after a double jump, or falling. */
	public static final float AIR = 0.35F;
	/** Slide boost: the modules aim back along the slide and burn hard, as if driving it. */
	public static final float SLIDE = 0.75F;
	/** Wall run: the module away from the wall "carries" the pilot with a strong controlled jet ... */
	public static final float WALL_CARRY = 0.95F;
	/** ... while the module against the wall only idles a little above the air burn. */
	public static final float WALL_SIDE = 0.45F;
	/** Ticks a double-jump flare takes to die away. */
	private static final float FLARE_TICKS = 8.0F;
	private static final float RISE_RATE = 12.0F;
	private static final float FALL_RATE = 7.0F;
	/** A full burn has eased below 2% after this many ticks at {@link #FALL_RATE} (ln 50 / 7 s = 11 ticks). */
	private static final int DIE_DOWN_TICKS = 12;

	/** Turbine speed, radians per second: idling, and added per unit of burn. */
	private static final float TURBINE_IDLE = (float) (Math.PI * 1.2);
	private static final float TURBINE_BURN = (float) (Math.PI * 9.0);

	private static final Map<PlayerEntity, State> STATES = new WeakHashMap<>();

	private static final class State {
		long fuelTick = Long.MIN_VALUE / 2;
		/** Fuelled level per thruster port (0 = the mesh's +X module, 1 = the -X one). */
		final float[] fuelLevel = new float[2];
		long flareTick = Long.MIN_VALUE / 2;
		/** Smoothed level per port. */
		final float[] level = new float[2];
		/** Turbine angle per port, radians: it idles round slowly and spins up with the burn. */
		final float[] spin = new float[2];
		long lastNanos;
	}

	private PilotThrusters() {
	}

	private static State state(PlayerEntity player) {
		return STATES.computeIfAbsent(player, ignored -> new State());
	}

	/** Keeps both thrusters burning at least at {@code level} this tick (the strongest call of a tick wins). */
	static void fuel(PlayerEntity player, float level) {
		fuel(player, level, level);
	}

	/** Keeps each thruster burning at its own level this tick (per port, the strongest call of a tick wins). */
	static void fuel(PlayerEntity player, float port0, float port1) {
		State state = state(player);
		long now = player.getWorld().getTime();
		boolean same = state.fuelTick == now;
		state.fuelLevel[0] = same ? Math.max(state.fuelLevel[0], port0) : port0;
		state.fuelLevel[1] = same ? Math.max(state.fuelLevel[1], port1) : port1;
		state.fuelTick = now;
	}

	/** A double-jump surge on top of the current burn. */
	static void flare(PlayerEntity player) {
		state(player).flareTick = player.getWorld().getTime();
	}

	/** Whether movement fuelled the thrusters this tick or the last one. */
	static boolean fuelled(PlayerEntity player) {
		State state = STATES.get(player);
		return state != null && player.getWorld().getTime() - state.fuelTick <= 1;
	}

	/** The double-jump surge, 1 right after the jump easing to 0. */
	public static float flare(PlayerEntity player, float tickDelta) {
		State state = STATES.get(player);
		if (state == null) {
			return 0.0F;
		}

		float t = (player.getWorld().getTime() - state.flareTick + tickDelta) / FLARE_TICKS;
		return t < 0.0F || t >= 1.0F ? 0.0F : (1.0F - t) * (1.0F - t);
	}

	/**
	 * One thruster's burn level smoothed to this frame: eases toward its fuelled level (0 once fuelling
	 * stops), rising quickly and dying down a little slower. Safe to call several times per frame.
	 */
	public static float level(PlayerEntity player, int port) {
		State state = STATES.get(player);
		if (state == null) {
			return 0.0F;
		}

		advance(player, state);
		return state.level[port];
	}

	/** The turbine angle inside one thruster, radians (advanced with the burn level). */
	public static float spin(PlayerEntity player, int port) {
		State state = STATES.get(player);
		if (state == null) {
			return 0.0F;
		}

		advance(player, state);
		return state.spin[port];
	}

	/** The stronger of the two thrusters' smoothed levels. */
	public static float level(PlayerEntity player) {
		State state = STATES.get(player);
		if (state == null) {
			return 0.0F;
		}

		advance(player, state);
		return Math.max(state.level[0], state.level[1]);
	}

	private static void advance(PlayerEntity player, State state) {
		long now = Util.getMeasuringTimeNano();
		float dt = state.lastNanos == 0L ? 0.0F : Math.min((now - state.lastNanos) / 1.0E9F, 0.1F);
		state.lastNanos = now;
		boolean fuelled = fuelled(player);
		for (int port = 0; port < 2; port++) {
			float target = fuelled ? state.fuelLevel[port] : 0.0F;
			float rate = target > state.level[port] ? RISE_RATE : FALL_RATE;
			state.level[port] = MathHelper.lerp(1.0F - (float) Math.exp(-rate * dt), state.level[port], target);
			state.spin[port] = (state.spin[port] + dt * TURBINE_IDLE + dt * TURBINE_BURN * state.level[port]) % (float) (Math.PI * 2.0);
		}
	}

	/**
	 * Whether the flame is still burning or dying down: fuelled within the last {@code DIE_DOWN_TICKS}.
	 * Tick-based, so it holds even while the flame is not being drawn (first person).
	 */
	static boolean lit(PlayerEntity player) {
		State state = STATES.get(player);
		return state != null && player.getWorld().getTime() - state.fuelTick <= DIE_DOWN_TICKS;
	}

	/**
	 * Opening of one thruster flap, radians: closed on the ground, opened a little by the idle air burn,
	 * wide on a wall run and flung open by a double jump, with a small flutter while burning. Each flap
	 * flutters with its own phase so the nozzle looks alive rather than mechanical.
	 */
	public static float flapAngle(PlayerEntity player, int flap, float level, float tickDelta) {
		float flare = flare(player, tickDelta);
		float burn = (float) Math.pow(MathHelper.clamp(level, 0.0F, 1.2F), 0.7);
		float time = (player.age + tickDelta) * 0.05F * 20.0F;
		float flutter = burn > 0.01F ? 0.026F * burn * MathHelper.sin(time * 1.7F + flap * 2.1F) : 0.0F;
		return 0.38F * burn + 0.30F * flare + flutter;
	}
}
