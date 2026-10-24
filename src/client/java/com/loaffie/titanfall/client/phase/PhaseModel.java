package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.client.render.ObjModel;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * The Phase Shift gauntlet's mesh ({@code models/entity/phase_shift.obj}, built by
 * {@code tools/generate-phase-model.py}) and its light show.
 *
 * <p>The mesh is in the Grapple's arm space (pixels, left arm, centre line at x = 0, -Z the back of the hand),
 * so {@link GrappleModel#onArm} puts it on either arm. At rest the coil is dark and the hub glows a faint
 * blue; while phasing the coil burns blue and electric arcs crawl over it and leap off its rim.
 */
public final class PhaseModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/phase_shift.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/phase_shift.obj"));

	/** The ring's centre on its face, mesh pixels (the generator's COIL_Y and COIL_FACE_Z). */
	public static final Vector3f COIL = new Vector3f(0.0F, 7.95F, -3.05F);
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int CORE_IDLE = 0xFF1E5A86;
	private static final int CORE_ACTIVE = 0xFFE4F6FF;
	private static final int COIL_ACTIVE = 0xFF3FA8FF;
	private static final int LED = 0xFFFF3A26;
	private static final int ARC_CORE = 0xFFF2FBFF;
	private static final int ARC_GLOW = 0xFF2F86FF;
	/** The emissive swatch of the atlas, for the arcs' texture coordinates. */
	private static final float LIGHT_U = 40.0F / 64.0F;
	private static final float LIGHT_V = 24.0F / 64.0F;
	private static final int ARCS = 4;
	private static final int ARC_POINTS = 6;

	private PhaseModel() {
	}

	public static ObjModel mesh() {
		return MESH.get();
	}

	/**
	 * Draws the gauntlet in mesh space. {@code glow} 0..1 lights the coil (0 at rest); {@code time} (ticks)
	 * animates the flicker and the arcs, and {@code seed} keeps different pilots' arcs apart; {@code mirrored}
	 * as {@link GrappleModel#onArm} returned.
	 */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float glow, float time, int seed,
			boolean mirrored) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer solid = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		mesh.render("gauntlet", entry, solid, light, overlay, WHITE, mirrored);
		mesh.render("coil", entry, solid, light, overlay, WHITE, mirrored);

		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("led", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, GrappleModel.dim(LED, 0.8F + 0.2F * MathHelper.sin(time * 0.3F)),
				mirrored);
		mesh.render("core", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, mix(CORE_IDLE, CORE_ACTIVE, glow), mirrored);
		if (glow > 0.01F) {
			// A ragged electric flicker on top of the glow.
			float flicker = 0.78F + 0.22F * MathHelper.sin(time * 2.7F) * MathHelper.sin(time * 1.3F + 1.0F);
			mesh.render("coil_glow", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, GrappleModel.dim(COIL_ACTIVE, glow * flicker), mirrored);
			if (glow > 0.2F) {
				renderArcs(entry, emissive, glow, (int) (time / 2.0F) * 31 + seed);
			}
		}
	}

	/**
	 * Electric arcs over the coil: jagged lines crawling round the ring and jumping off its rim, re-struck
	 * every two ticks. Each is a hot white core inside a wider blue glow, drawn flat on the coil's face and
	 * from both sides (the emissive layer culls back faces).
	 */
	private static void renderArcs(MatrixStack.Entry entry, VertexConsumer consumer, float strength, int seed) {
		Random random = Random.create(seed);
		float[] xs = new float[ARC_POINTS];
		float[] ys = new float[ARC_POINTS];
		float[] zs = new float[ARC_POINTS];
		for (int arc = 0; arc < ARCS; arc++) {
			float angle = random.nextFloat() * MathHelper.TAU;
			boolean leap = arc % 2 == 0;
			float radius = leap ? 1.1F + random.nextFloat() * 0.3F : 1.2F + random.nextFloat() * 0.25F;
			float z = COIL.z - 0.06F;
			for (int k = 0; k < ARC_POINTS; k++) {
				xs[k] = MathHelper.cos(angle) * radius;
				ys[k] = COIL.y + MathHelper.sin(angle) * radius;
				zs[k] = z;
				if (leap) {
					// Outward off the rim, wandering, rising off the hand.
					angle += (random.nextFloat() - 0.5F) * 0.9F;
					radius += 0.3F + random.nextFloat() * 0.45F;
					z -= random.nextFloat() * 0.25F;
				} else {
					// Round the ring, jittering in and out.
					angle += 0.35F + random.nextFloat() * 0.25F;
					radius = 1.35F + (random.nextFloat() - 0.5F) * 0.5F;
				}
			}

			int glowColor = GrappleModel.dim(ARC_GLOW, 0.55F * strength);
			int coreColor = GrappleModel.dim(ARC_CORE, strength);
			for (int k = 0; k + 1 < ARC_POINTS; k++) {
				// The leaping arcs thin out toward their tips.
				float taper = leap ? 1.0F - k / (float) ARC_POINTS : 1.0F;
				segment(entry, consumer, xs[k], ys[k], zs[k], xs[k + 1], ys[k + 1], zs[k + 1], 0.3F * taper, glowColor);
				segment(entry, consumer, xs[k], ys[k], zs[k] - 0.01F, xs[k + 1], ys[k + 1], zs[k + 1] - 0.01F, 0.1F * taper, coreColor);
			}
		}
	}

	/** One flat strip between two points, its width across the line in the coil's plane. */
	private static void segment(MatrixStack.Entry entry, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1,
			float width, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = MathHelper.sqrt(dx * dx + dy * dy);
		if (length < 1.0E-4F) {
			return;
		}

		float nx = -dy / length * width / 2.0F;
		float ny = dx / length * width / 2.0F;
		quad(entry, consumer, x0 + nx, y0 + ny, z0, x0 - nx, y0 - ny, z0, x1 - nx, y1 - ny, z1, x1 + nx, y1 + ny, z1, color);
		quad(entry, consumer, x1 + nx, y1 + ny, z1, x1 - nx, y1 - ny, z1, x0 - nx, y0 - ny, z0, x0 + nx, y0 + ny, z0, color);
	}

	private static void quad(MatrixStack.Entry entry, VertexConsumer consumer, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int color) {
		vertex(entry, consumer, ax, ay, az, color);
		vertex(entry, consumer, bx, by, bz, color);
		vertex(entry, consumer, cx, cy, cz, color);
		vertex(entry, consumer, dx, dy, dz, color);
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer consumer, float x, float y, float z, int color) {
		consumer.vertex(entry, x, y, z).color(color).texture(LIGHT_U, LIGHT_V).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
				.normal(entry, 0.0F, 0.0F, -1.0F);
	}

	/** Blends two opaque ARGB colours. */
	static int mix(int from, int to, float t) {
		int r = Math.round(MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(MathHelper.lerp(t, from & 0xFF, to & 0xFF));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "phase_shift_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
