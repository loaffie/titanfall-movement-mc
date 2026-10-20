package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: the decoy the pilot asked for was refused (not charged, not worn, phased...), so the charge comes back. */
public record HoloRefusedPayload() implements CustomPayload {
	public static final CustomPayload.Id<HoloRefusedPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "holo_refused"));
	public static final PacketCodec<RegistryByteBuf, HoloRefusedPayload> CODEC = PacketCodec.unit(new HoloRefusedPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
