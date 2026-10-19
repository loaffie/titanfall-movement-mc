package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.grapple.GrapplePayload;
import com.loaffie.titanfall.grapple.GrappleSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every grapple line the client knows about, by player entity id. The local pilot's line is driven by
 * {@code GrappleController}; other pilots' lines are rebuilt here from the events the server relays: the
 * hook is flown out locally along the fired direction, and when the "bit" event arrives it carries on to
 * where the shooter's own client says it struck.
 */
public final class GrappleLines {
	private static final Map<Integer, GrappleLine> LINES = new HashMap<>();

	private GrappleLines() {
	}

	public static GrappleLine of(PlayerEntity player) {
		return LINES.computeIfAbsent(player.getId(), id -> new GrappleLine());
	}

	/** The player's line if it is doing anything, else null. */
	public static GrappleLine active(PlayerEntity player) {
		GrappleLine line = LINES.get(player.getId());
		return line != null && line.active() ? line : null;
	}

	public static Collection<Map.Entry<Integer, GrappleLine>> all() {
		return LINES.entrySet();
	}

	/** Another pilot's grapple event, relayed by the server (client thread). */
	public static void receive(GrappleSyncPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !(client.world.getEntityById(payload.player()) instanceof PlayerEntity player) || player == client.player) {
			return;
		}

		GrappleLine line = of(player);
		GrapplePayload event = payload.event();
		switch (event.phase()) {
			case GrapplePayload.FIRE -> {
				line.fly(event.pos(), event.dir());
				GrappleSounds.fire(player);
				GrappleEffects.fire(player, line);
			}
			case GrapplePayload.ATTACH -> {
				Entity entity = event.entity() >= 0 ? client.world.getEntityById(event.entity()) : null;
				Vec3d normal = entity == null ? event.dir() : null;
				if (line.phase == GrappleLine.Phase.FLYING) {
					line.pendingAnchor = event.pos();
					line.pendingNormal = normal;
					line.pendingEntity = entity == null ? -1 : entity.getId();
				} else {
					line.hook = event.pos();
					bite(player, line, event.pos(), normal, entity);
				}
			}
			case GrapplePayload.RELEASE -> {
				if (line.active() && line.phase != GrappleLine.Phase.RETRACTING) {
					line.retract(client.world);
					GrappleSounds.release(player);
				}
			}
			default -> {
			}
		}
	}

	/** The hook strikes: attach the line and play the impact. */
	public static void bite(PlayerEntity player, GrappleLine line, Vec3d at, Vec3d normal, Entity entity) {
		line.attach(at, normal, entity);
		GrappleSounds.impact(player, at);
		GrappleEffects.impact(player.getWorld(), line, at, normal, entity);
		GrappleSounds.startReel(player, line);
	}

	/** End of every client tick: age every line, finish reel-ins, and fly other pilots' hooks. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			LINES.clear();
			return;
		}

		double step = TitanfallConfig.get().grappleHookSpeed / 20.0;
		double range = TitanfallConfig.get().grappleRange;
		Iterator<Map.Entry<Integer, GrappleLine>> iterator = LINES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, GrappleLine> entry = iterator.next();
			GrappleLine line = entry.getValue();
			Entity owner = client.world.getEntityById(entry.getKey());
			if (!(owner instanceof PlayerEntity player) || owner.isRemoved()) {
				iterator.remove();
				continue;
			}

			line.age++;
			if (line.phase == GrappleLine.Phase.RETRACTING && line.age >= GrappleLine.RETRACT_TICKS) {
				line.clear();
			} else if (line.phase == GrappleLine.Phase.ATTACHED && line.entityId >= 0 && line.entity(client.world) == null) {
				line.retract(client.world);
			}

			if (player != client.player && line.phase == GrappleLine.Phase.FLYING) {
				flyRemote(player, line, step, range);
			}

			GrappleEffects.tick(player, line);
		}
	}

	private static void flyRemote(PlayerEntity player, GrappleLine line, double step, double range) {
		line.prevHook = line.hook;
		if (line.pendingAnchor != null) {
			Vec3d toAnchor = line.pendingAnchor.subtract(line.hook);
			if (toAnchor.length() <= step) {
				Entity entity = line.pendingEntity >= 0 ? player.getWorld().getEntityById(line.pendingEntity) : null;
				Vec3d at = line.pendingAnchor;
				line.hook = at;
				bite(player, line, at, line.pendingNormal, entity);
			} else {
				line.dir = toAnchor.normalize();
				line.hook = line.hook.add(line.dir.multiply(step));
			}
		} else if (line.hook.squaredDistanceTo(player.getEyePos()) < range * range) {
			line.hook = line.hook.add(line.dir.multiply(step));
		}
	}
}
