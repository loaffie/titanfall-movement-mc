package com.loaffie.titanfall.grapple;

import com.loaffie.titanfall.TitanfallMovement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Client to server: what the local pilot's grapple just did. The hook, the pull and the cooldown are run by
 * the client (player movement is client-authoritative in Minecraft); the server relays each event to the
 * other players in range, so they see and hear the hook, and pulls an airborne entity the hook bit.
 *
 * <ul>
 *   <li>{@link #FIRE}: hook launched from {@code pos} (the eye) along {@code dir}.</li>
 *   <li>{@link #ATTACH}: hook bit at {@code pos}; {@code entity} is the hooked entity's id, or -1 for a block, whose
 *   outward surface normal is then in {@code dir}.</li>
 *   <li>{@link #RELEASE}: hook let go or missed, and is reeling back in.</li>
 * </ul>
 */
public record GrapplePayload(byte phase, Vec3d pos, Vec3d dir, int entity) implements CustomPayload {
	public static final byte FIRE = 1;
	public static final byte ATTACH = 2;
	public static final byte RELEASE = 3;

	public static final CustomPayload.Id<GrapplePayload> ID = new CustomPayload.Id<>(Identifier.of(TitanfallMovement.MOD_ID, "grapple"));
	public static final PacketCodec<RegistryByteBuf, GrapplePayload> CODEC = PacketCodec.of(GrapplePayload::write, GrapplePayload::read);

	public static GrapplePayload release() {
		return new GrapplePayload(RELEASE, Vec3d.ZERO, Vec3d.ZERO, -1);
	}

	private void write(RegistryByteBuf buf) {
		buf.writeByte(phase);
		writeVec(buf, pos);
		writeVec(buf, dir);
		buf.writeVarInt(entity);
	}

	private static GrapplePayload read(RegistryByteBuf buf) {
		return new GrapplePayload(buf.readByte(), readVec(buf), readVec(buf), buf.readVarInt());
	}

	static void writeVec(RegistryByteBuf buf, Vec3d vec) {
		buf.writeDouble(vec.x);
		buf.writeDouble(vec.y);
		buf.writeDouble(vec.z);
	}

	static Vec3d readVec(RegistryByteBuf buf) {
		return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
