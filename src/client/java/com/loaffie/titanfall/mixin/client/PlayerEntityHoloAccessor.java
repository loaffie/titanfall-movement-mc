package com.loaffie.titanfall.mixin.client;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a Holo Pilot decoy's stand-in player needs to look like its pilot: the skin layers worn, and a cape that swings. */
@Mixin(PlayerEntity.class)
public interface PlayerEntityHoloAccessor {
	@Accessor("PLAYER_MODEL_PARTS")
	static TrackedData<Byte> titanfall$modelParts() {
		throw new AssertionError();
	}

	@Invoker("updateCapeAngles")
	void titanfall$updateCapeAngles();
}
