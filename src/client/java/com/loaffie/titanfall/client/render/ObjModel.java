package com.loaffie.titanfall.client.render;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain OBJ model split into named objects, loaded from the client resources and drawn straight into an
 * entity vertex consumer. Faces are fan-triangulated and each triangle is emitted as a degenerate quad
 * (entity layers draw quads). Loaded lazily and dropped on resource reload, so a resource pack can replace
 * it; if it cannot be read the failure is logged once and {@link #get} keeps returning null.
 */
public final class ObjModel {
	/** Floats per stored corner: x, y, z, u, v, nx, ny, nz. */
	private static final int STRIDE = 8;

	private final Map<String, float[]> groups;

	private ObjModel(Map<String, float[]> groups) {
		this.groups = groups;
	}

	/** A lazily loaded model at {@code id}. */
	public static final class Handle {
		private final Identifier id;
		private ObjModel model;
		private boolean failed;

		public Handle(Identifier id) {
			this.id = id;
		}

		public ObjModel get() {
			if (model == null && !failed) {
				try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(id)) {
					model = parse(in);
				} catch (IOException | RuntimeException exception) {
					failed = true;
					TitanfallMovement.LOGGER.error("Could not load the model {}", id, exception);
				}
			}

			return model;
		}

		public void invalidate() {
			model = null;
			failed = false;
		}
	}

	static ObjModel parse(InputStream in) throws IOException {
		List<float[]> positions = new ArrayList<>();
		List<float[]> uvs = new ArrayList<>();
		List<float[]> normals = new ArrayList<>();
		Map<String, Floats> groups = new HashMap<>();
		Floats current = groups.computeIfAbsent("default", key -> new Floats());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#') {
				continue;
			}

			String[] parts = line.split("\\s+");
			switch (parts[0]) {
				case "v" -> positions.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
				case "vt" -> uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
				case "vn" -> normals.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
				case "o", "g" -> current = groups.computeIfAbsent(parts[1], key -> new Floats());
				case "f" -> {
					for (int corner = 2; corner + 1 <= parts.length - 1; corner++) {
						corner(current, parts[1], positions, uvs, normals);
						corner(current, parts[corner], positions, uvs, normals);
						corner(current, parts[corner + 1], positions, uvs, normals);
					}
				}
				default -> {
				}
			}
		}

		Map<String, float[]> baked = new HashMap<>();
		groups.forEach((name, list) -> baked.put(name, list.toArray()));
		return new ObjModel(baked);
	}

	private static void corner(Floats out, String token, List<float[]> positions, List<float[]> uvs, List<float[]> normals) {
		String[] idx = token.split("/");
		float[] p = positions.get(Integer.parseInt(idx[0]) - 1);
		float[] t = idx.length > 1 && !idx[1].isEmpty() ? uvs.get(Integer.parseInt(idx[1]) - 1) : new float[]{0.0F, 0.0F};
		float[] n = idx.length > 2 && !idx[2].isEmpty() ? normals.get(Integer.parseInt(idx[2]) - 1) : new float[]{0.0F, -1.0F, 0.0F};
		out.add(p[0], p[1], p[2], t[0], t[1], n[0], n[1], n[2]);
	}

	public boolean has(String group) {
		return groups.containsKey(group);
	}

	/** Draws one object; {@code color} is ARGB and tints the texture (white = unchanged). */
	public void render(String group, MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, int color) {
		float[] d = groups.get(group);
		if (d == null) {
			return;
		}

		for (int tri = 0; tri < d.length; tri += STRIDE * 3) {
			emit(entry, consumer, d, tri, light, overlay, color);
			emit(entry, consumer, d, tri + STRIDE, light, overlay, color);
			emit(entry, consumer, d, tri + STRIDE * 2, light, overlay, color);
			emit(entry, consumer, d, tri + STRIDE * 2, light, overlay, color);
		}
	}

	private static void emit(MatrixStack.Entry entry, VertexConsumer consumer, float[] d, int i, int light, int overlay, int color) {
		consumer.vertex(entry, d[i], d[i + 1], d[i + 2])
				.color(color)
				.texture(d[i + 3], d[i + 4])
				.overlay(overlay)
				.light(light)
				.normal(entry, d[i + 5], d[i + 6], d[i + 7]);
	}

	private static final class Floats {
		private float[] data = new float[1024];
		private int size;

		void add(float... values) {
			if (size + values.length > data.length) {
				data = Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
			}

			System.arraycopy(values, 0, data, size, values.length);
			size += values.length;
		}

		float[] toArray() {
			return Arrays.copyOf(data, size);
		}
	}
}
