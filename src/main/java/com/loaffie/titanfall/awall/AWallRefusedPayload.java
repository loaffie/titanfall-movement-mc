package com.loaffie.titanfall.awall;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: the throw was refused (not recharged, not worn, phased...), so the charge comes back. */
public record AWallRefusedPayload() implements CustomPayload {
	public static final CustomPayload.Id<AWallRefusedPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "awall_refused"));
	public static final PacketCodec<RegistryByteBuf, AWallRefusedPayload> CODEC = PacketCodec.unit(new AWallRefusedPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
