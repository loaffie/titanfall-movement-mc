package com.loaffie.titanfall.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/**
 * Draws the Pilot Jumpkit item - in the inventory, the hotbar, both hands in first and third person, on
 * the ground and in item frames - with the same OBJ mesh and textures as the worn kit, including the
 * glowing thruster exhausts.
 *
 * <p>The item model ({@code models/item/pilot_jumpkit.json}) uses {@code builtin/entity}, so vanilla
 * applies that file's {@code display} transforms for each view and then hands this renderer a one-block
 * cube (0..1 on each axis). The mesh is centred in it and scaled so its widest side spans 95% of the
 * block; its body-space axes are turned into item space (Y up), with the pack's outer face - the side
 * you see on a player's back - facing the viewer.
 */
public final class JumpkitItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	private static final float FILL = 0.95F;

	private ModelPart fallbackKit;
	private ModelPart fallbackGlow;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		matrices.push();
		matrices.translate(0.5F, 0.5F, 0.5F);
		JumpkitMesh mesh = JumpkitMesh.get();
		if (mesh != null) {
			float[] b = mesh.bounds();
			float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
			float scale = FILL / size;
			// Body space is Y-down; item space is Y-up. Flip Y, centre the box on the block centre.
			matrices.scale(scale, -scale, scale);
			matrices.translate(-(b[0] + b[3]) / 2.0F, -(b[1] + b[4]) / 2.0F, -(b[2] + b[5]) / 2.0F);
			// The kit at rest: modules in place, flaps closed.
			mesh.renderStatic(matrices, vertexConsumers, RenderLayer.getEntityCutoutNoCull(JumpkitFeatureRenderer.OBJ_TEXTURE),
					RenderLayer.getEyes(JumpkitFeatureRenderer.OBJ_EMISSIVE), light, overlay, WHITE, WHITE);
		} else {
			// The OBJ failed to load: show the built-in cuboid kit, which spans about 22 x 8 px around y 10.
			if (fallbackKit == null) {
				ModelPart root = JumpkitModel.getTexturedModelData().createModel();
				fallbackKit = root.getChild("kit");
				fallbackGlow = root.getChild("glow");
			}

			matrices.scale(1.0F / 16.0F * 1.3F, -1.0F / 16.0F * 1.3F, 1.0F / 16.0F * 1.3F);
			matrices.translate(0.0F, -10.0F, 0.0F);
			fallbackKit.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(JumpkitFeatureRenderer.TEXTURE)), light, overlay);
			fallbackGlow.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEyes(JumpkitFeatureRenderer.TEXTURE)), FULL_BRIGHT, overlay, WHITE);
		}

		matrices.pop();
	}
}
