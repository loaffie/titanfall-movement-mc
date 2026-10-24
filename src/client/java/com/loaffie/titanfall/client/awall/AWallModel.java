package com.loaffie.titanfall.client.awall;

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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The A-Wall's meshes ({@code tools/generate-awall-model.py}): the pack on the upper back
 * ({@code models/entity/awall.obj}, in the player model's body space) - a plate of six discs in three rows
 * either side of their latches, as the A-Wall pilot carries - and the emitter disc ({@code models/entity/awall_disc.obj}, pixels, +Y up). The rack's top
 * slot stands empty while the next disc comes; the thrown disc's legs unfold and its mast rises as the wall goes
 * up.
 */
public final class AWallModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/awall.png");
	private static final ObjModel.Handle PACK = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/awall.obj"));
	private static final ObjModel.Handle DISC = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/awall_disc.obj"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	/** The wall's amber, and the lights' when the disc is ready. */
	public static final int AMBER = 0xFFFF9A2E;
	/** The suit's cyan, on the discs in the rack. */
	public static final int CYAN = 0xFF96E8FF;
	/** Over a chestplate the pack sits this much further out, pixels (the armour's inflation). */
	private static final float OVER_ARMOR = 1.05F;
	/** The disc's leg hinges and the mast's foot, disc pixels (the generator's LEG_HINGE_R/Y, MAST_BASE). */
	private static final float LEG_HINGE_R = 2.55F;
	private static final float LEG_HINGE_Y = -0.05F;
	private static final float MAST_BASE = 1.45F;
	/** Unfolded, a leg swings this far down and out about its hinge, degrees, lifting the disc by LEG_LIFT. */
	private static final float LEG_SWING = 125.0F;
	private static final float LEG_LIFT = 1.5F;

	private AWallModel() {
	}

	/** From the body part's space (after {@code body.rotate}, in blocks) to the pack's, over a chestplate if one is worn. */
	public static void onBack(MatrixStack matrices, boolean overArmor) {
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		if (overArmor) {
			matrices.translate(0.0F, 0.0F, OVER_ARMOR);
		}
	}

	/**
	 * The pack in body space: the rack of six discs, a cyan slit beside each. {@code charge} is how far the next
	 * disc has come, 0..1; until it is there ({@code nextDisc} false) its slot stands empty, the contact in its
	 * socket and its slit glowing amber brighter as it comes.
	 */
	public static void renderPack(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float charge, boolean nextDisc) {
		ObjModel mesh = PACK.get();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer base = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		mesh.render("rig", entry, base, light, overlay, WHITE);
		mesh.render("straps", entry, base, light, overlay, WHITE);
		mesh.render("discs", entry, base, light, overlay, WHITE);
		if (nextDisc) {
			mesh.render("disc0", entry, base, light, overlay, WHITE);
		}

		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("lights", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, CYAN);
		if (nextDisc) {
			mesh.render("light0", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, CYAN);
		} else {
			// The empty slot: the contact in its socket, and the slit beside it, glow amber brighter as the next disc comes.
			int charging = GrappleModel.dim(AMBER, 0.15F + 0.6F * MathHelper.clamp(charge, 0.0F, 1.0F));
			mesh.render("socket0", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, charging);
			mesh.render("light0", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, charging);
		}
	}

	/**
	 * The disc, in its own pixels (+Y up, standing on y = 0). {@code unfold} is how far its legs are out and its
	 * mast up, 0..1; {@code glow} the colour of its lights.
	 */
	public static void renderDisc(MatrixStack matrices, VertexConsumerProvider consumers, int light, float unfold, int glow) {
		ObjModel mesh = DISC.get();
		if (mesh == null) {
			return;
		}

		float u = MathHelper.clamp(unfold, 0.0F, 1.0F);
		VertexConsumer base = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		matrices.push();
		matrices.translate(0.0F, LEG_LIFT * u, 0.0F);
		for (int k = 0; k < 3; k++) {
			float a = k * MathHelper.TAU / 3.0F;
			matrices.push();
			matrices.translate(MathHelper.cos(a) * LEG_HINGE_R, LEG_HINGE_Y, MathHelper.sin(a) * LEG_HINGE_R);
			// About the tangent at the hinge: the leg, folded in under the disc, swings down and out.
			matrices.multiply(RotationAxis.of(new org.joml.Vector3f(-MathHelper.sin(a), 0.0F, MathHelper.cos(a))).rotationDegrees(LEG_SWING * u));
			matrices.translate(-MathHelper.cos(a) * LEG_HINGE_R, -LEG_HINGE_Y, -MathHelper.sin(a) * LEG_HINGE_R);
			mesh.render("leg" + k, matrices.peek(), base, light, OverlayTexture.DEFAULT_UV, WHITE);
			matrices.pop();
		}

		mesh.render("body", matrices.peek(), base, light, OverlayTexture.DEFAULT_UV, WHITE);
		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("glow", matrices.peek(), emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glow);
		if (u > 0.0F) {
			// The mast telescopes up out of the cap.
			matrices.push();
			matrices.translate(0.0F, MAST_BASE, 0.0F);
			matrices.scale(1.0F, Math.max(0.02F, u), 1.0F);
			matrices.translate(0.0F, -MAST_BASE, 0.0F);
			mesh.render("mast", matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
			mesh.render("tip", matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glow);
			matrices.pop();
		}

		matrices.pop();
	}

	/** Height of the disc's mast tip above its base when fully up, blocks: where the wall's light seems to come from. */
	public static float mastTop() {
		return (LEG_LIFT + MAST_BASE + 3.85F) / 16.0F;
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "awall_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				PACK.invalidate();
				DISC.invalidate();
			}
		};
	}
}
