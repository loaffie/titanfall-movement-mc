package com.loaffie.titanfall.holo;

import com.loaffie.titanfall.item.PilotGear;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Holo Pilot projector. Right-clicking it equips it - onto the chest (Trinkets' necklace slot) when
 * Trinkets is installed, otherwise into the off hand - and the Tactical key then sends out a decoy.
 */
public class PilotHoloItem extends Item {
	public PilotHoloItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return PilotGear.equip(world, user, hand);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		String slot = PilotGear.trinketsLoaded() ? "trinkets" : "offhand";
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_holo.tooltip.slot." + slot).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_holo.tooltip.use").formatted(Formatting.DARK_AQUA));
	}
}
