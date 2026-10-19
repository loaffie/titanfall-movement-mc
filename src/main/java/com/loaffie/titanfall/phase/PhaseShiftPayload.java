package com.loaffie.titanfall.phase;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client to server: "my pilot just phase shifted". */
public record PhaseShiftPayload() implements CustomPayload {
	public static final CustomPayload.Id<PhaseShiftPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "phase_shift"));
	public static final PacketCodec<RegistryByteBuf, PhaseShiftPayload> CODEC = PacketCodec.unit(new PhaseShiftPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
