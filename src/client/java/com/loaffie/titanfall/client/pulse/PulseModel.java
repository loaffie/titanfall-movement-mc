package com.loaffie.titanfall.client.pulse;

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
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Pulse Blade's mesh ({@code models/entity/pulse_blade.obj}, built by {@code tools/generate-pulse-model.py})
 * and where it sits: in its sheath on the front of the left hip, in the hand, or flying and stuck.
 *
 * <p>Mesh space is in pixels with the kunai's axis on +Y (ring at the origin, tip at {@link #TIP_Y}). The
 * band and the blade's rim glow blue, brighter as it pulses; the read-out shows green ("SAFE") in the
 * sheath and orange ("ARMED") once drawn.
 */
public final class PulseModel {
	public static final Identifier TEXTURE = Identifier.of(TitanfallMovement.MOD_ID, "textures/entity/pulse_blade.png");
	private static final ObjModel.Handle MESH = new ObjModel.Handle(Identifier.of(TitanfallMovement.MOD_ID, "models/entity/pulse_blade.obj"));

	/** The blade's tip and the middle of the grip, mesh pixels along the axis (the generator's numbers). */
	public static final float TIP_Y = 9.55F;
	public static final float GRIP_Y = 2.95F;
	/** The emissive swatch of the atlas, for the arc and the wave. */
	public static final float LIGHT_U = 40.0F / 64.0F;
	public static final float LIGHT_V = 24.0F / 64.0F;
	public static final int FULL_BRIGHT = 0xF000F0;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int BAND = 0xFF3F8CFF;
	private static final int EDGE = 0xFF7FC6FF;
	private static final int SAFE = 0xFF4FE09A;
	private static final int ARMED = 0xFFFF7A2E;

	/**
	 * The sheath on the body part (pixels: +X the pilot's left, +Y down, -Z the front): on the front of the left
	 * hip, ring up at the belt, the blade down and tipped out a little, its clip against the body.
	 */
	private static final float HIP_X = 3.3F;
	private static final float HIP_Y = 5.4F;
	private static final float HIP_Z = -2.0F;
	private static final float HIP_TILT = -16.0F;
	/** Mesh depth of the sheath's clip: it sits on the body's front face. */
	private static final float CLIP_BACK = 0.62F;

	/** The held kunai: its grip near the end of the fist (arm pixels), and its axis out of the front of the hand. */
	private static final Vector3f HELD_GRIP = new Vector3f(0.0F, 9.4F, -1.5F);
	private static final Vector3f HELD_AXIS = new Vector3f(0.0F, 0.55F, -0.83F).normalize();

	private PulseModel() {
	}

	public static ObjModel mesh() {
		return MESH.get();
	}

	/** From the body part's space (after {@code body.rotate}, in blocks) to the sheath's mesh space. */
	public static void onHip(MatrixStack matrices) {
		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		matrices.translate(HIP_X, HIP_Y, HIP_Z);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(HIP_TILT));
		matrices.translate(0.0F, 0.0F, -CLIP_BACK);
	}

	/** The sheath, with the kunai in it when {@code loaded} (a pilot's sheath is empty while the next one charges). */
	public static void renderSheathed(MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean loaded) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		mesh.render("sheath", matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
		if (loaded) {
			renderKunai(matrices, consumers, light, false, 0.0F);
		}
	}

	/** The kunai in mesh space; {@code glow} 0..1 brightens the band and rim (a pulse). */
	public static void renderKunai(MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean armed, float glow) {
		ObjModel mesh = mesh();
		if (mesh == null) {
			return;
		}

		MatrixStack.Entry entry = matrices.peek();
		mesh.render("kunai", entry, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, WHITE);
		VertexConsumer emissive = consumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		mesh.render("band", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, GrappleModel.dim(BAND, 0.7F + 0.3F * glow));
		mesh.render("edge", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, GrappleModel.dim(EDGE, (armed ? 0.55F : 0.3F) + 0.45F * glow));
		mesh.render("display", entry, emissive, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, armed ? ARMED : SAFE);
	}

	/**
	 * The kunai in a fist, in the arm part's own space (pixels, after the arm's transform; {@code centre} is the
	 * arm's centre line, 1 px off the joint for a wide arm, 0.5 for a slim one). Held the way vanilla holds a
	 * sword: gripped at the end of the fist, its grip and ring inside the hand and the blade leaving through the
	 * front of the hand, angled out past the fingers - so it turns with the wrist through the whole throw.
	 */
	public static void renderHeld(MatrixStack matrices, VertexConsumerProvider consumers, int light, float centre) {
		matrices.push();
		matrices.translate(centre, HELD_GRIP.y, HELD_GRIP.z);
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), HELD_AXIS));
		matrices.translate(0.0F, -GRIP_Y, 0.0F);
		renderKunai(matrices, consumers, light, true, 0.0F);
		matrices.pop();
	}

	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "pulse_blade_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				MESH.invalidate();
			}
		};
	}
}
