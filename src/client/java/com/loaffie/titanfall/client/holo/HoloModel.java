package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.client.grapple.GrappleModel;
import com.loaffie.titanfall.client.render.ObjModel;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * The Holo Pilot projector's mesh ({@code models/entity/holo.obj}, built by {@code tools/generate-holo-model.py}),
 * in the player model's body space. The lens glows by the charge - pale cyan with a charge held, amber
 * while the first comes back - and each of the two lights under it is lit while its charge is held.
 */
public final class HoloModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/holo.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/holo.obj"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	public static final int READY = 0xFF8FE6FF;
	public static final int CHARGING = 0xFFFF9A2E;
	private static final int PIP_OFF = 0xFF16222A;
	/** Over a chestplate the rig sits this much further out, pixels (the armour's inflation). */
	private static final float OVER_ARMOR = 1.05F;

	private HoloModel() {
	}

	/** From the body part's space (after {@code body.rotate}, in blocks) to the mesh's, over a chestplate if one is worn. */
	public static void onChest(MatrixStack matrices, boolean overArmor) {
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		if (overArmor) {
			matrices.translate(0.0F, 0.0F, -OVER_ARMOR);
		}
	}

	/**
	 * The rig in mesh space. {@code lens} is the lens colour; {@code charges} how many lights are lit, and
	 * {@code partial} how far the next one has come back (it glows faintly, brightening as it fills).
	 */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, int lens, int charges, float partial) {
		ObjModel mesh = MESH.get();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		mesh.render("rig", entry, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, WHITE);
		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("lens", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, lens);
		for (int i = 0; i < 2; i++) {
			int color = i < charges ? READY : i == charges ? GrappleModel.dim(CHARGING, 0.15F + 0.5F * partial) : PIP_OFF;
			mesh.render("pip" + i, entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, color);
		}
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "holo_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
