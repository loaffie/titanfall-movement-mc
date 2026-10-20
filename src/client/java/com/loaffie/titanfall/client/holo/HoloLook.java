package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * How a decoy's stand-in is drawn this frame. Most of the time exactly as its pilot - that is the point of
 * it - but it projects in and breaks up as a hologram: see-through, cooled toward cyan, with bright scan
 * lines crawling up it; and it does the same, briefly, with each pulse. Its own side sees a faint scan over
 * it all the time, so they can tell it from the pilot. Drawn through the living-entity renderer's own path
 * (see {@code LivingEntityRendererCloakMixin}), which asks {@link #of} for the entity it is drawing.
 */
public final class HoloLook {
	private static final Identifier SCAN = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/holo_scan.png");
	/** The scan its own side always sees. */
	private static final float FRIENDLY_SCAN = 0.14F;
	/** Closer than this to the camera it fades out, so a pilot doesn't see the inside of their own decoy's head. */
	private static final double NEAR_FADE_START = 1.0;
	private static final double NEAR_FADE_LENGTH = 2.0;
	/**
	 * Below this it is drawn without its armour, held items, gear and name: those can't be drawn see-through,
	 * so they come in only once the body is all but solid.
	 */
	private static final float GEAR_ALPHA = 0.9F;

	/**
	 * @param alpha    how solid it is, 0..1
	 * @param scan     how bright the scan lines are, 0..1
	 * @param tint     how far it is cooled toward cyan, 0..1
	 * @param hideGear its armour, what it holds, its gear and its name aren't drawn (it is still faint)
	 */
	public record Look(float alpha, float scan, float tint, boolean hideGear) {
	}

	private static Entity drawing;
	private static Look current;

	private HoloLook() {
	}

	/** The look of a decoy this frame, {@code toCamera} blocks from the camera. */
	public static Look of(HoloDecoys.State state, float tickDelta, double toCamera) {
		float projected = state.projected(tickDelta);
		float dissolve = state.decoy.dissolve(tickDelta);
		float pulse = state.pulse(tickDelta);
		float near = (float) MathHelper.clamp((toCamera - NEAR_FADE_START) / NEAR_FADE_LENGTH, 0.0, 1.0);
		float forming = Math.max(1.0F - projected, dissolve);
		float alpha = projected * (1.0F - dissolve) * near;
		float scan = Math.max(forming, pulse * 0.85F) + (state.friendly() ? FRIENDLY_SCAN : 0.0F);
		float tint = Math.max(forming, pulse * 0.7F);
		return new Look(alpha, MathHelper.clamp(scan, 0.0F, 1.0F), tint, alpha < GEAR_ALPHA);
	}

	public static void begin(Entity standIn, Look look) {
		drawing = standIn;
		current = look;
	}

	public static void end() {
		drawing = null;
		current = null;
	}

	/** The look of the entity being drawn, if it is a decoy's stand-in. */
	public static Look of(Entity entity) {
		return entity == drawing ? current : null;
	}

	public static boolean seeThrough(Look look) {
		return look.alpha() < 0.995F;
	}

	/** The translucent layer a see-through decoy is drawn in. */
	public static RenderLayer bodyLayer(Identifier texture) {
		return RenderLayer.getEntityTranslucent(texture);
	}

	/** The body's colour: the skin at the look's alpha, cooled toward cyan by its tint. */
	public static int bodyColor(Look look) {
		int r = Math.round(255 * MathHelper.lerp(look.tint(), 1.0F, 0.45F));
		int g = Math.round(255 * MathHelper.lerp(look.tint(), 1.0F, 0.88F));
		int a = Math.round(255 * MathHelper.clamp(look.alpha(), 0.0F, 1.0F));
		return (a << 24) | (r << 16) | (g << 8) | 0xFF;
	}

	/** The scan lines crawling up the body, drawn with the model as it was just posed. */
	public static void renderScan(Model model, MatrixStack matrices, VertexConsumerProvider consumers, int light, Look look, float time) {
		float strength = look.scan() * Math.max(0.35F, look.alpha() + look.tint() * 0.6F);
		if (strength <= 0.01F) {
			return;
		}

		int k = Math.round(255 * MathHelper.clamp(strength, 0.0F, 1.0F));
		int color = 0xFF000000 | (Math.round(k * 0.35F) << 16) | (Math.round(k * 0.85F) << 8) | k;
		model.render(matrices, consumers.getBuffer(RenderLayer.getEnergySwirl(SCAN, 0.0F, -time * 0.02F)), light, OverlayTexture.DEFAULT_UV, color);
	}
}
