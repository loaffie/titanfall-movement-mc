package com.loaffie.titanfall.slide;

import com.loaffie.titanfall.TitanfallMovement;
import com.loaffie.titanfall.item.Jumpkit;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: "my pilot started / stopped sliding". Movement is client-authoritative in Minecraft,
 * so the client decides when a slide happens; the server only needs the flag to give the player the
 * same 0.8-block slide hitbox. One boolean, sent on change only.
 */
public record SlidePayload(boolean sliding) implements CustomPayload {
	public static final CustomPayload.Id<SlidePayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "slide"));
	public static final PacketCodec<RegistryByteBuf, SlidePayload> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, SlidePayload::sliding, SlidePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Common init: register the payload type and the server-side handler (runs on the server thread). */
	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) ->
				SlideState.setSliding(context.player(), payload.sliding() && Jumpkit.abilitiesActive(context.player())));
	}
}
