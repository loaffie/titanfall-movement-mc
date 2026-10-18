package com.loaffie.titanfall.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;

/**
 * An additive particle sheet: colours add onto what is behind them, the way light does, so overlapping
 * jets and sparks bloom into a white-hot core instead of stacking like paper. Depth writes are off so
 * the glow never cuts holes in other particles. {@code ParticleManagerMixin} appends this sheet to the
 * engine's render order (drawn last) and restores the default blend function afterwards.
 */
public final class PilotParticleSheets {
	public static final ParticleTextureSheet ADDITIVE_GLOW = new ParticleTextureSheet() {
		@Override
		public BufferBuilder begin(Tessellator tessellator, TextureManager textureManager) {
			RenderSystem.depthMask(false);
			RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			return tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
		}

		@Override
		public String toString() {
			return "TITANFALL_ADDITIVE_GLOW";
		}
	};

	private PilotParticleSheets() {
	}
}
