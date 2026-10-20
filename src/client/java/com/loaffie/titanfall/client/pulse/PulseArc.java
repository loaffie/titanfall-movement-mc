package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.movement.PulseController;
import com.loaffie.titanfall.pulse.PulseBlade;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The aiming arc while the Pulse Blade is held, after Titanfall 2's grenade arc indicator
 * ({@code P_grenade_arc_proto_orange}): the kunai's flight traced ahead of it as an orange dashed line
 * running out to where it will stick, with a ring on the surface there - or round whoever it would hit.
 *
 * <p>The trace runs the kunai's own flight (the same start, launch and gravity as the thrown entity, tick by
 * tick, stopping at blocks and at anything living) for the throw {@link PulseAim} makes - converging on the
 * crosshair - so the line runs out to exactly where it lands, under the crosshair.
 */
public final class PulseArc {
	/** Longest flight traced, ticks (two seconds: about 175 blocks at full speed). */
	private static final int MAX_TICKS = 40;
	/** The first stretch of the arc, right at the hand, is left out (it would fill the view), blocks. */
	private static final double SKIP = 1.4;
	/** Dashes: one every period, this much of it lit; they run toward the landing point, blocks a second. */
	private static final double DASH_PERIOD = 0.9;
	private static final double DASH_ON = 0.55;
	private static final double DASH_SPEED = 3.0;
	private static final int CORE = 0xFFFFB060;
	private static final int GLOW = 0xFF7A3008;
	private static final int TARGET_ENTITY = 0xFFFF5A3A;
	private static final int RING_SEGMENTS = 28;

	private PulseArc() {
	}

	/** The traced flight: its points, one a tick, and what ends it. */
	private record Trace(List<Vec3d> points, HitResult hit) {
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		VertexConsumerProvider consumers = context.consumers();
		if (player == null || consumers == null || !TitanfallConfig.get().pulseArc || !PulseController.aiming(player)) {
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(false);
		Trace trace = trace(player, tickDelta);
		Vec3d cam = context.camera().getPos();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(PulseModel.TEXTURE));
		MatrixStack.Entry entry = new MatrixStack().peek();
		drawLine(entry, buffer, trace.points(), cam);
		if (trace.hit() instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
			Vec3d normal = Vec3d.of(block.getSide().getVector());
			drawRing(entry, buffer, block.getPos().add(normal.multiply(0.02)).subtract(cam), normal, 0.4F, CORE);
			drawRing(entry, buffer, block.getPos().add(normal.multiply(0.02)).subtract(cam), normal, 0.12F, CORE);
		} else if (trace.hit() instanceof EntityHitResult hit) {
			Entity target = hit.getEntity();
			Vec3d center = target.getLerpedPos(tickDelta).add(0.0, target.getHeight() / 2.0, 0.0).subtract(cam);
			drawRing(entry, buffer, center, center.normalize().multiply(-1.0), (float) Math.max(target.getWidth(), target.getHeight()) * 0.6F,
					TARGET_ENTITY);
		}
	}

	/** Runs the kunai's flight from the hand as it would be thrown now. */
	private static Trace trace(ClientPlayerEntity player, float tickDelta) {
		World world = player.getWorld();
		PulseAim.Aim aim = PulseAim.of(player, tickDelta);
		Vec3d position = aim.origin();
		Vec3d velocity = aim.velocity();
		List<Vec3d> points = new ArrayList<>();
		points.add(position);
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			Vec3d next = position.add(velocity);
			BlockHitResult block = world.raycast(new RaycastContext(position, next, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, player));
			if (block.getType() != HitResult.Type.MISS) {
				next = block.getPos();
			}

			EntityHitResult entity = ProjectileUtil.getEntityCollision(world, player, position, next, new Box(position, next).expand(1.0),
					target -> target instanceof LivingEntity && target.canHit() && !target.isSpectator());
			if (entity != null) {
				points.add(entity.getPos());
				return new Trace(points, entity);
			}

			points.add(next);
			if (block.getType() != HitResult.Type.MISS) {
				return new Trace(points, block);
			}

			position = next;
			velocity = velocity.add(0.0, -PulseBlade.gravity(), 0.0);
		}

		return new Trace(points, null);
	}

	/** The dashed line, skipping the stretch at the hand and fading in after it. */
	private static void drawLine(MatrixStack.Entry entry, VertexConsumer buffer, List<Vec3d> points, Vec3d cam) {
		double travelled = 0.0;
		double offset = (Util.getMeasuringTimeMs() / 1000.0 * DASH_SPEED) % DASH_PERIOD;
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3d a = points.get(i);
			Vec3d b = points.get(i + 1);
			double length = a.distanceTo(b);
			if (length < 1.0E-4) {
				continue;
			}

			// Dashes within this segment, in path distance.
			double start = travelled;
			double end = travelled + length;
			double first = Math.floor((start - offset) / DASH_PERIOD) * DASH_PERIOD + offset;
			for (double dash = first; dash < end; dash += DASH_PERIOD) {
				double from = Math.max(Math.max(dash, start), SKIP);
				double to = Math.min(dash + DASH_ON, end);
				if (to <= from) {
					continue;
				}

				Vec3d p0 = a.lerp(b, (from - start) / length).subtract(cam);
				Vec3d p1 = a.lerp(b, (to - start) / length).subtract(cam);
				// Faded in after the stretch at the hand, and out wherever it passes close to the eye (in third person
				// it can run straight at the camera).
				float fade = (float) (MathHelper.clamp((from - SKIP) / 1.5, 0.0, 1.0) * MathHelper.clamp((Math.min(p0.length(), p1.length()) - 1.0) / 2.0, 0.0, 1.0));
				if (fade <= 0.01F) {
					continue;
				}

				float width = (float) MathHelper.clamp(p0.length() * 0.011, 0.03, 0.4);
				strip(entry, buffer, p0, p1, width * 3.0F, GrappleModel.dim(GLOW, fade));
				strip(entry, buffer, p0, p1, width, GrappleModel.dim(CORE, fade));
			}

			travelled = end;
		}
	}

	/** A ring of {@code radius} round {@code center}, lying across {@code normal}. */
	private static void drawRing(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d center, Vec3d normal, float radius, int color) {
		Vec3d u = normal.crossProduct(Math.abs(normal.y) > 0.9 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0)).normalize();
		Vec3d v = normal.crossProduct(u).normalize();
		float pulse = 1.0F + 0.12F * MathHelper.sin(Util.getMeasuringTimeMs() / 120.0F);
		double r = radius * pulse;
		double thickness = Math.max(0.03, radius * 0.18);
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double a0 = MathHelper.TAU * i / RING_SEGMENTS;
			double a1 = MathHelper.TAU * (i + 1) / RING_SEGMENTS;
			Vec3d d0 = u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0)));
			Vec3d d1 = u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
			quad(entry, buffer, center.add(d0.multiply(r - thickness)), center.add(d0.multiply(r)),
					center.add(d1.multiply(r)), center.add(d1.multiply(r - thickness)), color);
		}
	}

	/** A camera-facing strip from {@code a} to {@code b} (camera-relative). */
	private static void strip(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d a, Vec3d b, float width, int color) {
		Vec3d side = b.subtract(a).crossProduct(a.add(b).multiply(0.5));
		if (side.lengthSquared() < 1.0E-10) {
			return;
		}

		side = side.normalize().multiply(width / 2.0);
		quad(entry, buffer, a.add(side), a.subtract(side), b.subtract(side), b.add(side), color);
	}

	/** One quad from both sides (the emissive layer culls back faces). */
	private static void quad(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
		vertex(entry, buffer, a, color);
		vertex(entry, buffer, b, color);
		vertex(entry, buffer, c, color);
		vertex(entry, buffer, d, color);
		vertex(entry, buffer, d, color);
		vertex(entry, buffer, c, color);
		vertex(entry, buffer, b, color);
		vertex(entry, buffer, a, color);
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d p, int color) {
		buffer.vertex(entry, (float) p.x, (float) p.y, (float) p.z).color(color).texture(PulseModel.LIGHT_U, PulseModel.LIGHT_V)
				.overlay(OverlayTexture.DEFAULT_UV).light(PulseModel.FULL_BRIGHT).normal(entry, 0.0F, 1.0F, 0.0F);
	}
}
