package com.loaffie.titanfall.client.holo;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the Holo Pilot item with the rig's mesh: in the inventory, on the ground and in item frames. The item
 * model ({@code models/item/pilot_holo.json}) shrinks it to nothing in the hands, because a held projector is
 * worn - the feature renderer draws it on the chest instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The rig (about
 * 8.6 x 7.3 px with its straps) is centred in it facing the viewer (+Z), and scaled to fill 85% of the block.
 */
public final class HoloItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre, pixels. */
	private static final float CENTRE_Y = 3.3F;
	private static final float CENTRE_Z = -2.8F;
	private static final float SCALE = 0.85F / 8.6F;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.scale(SCALE, SCALE, SCALE);
		// Mesh +Y runs down the body and -Z out of the chest: turn it upright, front to the viewer.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
		matrices.translate(0.0F, -CENTRE_Y, -CENTRE_Z);
		HoloModel.render(matrices, vertexConsumers, light, overlay, HoloModel.READY, 2, 0.0F);
		matrices.pop();
	}
}
