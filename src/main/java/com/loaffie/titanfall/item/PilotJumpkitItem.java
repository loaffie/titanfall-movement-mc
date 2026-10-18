package com.loaffie.titanfall.item;

import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Pilot Jumpkit: a hip-mounted thruster rig worn in the chest equipment slot.
 *
 * <p>It is a plain {@link Item} implementing {@link Equipment} - the same pattern vanilla uses for the
 * elytra - rather than an {@code ArmorItem}. That gives it every equipment behaviour for free (shift
 * click and drag into the chest slot, right-click to equip and swap, dispensers equipping it) while
 * keeping the vanilla armor layer from drawing a chestplate shell over the player: the kit is drawn by
 * its own feature renderer around the waist instead.
 *
 * <p>Crafted from iron, netherite scrap, redstone, an elytra, fireworks and copper (see
 * {@code data/titanfall_movement/recipe/pilot_jumpkit.json}); the recipe unlocks in the recipe book once
 * the player holds an elytra or netherite scrap. It is also in the Combat creative tab.
 */
public class PilotJumpkitItem extends Item implements Equipment {
	public PilotJumpkitItem(Settings settings) {
		super(settings);
		DispenserBlock.registerBehavior(this, ArmorItem.DISPENSER_BEHAVIOR);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return this.equipAndSwap(this, world, user, hand);
	}

	@Override
	public EquipmentSlot getSlotType() {
		return EquipmentSlot.CHEST;
	}

	@Override
	public RegistryEntry<SoundEvent> getEquipSound() {
		return SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_jumpkit.tooltip.slot").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_jumpkit.tooltip.abilities").formatted(Formatting.DARK_AQUA));
	}
}
