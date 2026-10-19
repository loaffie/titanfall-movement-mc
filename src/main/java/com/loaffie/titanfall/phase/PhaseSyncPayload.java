package com.loaffie.titanfall.phase;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client: a player phased out for {@code ticks} (sent to the players tracking them), or - with
 * {@code ticks} 0 - came back early (sent to everyone, the pilot included: death, or a refused shift).
 */
public record PhaseSyncPayload(int player, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<PhaseSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "phase_sync"));
	public static final PacketCodec<RegistryByteBuf, PhaseSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PhaseSyncPayload::player, PacketCodecs.VAR_INT, PhaseSyncPayload::ticks, PhaseSyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
