package com.loaffie.titanfall.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * All pilot particles: full-bright, additive, with colour, alpha and size animated over their life by
 * smooth curves rather than vanilla's fixed sprites. Three ways of facing the camera:
 *
 * <ul>
 *   <li>{@link Shape#BILLBOARD}: a camera-facing quad (afterglow wisps, ignition flares).</li>
 *   <li>{@link Shape#STREAK}: a quad stretched along the particle's screen-space velocity, so fast jets and
 *   sparks read as motion-blurred streaks of light.</li>
 *   <li>{@link Shape#RING}: a flat quad facing a fixed normal (the spawn "velocity" of a shockwave), so the
 *   ring lies on the ground under a slide hop or on the wall after a wall kick.</li>
 *   <li>{@link Shape#EXHAUST_RING}: a ring that faces <em>and travels along</em> its spawn velocity, so a
 *   row of them shoots straight down a thruster's exhaust axis like shock diamonds.</li>
 * </ul>
 */
public class PilotParticle extends SpriteBillboardParticle {
	public enum Shape { BILLBOARD, STREAK, RING, EXHAUST_RING }

	/** Look of one particle type: colour ramp start -> mid -> end, sizes, life, physics. */
	public record Style(Shape shape, int lifeMin, int lifeMax, float sizeStart, float sizeEnd, float stretch,
			float[] colorStart, float[] colorMid, float[] colorEnd, float alpha, float drag, float gravity, boolean collide) {
	}

	public static final Style JET = new Style(Shape.STREAK, 7, 11, 0.10F, 0.34F, 2.6F,
			rgb(0xF2FFFF), rgb(0x3FD8FF), rgb(0x0B3DFF), 0.95F, 0.80F, 0.0F, false);
	public static final Style SPARK = new Style(Shape.STREAK, 12, 22, 0.035F, 0.015F, 4.0F,
			rgb(0xFFFFFF), rgb(0xFFB347), rgb(0xFF3A12), 1.0F, 0.94F, 0.9F, true);
	public static final Style SHOCKWAVE = new Style(Shape.RING, 9, 11, 0.20F, 1.70F, 0.0F,
			rgb(0xE8FFFF), rgb(0x48D9FF), rgb(0x1340FF), 0.85F, 1.0F, 0.0F, false);
	public static final Style AFTERGLOW = new Style(Shape.BILLBOARD, 14, 22, 0.18F, 0.55F, 0.0F,
			rgb(0x9CF4FF), rgb(0x2A9BFF), rgb(0x0A1A66), 0.45F, 0.90F, -0.02F, false);
	/** Double-jump exhaust ring: starts nozzle-sized and widens as it rides down the jet. */
	public static final Style EXHAUST_RING = new Style(Shape.EXHAUST_RING, 9, 9, 0.07F, 0.62F, 0.0F,
			rgb(0xF4FFFF), rgb(0x52DCFF), rgb(0x123CFF), 0.9F, 0.82F, 0.0F, false);
	public static final Style IGNITION = new Style(Shape.BILLBOARD, 4, 5, 0.45F, 0.15F, 0.0F,
			rgb(0xFFFFFF), rgb(0xA8F4FF), rgb(0x2C8CFF), 1.0F, 1.0F, 0.0F, false);

	/** Grapple muzzle flash and hook bite: hot white, cooling to a pale cyan, gone in a few frames. */
	public static final Style GRAPPLE_FLASH = new Style(Shape.BILLBOARD, 3, 4, 0.34F, 0.08F, 0.0F,
			rgb(0xFFFFFF), rgb(0xFFF1D6), rgb(0x7FD8FF), 1.0F, 1.0F, 0.0F, false);
	/** Ring punched out round the hook as it bites, lying on the surface. */
	public static final Style GRAPPLE_RING = new Style(Shape.RING, 6, 7, 0.06F, 0.62F, 0.0F,
			rgb(0xFFFFFF), rgb(0xBFEFFF), rgb(0x2C7CFF), 0.8F, 1.0F, 0.0F, false);
	/** Thin tracer the hook leaves in flight. */
	public static final Style GRAPPLE_TRAIL = new Style(Shape.STREAK, 4, 6, 0.022F, 0.012F, 1.6F,
			rgb(0xE8FBFF), rgb(0x6FD3FF), rgb(0x1A4CFF), 0.55F, 0.6F, 0.0F, false);

	private final Style style;
	private final Vector3f ringNormal;
	private final float spin;

	protected PilotParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, Style style, SpriteProvider sprites) {
		super(world, x, y, z, 0.0, 0.0, 0.0);
		this.style = style;
		this.setSprite(sprites);
		this.maxAge = style.lifeMin() + this.random.nextInt(Math.max(1, style.lifeMax() - style.lifeMin() + 1));
		this.velocityMultiplier = style.drag();
		this.gravityStrength = style.gravity();
		this.collidesWithWorld = style.collide();
		boolean ring = style.shape() == Shape.RING || style.shape() == Shape.EXHAUST_RING;
		// Rings are drawn exactly as aimed: no random spin.
		this.spin = ring ? 0.0F : (this.random.nextFloat() - 0.5F) * 0.3F;
		this.angle = ring ? 0.0F : this.random.nextFloat() * (float) (Math.PI * 2.0);
		this.prevAngle = this.angle;
		if (ring) {
			Vector3f normal = new Vector3f((float) vx, (float) vy, (float) vz);
			this.ringNormal = normal.lengthSquared() > 1.0E-6F ? normal.normalize() : new Vector3f(0.0F, 1.0F, 0.0F);
			if (style.shape() == Shape.EXHAUST_RING) {
				this.velocityX = vx;
				this.velocityY = vy;
				this.velocityZ = vz;
			}
		} else {
			this.ringNormal = null;
			this.velocityX = vx;
			this.velocityY = vy;
			this.velocityZ = vz;
		}

		applyRamp(0.0F);
	}

	@Override
	public ParticleTextureSheet getType() {
		return PilotParticleSheets.ADDITIVE_GLOW;
	}

	/** Light-emitting: never darkened by block or sky light. */
	@Override
	public int getBrightness(float tint) {
		return 0xF000F0;
	}

	@Override
	public void tick() {
		super.tick();
		this.prevAngle = this.angle;
		this.angle += this.spin;
		applyRamp(this.age / (float) this.maxAge);
	}

	/** Colour, alpha and size along the particle's life, eased so they start and finish softly. */
	private void applyRamp(float life) {
		float t = MathHelper.clamp(life, 0.0F, 1.0F);
		float[] from = t < 0.35F ? style.colorStart() : style.colorMid();
		float[] to = t < 0.35F ? style.colorMid() : style.colorEnd();
		float k = t < 0.35F ? t / 0.35F : (t - 0.35F) / 0.65F;
		k = k * k * (3.0F - 2.0F * k);
		this.setColor(MathHelper.lerp(k, from[0], to[0]), MathHelper.lerp(k, from[1], to[1]), MathHelper.lerp(k, from[2], to[2]));
		float fade = 1.0F - t * t;
		this.setAlpha(style.alpha() * fade);
		float grow = 1.0F - (1.0F - t) * (1.0F - t);
		this.scale = MathHelper.lerp(grow, style.sizeStart(), style.sizeEnd());
	}

	@Override
	public void buildGeometry(VertexConsumer consumer, Camera camera, float tickDelta) {
		Vec3d cam = camera.getPos();
		float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
		float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
		float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);
		float size = this.getSize(tickDelta);
		Vector3f right;
		Vector3f up;

		switch (style.shape()) {
			case RING, EXHAUST_RING -> {
				right = new Vector3f(ringNormal).cross(Math.abs(ringNormal.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize().mul(size);
				up = new Vector3f(ringNormal).cross(right).normalize().mul(size);
			}
			case STREAK -> {
				Vector3f velocity = new Vector3f((float) this.velocityX, (float) this.velocityY, (float) this.velocityZ);
				Vector3f toCamera = new Vector3f(-px, -py, -pz);
				Vector3f side = new Vector3f(velocity).cross(toCamera);
				if (velocity.lengthSquared() < 1.0E-6F || side.lengthSquared() < 1.0E-8F) {
					Quaternionf rotation = camera.getRotation();
					right = new Vector3f(size, 0, 0).rotate(rotation);
					up = new Vector3f(0, size, 0).rotate(rotation);
				} else {
					// Long axis along the motion, scaled by speed; short axis across it, facing the camera.
					float length = size + velocity.length() * style.stretch();
					up = new Vector3f(velocity).normalize().mul(length);
					right = side.normalize().mul(size);
				}
			}
			default -> {
				Quaternionf rotation = new Quaternionf(camera.getRotation()).rotateZ(MathHelper.lerp(tickDelta, this.prevAngle, this.angle));
				right = new Vector3f(size, 0, 0).rotate(rotation);
				up = new Vector3f(0, size, 0).rotate(rotation);
			}
		}

		float u0 = this.getMinU();
		float u1 = this.getMaxU();
		float v0 = this.getMinV();
		float v1 = this.getMaxV();
		int light = this.getBrightness(tickDelta);
		corner(consumer, px, py, pz, right, up, 1, -1, u1, v1, light);
		corner(consumer, px, py, pz, right, up, 1, 1, u1, v0, light);
		corner(consumer, px, py, pz, right, up, -1, 1, u0, v0, light);
		corner(consumer, px, py, pz, right, up, -1, -1, u0, v1, light);
		if (this.ringNormal != null) {
			// Rings are seen from both sides.
			corner(consumer, px, py, pz, right, up, -1, -1, u0, v1, light);
			corner(consumer, px, py, pz, right, up, -1, 1, u0, v0, light);
			corner(consumer, px, py, pz, right, up, 1, 1, u1, v0, light);
			corner(consumer, px, py, pz, right, up, 1, -1, u1, v1, light);
		}
	}

	private void corner(VertexConsumer consumer, float px, float py, float pz, Vector3f right, Vector3f up, int sx, int sy, float u, float v, int light) {
		consumer.vertex(px + right.x * sx + up.x * sy, py + right.y * sx + up.y * sy, pz + right.z * sx + up.z * sy)
				.texture(u, v)
				.color(this.red, this.green, this.blue, this.alpha)
				.light(light);
	}

	private static float[] rgb(int color) {
		return new float[]{((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
	}

	/** Factory binding one style to a particle type's sprites. */
	public record Factory(SpriteProvider sprites, Style style) implements ParticleFactory<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
			return new PilotParticle(world, x, y, z, vx, vy, vz, style, sprites);
		}
	}
}
