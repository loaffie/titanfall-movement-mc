package com.loaffie.titanfall.client.stim;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the Stim item as the 3D injector (inventory, ground, item frames), full of stim. Like the Grapple,
 * it shrinks to nothing in the hands ({@code models/item/pilot_stim.json}): a held Stim is worn on the hip.
 *
 * <p>The injector (about 1.4 x 5.8 x 1.4 px along its axis) is laid diagonally across the one-block cube
 * vanilla hands over, needle down and to the left like a vanilla tool sprite.
 */
public final class StimItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** The injector's length centre on its axis (cap -0.8 to needle 5.0), pixels, and the scale to fill the block's diagonal. */
	private static final float CENTRE_Y = 2.1F;
	private static final float SCALE = 1.15F / 5.8F;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.translate(0.0F, -CENTRE_Y, 0.0F);
		StimModel.renderInjector(matrices, vertexConsumers, light, 1.0F);
		matrices.pop();
	}
}
