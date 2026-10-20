package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.pulse.PulseBlade;
import com.loaffie.titanfall.pulse.PulseBladeEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a thrown Pulse Blade: tip first along its flight, spinning about its axis as it goes ("spirals in mid
 * air"), then buried tip-first where it stuck, its band and edges flaring blue with each pulse.
 */
public class PulseBladeRenderer extends EntityRenderer<PulseBladeEntity> {
	/** Spin in flight, degrees a tick. */
	private static final float SPIN = 55.0F;

	public PulseBladeRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(PulseBladeEntity blade, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		Vec3d direction;
		if (blade.isStuck()) {
			direction = blade.stuckDirection();
		} else {
			float bladeYaw = MathHelper.lerpAngleDegrees(tickDelta, blade.prevYaw, blade.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
			float pitch = MathHelper.lerp(tickDelta, blade.prevPitch, blade.getPitch()) * MathHelper.RADIANS_PER_DEGREE;
			direction = new Vec3d(MathHelper.sin(bladeYaw) * MathHelper.cos(pitch), MathHelper.sin(pitch), MathHelper.cos(bladeYaw) * MathHelper.cos(pitch));
		}

		matrices.push();
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction.toVector3f().normalize()));
		if (!blade.isStuck()) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((blade.age + tickDelta) * SPIN));
		}

		matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
		matrices.translate(0.0F, -PulseModel.TIP_Y, 0.0F);
		PulseModel.renderKunai(matrices, consumers, light, true, glow(blade, tickDelta));
		matrices.pop();
		super.render(blade, yaw, tickDelta, matrices, consumers, light);
	}

	/** The flare of each pulse: full as it goes out, fading over a fifth of a second. */
	private static float glow(PulseBladeEntity blade, float tickDelta) {
		int stuck = blade.stuckTicks();
		if (stuck < 0) {
			return 0.0F;
		}

		int interval = PulseBlade.pulseInterval(TitanfallConfig.get());
		float since = (stuck - 1 + interval) % interval + tickDelta;
		return (float) Math.exp(-since / 4.0);
	}

	@Override
	public Identifier getTexture(PulseBladeEntity blade) {
		return PulseModel.TEXTURE;
	}
}
