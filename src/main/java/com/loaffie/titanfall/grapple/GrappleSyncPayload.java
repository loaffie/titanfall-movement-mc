package com.loaffie.titanfall.grapple;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: another player's grapple event ({@link GrapplePayload}), tagged with that player's entity id. */
public record GrappleSyncPayload(int player, GrapplePayload event) implements CustomPayload {
	public static final CustomPayload.Id<GrappleSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "grapple_sync"));
	public static final PacketCodec<RegistryByteBuf, GrappleSyncPayload> CODEC = PacketCodec.of(GrappleSyncPayload::write, GrappleSyncPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(player);
		GrapplePayload.CODEC.encode(buf, event);
	}

	private static GrappleSyncPayload read(RegistryByteBuf buf) {
		return new GrappleSyncPayload(buf.readVarInt(), GrapplePayload.CODEC.decode(buf));
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
