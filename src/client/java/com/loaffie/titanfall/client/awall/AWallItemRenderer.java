package com.loaffie.titanfall.client.awall;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the A-Wall item with the pack's mesh: in the inventory, on the ground and in item frames. The item model
 * ({@code models/item/pilot_awall.json}) shrinks it to nothing in the hands, because a held pack is worn - the
 * feature renderer draws it on the back instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The rack (about
 * 4.9 x 5.3 px, its straps aside) is centred in it with its discs to the viewer, and scaled to fill most of the
 * block.
 */
public final class AWallItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre, pixels. */
	private static final float CENTRE_Y = 3.1F;
	private static final float CENTRE_Z = 2.9F;
	private static final float SCALE = 0.8F / 6.2F;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.scale(SCALE, SCALE, SCALE);
		// Mesh +Y runs down the body and +Z out of the back: turn it upright, back to the viewer.
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		matrices.translate(0.0F, -CENTRE_Y, -CENTRE_Z);
		AWallModel.renderPack(matrices, vertexConsumers, light, overlay, 1.0F, true);
		matrices.pop();
	}
}
