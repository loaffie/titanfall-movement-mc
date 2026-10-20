package com.loaffie.titanfall.client.pulse;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the Pulse Blade item with the kunai mesh: in the inventory, on the ground and in item frames. The
 * item model ({@code models/item/pilot_pulse_blade.json}) shrinks it to nothing in the hands, because a held
 * Pulse Blade is worn - the feature renderer draws it in its sheath on the hip instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The kunai
 * (9.7 px from ring to tip) is centred in it laid diagonally like a vanilla sword, tip up and to the right,
 * flat face to the viewer, filling 95% of the diagonal.
 */
public final class PulseItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre along the axis, pixels. */
	private static final float CENTRE_Y = 4.8F;
	private static final float SCALE = 0.95F * 1.414F / 9.7F;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0F));
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.translate(0.0F, -CENTRE_Y, 0.0F);
		PulseModel.renderKunai(matrices, vertexConsumers, light, false, 0.0F);
		matrices.pop();
	}
}
