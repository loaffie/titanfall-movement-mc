package com.loaffie.titanfall.stim;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server to client: another player injected a Stim lasting {@code ticks} (the speed boost's length). */
public record StimSyncPayload(int player, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<StimSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "stim_sync"));
	public static final PacketCodec<RegistryByteBuf, StimSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, StimSyncPayload::player, PacketCodecs.VAR_INT, StimSyncPayload::ticks, StimSyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
