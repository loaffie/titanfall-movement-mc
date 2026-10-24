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

/**
 * Draws the A-Wall: the disc spinning through the air, then planted on its legs with its mast up, and the wall
 * it holds - a curved sheet of amber cells ({@code textures/entity/awall_field.png}) drifting slowly upward,
 * brightest along its top and its two ends.
 *
 * <p>The wall rises from the disc, spreading out to its ends and up as it goes ({@link AWallStates}), flares
 * where it stops a shot, reddens and gutters as its health runs down, stutters in its last seconds, and shrinks
 * back into the disc as it comes down.
 */
public class AWallRenderer extends EntityRenderer<AWallEntity> {
	private static final Identifier FIELD = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/awall_field.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Columns across the arc, and the heights (0..1) of the rows of vertices up it: closer at the top and bottom for the bright edges. */
	private static final int COLUMNS = 24;
	private static final float[] ROWS = {0.0F, 0.05F, 0.3F, 0.6F, 0.86F, 0.95F, 1.0F};
	/** Blocks of wall per tile of the field texture. */
	private static final float TILE = 1.6F;
	/** The wall's colour, full health and none: amber going to a hot red. */
	private static final float[] AMBER = {1.0F, 0.6F, 0.18F};
	private static final float[] RED = {1.0F, 0.22F, 0.1F};
	/** How fast the cells drift up the wall, texture tiles a tick. */
	private static final float DRIFT = 0.004F;
	/**
	 * Additive and full-bright, both sides drawn - the charged creeper's energy layer, but not writing depth: a sheet
	 * of light, it shouldn't hide the particles and the clouds of stopped shots behind it. One layer for every wall
	 * (the drift is in the texture coordinates), so walls share a buffer instead of each frame making its own.
	 */
	private static final RenderLayer FIELD_LAYER = RenderLayer.of("titanfall_awall_field", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
			VertexFormat.DrawMode.QUADS, 1536, false, true, RenderLayer.MultiPhaseParameters.builder()
					.program(RenderPhase.ENERGY_SWIRL_PROGRAM)
					.texture(new RenderPhase.Texture(FIELD, false, false))
					.transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
					.cull(RenderPhase.DISABLE_CULLING)
					.lightmap(RenderPhase.ENABLE_LIGHTMAP)
					.overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
					.writeMaskState(RenderPhase.COLOR_MASK)
					.build(false));
	/** Brightness of the face, and the most its edges add. */
	private static final float FACE = 0.3F;
	private static final float EDGE = 0.75F;

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
	 * The sheet, in the entity's space (blocks, world axes): a band of the cylinder about the axis a radius behind
	 * the disc, {@code raised} of the way out to its ends and up to its top.
	 */
	private static void drawWall(AWallEntity wall, MatrixStack matrices, VertexConsumerProvider consumers, float raised, float brightness,
			float health, float time) {
		float spread = raised;
		float rise = MathHelper.clamp(raised * 1.3F - 0.3F, 0.08F, 1.0F);
		float halfArc = (float) AWall.HALF_ARC * spread;
		float height = (float) AWall.HEIGHT * rise;
		float radius = (float) AWall.RADIUS;
		float yawRad = wall.wallYaw() * MathHelper.RADIANS_PER_DEGREE;
		float fx = -MathHelper.sin(yawRad);
		float fz = MathHelper.cos(yawRad);
		float cx = -fx * radius;
		float cz = -fz * radius;
		float base = (float) Math.atan2(fz, fx);
		float bottom = (float) -AWall.SKIRT;

		VertexConsumer buffer = consumers.getBuffer(FIELD_LAYER);
		float drift = time * DRIFT % 1.0F;
		MatrixStack.Entry entry = matrices.peek();
		for (int c = 0; c < COLUMNS; c++) {
			float s0 = -1.0F + 2.0F * c / COLUMNS;
			float s1 = -1.0F + 2.0F * (c + 1) / COLUMNS;
			float a0 = base + s0 * halfArc;
			float a1 = base + s1 * halfArc;
			float x0 = cx + MathHelper.cos(a0) * radius;
			float z0 = cz + MathHelper.sin(a0) * radius;
			float x1 = cx + MathHelper.cos(a1) * radius;
			float z1 = cz + MathHelper.sin(a1) * radius;
			// Texture u runs along the arc, fixed to the full wall so the cells don't slide as it spreads.
			float u0 = s0 * (float) AWall.HALF_ARC * radius / TILE;
			float u1 = s1 * (float) AWall.HALF_ARC * radius / TILE;
			for (int r = 0; r < ROWS.length - 1; r++) {
				float h0 = ROWS[r];
				float h1 = ROWS[r + 1];
				float y0 = bottom + (height - bottom) * h0;
				float y1 = bottom + (height - bottom) * h1;
				float v0 = drift - y0 / TILE;
				float v1 = drift - y1 / TILE;
				vertex(entry, buffer, x0, y0, z0, u0, v0, intensity(s0, h0, brightness), health, a0);
				vertex(entry, buffer, x1, y0, z1, u1, v0, intensity(s1, h0, brightness), health, a1);
				vertex(entry, buffer, x1, y1, z1, u1, v1, intensity(s1, h1, brightness), health, a1);
				vertex(entry, buffer, x0, y1, z0, u0, v1, intensity(s0, h1, brightness), health, a0);
			}
		}
	}

	/** How bright the wall is at a point: the face, lit up toward its top and its two ends, and fading out at the very bottom. */
	private static float intensity(float across, float up, float brightness) {
		float side = Math.abs(across);
		float edge = Math.max(up * up * up * up, side * side * side * side * side * side);
		float foot = MathHelper.clamp(up / 0.05F, 0.35F, 1.0F);
		return (FACE + EDGE * edge) * brightness * foot;
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer buffer, float x, float y, float z, float u, float v, float intensity,
			float health, float angle) {
		buffer.vertex(entry, x, y, z)
				.color(colour(health, intensity))
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
