package com.loaffie.titanfall.client.cloak;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Draws the Cloak item with the bracer mesh: in the inventory, on the ground and in item frames. The item
 * model ({@code models/item/pilot_cloak.json}) shrinks it to nothing in the hands, because a held Cloak is
 * worn - the feature renderer draws it on the forearm instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The wrist band
 * (about 4.5 x 5 x 5 px with its cable) is centred in it lying along the item's X axis, its lenses up toward
 * the viewer, and scaled so it fills 80% of the block.
 */
public final class CloakItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre, pixels. */
	private static final float CENTRE_Y = 7.4F;
	private static final float CENTRE_Z = -0.45F;
	private static final float SCALE = 0.8F / 5.2F;
	/** Mesh +X to item +Z, mesh +Y (toward the hand) to item -X, mesh -Z (the top, with the lenses) to item +Y. */
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
		matrices.translate(0.0F, -CENTRE_Y, -CENTRE_Z);
		CloakModel.render(matrices, vertexConsumers, light, overlay, CloakModel.READY);
		matrices.pop();
	}
}
