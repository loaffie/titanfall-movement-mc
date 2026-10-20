package com.loaffie.titanfall.pulse;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client: another player drew their Pulse Blade to aim ({@link PulseThrowPayload#AIM}), threw it
 * ({@link PulseThrowPayload#THROW}) or put it away ({@link PulseThrowPayload#CANCEL}), for their animation.
 */
public record PulseSyncPayload(int player, int stage) implements CustomPayload {
	public static final CustomPayload.Id<PulseSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "pulse_sync"));
	public static final PacketCodec<RegistryByteBuf, PulseSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PulseSyncPayload::player, PacketCodecs.VAR_INT, PulseSyncPayload::stage, PulseSyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
