package com.loaffie.titanfall.awall;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the pilot let the A-Wall's disc go, along their view ({@code yaw}, {@code pitch}) and with
 * their own motion at that moment (the client runs its pilot's movement, so the server takes it from there).
 */
public record AWallThrowPayload(float yaw, float pitch, double vx, double vy, double vz) implements CustomPayload {
	public static final CustomPayload.Id<AWallThrowPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "awall_throw"));
	public static final PacketCodec<RegistryByteBuf, AWallThrowPayload> CODEC = PacketCodec.of(AWallThrowPayload::write, AWallThrowPayload::read);

	private static AWallThrowPayload read(RegistryByteBuf buf) {
		return new AWallThrowPayload(buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeFloat(yaw);
		buf.writeFloat(pitch);
		buf.writeDouble(vx);
		buf.writeDouble(vy);
		buf.writeDouble(vz);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
