package com.loaffie.titanfall.client.render;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Fallback geometry of the Pilot Jumpkit - used only if the OBJ model ({@link JumpkitMesh}) cannot be
 * loaded - plus the OBJ's placement constants. Coordinates are in the same space as the player model's {@code body} part
 * (pixels, origin at the neck, +Y down, +X toward the player's left, -Z toward the front). The torso
 * cube spans x -4..4, y 0..12, z -2..2, so y 9..12 is the waist and hips.
 *
 * <pre>
 *  front view            side view
 *     _______                 __
 *  []|___o___|[]             |  |  back unit (lower back)
 *  ||         ||          [ belt ]
 *  \/         \/            \/   side thruster + nozzle
 * </pre>
 *
 * <p>Two roots: {@code kit} is the solid hardware drawn with the normal cutout layer, {@code glow} is a
 * pair of thin exhaust plates under the nozzles drawn with the emissive "eyes" layer so the thrusters
 * read as live even in the dark.
 */
public final class JumpkitModel {
	/**
	 * Placement of the OBJ jumpkit ({@link JumpkitMesh}) on the body part, in model pixels. As in Titanfall 2
	 * the kit is secured at the waistline: the mesh origin (top-centre of the pack's mounting face) sits on
	 * the lower back, so the pack rides on the belt and the thruster pods hang at the hips. The torso spans
	 * y 0..12 (neck to belt) and its back surface is at z = 2. {@code OBJ_SCALE} converts OBJ units to
	 * model pixels: the 389-unit-wide kit is about 15 px across.
	 */
	public static final float OBJ_SCALE = 0.039F;
	public static final float OBJ_OFFSET_X = 0.0F;
	public static final float OBJ_OFFSET_Y = 6.0F;
	public static final float OBJ_OFFSET_Z = 1.6F;

	/**
	 * Thruster nozzle centre in OBJ units (body axes: X side, Y down, Z back, before scale and offset), and
	 * the exhaust direction in the same axes - measured from the baked mesh's exhaust plates. Particles are
	 * emitted from here, so they leave the actual ports on the model.
	 */
	public static final float NOZZLE_X = 136.2F;
	public static final float NOZZLE_Y = 124.7F;
	public static final float NOZZLE_Z = 92.4F;
	public static final float NOZZLE_DIR_X = 0.16F;
	public static final float NOZZLE_DIR_Y = 0.79F;
	public static final float NOZZLE_DIR_Z = 0.58F;

	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(TitanfallMovement.MOD_ID, "pilot_jumpkit"), "main");

	/** Outward tilt of each thruster pod, radians: the nozzles splay slightly away from the legs. */
	private static final float THRUSTER_SPLAY = 0.2F;

	private final ModelPart kit;
	private final ModelPart glow;

	public JumpkitModel(ModelPart root) {
		this.kit = root.getChild("kit");
		this.glow = root.getChild("glow");
	}

	public ModelPart kit() {
		return kit;
	}

	public ModelPart glow() {
		return glow;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		ModelPartData kit = root.addChild("kit", ModelPartBuilder.create(), ModelTransform.NONE);
		// Waist belt, inflated just past the leggings layer (0.5) so the two never z-fight.
		kit.addChild("belt", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, 9.5F, -2.0F, 8, 2, 4, new Dilation(0.6F)), ModelTransform.NONE);
		// Front buckle / power indicator.
		kit.addChild("buckle", ModelPartBuilder.create().uv(0, 6).cuboid(-1.5F, 9.5F, -3.4F, 3, 2, 1), ModelTransform.NONE);
		// Jumpkit core on the lower back, feeding both pods.
		kit.addChild("core", ModelPartBuilder.create().uv(24, 0).cuboid(-3.0F, 6.5F, 2.4F, 6, 5, 2), ModelTransform.NONE);
		// Hip thruster pods. Each pivots at the side of the belt so it can splay outward.
		kit.addChild("left_thruster", ModelPartBuilder.create()
						.uv(0, 10).cuboid(0.0F, -3.0F, -1.5F, 2, 5, 3)
						.uv(10, 10).cuboid(0.0F, 2.0F, -1.0F, 2, 2, 2, new Dilation(-0.25F)),
				ModelTransform.of(4.6F, 10.5F, 0.5F, 0.0F, 0.0F, -THRUSTER_SPLAY));
		kit.addChild("right_thruster", ModelPartBuilder.create()
						.uv(0, 18).mirrored().cuboid(-2.0F, -3.0F, -1.5F, 2, 5, 3)
						.uv(10, 18).mirrored().cuboid(-2.0F, 2.0F, -1.0F, 2, 2, 2, new Dilation(-0.25F)),
				ModelTransform.of(-4.6F, 10.5F, 0.5F, 0.0F, 0.0F, THRUSTER_SPLAY));

		ModelPartData glow = root.addChild("glow", ModelPartBuilder.create(), ModelTransform.NONE);
		glow.addChild("left_exhaust", ModelPartBuilder.create().uv(20, 10).cuboid(0.0F, 3.85F, -1.0F, 2, 1, 2, new Dilation(-0.45F)),
				ModelTransform.of(4.6F, 10.5F, 0.5F, 0.0F, 0.0F, -THRUSTER_SPLAY));
		glow.addChild("right_exhaust", ModelPartBuilder.create().uv(20, 10).mirrored().cuboid(-2.0F, 3.85F, -1.0F, 2, 1, 2, new Dilation(-0.45F)),
				ModelTransform.of(-4.6F, 10.5F, 0.5F, 0.0F, 0.0F, THRUSTER_SPLAY));

		return TexturedModelData.of(data, 64, 32);
	}
}
