package com.loaffie.titanfall.client.stim;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.client.render.ObjModel;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Stim's mesh ({@code models/entity/stim.obj}, built by {@code tools/generate-stim-model.py}): the
 * autoinjector and its hip holster, and where they sit on the pilot.
 *
 * <p>Mesh units are player-model pixels, the injector's axis on +Y with the needle at {@link #NEEDLE_Y}.
 * The fluid in the vial is drawn full-bright and scaled toward the needle end as the dose goes in, so the
 * vial visibly empties during the jab and fills again as the stim recharges.
 */
public final class StimModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/stim.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/stim.obj"));
	private static final float VIAL_END = 3.05F;
	public static final float NEEDLE_Y = 5.0F;
	/**
	 * Where the fist closes round the injector, mesh pixels along its axis: on the plunger cap, thumb on
	 * the button, so the body, vial and needle all stand clear of the fist.
	 */
	private static final float GRIP_Y = -1.6F;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int FLUID = 0xFF4FCFFF;
	private static final int GLASS = 0xFF2C5464;

	/**
	 * The holster on the body part (pixels: +X the pilot's left, +Y down, -Z the front): on the front of the
	 * right hip, at the belt, its back plate against the body and the injector tipped out a little.
	 */
	private static final float HIP_X = -3.3F;
	private static final float HIP_Y = 8.3F;
	private static final float HIP_Z = -2.0F;
	private static final float HIP_TILT = 14.0F;
	/** Mesh depth of the holster's back plate: it sits on the body's front face. */
	private static final float PLATE_BACK = 1.1F;

	private StimModel() {
	}

	public static ObjModel mesh() {
		return MESH.get();
	}

	/** From the body part's space (after {@code body.rotate}, in blocks) to the holster's mesh space. */
	public static void onHip(MatrixStack matrices) {
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		matrices.translate(HIP_X, HIP_Y, HIP_Z);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(HIP_TILT));
		matrices.translate(0.0F, 0.0F, -PLATE_BACK);
	}

	public static void renderHolster(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ObjModel mesh = mesh();
		if (mesh != null) {
			mesh.render("holster", matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
		}
	}

	/** The injector in mesh space, its vial {@code fill} (0..1) full of glowing stim. */
	public static void renderInjector(MatrixStack matrices, VertexConsumerProvider consumers, int light, float fill) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		mesh.render("injector", matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
		mesh.render("glass", matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, GLASS);
		if (fill > 0.02F) {
			// The plunger drives the fluid out through the needle: the column shrinks toward the needle end.
			matrices.push();
			matrices.translate(0.0F, VIAL_END, 0.0F);
			matrices.scale(1.0F, fill, 1.0F);
			matrices.translate(0.0F, -VIAL_END, 0.0F);
			mesh.render("fluid", matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
					brightness(0.45F + 0.55F * fill));
			matrices.pop();
		}
	}

	/**
	 * The injector in a fist, its needle aimed at {@code target}. {@code grip} is the fist's centre in the
	 * current space (mesh pixels after the caller's arm transform); {@code target} is camera-relative and
	 * world-aligned, like the world and hand render passes, and is carried into the current space through
	 * the inverse of its matrix. {@code jab} (pixels) drives the needle in toward the target.
	 */
	public static void renderInFist(MatrixStack matrices, VertexConsumerProvider consumers, int light, Vector3f grip, Vector3f target,
			float jab, float fill) {
		Matrix4f toLocal = new Matrix4f(matrices.peek().getPositionMatrix()).invert();
		Vector3f local = toLocal.transformPosition(new Vector3f(target));
		Vector3f aim = local.sub(grip, new Vector3f());
		if (aim.lengthSquared() < 1.0E-6F) {
			aim.set(0.0F, 1.0F, 0.0F);
		}

		aim.normalize();
		matrices.push();
		matrices.translate(grip.x + aim.x * jab, grip.y + aim.y * jab, grip.z + aim.z * jab);
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), aim));
		matrices.translate(0.0F, -GRIP_Y, 0.0F);
		renderInjector(matrices, consumers, light, fill);
		matrices.pop();
	}

	private static int brightness(float k) {
		int r = Math.round(((FLUID >> 16) & 0xFF) * k);
		int g = Math.round(((FLUID >> 8) & 0xFF) * k);
		int b = Math.round((FLUID & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "stim_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
