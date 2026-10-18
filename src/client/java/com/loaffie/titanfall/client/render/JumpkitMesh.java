package com.loaffie.titanfall.client.render;

import com.loaffie.titanfall.TitanfallMovement;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Pilot Jumpkit's OBJ mesh ({@code assets/titanfall_movement/models/entity/jumpkit.obj}), loaded once
 * from the client resources and drawn straight into an entity vertex consumer.
 *
 * <p>The file is produced by {@code tools/bake-jumpkit.py} (which also runs {@code split-jumpkit-flaps.py}
 * and {@code split-jumpkit-pods.py}): triangles with positions already in the player body's axes (X side,
 * Y down, Z back), atlas UVs and flat normals, split into objects:
 * <ul>
 *   <li>{@code body} - the pack itself (frame, tank, hubs), fixed to the belt;</li>
 *   <li>{@code pod_r}/{@code pod_l} - the two thruster modules (pod housing + capsule arm), each articulated
 *   about its {@code # pivot} (the side hub);</li>
 *   <li>{@code hose_r}/{@code hose_l} - hose ends, skinned between pack and pod with the {@code # skin}
 *   weights so they bend instead of tearing;</li>
 *   <li>{@code glow_r}/{@code glow_l} - the exhaust plates again, for the emissive layer;</li>
 *   <li>{@code flap_*} - the thruster flaps, hinged on their {@code # hinge} lines.</li>
 * </ul>
 * Older single-piece models ({@code body} + {@code glow} only) still load and draw unarticulated.
 * Entity render layers draw quads, so each triangle is emitted as a quad whose last corner repeats the
 * third - a degenerate quad that rasterises exactly as the triangle.
 *
 * <p>The mesh is reloaded with resource packs (F3+T), so a pack can replace the model or textures.
 */
public final class JumpkitMesh {
	public static final Identifier MODEL = Identifier.of(TitanfallMovement.MOD_ID, "models/entity/jumpkit.obj");
	/** Floats per stored corner: x, y, z, u, v, nx, ny, nz. */
	private static final int STRIDE = 8;
	/** Directions sampled for each part's collision hull. */
	private static final int HULL_DIRECTIONS = 96;
	private static final int POD_SAMPLES = 250;

	private static JumpkitMesh instance;
	private static boolean failed;

	/** Thruster flap objects, right pod then left, as split out by {@code tools/split-jumpkit-flaps.py}. */
	public static final String[] FLAPS = {"flap_r0", "flap_r1", "flap_r2", "flap_l0", "flap_l1", "flap_l2"};
	/** Per side (0 = mesh +X "r", 1 = mesh -X "l"): pod, glow and hose object names. */
	public static final String[] PODS = {"pod_r", "pod_l"};
	public static final String[] GLOWS = {"glow_r", "glow_l"};
	public static final String[] HOSES = {"hose_r", "hose_l"};
	/** Nozzle inner walls, duplicated for the emissive pass. */
	public static final String[] INNER_GLOWS = {"glowin_r", "glowin_l"};
	/** The turbine disc's cell in the emissive atlas (cell 10: column 2, row 2 of 64 px cells in 256). */
	private static final float TURBINE_U0 = 129.0F / 256.0F;
	private static final float TURBINE_U1 = 191.0F / 256.0F;
	private static final float TURBINE_V0 = 129.0F / 256.0F;
	private static final float TURBINE_V1 = 191.0F / 256.0F;

	private final Map<String, float[]> groups;
	/** Flap hinges by object name: pivot x, y, z and unit axis x, y, z (mesh units, body space). */
	private final Map<String, float[]> hinges;
	/** Pod joints by object name: x, y, z. */
	private final Map<String, float[]> pivots;
	/** Per-corner skin weights of the hose objects (one per stored corner). */
	private final Map<String, float[]> skins;
	/** Bounding box of all vertices, mesh units: minX, minY, minZ, maxX, maxY, maxZ. */
	private final float[] bounds;
	/** Collision hulls, flattened xyz: the pack, and each thruster module (pod, flaps and exhaust). */
	private final float[] packHull;
	private final float[][] podHulls;
	/** A dense, even sample of each module's surface (~250 points), for overlap tests against the arms. */
	private final float[][] podSamples;

	private JumpkitMesh(Map<String, float[]> groups, Map<String, float[]> hinges, Map<String, float[]> pivots,
			Map<String, float[]> skins, float[] bounds) {
		this.groups = groups;
		this.hinges = hinges;
		this.pivots = pivots;
		this.skins = skins;
		this.bounds = bounds;
		this.packHull = hull("body");
		this.podSamples = new float[][]{sample(POD_SAMPLES, PODS[0]), sample(POD_SAMPLES, PODS[1])};
		this.podHulls = new float[][]{hull(PODS[0], FLAPS[0], FLAPS[1], FLAPS[2], GLOWS[0]),
				hull(PODS[1], FLAPS[3], FLAPS[4], FLAPS[5], GLOWS[1])};
	}

	/** Bounding box of the mesh in mesh units: minX, minY, minZ, maxX, maxY, maxZ. */
	public float[] bounds() {
		return bounds.clone();
	}

	/** Whether the thruster modules are separate objects that can be articulated. */
	public boolean articulated() {
		return groups.containsKey(PODS[0]) && groups.containsKey(PODS[1]) && pivots.containsKey(PODS[0]) && pivots.containsKey(PODS[1]);
	}

	/** Joint of a thruster module (side 0 = "r", 1 = "l"), mesh units. */
	public float[] pivot(int side) {
		return pivots.get(PODS[side]);
	}

	/** The pack's collision hull points (flattened xyz, mesh units). */
	public float[] packHull() {
		return packHull;
	}

	/** A thruster module's collision hull points (flattened xyz, mesh units). */
	public float[] podHull(int side) {
		return podHulls[side];
	}

	/** A dense sample of a module's surface points (flattened xyz, mesh units). */
	public float[] podSample(int side) {
		return podSamples[side];
	}

	/** About {@code count} of a group's distinct vertices, evenly strided (flattened xyz). */
	private float[] sample(int count, String name) {
		float[] data = groups.get(name);
		if (data == null) {
			return new float[0];
		}

		Set<Integer> seen = new LinkedHashSet<>();
		List<float[]> unique = new ArrayList<>();
		for (int i = 0; i < data.length; i += STRIDE) {
			float[] p = {data[i], data[i + 1], data[i + 2]};
			if (seen.add(java.util.Arrays.hashCode(p))) {
				unique.add(p);
			}
		}

		int stride = Math.max(1, unique.size() / count);
		List<float[]> picked = new ArrayList<>();
		for (int i = 0; i < unique.size(); i += stride) {
			picked.add(unique.get(i));
		}

		float[] flat = new float[picked.size() * 3];
		for (int i = 0; i < picked.size(); i++) {
			System.arraycopy(picked.get(i), 0, flat, i * 3, 3);
		}

		return flat;
	}

	/** The loaded mesh, or null if it is missing or broken (the renderer then falls back to the cuboid kit). */
	public static JumpkitMesh get() {
		if (instance == null && !failed) {
			try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(MODEL)) {
				instance = parse(in);
			} catch (IOException | RuntimeException exception) {
				failed = true;
				TitanfallMovement.LOGGER.error("Could not load the jumpkit model {}, using the fallback model", MODEL, exception);
			}
		}

		return instance;
	}

	/** Clears the cached mesh whenever client resources reload. */
	public static SimpleSynchronousResourceReloadListener reloadListener() {
		return new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TitanfallMovement.MOD_ID, "jumpkit_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				instance = null;
				failed = false;
			}
		};
	}

	static JumpkitMesh parse(InputStream in) throws IOException {
		List<float[]> positions = new ArrayList<>();
		List<float[]> uvs = new ArrayList<>();
		List<float[]> normals = new ArrayList<>();
		Map<String, FloatList> groups = new HashMap<>();
		FloatList current = groups.computeIfAbsent("body", key -> new FloatList());
		Map<String, float[]> hinges = new HashMap<>();
		Map<String, float[]> pivots = new HashMap<>();
		Map<String, float[]> skinRanges = new HashMap<>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("# hinge ")) {
				// # hinge <object> <pivot x y z> <axis x y z>
				float[] values = numbers(line, 6);
				if (values != null) {
					hinges.put(line.split("\\s+")[2], values);
				}

				continue;
			}

			if (line.startsWith("# pivot ")) {
				float[] values = numbers(line, 3);
				if (values != null) {
					pivots.put(line.split("\\s+")[2], values);
				}

				continue;
			}

			if (line.startsWith("# skin ")) {
				float[] values = numbers(line, 2);
				if (values != null) {
					skinRanges.put(line.split("\\s+")[2], values);
				}

				continue;
			}

			if (line.isEmpty() || line.charAt(0) == '#') {
				continue;
			}

			String[] parts = line.split("\\s+");
			switch (parts[0]) {
				case "v" -> positions.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
				case "vt" -> uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
				case "vn" -> normals.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
				case "o", "g" -> current = groups.computeIfAbsent(parts[1], key -> new FloatList());
				case "f" -> {
					// Fan-triangulate, so quads or n-gons from other exporters also load.
					for (int corner = 2; corner + 1 <= parts.length - 1; corner++) {
						addCorner(current, parts[1], positions, uvs, normals);
						addCorner(current, parts[corner], positions, uvs, normals);
						addCorner(current, parts[corner + 1], positions, uvs, normals);
					}
				}
				default -> {
				}
			}
		}

		Map<String, float[]> baked = new HashMap<>();
		groups.forEach((name, list) -> baked.put(name, list.toArray()));
		Map<String, float[]> skins = new HashMap<>();
		skinRanges.forEach((name, range) -> {
			float[] data = baked.get(name);
			if (data != null) {
				float[] weights = new float[data.length / STRIDE];
				for (int i = 0; i < weights.length; i++) {
					weights[i] = smoothstep(range[0], range[1], Math.abs(data[i * STRIDE]));
				}

				skins.put(name, weights);
			}
		});

		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (float[] p : positions) {
			for (int axis = 0; axis < 3; axis++) {
				bounds[axis] = Math.min(bounds[axis], p[axis]);
				bounds[axis + 3] = Math.max(bounds[axis + 3], p[axis]);
			}
		}

		return new JumpkitMesh(baked, hinges, pivots, skins, bounds);
	}

	private static float[] numbers(String line, int count) {
		String[] parts = line.split("\\s+");
		if (parts.length < 3 + count) {
			return null;
		}

		float[] values = new float[count];
		for (int i = 0; i < count; i++) {
			values[i] = Float.parseFloat(parts[3 + i]);
		}

		return values;
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * The support points of some objects: for each of {@link #HULL_DIRECTIONS} evenly spread directions,
	 * the vertex furthest along it. Any rotation or translation of the part keeps its extreme point in a
	 * direction close to one of these, so testing these few points against a wall or the floor is as good
	 * as testing every vertex, to well under a pixel.
	 */
	private float[] hull(String... names) {
		Set<Integer> picked = new LinkedHashSet<>();
		List<float[]> sources = new ArrayList<>();
		for (String name : names) {
			float[] data = groups.get(name);
			if (data != null) {
				sources.add(data);
			}
		}

		List<float[]> points = new ArrayList<>();
		for (int d = 0; d < HULL_DIRECTIONS; d++) {
			// Fibonacci sphere.
			float y = 1.0F - 2.0F * (d + 0.5F) / HULL_DIRECTIONS;
			float r = (float) Math.sqrt(1.0F - y * y);
			float phi = d * 2.399963F;
			float dx = (float) Math.cos(phi) * r;
			float dz = (float) Math.sin(phi) * r;
			float best = -Float.MAX_VALUE;
			float[] bestPoint = null;
			for (float[] data : sources) {
				for (int i = 0; i < data.length; i += STRIDE) {
					float dot = data[i] * dx + data[i + 1] * y + data[i + 2] * dz;
					if (dot > best) {
						best = dot;
						bestPoint = new float[]{data[i], data[i + 1], data[i + 2]};
					}
				}
			}

			if (bestPoint != null && picked.add(java.util.Arrays.hashCode(bestPoint))) {
				points.add(bestPoint);
			}
		}

		float[] flat = new float[points.size() * 3];
		for (int i = 0; i < points.size(); i++) {
			System.arraycopy(points.get(i), 0, flat, i * 3, 3);
		}

		return flat;
	}

	private static void addCorner(FloatList out, String token, List<float[]> positions, List<float[]> uvs, List<float[]> normals) {
		String[] idx = token.split("/");
		float[] p = positions.get(Integer.parseInt(idx[0]) - 1);
		float[] t = idx.length > 1 && !idx[1].isEmpty() ? uvs.get(Integer.parseInt(idx[1]) - 1) : new float[]{0.0F, 0.0F};
		float[] n = idx.length > 2 && !idx[2].isEmpty() ? normals.get(Integer.parseInt(idx[2]) - 1) : new float[]{0.0F, -1.0F, 0.0F};
		out.add(p[0], p[1], p[2], t[0], t[1], n[0], n[1], n[2]);
	}

	/** Draws one group; {@code color} is ARGB and tints the texture (white = unchanged). */
	public void render(String group, MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, int color) {
		float[] data = groups.get(group);
		if (data == null) {
			return;
		}

		for (int tri = 0; tri < data.length; tri += STRIDE * 3) {
			emit(entry, consumer, data, tri, light, overlay, color);
			emit(entry, consumer, data, tri + STRIDE, light, overlay, color);
			emit(entry, consumer, data, tri + STRIDE * 2, light, overlay, color);
			emit(entry, consumer, data, tri + STRIDE * 2, light, overlay, color);
		}
	}

	/**
	 * Draws a skinned hose. Each corner is turned about the pod's hub by its skin weight's share of the
	 * pod's rotation (a slerp from no rotation to the full one), so the hose bends round the hub like a real
	 * hose. Unlike blending positions (linear blend skinning), a partial rotation keeps every corner at
	 * its distance from the hub, so the hose never stretches or pinches however far the pod swings.
	 * Without skin weights the group is drawn rigidly on the pack.
	 */
	public void renderSkinned(String group, MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, int color,
			float[] pivot, Quaternionf rotation) {
		float[] data = groups.get(group);
		float[] weights = skins.get(group);
		if (data == null) {
			return;
		}

		if (weights == null || pivot == null || rotation == null) {
			render(group, entry, consumer, light, overlay, color);
			return;
		}

		Vector3f point = new Vector3f();
		Vector3f normal = new Vector3f();
		Quaternionf partial = new Quaternionf();
		float[] corner = new float[STRIDE * 3];
		for (int tri = 0; tri < data.length; tri += STRIDE * 3) {
			for (int k = 0; k < 3; k++) {
				int i = tri + k * STRIDE;
				float w = weights[i / STRIDE];
				int o = k * STRIDE;
				System.arraycopy(data, i, corner, o, STRIDE);
				if (w > 0.0F) {
					partial.identity().slerp(rotation, w);
					partial.transform(point.set(data[i] - pivot[0], data[i + 1] - pivot[1], data[i + 2] - pivot[2]));
					partial.transform(normal.set(data[i + 5], data[i + 6], data[i + 7]));
					corner[o] = point.x + pivot[0];
					corner[o + 1] = point.y + pivot[1];
					corner[o + 2] = point.z + pivot[2];
					corner[o + 5] = normal.x;
					corner[o + 6] = normal.y;
					corner[o + 7] = normal.z;
				}
			}

			emit(entry, consumer, corner, 0, light, overlay, color);
			emit(entry, consumer, corner, STRIDE, light, overlay, color);
			emit(entry, consumer, corner, STRIDE * 2, light, overlay, color);
			emit(entry, consumer, corner, STRIDE * 2, light, overlay, color);
		}
	}


	/**
	 * Draws one side's thruster flaps, each swung about its hinge by {@code angles[i]} radians (positive
	 * opens it outward, away from the exhaust); null draws them closed. Side 0 = "r", 1 = "l"; the angles
	 * array is indexed like {@link #FLAPS}. Meshes without flap objects draw nothing here.
	 */
	public void renderFlaps(int side, MatrixStack matrices, VertexConsumer consumer, int light, int overlay, int color, float[] angles) {
		for (int i = side * 3; i < side * 3 + 3; i++) {
			String name = FLAPS[i];
			if (!groups.containsKey(name)) {
				continue;
			}

			float[] hinge = hinges.get(name);
			float angle = angles == null ? 0.0F : angles[i];
			matrices.push();
			if (hinge != null && angle != 0.0F) {
				matrices.translate(hinge[0], hinge[1], hinge[2]);
				matrices.multiply(new Quaternionf().rotationAxis(angle, hinge[3], hinge[4], hinge[5]));
				matrices.translate(-hinge[0], -hinge[1], -hinge[2]);
			}

			render(name, matrices.peek(), consumer, light, overlay, color);
			matrices.pop();
		}
	}

	/**
	 * Draws a disc of the turbine texture inside a nozzle (mesh units): centred on {@code center}, facing
	 * along {@code axis}, {@code radius} wide, turned by {@code angle} radians about the axis. Drawn from
	 * both sides. Meant for the emissive (additive) layer, where the texture's black is invisible.
	 */
	public static void renderDisc(MatrixStack.Entry entry, VertexConsumer consumer, Vector3f center, Vector3f axis, float radius,
			float angle, int overlay, int color) {
		Vector3f n = new Vector3f(axis).normalize();
		Vector3f e1 = new Vector3f(n).cross(Math.abs(n.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
		Vector3f e2 = new Vector3f(n).cross(e1).normalize();
		float c = (float) Math.cos(angle) * radius;
		float s = (float) Math.sin(angle) * radius;
		Vector3f a = new Vector3f(e1).mul(c).add(new Vector3f(e2).mul(s));
		Vector3f b = new Vector3f(e2).mul(c).sub(new Vector3f(e1).mul(s));
		float[][] corners = {
				{center.x - a.x - b.x, center.y - a.y - b.y, center.z - a.z - b.z, TURBINE_U0, TURBINE_V0},
				{center.x + a.x - b.x, center.y + a.y - b.y, center.z + a.z - b.z, TURBINE_U1, TURBINE_V0},
				{center.x + a.x + b.x, center.y + a.y + b.y, center.z + a.z + b.z, TURBINE_U1, TURBINE_V1},
				{center.x - a.x + b.x, center.y - a.y + b.y, center.z - a.z + b.z, TURBINE_U0, TURBINE_V1}};
		for (int pass = 0; pass < 2; pass++) {
			float sign = pass == 0 ? 1.0F : -1.0F;
			for (int k = 0; k < 4; k++) {
				float[] v = corners[pass == 0 ? k : 3 - k];
				consumer.vertex(entry, v[0], v[1], v[2])
						.color(color)
						.texture(v[3], v[4])
						.overlay(overlay)
						.light(0xF000F0)
						.normal(entry, n.x * sign, n.y * sign, n.z * sign);
			}
		}
	}

	/**
	 * Draws the whole kit at rest (flaps closed, modules in place): the base pass, then the emissive pass.
	 * Each layer's buffer is fetched right before it is filled - fetching another layer's buffer can finish
	 * the previous one, so consumers are never held across layers.
	 */
	public void renderStatic(MatrixStack matrices, VertexConsumerProvider provider, RenderLayer baseLayer, RenderLayer glowLayer,
			int light, int overlay, int color, int glowColor) {
		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer base = provider.getBuffer(baseLayer);
		render("body", entry, base, light, overlay, color);
		for (int side = 0; side < 2; side++) {
			render(PODS[side], entry, base, light, overlay, color);
			render(HOSES[side], entry, base, light, overlay, color);
			renderFlaps(side, matrices, base, light, overlay, color, null);
		}

		VertexConsumer glow = provider.getBuffer(glowLayer);
		render("glow", entry, glow, 0xF000F0, overlay, glowColor);
		render(GLOWS[0], entry, glow, 0xF000F0, overlay, glowColor);
		render(GLOWS[1], entry, glow, 0xF000F0, overlay, glowColor);
		render(INNER_GLOWS[0], entry, glow, 0xF000F0, overlay, glowColor);
		render(INNER_GLOWS[1], entry, glow, 0xF000F0, overlay, glowColor);
	}

	private static void emit(MatrixStack.Entry entry, VertexConsumer consumer, float[] d, int i, int light, int overlay, int color) {
		consumer.vertex(entry, d[i], d[i + 1], d[i + 2])
				.color(color)
				.texture(d[i + 3], d[i + 4])
				.overlay(overlay)
				.light(light)
				.normal(entry, d[i + 5], d[i + 6], d[i + 7]);
	}

	/** Minimal growable float array, to avoid boxing 180k floats. */
	private static final class FloatList {
		private float[] data = new float[1024];
		private int size;

		void add(float... values) {
			if (size + values.length > data.length) {
				data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
			}

			System.arraycopy(values, 0, data, size, values.length);
			size += values.length;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}
}
