package com.loaffie.titanfall.pulse;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: the pilot's Pulse Blade. {@code stage} {@link #AIM} - they drew it and are aiming;
 * {@link #THROW} - they let it go, with the view ({@code yaw}, {@code pitch}) and their own motion at that
 * moment (the client runs its pilot's movement, so the server takes these from it).
 */
public record PulseThrowPayload(int stage, float yaw, float pitch, double vx, double vy, double vz) implements CustomPayload {
	public static final int CANCEL = 0;
	public static final int AIM = 1;
	public static final int THROW = 2;
	public static final CustomPayload.Id<PulseThrowPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "pulse_throw"));
	public static final PacketCodec<RegistryByteBuf, PulseThrowPayload> CODEC = PacketCodec.of(PulseThrowPayload::write, PulseThrowPayload::read);

	private static PulseThrowPayload read(RegistryByteBuf buf) {
		return new PulseThrowPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(stage);
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
