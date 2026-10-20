package com.loaffie.titanfall.mixin.client;

import com.loaffie.titanfall.client.cloak.CloakShimmer;
import com.loaffie.titanfall.client.cloak.CloakStates;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cloaked pilot is drawn as the cloak's shimmer: the body in a translucent layer at a few percent (vanilla
 * would draw an invisible player not at all), the crawling sheen over it, and nothing else - no armour, held
 * items or gear, which vanilla still draws on invisible players.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCloakMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {
	@Shadow
	protected M model;

	/** The entity being drawn and the frame's tick delta, for the injections below that don't see them. */
	private static LivingEntity titanfall$drawing;
	private static float titanfall$tickDelta;

	protected LivingEntityRendererCloakMixin(EntityRendererFactory.Context context) {
		super(context);
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void titanfall$noteDrawing(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		titanfall$drawing = entity;
		titanfall$tickDelta = tickDelta;
	}

	@Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
	private void titanfall$cloakLayer(T entity, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
		if (CloakStates.amount(entity, titanfall$tickDelta) > 0.0F) {
			cir.setReturnValue(CloakShimmer.bodyLayer(this.getTexture(entity)));
		}
	}

	@ModifyArg(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
			index = 4)
	private int titanfall$cloakColor(int color) {
		LivingEntity entity = titanfall$drawing;
		float amount = entity == null ? 0.0F : CloakStates.amount(entity, titanfall$tickDelta);
		return amount > 0.0F ? CloakShimmer.bodyColor(CloakShimmer.visibility(entity, titanfall$tickDelta), amount) : color;
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
					shift = At.Shift.AFTER))
	private void titanfall$cloakSheen(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		float amount = CloakStates.amount(entity, tickDelta);
		if (amount > 0.0F) {
			CloakShimmer.renderSheen(this.model, matrices, consumers, light, amount, CloakShimmer.visibility(entity, tickDelta), entity.age + tickDelta);
		}
	}

	/** Vanilla skips the feature layers for spectators; a cloaked pilot's are skipped too. */
	@Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isSpectator()Z"))
	private boolean titanfall$cloakHidesGear(LivingEntity entity) {
		return entity.isSpectator() || CloakStates.amount(entity, titanfall$tickDelta) > 0.0F;
	}
}
