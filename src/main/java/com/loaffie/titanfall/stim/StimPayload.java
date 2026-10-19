package com.loaffie.titanfall.stim;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client to server: "my pilot just injected the Stim". */
public record StimPayload() implements CustomPayload {
	public static final CustomPayload.Id<StimPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "stim"));
	public static final PacketCodec<RegistryByteBuf, StimPayload> CODEC = PacketCodec.unit(new StimPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
