package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.grapple.Grapple;
import com.loaffie.titanfall.movement.physics.HVec;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Draws every grapple line in the world - the cable from the launcher's muzzle to the hook, and the hook
 * itself - after the entities, so the muzzle positions the feature renderer recorded this frame are fresh.
 *
 * <p>The cable is a camera-facing ribbon. In flight it trails a whip that dies down as it pays out; once
 * the hook bites it draws taut, with a faint hum while it pulls; reeling back in it goes slack and snakes
 * home behind the hook.
 */
public final class GrappleRenderer {
	private static final int SEGMENTS = 28;
	private static final float CABLE_WIDTH = 0.028F;
	/** Hook drawn a little over life size, so it reads at range. Mesh pixels to blocks. */
	private static final float HOOK_SCALE = 1.6F / 16.0F;
	/** Hook glow, and how far its eyelet stands proud of a surface it is buried in (mesh pixels). */
	private static final int HOOK_GLOW = GrappleModel.dim(GrappleModel.LIGHT_ACTIVE, 0.8F);
	private static final float HOOK_PROUD = 1.5F;
	/** Cable texels in the gauntlet atlas (the braided-line swatch). */
	private static final float CABLE_U0 = 17.0F / 64.0F;
	private static final float CABLE_U1 = 30.0F / 64.0F;
	private static final float CABLE_V0 = 17.0F / 64.0F;
	private static final float CABLE_V1 = 30.0F / 64.0F;

	private static long frame;
	/** World projection's vertical half-angle tangent, for placing the first-person muzzle in the world. */
	private static float worldTanHalfFov = (float) Math.tan(Math.toRadians(35.0));

	private GrappleRenderer() {
	}

	public static long frame() {
		return frame;
	}

	public static void startFrame(WorldRenderContext context) {
		frame++;
		float m11 = context.projectionMatrix().m11();
		if (m11 > 1.0E-4F) {
			worldTanHalfFov = 1.0F / m11;
		}
	}

	public static void render(WorldRenderContext context) {
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		if (world == null || consumers == null) {
			return;
		}

		Camera camera = context.camera();
		Vec3d cam = camera.getPos();
		float tickDelta = context.tickCounter().getTickDelta(false);
		MatrixStack matrices = new MatrixStack();
		for (Map.Entry<Integer, GrappleLine> entry : GrappleLines.all()) {
			GrappleLine line = entry.getValue();
			if (!line.active() || !(world.getEntityById(entry.getKey()) instanceof PlayerEntity player)) {
				continue;
			}

			Vec3d muzzle = muzzle(player, line, tickDelta);
			Vec3d hook = line.position(world, tickDelta);
			Vec3d forward = line.dir;
			if (line.phase == GrappleLine.Phase.RETRACTING) {
				float home = line.retractProgress(tickDelta);
				hook = line.retractFrom.lerp(muzzle, home);
				if (home >= 0.98F) {
					continue;
				}

				forward = hook.subtract(muzzle).normalize();
			} else if (line.phase == GrappleLine.Phase.ATTACHED && line.normal != null) {
				forward = line.normal.multiply(-1.0);
			}

			drawCable(matrices, consumers, world, cam, muzzle, hook, line, tickDelta);
			drawHook(matrices, consumers, world, cam, hook, forward, line.phase == GrappleLine.Phase.ATTACHED && line.normal != null);
		}
	}

	/**
	 * Where the line leaves the launcher this frame. The local pilot in first person: the first-person
	 * launcher's muzzle, carried into the world. Anyone drawn in the world: the muzzle the feature renderer
	 * recorded. Otherwise (not drawn yet): an estimate at the wearing forearm.
	 */
	public static Vec3d muzzle(PlayerEntity player, GrappleLine line, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (player == client.player && client.options.getPerspective().isFirstPerson()) {
			Vec3d firstPerson = GrappleFirstPerson.muzzleInWorld(client.gameRenderer.getCamera(), worldTanHalfFov);
			if (firstPerson != null) {
				return firstPerson;
			}
		}

		if (line.tip != null && frame - line.tipFrame <= 1) {
			return line.tip;
		}

		Arm arm = Grapple.wornArm(player);
		double side = arm == null || arm == Arm.RIGHT ? -1.0 : 1.0;
		float bodyYaw = MathHelper.lerp(tickDelta, player.prevBodyYaw, player.bodyYaw);
		HVec right = HVec.rightFromYaw(bodyYaw);
		Vec3d look = player.getRotationVec(tickDelta);
		Vec3d base = player.getLerpedPos(tickDelta);
		return base.add(-right.x() * 0.36 * side, 1.25, -right.z() * 0.36 * side).add(look.multiply(0.55));
	}

	private static void drawCable(MatrixStack matrices, VertexConsumerProvider consumers, ClientWorld world, Vec3d cam,
			Vec3d from, Vec3d to, GrappleLine line, float tickDelta) {
		Vec3d span = to.subtract(from);
		double length = span.length();
		if (length < 1.0E-3) {
			return;
		}

		Vec3d along = span.multiply(1.0 / length);
		Vec3d across = along.crossProduct(Math.abs(along.y) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0)).normalize();
		float time = line.age + tickDelta;
		// Slack (a sag toward the ground) and a travelling whip, both in blocks.
		double sag;
		double whip;
		switch (line.phase) {
			case FLYING -> {
				sag = 0.02 * length;
				whip = 0.18 * Math.exp(-time * 0.35);
			}
			case RETRACTING -> {
				sag = 0.06 * length;
				whip = 0.12;
			}
			default -> {
				// Taut within a few ticks of biting; a faint hum while the reel works.
				float settle = (float) Math.exp(-time * 0.9);
				sag = 0.05 * length * settle;
				whip = 0.12 * settle + 0.006 * MathHelper.clamp(line.pull / 12.0F, 0.0F, 1.0F);
			}
		}

		VertexConsumer consumer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GrappleModel.TEXTURE));
		MatrixStack.Entry entry = matrices.peek();
		Vec3d previous = cablePoint(from, span, across, 0.0, sag, whip, time, line.phase);
		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(from));
		for (int i = 1; i <= SEGMENTS; i++) {
			double t = i / (double) SEGMENTS;
			Vec3d point = cablePoint(from, span, across, t, sag, whip, time, line.phase);
			if (i % 7 == 0) {
				light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(point));
			}

			Vec3d direction = point.subtract(previous);
			Vec3d toCamera = cam.subtract(previous);
			Vec3d side = direction.crossProduct(toCamera);
			if (side.lengthSquared() < 1.0E-10) {
				side = across;
			}

			side = side.normalize().multiply(CABLE_WIDTH / 2.0);
			Vec3d a = previous.subtract(cam);
			Vec3d b = point.subtract(cam);
			vertex(consumer, entry, a.subtract(side), CABLE_U0, CABLE_V0, light);
			vertex(consumer, entry, a.add(side), CABLE_U1, CABLE_V0, light);
			vertex(consumer, entry, b.add(side), CABLE_U1, CABLE_V1, light);
			vertex(consumer, entry, b.subtract(side), CABLE_U0, CABLE_V1, light);
			previous = point;
		}
	}

	private static Vec3d cablePoint(Vec3d from, Vec3d span, Vec3d across, double t, double sag, double whip, float time, GrappleLine.Phase phase) {
		double bell = 4.0 * t * (1.0 - t);
		// The whip travels out along the line in flight and back toward the launcher while reeling in.
		double travel = phase == GrappleLine.Phase.RETRACTING ? 3.0 * t + time * 0.9 : 3.0 * t - time * 0.6;
		double wave = whip * bell * Math.sin(2.0 * Math.PI * travel);
		return from.add(span.multiply(t)).add(0.0, -sag * bell, 0.0).add(across.multiply(wave));
	}

	private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d p, float u, float v, int light) {
		consumer.vertex(entry, (float) p.x, (float) p.y, (float) p.z)
				.color(0xFFFFFFFF)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(entry, 0.0F, 1.0F, 0.0F);
	}

	/** The hook, claws along {@code forward}; buried in a surface it stands just proud of it. */
	private static void drawHook(MatrixStack matrices, VertexConsumerProvider consumers, ClientWorld world, Vec3d cam,
			Vec3d at, Vec3d forward, boolean buried) {
		Vec3d f = forward.lengthSquared() < 1.0E-6 ? new Vec3d(0.0, 0.0, 1.0) : forward.normalize();
		matrices.push();
		matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float) f.x, (float) f.y, (float) f.z)));
		matrices.scale(HOOK_SCALE, HOOK_SCALE, HOOK_SCALE);
		if (buried) {
			matrices.translate(0.0F, -HOOK_PROUD, 0.0F);
		}

		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at.subtract(f.multiply(0.2))));
		GrappleModel.renderHook(matrices, consumers, light, HOOK_GLOW);
		matrices.pop();
	}
}
