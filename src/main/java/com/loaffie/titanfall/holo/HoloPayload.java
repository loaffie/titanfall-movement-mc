package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: "send out a decoy", with the pilot's move at that moment for the decoy to carry on - the
 * client is authoritative for its own movement, so the server's copy of it would be a tick or two stale and
 * know nothing of slides or wall runs.
 *
 * @param vx      velocity, blocks/tick
 * @param move    what the pilot was doing: one of {@link HoloDecoyEntity.Move}'s ordinals
 * @param run     the movement keys were pushed past {@link HoloPilot#STICK_TO_RUN}
 * @param runYaw  the way they were heading (the keys' direction, or along the slide or the wall), degrees
 * @param wallX   the wall's outward normal while wall running, else zero
 */
public record HoloPayload(double vx, double vy, double vz, int move, boolean run, float runYaw, float wallX, float wallZ)
		implements CustomPayload {
	public static final CustomPayload.Id<HoloPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "holo"));
	public static final PacketCodec<RegistryByteBuf, HoloPayload> CODEC = PacketCodec.of(HoloPayload::write, HoloPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeDouble(vx);
		buf.writeDouble(vy);
		buf.writeDouble(vz);
		buf.writeByte(move);
		buf.writeBoolean(run);
		buf.writeFloat(runYaw);
		buf.writeFloat(wallX);
		buf.writeFloat(wallZ);
	}

	private static HoloPayload read(RegistryByteBuf buf) {
		return new HoloPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(), buf.readBoolean(), buf.readFloat(),
				buf.readFloat(), buf.readFloat());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
