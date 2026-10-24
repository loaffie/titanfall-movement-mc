package com.loaffie.titanfall.client.awall;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.awall.AWall;
import com.loaffie.titanfall.awall.AWallEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.TreeSet;

/**
 * Draws the A-Wall: the disc spinning through the air, then planted on its legs with its mast up, and the wall
 * it holds as Titanfall draws it - mostly clear, a faint amber tint crossed by fine hatching
 * ({@code textures/entity/awall_field.png}) drifting slowly upward, inside a bright amber rim that follows its
 * stepped top edge, its ends and its foot.
 *
 * <p>The wall rises from the disc, spreading out to its ends and up as it goes ({@link AWallStates}), flares
 * where it stops a shot, reddens and gutters as its base is damaged, stutters in its last seconds, and shrinks
 * back into the disc as it comes down.
 */
public class AWallRenderer extends EntityRenderer<AWallEntity> {
	private static final Identifier FIELD = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/awall_field.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Heights (0..1, foot to top) of the rows of vertices up the field: closer at the top and the foot, where it glows. */
	private static final float[] ROWS = {0.0F, 0.1F, 0.5F, 0.85F, 1.0F};
	/**
	 * The wall's outline, as Titanfall draws it: the top edge steps down and up again along the wall, highest in the
	 * middle, and both top corners are cut away. Pairs of (across, 0..1 from the middle to an end; height, 0..1 of
	 * the full), from the middle out, mirrored for the other half.
	 */
	private static final float[] TOP = {0.0F, 1.0F, 0.2F, 1.0F, 0.27F, 0.86F, 0.5F, 0.86F, 0.56F, 0.94F, 0.84F, 0.94F, 1.0F, 0.68F};
	/** The foot's corners are cut away too: (across, height above the ground as a share of the full height). */
	private static final float[] FOOT = {0.0F, 0.0F, 0.9F, 0.0F, 1.0F, 0.1F};
	/** Where the wall's columns of vertices fall across it, -1..1: every outline corner, and enough between to keep it round. */
	private static final float[] COLUMNS = columns();
	/** Blocks of wall per tile of the field texture. */
	private static final float TILE = 1.0F;
	/** The wall's colour, full health and none: amber going to a hot red. */
	private static final float[] AMBER = {1.0F, 0.55F, 0.12F};
	private static final float[] RED = {1.0F, 0.2F, 0.08F};
	/** How fast the hatching drifts up the wall, texture tiles a tick. */
	private static final float DRIFT = 0.006F;
	/** The bright line round the wall's edge, blocks, and the glow it throws onto the field inside it. */
	private static final float RIM = 0.035F;
	private static final float RIM_GLOW = 0.22F;
	/**
	 * The field: a full-bright amber tint blended over what is behind it (so it stays amber against a bright sky,
	 * where light added on top would wash out to white), both sides drawn, and not writing depth: a sheet of light,
	 * it shouldn't hide the particles and the clouds of stopped shots behind it. One layer for every wall (the drift
	 * is in the texture coordinates), so walls share a buffer.
	 */
	private static final RenderLayer FIELD_LAYER = RenderLayer.of("titanfall_awall_field", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
			VertexFormat.DrawMode.QUADS, 1536, false, true, RenderLayer.MultiPhaseParameters.builder()
					.program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
					.texture(new RenderPhase.Texture(FIELD, false, false))
					.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
					.cull(RenderPhase.DISABLE_CULLING)
					.lightmap(RenderPhase.ENABLE_LIGHTMAP)
					.overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
					.writeMaskState(RenderPhase.COLOR_MASK)
					.build(false));
	/** The rim: untextured amber, blended by its alpha, likewise not writing depth. */
	private static final RenderLayer RIM_LAYER = RenderLayer.of("titanfall_awall_rim", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS,
			1536, false, true, RenderLayer.MultiPhaseParameters.builder()
					.program(RenderPhase.COLOR_PROGRAM)
					.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
					.cull(RenderPhase.DISABLE_CULLING)
					.writeMaskState(RenderPhase.COLOR_MASK)
					.build(false));
	/** Opacity of the field: Titanfall's wall is mostly clear, a faint tint with the hatching just showing. */
	private static final float FACE = 0.42F;
	/** How much stronger the field is toward its top and its foot. */
	private static final float FACE_EDGE = 0.4F;

	public AWallRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.18F;
	}

	@Override
	public void render(AWallEntity wall, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		AWallStates.State state = AWallStates.of(wall);
		float time = wall.age + tickDelta;
		matrices.push();
		if (!wall.planted()) {
			// In flight: flat, spinning, tipped a little the way it is going.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerp(tickDelta, wall.prevYaw, wall.getYaw())));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(8.0F));
			matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			matrices.translate(0.0F, -0.7F, 0.0F);
			AWallModel.renderDisc(matrices, consumers, light, 0.0F, AWallModel.AMBER);
			matrices.pop();
			return;
		}

		float raised = state == null ? 1.0F : state.raised(tickDelta);
		float unfold = state == null ? 1.0F : state.unfold(tickDelta);
		float flash = state == null ? 0.0F : state.flash(tickDelta);
		float warning = state == null ? 0.0F : state.warning();
		float health = wall.healthFraction();

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-wall.wallYaw()));
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		int discGlow = colour(health, 0.6F + 0.4F * raised + 0.3F * flash);
		AWallModel.renderDisc(matrices, consumers, light, unfold, discGlow);
		matrices.pop();

		if (raised > 0.001F) {
			// Guttering: weak walls flicker, and so does any wall about to drop.
			float gutter = Math.max(1.0F - health * 2.5F, 0.0F) * 0.5F + warning * 0.6F;
			float flicker = gutter <= 0.0F ? 1.0F : 1.0F - gutter * (0.5F + 0.5F * MathHelper.sin(time * 2.9F) * MathHelper.sin(time * 1.3F + 1.0F));
			float brightness = MathHelper.clamp(flicker * (1.0F + 0.8F * flash), 0.0F, 2.0F);
			drawWall(wall, matrices, consumers, raised, brightness, health, time);
		}

		matrices.pop();
	}

	/** The disc's and wall's colour: amber, redder as {@code health} runs out, scaled by {@code k}. */
	private static int colour(float health, float k) {
		float h = MathHelper.clamp(health, 0.0F, 1.0F);
		int r = Math.round(MathHelper.clamp(MathHelper.lerp(h, RED[0], AMBER[0]) * k, 0.0F, 1.0F) * 255.0F);
		int g = Math.round(MathHelper.clamp(MathHelper.lerp(h, RED[1], AMBER[1]) * k, 0.0F, 1.0F) * 255.0F);
		int b = Math.round(MathHelper.clamp(MathHelper.lerp(h, RED[2], AMBER[2]) * k, 0.0F, 1.0F) * 255.0F);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/**
	 * The wall, in the entity's space (blocks, world axes): a band of the cylinder about the axis a radius behind its
	 * middle, {@code raised} of the way out to its ends and up to its top - the faint field, and the bright rim
	 * round its outline.
	 */
	private static void drawWall(AWallEntity wall, MatrixStack matrices, VertexConsumerProvider consumers, float raised, float brightness,
			float health, float time) {
		float rise = MathHelper.clamp(raised * 1.3F - 0.3F, 0.08F, 1.0F);
		float halfArc = (float) AWall.HALF_ARC * raised;
		float height = (float) AWall.HEIGHT * rise;
		float radius = (float) AWall.RADIUS;
		float yawRad = wall.wallYaw() * MathHelper.RADIANS_PER_DEGREE;
		float fx = -MathHelper.sin(yawRad);
		float fz = MathHelper.cos(yawRad);
		// The axis stands a radius behind the wall's middle, which is a little in front of the disc.
		float centre = radius - (float) AWall.BASE_SETBACK;
		float cx = -fx * centre;
		float cz = -fz * centre;
		float base = (float) Math.atan2(fz, fx);
		float ground = (float) -AWall.SKIRT;
		float drift = time * DRIFT % 1.0F;
		MatrixStack.Entry entry = matrices.peek();

		VertexConsumer field = consumers.getBuffer(FIELD_LAYER);
		for (int c = 0; c < COLUMNS.length - 1; c++) {
			float s0 = COLUMNS[c];
			float s1 = COLUMNS[c + 1];
			float a0 = base + s0 * halfArc;
			float a1 = base + s1 * halfArc;
			float x0 = cx + MathHelper.cos(a0) * radius;
			float z0 = cz + MathHelper.sin(a0) * radius;
			float x1 = cx + MathHelper.cos(a1) * radius;
			float z1 = cz + MathHelper.sin(a1) * radius;
			float foot0 = ground + height * outline(FOOT, s0);
			float foot1 = ground + height * outline(FOOT, s1);
			float top0 = ground + (height - ground) * outline(TOP, s0);
			float top1 = ground + (height - ground) * outline(TOP, s1);
			// Texture u runs along the arc, fixed to the full wall so the hatching doesn't slide as it spreads.
			float u0 = s0 * (float) AWall.HALF_ARC * radius / TILE;
			float u1 = s1 * (float) AWall.HALF_ARC * radius / TILE;
			for (int r = 0; r < ROWS.length - 1; r++) {
				float ya0 = MathHelper.lerp(ROWS[r], foot0, top0);
				float ya1 = MathHelper.lerp(ROWS[r], foot1, top1);
				float yb0 = MathHelper.lerp(ROWS[r + 1], foot0, top0);
				float yb1 = MathHelper.lerp(ROWS[r + 1], foot1, top1);
				int ca = rimColour(health, faceIntensity(ROWS[r]) * brightness);
				int cb = rimColour(health, faceIntensity(ROWS[r + 1]) * brightness);
				fieldVertex(entry, field, x0, ya0, z0, u0, drift - ya0 / TILE, ca, a0);
				fieldVertex(entry, field, x1, ya1, z1, u1, drift - ya1 / TILE, ca, a1);
				fieldVertex(entry, field, x1, yb1, z1, u1, drift - yb1 / TILE, cb, a1);
				fieldVertex(entry, field, x0, yb0, z0, u0, drift - yb0 / TILE, cb, a0);
			}
		}

		VertexConsumer rim = consumers.getBuffer(RIM_LAYER);
		int line = rimColour(health, brightness);
		int glow = rimColour(health, brightness * 0.35F);
		int clear = rimColour(health, 0.0F);
		for (int c = 0; c < COLUMNS.length - 1; c++) {
			float s0 = COLUMNS[c];
			float s1 = COLUMNS[c + 1];
			float a0 = base + s0 * halfArc;
			float a1 = base + s1 * halfArc;
			float x0 = cx + MathHelper.cos(a0) * radius;
			float z0 = cz + MathHelper.sin(a0) * radius;
			float x1 = cx + MathHelper.cos(a1) * radius;
			float z1 = cz + MathHelper.sin(a1) * radius;
			float top0 = ground + (height - ground) * outline(TOP, s0);
			float top1 = ground + (height - ground) * outline(TOP, s1);
			float foot0 = ground + height * outline(FOOT, s0);
			float foot1 = ground + height * outline(FOOT, s1);
			// The top edge: a hard bright line, and a glow fading down from it.
			band(entry, rim, x0, z0, x1, z1, top0 - RIM, top1 - RIM, top0, top1, line, line);
			band(entry, rim, x0, z0, x1, z1, top0 - RIM_GLOW, top1 - RIM_GLOW, top0 - RIM, top1 - RIM, clear, glow);
			// The foot: a dimmer line along the ground.
			band(entry, rim, x0, z0, x1, z1, foot0, foot1, foot0 + RIM, foot1 + RIM, glow, glow);
		}

		// The two ends: lines up the wall's sides.
		float end = RIM / radius;
		for (int side = -1; side <= 1; side += 2) {
			float a0 = base + side * halfArc;
			float a1 = base + side * Math.max(halfArc - end, 0.0F);
			float x0 = cx + MathHelper.cos(a0) * radius;
			float z0 = cz + MathHelper.sin(a0) * radius;
			float x1 = cx + MathHelper.cos(a1) * radius;
			float z1 = cz + MathHelper.sin(a1) * radius;
			float foot = ground + height * outline(FOOT, side);
			float top = ground + (height - ground) * outline(TOP, side);
			band(entry, rim, x0, z0, x1, z1, foot, foot, top, top, line, line);
		}
	}

	/** The outline's height at {@code across} (-1..1), from (across, height) pairs for the half from the middle out. */
	private static float outline(float[] points, float across) {
		float a = Math.abs(across);
		for (int i = 2; i < points.length; i += 2) {
			if (a <= points[i]) {
				float t = (a - points[i - 2]) / Math.max(points[i] - points[i - 2], 1.0E-6F);
				return MathHelper.lerp(t, points[i - 1], points[i + 1]);
			}
		}

		return points[points.length - 1];
	}

	/** The column positions: the outline's corners on both halves, filled in so no column spans more than a 24th of the wall. */
	private static float[] columns() {
		TreeSet<Float> at = new TreeSet<>();
		for (int i = 0; i <= 24; i++) {
			at.add(-1.0F + 2.0F * i / 24.0F);
		}

		for (float[] points : new float[][] {TOP, FOOT}) {
			for (int i = 0; i < points.length; i += 2) {
				at.add(points[i]);
				at.add(-points[i]);
			}
		}

		float[] out = new float[at.size()];
		int k = 0;
		for (float value : at) {
			out[k++] = value;
		}

		return out;
	}

	/** The field's opacity at a height up it (0..1): faint, thickening toward its top and its foot. */
	private static float faceIntensity(float up) {
		float down = 1.0F - up;
		return FACE + FACE_EDGE * (up * up * up * up + down * down * down * down * 0.6F);
	}

	/** The wall's amber (redder as the base weakens) at full strength, {@code k} its opacity. */
	private static int rimColour(float health, float k) {
		int alpha = Math.round(MathHelper.clamp(k, 0.0F, 1.0F) * 255.0F);
		return (alpha << 24) | (colour(health, 1.0F) & 0xFFFFFF);
	}

	/** A strip of the rim between two columns, from {@code lowA}/{@code lowB} up to {@code highA}/{@code highB}, coloured at its foot and top. */
	private static void band(MatrixStack.Entry entry, VertexConsumer buffer, float x0, float z0, float x1, float z1, float lowA, float lowB,
			float highA, float highB, int footColour, int topColour) {
		buffer.vertex(entry, x0, lowA, z0).color(footColour);
		buffer.vertex(entry, x1, lowB, z1).color(footColour);
		buffer.vertex(entry, x1, highB, z1).color(topColour);
		buffer.vertex(entry, x0, highA, z0).color(topColour);
	}

	private static void fieldVertex(MatrixStack.Entry entry, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour,
			float angle) {
		buffer.vertex(entry, x, y, z)
				.color(colour)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(FULL_BRIGHT)
				.normal(entry, MathHelper.cos(angle), 0.0F, MathHelper.sin(angle));
	}

	@Override
	public Identifier getTexture(AWallEntity wall) {
		return AWallModel.TEXTURE;
	}
}
