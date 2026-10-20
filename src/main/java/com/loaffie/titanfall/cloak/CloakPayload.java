package com.loaffie.titanfall.cloak;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client to server: "my pilot just cloaked". */
public record CloakPayload() implements CustomPayload {
	public static final CustomPayload.Id<CloakPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "cloak"));
	public static final PacketCodec<RegistryByteBuf, CloakPayload> CODEC = PacketCodec.unit(new CloakPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
