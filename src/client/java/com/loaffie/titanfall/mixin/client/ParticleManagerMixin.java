package com.loaffie.titanfall.mixin.client;

import com.google.common.collect.ImmutableList;
import com.loaffie.titanfall.client.particle.PilotParticleSheets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The particle engine only draws sheets in its fixed render order, so the additive glow sheet is appended
 * to it (last, after all vanilla particles). After rendering, the default blend function is restored
 * because the engine itself only disables blending, and later translucent passes assume the default.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Shadow
	@Final
	@Mutable
	private static List<ParticleTextureSheet> PARTICLE_TEXTURE_SHEETS;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void titanfall$addGlowSheet(CallbackInfo ci) {
		PARTICLE_TEXTURE_SHEETS = ImmutableList.<ParticleTextureSheet>builder()
				.addAll(PARTICLE_TEXTURE_SHEETS)
				.add(PilotParticleSheets.ADDITIVE_GLOW)
				.build();
	}

	@Inject(method = "renderParticles(Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;F)V", at = @At("TAIL"))
	private void titanfall$restoreBlend(LightmapTextureManager lightmap, Camera camera, float tickDelta, CallbackInfo ci) {
		RenderSystem.defaultBlendFunc();
	}
}
