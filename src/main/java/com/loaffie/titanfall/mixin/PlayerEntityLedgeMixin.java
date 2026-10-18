package com.loaffie.titanfall.mixin;

import com.loaffie.titanfall.item.Jumpkit;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla stops a sneaking player from walking off a ledge by clamping their movement at the edge.
 * Sliding uses the sneak key, and a slide-hop chain holds it the whole time, so the clamp would stop
 * every slide at the first drop.
 *
 * <p>This must run on <em>both</em> sides. The server replays every client move through
 * {@code move(MovementType.PLAYER, ...)}, which applies the same clamp; if only the client skipped it,
 * the server would end up more than 0.25 blocks behind and teleport the pilot back to the ledge
 * (also in singleplayer, where the integrated server runs the same check).
 *
 * <p>The clamp is skipped only for a jumpkit pilot moving faster than 0.15 blocks/tick (3 b/s).
 * Ordinary crouch-walking is about 0.065 blocks/tick and keeps vanilla's edge protection, while a
 * slide never drops below 4.3 b/s (0.215 blocks/tick) before it ends.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityLedgeMixin {
	private static final double SLIDE_SPEED_SQUARED = 0.15 * 0.15;

	@Inject(
			method = "adjustMovementForSneaking(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/MovementType;)Lnet/minecraft/util/math/Vec3d;",
			at = @At("HEAD"),
			cancellable = true)
	private void titanfall$letSlidesLeaveLedges(Vec3d movement, MovementType type, CallbackInfoReturnable<Vec3d> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (movement.horizontalLengthSquared() > SLIDE_SPEED_SQUARED && Jumpkit.abilitiesActive(self)) {
			cir.setReturnValue(movement);
		}
	}
}
