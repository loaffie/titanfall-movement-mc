package com.loaffie.titanfall.client.grapple;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Draws the Grapple item with the gauntlet mesh: in the inventory, on the ground and in item frames. The
 * item model ({@code models/item/pilot_grapple.json}) shrinks it to nothing in the hands, because a held
 * Grapple is worn - the feature renderer draws it strapped to the forearm instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The gauntlet
 * (about 6 x 9 x 8 px) is centred in it, turned to lie along the item's X axis - hand end to the left,
 * launcher on top, spool facing the viewer - and scaled so its 9.6 px length fills 85% of the block.
 */
public final class GrappleItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre, pixels. */
	private static final float CENTRE_X = 0.06F;
	private static final float CENTRE_Y = 8.9F;
	private static final float CENTRE_Z = -1.1F;
	private static final float SCALE = 0.85F / 9.6F;
	/** Mesh +X (outside of the arm) to item +Z, mesh +Y (toward the hand) to item -X, mesh -Z (top) to item +Y. */
	private static final Quaternionf LAY_FLAT = new Quaternionf().setFromNormalized(new Matrix3f(
			0.0F, 0.0F, 1.0F,
			-1.0F, 0.0F, 0.0F,
			0.0F, -1.0F, 0.0F));

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.multiply(LAY_FLAT);
		matrices.translate(-CENTRE_X, -CENTRE_Y, -CENTRE_Z);
		GrappleModel.renderGauntlet(matrices, vertexConsumers, light, overlay, true, GrappleModel.LIGHT_READY);
		matrices.pop();
	}
}
