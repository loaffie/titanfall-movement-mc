package com.loaffie.titanfall.client.particle;

import com.loaffie.titanfall.client.anim.StandIns;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The steady thruster flame: one long-lived particle per nozzle that stays attached to it and redraws a
 * continuous blue flame there every frame, instead of a stream of separate bursts.
 *
 * <p>The spawn "velocity" carries the player's entity id and the port index (0 or 1, one per pod). Its
 * size follows {@link PilotThrusters#level}: a small idle flame while airborne, a full flame on a wall run
 * and a surge on a double jump, all eased per frame. Once the thrusters stop being fuelled it dies down and
 * removes itself. Each frame it reads the port's position and exhaust direction from where the jumpkit was
 * just drawn, so it never lags behind the pod, whatever the pose or speed. It is not drawn for an invisible
 * pilot, nor for your own pilot in first person (the kit is not drawn there either).
 *
 * <p>The flame is a bright cyan core ribbon along the exhaust axis plus a row of soft glow billboards that
 * taper and cool to deep blue toward the tip, with only a slight shimmer so it reads as a steady burn.
 */
public class ThrusterFlameParticle extends SpriteBillboardParticle {
	/** Flame length, blocks, at full burn. */
	private static final float LENGTH = 0.55F;
	private static final int SEGMENTS = 9;
	/**
	 * Radius of the nozzle bore as drawn (~27 OBJ units at the kit's scale), blocks: the stream fills the
	 * whole opening from the plate outward, and by the flap mouth (~0.1 blocks out) it is as wide as the
	 * flaps spread.
	 */
	private static final float NOZZLE_RADIUS = 0.062F;

	/** Live flames by player id and port, so the effect never stacks a second flame on the same nozzle. */
	private static final Map<Long, ThrusterFlameParticle> LIVE = new HashMap<>();

	private final int playerId;
	private final int port;
	private long lastTickTime;

	protected ThrusterFlameParticle(ClientWorld world, double x, double y, double z, int playerId, int port, SpriteProvider sprites) {
		super(world, x, y, z, 0.0, 0.0, 0.0);
		this.playerId = playerId;
		this.port = port;
		this.setSprite(sprites);
		this.maxAge = Integer.MAX_VALUE;
		this.gravityStrength = 0.0F;
		this.velocityMultiplier = 1.0F;
		this.collidesWithWorld = false;
		this.lastTickTime = world.getTime();
		LIVE.put(key(playerId, port), this);
	}

	private static long key(int playerId, int port) {
		return ((long) playerId << 1) | port;
	}

	/** True while this player's flame on that port exists and is still being ticked in this world. */
	static boolean burning(PlayerEntity player, int port) {
		ThrusterFlameParticle flame = LIVE.get(key(player.getId(), port));
		return flame != null && flame.isAlive() && flame.world == player.getWorld()
				&& player.getWorld().getTime() - flame.lastTickTime <= 2;
	}

	@Override
	public ParticleTextureSheet getType() {
		return PilotParticleSheets.ADDITIVE_GLOW;
	}

	@Override
	public int getBrightness(float tint) {
		return 0xF000F0;
	}

	private PlayerEntity player() {
		Entity entity = this.world.getEntityById(playerId);
		if (entity == null) {
			entity = StandIns.byId(playerId);
		}

		return entity instanceof PlayerEntity player ? player : null;
	}

	@Override
	public void tick() {
		this.age++;
		this.lastTickTime = this.world.getTime();
		PlayerEntity player = player();
		boolean burning = player != null && player.isAlive() && PilotThrusters.lit(player);
		if (!burning) {
			this.markDead();
			if (LIVE.get(key(playerId, port)) == this) {
				LIVE.remove(key(playerId, port));
			}

			return;
		}

		// Keep the particle's own position on the pod (used for sorting only).
		this.prevPosX = this.x;
		this.prevPosY = this.y;
		this.prevPosZ = this.z;
		this.x = player.getX();
		this.y = player.getY() + 0.7;
		this.z = player.getZ();
	}

	@Override
	public void buildGeometry(VertexConsumer consumer, Camera camera, float tickDelta) {
		PlayerEntity player = player();
		if (player == null) {
			return;
		}

		// Burn level: idle air flame ~0.35, wall run 1, plus the double-jump surge on top. Advanced every
		// frame even when hidden, so the level is current when the flame comes back into view.
		float level = PilotThrusters.level(player, port) + 0.8F * PilotThrusters.flare(player, tickDelta);
		if (player.isInvisible() || (camera.getFocusedEntity() == player && !camera.isThirdPerson())) {
			return;
		}

		float visibility = MathHelper.clamp(level / 0.15F, 0.0F, 1.0F);
		if (visibility <= 0.001F) {
			return;
		}

		float strength = MathHelper.clamp(level, 0.0F, 1.8F);

		PilotEffects.Port nozzle = PilotEffects.livePort(player, port, tickDelta);
		Vec3d cam = camera.getPos();
		Vector3f origin = new Vector3f((float) (nozzle.pos().x - cam.x), (float) (nozzle.pos().y - cam.y), (float) (nozzle.pos().z - cam.z));
		Vector3f dir = new Vector3f((float) nozzle.dir().x, (float) nozzle.dir().y, (float) nozzle.dir().z);
		// A slight shimmer only, so the burn reads as steady.
		float time = (this.age + tickDelta) * 0.9F + port * 1.7F;
		float shimmer = 1.0F + 0.05F * MathHelper.sin(time * 2.3F) + 0.03F * MathHelper.sin(time * 5.1F + 0.6F);
		float length = LENGTH * shimmer * (0.35F + 0.65F * strength);
		float spread = 0.75F + 0.25F * Math.min(strength, 1.4F);
		int light = 0xF000F0;
		float u0 = this.getMinU();
		float u1 = this.getMaxU();
		float v0 = this.getMinV();
		float v1 = this.getMaxV();

		// The stream is a wide plume: it leaves the nozzle at the nozzle's own width, swells as the exhaust
		// expands, then thins out toward the tip. Four soft layers, all additive: a deep-blue outer haze, the
		// plume body as a broad tapered ribbon, glowing puffs along the axis, and a hot white-cyan core.
		Vector3f toCamera = new Vector3f(origin).mul(-1.0F);
		Vector3f side = new Vector3f(dir).cross(toCamera);
		boolean edgeOn = side.lengthSquared() < 1.0E-8F;
		if (!edgeOn) {
			side.normalize();
			// Plume body: a ribbon widening from the nozzle to its fullest two-thirds out.
			Vector3f far = new Vector3f(dir).mul(length * 0.85F).add(origin);
			trapezoid(consumer, origin, far, side, NOZZLE_RADIUS * 2.3F * spread, NOZZLE_RADIUS * 3.6F * spread,
					u0, u1, v0, v1, 0.35F, 0.72F, 1.0F, 0.42F * visibility, light);
			// Core: the hot inner jet, nozzle-wide and about half the plume long.
			Vector3f coreEnd = new Vector3f(dir).mul(length * 0.55F).add(origin);
			trapezoid(consumer, origin, coreEnd, side, NOZZLE_RADIUS * 1.5F, NOZZLE_RADIUS * 0.8F,
					u0, u1, v0, v1, 0.88F, 0.98F, 1.0F, 0.85F * visibility, light);
		}

		// Exit discs facing along the exhaust: one filling the bore just off the plate, a wider fainter one
		// at the flap mouth, so looking into the nozzle the whole opening is lit, not just a jet down its
		// middle.
		disc(consumer, new Vector3f(dir).mul(0.015F).add(origin), dir, NOZZLE_RADIUS * 2.0F, u0, u1, v0, v1, 0.75F, 0.95F, 1.0F, 0.75F * visibility, light);
		disc(consumer, new Vector3f(dir).mul(0.1F).add(origin), dir, NOZZLE_RADIUS * 3.0F * spread, u0, u1, v0, v1, 0.25F, 0.6F, 1.0F, 0.35F * visibility, light);

		Quaternionf rotation = camera.getRotation();
		for (int i = 0; i < SEGMENTS; i++) {
			float t = i / (float) (SEGMENTS - 1);
			float width = plumeRadius(t) * spread;
			Vector3f center = new Vector3f(dir).mul(length * t).add(origin);
			// Glow puffs: cyan-white at the nozzle, cooling to deep blue at the tip.
			// The glow sprite fades out well inside its quad, so a puff is drawn about twice the plume's radius
			// to read at full width.
			Vector3f right = new Vector3f(width * 1.9F, 0.0F, 0.0F).rotate(rotation);
			Vector3f up = new Vector3f(0.0F, width * 1.9F, 0.0F).rotate(rotation);
			float a = MathHelper.lerp(t, 0.55F, 0.12F) * visibility;
			quad(consumer, center, right, up, u0, u1, v0, v1, MathHelper.lerp(t, 0.5F, 0.06F), MathHelper.lerp(t, 0.88F, 0.28F), 1.0F, a, light);
			// Outer haze: wider and faint, so the stream has a soft edge rather than a hard line.
			right.mul(1.6F);
			up.mul(1.6F);
			quad(consumer, center, right, up, u0, u1, v0, v1, 0.12F, 0.4F, 1.0F, 0.14F * (1.0F - t) * visibility, light);
		}
	}

	/** Half-width of the plume along its length (t = 0 at the nozzle, 1 at the tip), blocks. */
	private static float plumeRadius(float t) {
		float swell = NOZZLE_RADIUS * (1.05F + 2.2F * Math.min(t, 0.55F));
		float fade = t <= 0.55F ? 1.0F : 1.0F - 0.6F * smooth((t - 0.55F) / 0.45F);
		return swell * fade;
	}

	/** A glow disc facing along {@code normal}, both sides. */
	private static void disc(VertexConsumer consumer, Vector3f center, Vector3f normal, float radius, float u0, float u1, float v0, float v1,
			float r, float g, float b, float a, int light) {
		Vector3f e1 = new Vector3f(normal).cross(Math.abs(normal.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F)).normalize().mul(radius);
		Vector3f e2 = new Vector3f(normal).cross(e1).normalize().mul(radius);
		quad(consumer, center, e1, e2, u0, u1, v0, v1, r, g, b, a, light);
	}

	private static float smooth(float x) {
		float t = MathHelper.clamp(x, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** A ribbon from {@code start} to {@code end}, {@code w0} half-wide at the start and {@code w1} at the end, both sides. */
	private static void trapezoid(VertexConsumer consumer, Vector3f start, Vector3f end, Vector3f side, float w0, float w1,
			float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
		// Along the ribbon the texture runs from its centre row (brightest, at the nozzle) to its edge (dark, at
		// the far end), so the stream is full-strength right at the opening and fades out toward the tip.
		float vMid = (v0 + v1) * 0.5F;
		float[][] corners = {
				{start.x + side.x * w0, start.y + side.y * w0, start.z + side.z * w0, u1, vMid},
				{end.x + side.x * w1, end.y + side.y * w1, end.z + side.z * w1, u1, v0},
				{end.x - side.x * w1, end.y - side.y * w1, end.z - side.z * w1, u0, v0},
				{start.x - side.x * w0, start.y - side.y * w0, start.z - side.z * w0, u0, vMid}};
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				float[] c = corners[pass == 0 ? k : 3 - k];
				consumer.vertex(c[0], c[1], c[2]).texture(c[3], c[4]).color(r, g, b, a).light(light);
			}
		}
	}

	private static void quad(VertexConsumer consumer, Vector3f c, Vector3f right, Vector3f up, float u0, float u1, float v0, float v1,
			float r, float g, float b, float a, int light) {
		corner(consumer, c, right, up, 1, -1, u1, v1, r, g, b, a, light);
		corner(consumer, c, right, up, 1, 1, u1, v0, r, g, b, a, light);
		corner(consumer, c, right, up, -1, 1, u0, v0, r, g, b, a, light);
		corner(consumer, c, right, up, -1, -1, u0, v1, r, g, b, a, light);
		// Both faces, so the ribbon is visible from any side.
		corner(consumer, c, right, up, -1, -1, u0, v1, r, g, b, a, light);
		corner(consumer, c, right, up, -1, 1, u0, v0, r, g, b, a, light);
		corner(consumer, c, right, up, 1, 1, u1, v0, r, g, b, a, light);
		corner(consumer, c, right, up, 1, -1, u1, v1, r, g, b, a, light);
	}

	private static void corner(VertexConsumer consumer, Vector3f c, Vector3f right, Vector3f up, int sx, int sy, float u, float v,
			float r, float g, float b, float a, int light) {
		consumer.vertex(c.x + right.x * sx + up.x * sy, c.y + right.y * sx + up.y * sy, c.z + right.z * sx + up.z * sy)
				.texture(u, v)
				.color(r, g, b, a)
				.light(light);
	}

	/** Spawn velocity: x = player entity id, y = port index. */
	public record Factory(SpriteProvider sprites) implements ParticleFactory<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
			return new ThrusterFlameParticle(world, x, y, z, (int) Math.round(vx), (int) Math.round(vy), sprites);
		}
	}
}
