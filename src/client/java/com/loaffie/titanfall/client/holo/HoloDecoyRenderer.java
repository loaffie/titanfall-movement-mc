package com.loaffie.titanfall.client.holo;

import com.loaffie.titanfall.holo.HoloDecoyEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Draws a Holo Pilot decoy as its pilot: its stand-in ({@link HoloDecoys}) through the player renderer, with
 * the hologram's look ({@link HoloLook}) - and a sideways stutter of a few hundredths of a block while it
 * pulses or breaks up.
 */
public class HoloDecoyRenderer extends EntityRenderer<HoloDecoyEntity> {
	private static final float JITTER = 0.05F;

	public HoloDecoyRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
	}

	@Override
	public void render(HoloDecoyEntity decoy, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		HoloDecoys.State state = HoloDecoys.of(decoy);
		OtherClientPlayerEntity standIn = state == null ? null : state.standIn();
		if (standIn == null) {
			return;
		}

		Vec3d eye = decoy.getLerpedPos(tickDelta).add(0.0, decoy.getStandingEyeHeight(), 0.0);
		HoloLook.Look look = HoloLook.of(state, tickDelta, eye.distanceTo(this.dispatcher.camera.getPos()));
		this.shadowOpacity = look.alpha();
		if (look.alpha() <= 0.01F) {
			return;
		}

		float stutter = Math.max(look.tint() - 0.3F, 0.0F);
		float shift = stutter <= 0.0F ? 0.0F : JITTER * stutter * Math.signum(MathHelper.sin((decoy.age + tickDelta) * 7.3F));
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, standIn.prevYaw, standIn.getYaw());
		matrices.push();
		matrices.translate(shift, 0.0F, 0.0F);
		HoloLook.begin(standIn, look);
		try {
			this.dispatcher.getRenderer(standIn).render(standIn, bodyYaw, tickDelta, matrices, consumers, light);
		} finally {
			HoloLook.end();
			matrices.pop();
		}
	}

	@Override
	public Identifier getTexture(HoloDecoyEntity decoy) {
		return HoloModel.TEXTURE;
	}
}
