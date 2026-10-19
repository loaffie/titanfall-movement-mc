package com.loaffie.titanfall.item;

import com.loaffie.titanfall.compat.TrinketsCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Where pilot gear other than the jumpkit is worn: the Grapple and the Stim. With Trinkets installed they
 * go in their Trinkets slots (see {@code data/trinkets}); without it - or as well as it - they count as
 * worn while held in either hand.
 */
public final class PilotGear {
	private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");

	private PilotGear() {
	}

	public static boolean trinketsLoaded() {
		return TRINKETS;
	}

	/** The Trinkets slot group the item is equipped in ({@code "hand"}, {@code "offhand"}, {@code "legs"}...), or null. */
	public static String trinketGroup(LivingEntity entity, Item item) {
		return TRINKETS ? TrinketsCompat.equippedGroup(entity, item) : null;
	}

	/** Worn in a Trinkets slot or held in either hand. */
	public static boolean worn(LivingEntity entity, Item item) {
		return trinketGroup(entity, item) != null || entity.getOffHandStack().isOf(item) || entity.getMainHandStack().isOf(item);
	}

	/**
	 * Right-click to equip: into a free Trinkets slot that takes it, otherwise from the main hand into the
	 * empty off hand (where it counts as worn without Trinkets).
	 */
	public static TypedActionResult<ItemStack> equip(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (TRINKETS && TrinketsCompat.equip(user, stack)) {
			return TypedActionResult.success(stack, world.isClient());
		}

		if (hand == Hand.MAIN_HAND && user.getOffHandStack().isEmpty()) {
			user.setStackInHand(Hand.OFF_HAND, stack.copyAndEmpty());
			user.playSound(SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), 1.0F, 1.0F);
			return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
		}

		return TypedActionResult.pass(stack);
	}
}
