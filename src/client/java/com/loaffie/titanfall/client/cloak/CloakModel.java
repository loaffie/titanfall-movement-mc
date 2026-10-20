package com.loaffie.titanfall.client.cloak;

import com.loaffie.titanfall.TitanfallMovement;
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
 * The Cloak bracer's mesh ({@code models/entity/cloak.obj}, built by {@code tools/generate-cloak-model.py}),
 * in the Grapple's arm space, so {@code GrappleModel.onArm} puts it on either forearm. Its lenses and rail
 * strips glow by the charge: pale cyan when ready, amber while it refills.
 */
public final class CloakModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/cloak.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/cloak.obj"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	public static final int READY = 0xFF8FDCFF;
	public static final int CHARGING = 0xFFFF9A2E;

	private CloakModel() {
	}

	/** The bracer in mesh space, its lights in {@code light}. */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, int lightColor) {
		ObjModel mesh = MESH.get();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		mesh.render("bracer", entry, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, WHITE);
		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("lens", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, lightColor);
		mesh.render("strip", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, lightColor);
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "cloak_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
