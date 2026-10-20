package com.loaffie.titanfall.client.pulse;

import com.loaffie.titanfall.config.TitanfallConfig;
import com.loaffie.titanfall.particle.TitanfallParticles;
import com.loaffie.titanfall.pulse.PulseBlade;
import com.loaffie.titanfall.pulse.PulseBladeEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Pulse Blade's sonar, after Titanfall 2's ({@code ServerCallback_SonarPulseFromPosition},
 * {@code SonarStart}): each pulse sends a wave out from the kunai to the edge of its radius, and everything
 * the wave finds is revealed - outlined in red through walls - to the pilot who threw it and their team,
 * until a little after the next pulse would refresh it. Anyone it reveals hears an alarm and sees the edges
 * of their screen flash red. The particles: a trail behind the kunai in flight, sparks as it bites, and a
 * ring and sparks off it with every pulse.
 */
public final class PulseSonar implements PulseBladeEntity.Listener {
	/** The wave reaches the edge of the radius in this long, seconds, then is gone. */
	private static final float WAVE_SECONDS = 1.1F;
	/** A reveal lasts this much past the next pulse, ticks, so it doesn't flicker between pulses. */
	private static final int REVEAL_SLACK_TICKS = 8;
	private static final int WAVE_COLOR = 0x4FA8FF;
	private static final int LONGITUDES = 32;
	private static final int LATITUDES = 16;
	/** The shell fades out within this distance of the eye, blocks. */
	private static final double NEAR_FADE_START = 2.0;
	private static final double NEAR_FADE_LENGTH = 6.0;

	private record Wave(Vec3d center, double radius, long startNanos) {
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	/** Revealed entity ids and the world tick their reveal ends. */
	private static final Map<Integer, Long> REVEALED = new HashMap<>();

	public static final PulseSonar INSTANCE = new PulseSonar();

	private PulseSonar() {
	}

	private static boolean particles() {
		return TitanfallConfig.get().thrusterParticles;
	}

	/** Whether the entity is shown through walls to the local pilot right now. */
	public static boolean revealed(Entity entity) {
		if (REVEALED.isEmpty()) {
			return false;
		}

		Long until = REVEALED.get(entity.getId());
		return until != null && entity.getWorld().getTime() <= until;
	}

	@Override
	public void flying(PulseBladeEntity blade) {
		if (!particles() || blade.age < 2) {
			return;
		}

		Vec3d velocity = blade.getVelocity();
		Vec3d from = blade.getPos().subtract(velocity);
		World world = blade.getWorld();
		for (int i = 0; i < 3; i++) {
			Vec3d at = from.add(velocity.multiply(i / 3.0));
			world.addParticle(TitanfallParticles.PULSE_TRAIL, at.x, at.y, at.z, velocity.x * 0.05, velocity.y * 0.05, velocity.z * 0.05);
		}
	}

	@Override
	public void landed(PulseBladeEntity blade) {
		PulseSounds.impact(blade);
		if (!particles()) {
			return;
		}

		Random random = blade.getWorld().getRandom();
		Vec3d back = blade.stuckNormal();
		for (int i = 0; i < 10; i++) {
			Vec3d v = back.multiply(0.15 + random.nextDouble() * 0.15).add((random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.15,
					(random.nextDouble() - 0.5) * 0.25);
			blade.getWorld().addParticle(TitanfallParticles.PULSE_SPARK, blade.getX(), blade.getY(), blade.getZ(), v.x, v.y, v.z);
		}
	}

	@Override
	public void pulse(PulseBladeEntity blade, int index) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity local = client.player;
		TitanfallConfig config = TitanfallConfig.get();
		double radius = PulseBlade.radius(config);
		Vec3d normal = blade.stuckNormal();
		Vec3d center = blade.getPos().add(normal.multiply(0.3));
		WAVES.add(new Wave(center, radius, System.nanoTime()));
		PulseSounds.ping(blade);
		if (particles()) {
			Vec3d flat = blade.getPos().add(normal.multiply(0.03));
			blade.getWorld().addParticle(TitanfallParticles.PULSE_RING, flat.x, flat.y, flat.z, normal.x, normal.y, normal.z);
			Random random = blade.getWorld().getRandom();
			for (int i = 0; i < 5; i++) {
				blade.getWorld().addParticle(TitanfallParticles.PULSE_SPARK, center.x, center.y, center.z,
						(random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
			}
		}

		if (local == null) {
			return;
		}

		Entity owner = blade.getOwner();
		boolean friendly = owner == local || (owner != null && owner.isTeammate(local));
		double radiusSquared = radius * radius;
		if (friendly) {
			long until = blade.getWorld().getTime() + PulseBlade.pulseInterval(config) + REVEAL_SLACK_TICKS;
			Box box = new Box(center, center).expand(radius);
			for (LivingEntity target : blade.getWorld().getEntitiesByClass(LivingEntity.class, box,
					target -> target.squaredDistanceTo(center) <= radiusSquared && PulseBlade.reveals(owner, target))) {
				REVEALED.merge(target.getId(), until, Math::max);
			}
		} else if (local.squaredDistanceTo(center) <= radiusSquared && PulseBlade.reveals(owner, local)) {
			PulseHud.revealed();
		}
	}

	/** End of every client tick: drop finished waves and expired reveals. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			WAVES.clear();
			REVEALED.clear();
			return;
		}

		long now = System.nanoTime();
		WAVES.removeIf(wave -> (now - wave.startNanos()) / 1.0E9F > WAVE_SECONDS);
		long time = client.world.getTime();
		REVEALED.values().removeIf(until -> until < time);
	}

	/**
	 * The waves: expanding spheres, faint where they face the eye and bright at their rims - a shell of light
	 * sweeping out - fading as they reach the edge.
	 */
	public static void render(WorldRenderContext context) {
		VertexConsumerProvider consumers = context.consumers();
		if (WAVES.isEmpty() || consumers == null) {
			return;
		}

		Vec3d cam = context.camera().getPos();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(PulseModel.TEXTURE));
		MatrixStack.Entry entry = new MatrixStack().peek();
		long now = System.nanoTime();
		for (Wave wave : WAVES) {
			float t = MathHelper.clamp((now - wave.startNanos()) / 1.0E9F / WAVE_SECONDS, 0.0F, 1.0F);
			float eased = 1.0F - (1.0F - t) * (1.0F - t);
			float r = (float) (wave.radius() * eased);
			float strength = 0.75F * (1.0F - t) * (1.0F - t);
			if (r < 0.05F || strength < 0.01F) {
				continue;
			}

			sphere(entry, buffer, wave.center().subtract(cam), r, strength);
		}
	}

	private static void sphere(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d center, float r, float strength) {
		Vec3d[][] points = new Vec3d[LATITUDES + 1][LONGITUDES + 1];
		int[][] colors = new int[LATITUDES + 1][LONGITUDES + 1];
		for (int i = 0; i <= LATITUDES; i++) {
			float theta = MathHelper.PI * i / LATITUDES;
			for (int j = 0; j <= LONGITUDES; j++) {
				float phi = MathHelper.TAU * j / LONGITUDES;
				Vec3d normal = new Vec3d(MathHelper.sin(theta) * MathHelper.cos(phi), MathHelper.cos(theta), MathHelper.sin(theta) * MathHelper.sin(phi));
				Vec3d point = center.add(normal.multiply(r));
				// Rim light: brightest where the shell is seen edge-on. Close to the eye the patches are huge and the rim
				// term swings across one, so the shell fades out there (the eye is often inside the wave).
				double distance = point.length();
				double facing = Math.abs(normal.dotProduct(point.multiply(1.0 / Math.max(distance, 1.0E-4))));
				float rim = (float) Math.pow(1.0 - facing, 2.5);
				float near = (float) MathHelper.clamp((distance - NEAR_FADE_START) / NEAR_FADE_LENGTH, 0.0, 1.0);
				points[i][j] = point;
				colors[i][j] = color(strength * near * (0.08F + 0.92F * rim));
			}
		}

		for (int i = 0; i < LATITUDES; i++) {
			for (int j = 0; j < LONGITUDES; j++) {
				quad(entry, buffer, points[i][j], points[i + 1][j], points[i + 1][j + 1], points[i][j + 1],
						colors[i][j], colors[i + 1][j], colors[i + 1][j + 1], colors[i][j + 1]);
			}
		}
	}

	/** One patch, from both sides (the emissive layer culls back faces, and the eye may be inside the wave). */
	private static void quad(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int ca, int cb, int cc, int cd) {
		vertex(entry, buffer, a, ca);
		vertex(entry, buffer, b, cb);
		vertex(entry, buffer, c, cc);
		vertex(entry, buffer, d, cd);
		vertex(entry, buffer, d, cd);
		vertex(entry, buffer, c, cc);
		vertex(entry, buffer, b, cb);
		vertex(entry, buffer, a, ca);
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d p, int color) {
		buffer.vertex(entry, (float) p.x, (float) p.y, (float) p.z).color(color).texture(PulseModel.LIGHT_U, PulseModel.LIGHT_V)
				.overlay(OverlayTexture.DEFAULT_UV).light(PulseModel.FULL_BRIGHT).normal(entry, 0.0F, 1.0F, 0.0F);
	}

	/** The wave's colour at {@code k} of its full brightness (the emissive layer adds it on, so dark is clear). */
	static int color(float k) {
		int r = Math.round(((WAVE_COLOR >> 16) & 0xFF) * k);
		int g = Math.round(((WAVE_COLOR >> 8) & 0xFF) * k);
		int b = Math.round((WAVE_COLOR & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
