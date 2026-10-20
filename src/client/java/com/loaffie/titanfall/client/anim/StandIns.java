package com.loaffie.titanfall.client.anim;

import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in players: client-only look-alikes drawn in place of something else (a Holo Pilot decoy draws as
 * its pilot). They are never added to the world, so anything that finds a player by entity id - the
 * thruster flames following a pilot's nozzles - looks here too. Their ids are negative, clear of every id the
 * server hands out.
 */
public final class StandIns {
	private static final Map<Integer, AbstractClientPlayerEntity> BY_ID = new HashMap<>();

	private StandIns() {
	}

	public static void add(AbstractClientPlayerEntity player) {
		BY_ID.put(player.getId(), player);
	}

	public static void remove(AbstractClientPlayerEntity player) {
		BY_ID.remove(player.getId());
	}

	public static AbstractClientPlayerEntity byId(int id) {
		return id < 0 ? BY_ID.get(id) : null;
	}

	public static void clear() {
		BY_ID.clear();
	}
}
