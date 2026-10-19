package com.loaffie.titanfall.client.phase;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Draws the Phase Shift item with the gauntlet mesh: in the inventory, on the ground and in item frames. The
 * item model ({@code models/item/pilot_phase_shift.json}) shrinks it to nothing in the hands, because a held
 * gauntlet is worn - the feature renderer draws it on the back of the hand instead.
 *
 * <p>Vanilla hands this renderer a one-block cube (0..1) after the model's display transform. The gauntlet
 * (about 5 x 7 x 5 px) is centred in it with the back of the hand - the coil - toward the viewer and the
 * fingers up, and scaled so its 7 px length fills 80% of the block.
 */
public final class PhaseItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Mesh bounds centre, pixels. */
	private static final float CENTRE_Y = 6.25F;
	private static final float CENTRE_Z = -0.6F;
	private static final float SCALE = 0.8F / 7.0F;
	/** Mesh +X to item -X, mesh +Y (toward the fingers) to item +Y, mesh -Z (the back of the hand) to item +Z. */
	private static final Quaternionf FACE_VIEWER = new Quaternionf().setFromNormalized(new Matrix3f(
			-1.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, -1.0F));

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.multiply(FACE_VIEWER);
		matrices.translate(0.0F, -CENTRE_Y, -CENTRE_Z);
		PhaseModel.render(matrices, vertexConsumers, light, overlay, 0.0F, 0.0F, 0);
		matrices.pop();
	}
}
