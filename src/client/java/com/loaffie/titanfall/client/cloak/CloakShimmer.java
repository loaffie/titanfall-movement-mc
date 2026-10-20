package com.loaffie.titanfall.client.cloak;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * How a cloaked pilot looks: all but clear - the skin at a few percent, cooled toward blue-grey - with a faint
 * crawling sheen over the body like the bend of light round them. Titanfall 2's cloak shows more when the
 * pilot moves and flickers when they're hit; so does this, and it fades in and out as the cloak closes and
 * falls away. The pilot's own hands get the same look, a little stronger, so they can see them.
 */
public final class CloakShimmer {
	private static final Identifier SHEEN = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
	/** How much of the body shows standing still, and the most that moving adds. */
	private static final float STILL = 0.05F;
	private static final float MOVING = 0.16F;
	/** Hit: the cloak flickers up to this. */
	private static final float HIT = 0.55F;
	/** The pilot's own hands, cloaked. */
	public static final float OWN_HANDS = 0.3F;

	private CloakShimmer() {
	}

	/** How much of the entity shows through, 1 when it isn't cloaked. */
	public static float visibility(LivingEntity entity, float tickDelta) {
		float amount = CloakStates.amount(entity, tickDelta);
		if (amount <= 0.0F) {
			return 1.0F;
		}

		double dx = entity.getX() - entity.prevX;
		double dz = entity.getZ() - entity.prevZ;
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		float shows = STILL + Math.min(MOVING, speed * 0.55F);
		if (entity.hurtTime > 0) {
			// A ragged flicker for as long as the hurt flash lasts.
			float flicker = 0.5F + 0.5F * MathHelper.sin((entity.age + tickDelta) * 2.9F) * MathHelper.sin((entity.age + tickDelta) * 1.7F);
			shows = Math.max(shows, HIT * flicker);
		}

		return MathHelper.lerp(amount, 1.0F, shows);
	}

	/** The body's colour: the skin at {@code alpha}, cooled toward blue-grey as the cloak closes. */
	public static int bodyColor(float alpha, float amount) {
		int r = Math.round(255 * MathHelper.lerp(amount, 1.0F, 0.72F));
		int g = Math.round(255 * MathHelper.lerp(amount, 1.0F, 0.84F));
		int a = Math.round(255 * MathHelper.clamp(alpha, 0.0F, 1.0F));
		return (a << 24) | (r << 16) | (g << 8) | 0xFF;
	}

	/** The translucent layer the cloaked body is drawn in. */
	public static RenderLayer bodyLayer(Identifier texture) {
		return RenderLayer.getEntityTranslucent(texture);
	}

	/** The crawling sheen over a cloaked body, drawn with the model as it was just posed. */
	public static void renderSheen(Model model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float amount, float shows, float time) {
		if (amount <= 0.01F) {
			return;
		}

		// Brightest mid-fade (the light bending shut or open), and with whatever makes the body show.
		float edge = 1.0F - Math.abs(amount * 2.0F - 1.0F);
		float strength = amount * (0.06F + 0.45F * shows) + 0.3F * edge;
		int k = Math.round(255 * MathHelper.clamp(strength, 0.0F, 1.0F));
		int color = 0xFF000000 | (Math.round(k * 0.55F) << 16) | (Math.round(k * 0.72F) << 8) | k;
		model.render(matrices, consumers.getBuffer(RenderLayer.getEnergySwirl(SHEEN, time * 0.006F, time * 0.004F)), light,
				OverlayTexture.DEFAULT_UV, color);
	}
}
