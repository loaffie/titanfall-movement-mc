package com.loaffie.titanfall.client.phase;

import com.loaffie.titanfall.client.grapple.GrappleModel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The ghost a pilot leaves as they shift out: a translucent, blue, crackling copy of them frozen in their
 * last pose, which carries on a little way along the way they were heading and thins out to nothing in
 * half a second - the moment the pilot slips out of the world, for everyone watching.
 */
public final class PhaseGhost {
	private static final Identifier ENERGY = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
	private static final int LIFE_TICKS = 10;
	/** The ghost keeps this much of its speed each tick. */
	private static final double DRAG = 0.78;

	private static final class Ghost {
		final int playerId;
		final float bodyYaw;
		final float headYaw;
		final float pitch;
		final float limbAngle;
		final float limbDistance;
		final boolean sneaking;
		Vec3d pos;
		Vec3d prevPos;
		Vec3d velocity;
		int age;

		Ghost(PlayerEntity player) {
			this.playerId = player.getId();
			this.bodyYaw = player.bodyYaw;
			this.headYaw = player.headYaw - player.bodyYaw;
			this.pitch = player.getPitch();
			this.limbAngle = player.limbAnimator.getPos();
			this.limbDistance = player.limbAnimator.getSpeed();
			this.sneaking = player.isInSneakingPose();
			this.pos = player.getPos();
			this.prevPos = this.pos;
			this.velocity = player.getVelocity().multiply(1.0, 0.3, 1.0);
		}
	}

	private static final List<Ghost> GHOSTS = new ArrayList<>();

	private PhaseGhost() {
	}

	static void spawn(PlayerEntity player) {
		GHOSTS.add(new Ghost(player));
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			GHOSTS.clear();
			return;
		}

		Iterator<Ghost> iterator = GHOSTS.iterator();
		while (iterator.hasNext()) {
			Ghost ghost = iterator.next();
			ghost.prevPos = ghost.pos;
			ghost.pos = ghost.pos.add(ghost.velocity);
			ghost.velocity = ghost.velocity.multiply(DRAG);
			if (++ghost.age >= LIFE_TICKS) {
				iterator.remove();
			}
		}
	}

	public static void render(WorldRenderContext context) {
		VertexConsumerProvider consumers = context.consumers();
		if (GHOSTS.isEmpty() || context.world() == null || consumers == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d cam = context.camera().getPos();
		float tickDelta = context.tickCounter().getTickDelta(false);
		MatrixStack matrices = new MatrixStack();
		for (Ghost ghost : GHOSTS) {
			if (!(context.world().getEntityById(ghost.playerId) instanceof AbstractClientPlayerEntity player)) {
				continue;
			}

			float t = MathHelper.clamp((ghost.age + tickDelta) / LIFE_TICKS, 0.0F, 1.0F);
			float fade = (1.0F - t) * (1.0F - t);
			PlayerEntityRenderer renderer = (PlayerEntityRenderer) client.getEntityRenderDispatcher().getRenderer(player);
			PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
			pose(model, player, ghost);

			Vec3d at = ghost.prevPos.lerp(ghost.pos, tickDelta);
			matrices.push();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - ghost.bodyYaw));
			matrices.scale(-1.0F, -1.0F, 1.0F);
			// Thinning as it goes: narrower and a little taller, like it is being drawn out of the world.
			float thin = 1.0F - 0.35F * t;
			matrices.scale(0.9375F * thin, 0.9375F * (1.0F + 0.1F * t), 0.9375F * thin);
			matrices.translate(0.0F, -1.501F, 0.0F);
			int alpha = Math.round(130 * fade);
			model.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(player.getSkinTextures().texture())),
					0xF000F0, OverlayTexture.DEFAULT_UV, (alpha << 24) | 0x8CC8FF);
			float time = player.age + tickDelta;
			model.render(matrices, consumers.getBuffer(RenderLayer.getEnergySwirl(ENERGY, time * 0.02F, time * 0.015F)),
					0xF000F0, OverlayTexture.DEFAULT_UV, GrappleModel.dim(0xFF5FA8FF, fade));
			matrices.pop();
		}
	}

	/** Poses the shared player model as the pilot was when they shifted. */
	private static void pose(PlayerEntityModel<AbstractClientPlayerEntity> model, AbstractClientPlayerEntity player, Ghost ghost) {
		model.setVisible(true);
		model.hat.visible = player.isPartVisible(PlayerModelPart.HAT);
		model.jacket.visible = player.isPartVisible(PlayerModelPart.JACKET);
		model.leftPants.visible = player.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
		model.rightPants.visible = player.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
		model.leftSleeve.visible = player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
		model.rightSleeve.visible = player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
		model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
		model.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
		model.sneaking = ghost.sneaking;
		model.riding = false;
		model.child = false;
		model.handSwingProgress = 0.0F;
		model.setAngles(player, ghost.limbAngle, ghost.limbDistance, player.age, ghost.headYaw, ghost.pitch);
	}
}
