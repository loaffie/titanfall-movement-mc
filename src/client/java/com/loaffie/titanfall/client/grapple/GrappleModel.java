package com.loaffie.titanfall.client.grapple;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.client.render.ObjModel;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * The Grapple's mesh ({@code models/entity/grapple.obj}, built by {@code tools/generate-grapple-model.py})
 * and how it sits on an arm.
 *
 * <p>The mesh is modelled on a left arm in the player model's arm space, in pixels, with the arm's centre
 * line at x = 0. {@link #onArm} moves it onto the arm actually wearing it: to the arm's centre (wide arms
 * are 4 px across, slim ones 3), mirrored for a right arm and narrowed for a slim one.
 */
public final class GrappleModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/grapple.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/grapple.obj"));

	/** Muzzle centre in mesh pixels, where the line leaves the launcher and the loaded hook sits. */
	public static final Vector3f MUZZLE = new Vector3f(0.0F, 11.8F, -3.3F);
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	/** Status light: ready (cyan), charging (amber), reeling (hot white-cyan). */
	public static final int LIGHT_READY = 0xFF5FD8FF;
	public static final int LIGHT_CHARGING = 0xFFFF9A2E;
	public static final int LIGHT_ACTIVE = 0xFFC8F6FF;

	private GrappleModel() {
	}

	public static ObjModel mesh() {
		return MESH.get();
	}

	/**
	 * From the arm part's own space (after {@code arm.rotate}, in blocks) to mesh pixels on that arm. Wide arm
	 * centres sit at x = +-1 px from the joint, slim ones at +-0.5. Returns true when it mirrors the mesh (on a
	 * right arm), to pass on to the render call.
	 */
	public static boolean onArm(MatrixStack matrices, Arm arm, boolean slim) {
		float side = arm == Arm.LEFT ? 1.0F : -1.0F;
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		matrices.translate(side * (slim ? 0.5F : 1.0F), 0.0F, 0.0F);
		matrices.scale(side * (slim ? 0.8F : 1.0F), 1.0F, 1.0F);
		return arm == Arm.RIGHT;
	}

	/**
	 * Draws the gauntlet in mesh space: the lit body, the status light tinted {@code lightColor}, and - when
	 * {@code loaded} - the hook seated in the muzzle. {@code mirrored} as {@link #onArm} returned.
	 */
	public static void renderGauntlet(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay,
			boolean loaded, int lightColor, boolean mirrored) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		mesh.render("gauntlet", entry, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, WHITE, mirrored);
		if (loaded) {
			matrices.push();
			matrices.translate(MUZZLE.x, MUZZLE.y - 0.9F, MUZZLE.z);
			mesh.render("hook", matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, WHITE, mirrored);
			matrices.pop();
		}

		mesh.render("glow", entry, consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, lightColor, mirrored);
		if (loaded) {
			matrices.push();
			matrices.translate(MUZZLE.x, MUZZLE.y - 0.9F, MUZZLE.z);
			mesh.render("hook_glow", matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
					dim(lightColor, 0.6F), mirrored);
			matrices.pop();
		}
	}

	/** Draws the hook alone, in its own space (eyelet at the origin, claws along +Y, pixels). */
	public static void renderHook(MatrixStack matrices, VertexConsumerProvider consumers, int light, int glowColor) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		mesh.render("hook", entry, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
		mesh.render("hook_glow", entry, consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glowColor);
	}

	/** Scales an opaque ARGB colour's RGB by {@code k}. */
	public static int dim(int color, float k) {
		int r = Math.round(((color >> 16) & 0xFF) * k);
		int g = Math.round(((color >> 8) & 0xFF) * k);
		int b = Math.round((color & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "grapple_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
