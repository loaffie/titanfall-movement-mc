package com.loaffie.titanfall.cloak;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client: a player cloaked for {@code ticks} (sent to the players tracking them), or - with
 * {@code ticks} 0 - their cloak dropped (sent to everyone, the pilot included: it ran out, they fired, died,
 * or the server refused it).
 */
public record CloakSyncPayload(int player, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<CloakSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "cloak_sync"));
	public static final PacketCodec<RegistryByteBuf, CloakSyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CloakSyncPayload::player, PacketCodecs.VAR_INT, CloakSyncPayload::ticks, CloakSyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
