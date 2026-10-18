package com.loaffie.titanfall.slide;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Per-player "is sliding" flag, added to {@link PlayerEntity} by {@code PlayerEntitySlidePoseMixin}.
 *
 * <p>While it is set (and the player wears the jumpkit), the player takes the vanilla
 * {@code EntityPose.SLIDING} pose - unused by players in vanilla - whose dimensions this mod defines as
 * {@link #DIMENSIONS}: 0.6 x 0.8 blocks with the eyes at 0.6. The pose is part of the entity's synced
 * data, so every client sees the slide hitbox and pose, and because the server sets the same pose from
 * the same flag, its collision checks and suffocation test agree with the client in 1-block gaps.
 */
public interface SlideState {
	/** Slide hitbox: as wide as a player, 0.8 blocks tall, eyes 0.6 above the feet. */
	EntityDimensions DIMENSIONS = EntityDimensions.changing(0.6F, 0.8F).withEyeHeight(0.6F);

	boolean titanfall$isSliding();

	void titanfall$setSliding(boolean sliding);

	static boolean isSliding(PlayerEntity player) {
		return ((SlideState) player).titanfall$isSliding();
	}

	static void setSliding(PlayerEntity player, boolean sliding) {
		((SlideState) player).titanfall$setSliding(sliding);
	}
}
