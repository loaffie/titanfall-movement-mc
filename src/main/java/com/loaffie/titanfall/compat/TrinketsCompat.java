package com.loaffie.titanfall.compat;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Pair;

import java.util.List;
import java.util.Optional;

/**
 * Every call into Trinkets lives here, so the rest of the mod never loads a Trinkets class: this class is
 * only touched after {@code FabricLoader.isModLoaded("trinkets")} said yes, and the mod runs without
 * Trinkets installed.
 */
public final class TrinketsCompat {
	private TrinketsCompat() {
	}

	/**
	 * The slot group of the first trinket slot holding {@code item} ({@code "hand"}, {@code "offhand"}...),
	 * or null when it is not equipped. Trinkets' {@code hand} group is the main arm's, {@code offhand} the
	 * other one.
	 */
	public static String equippedGroup(LivingEntity entity, Item item) {
		Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(entity);
		if (component.isEmpty()) {
			return null;
		}

		List<Pair<SlotReference, ItemStack>> equipped = component.get().getEquipped(item);
		return equipped.isEmpty() ? null : equipped.get(0).getLeft().inventory().getSlotType().getGroup();
	}

	/** Puts the stack into the first free trinket slot that accepts it. */
	public static boolean equip(PlayerEntity player, ItemStack stack) {
		return TrinketItem.equipItem(player, stack);
	}
}
