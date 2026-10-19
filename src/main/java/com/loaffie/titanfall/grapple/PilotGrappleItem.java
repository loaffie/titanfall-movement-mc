package com.loaffie.titanfall.grapple;

import com.loaffie.titanfall.compat.TrinketsCompat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Grapple: Titanfall 2's wrist-mounted grappling hook, built into the forearm of the pilot's gauntlet.
 *
 * <p>Right-clicking it equips it - into a Trinkets glove slot when Trinkets is installed, otherwise into the
 * off hand - and from then on the Grapple key fires it. The item itself does nothing when "used"; the
 * hook, the pull and the cooldown all live in the client grapple controller, like the rest of the pilot
 * movement.
 */
public class PilotGrappleItem extends Item {
	public PilotGrappleItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (Grapple.trinketsLoaded() && TrinketsCompat.equip(user, stack)) {
			return TypedActionResult.success(stack, world.isClient());
		}

		// No free trinket slot (or no Trinkets): strap it to the off-hand forearm.
		if (hand == Hand.MAIN_HAND && user.getOffHandStack().isEmpty()) {
			user.setStackInHand(Hand.OFF_HAND, stack.copyAndEmpty());
			user.playSound(SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), 1.0F, 1.0F);
			return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
		}

		return TypedActionResult.pass(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		String slot = Grapple.trinketsLoaded() ? "trinkets" : "offhand";
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_grapple.tooltip.slot." + slot).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.titanfall_movement.pilot_grapple.tooltip.use").formatted(Formatting.DARK_AQUA));
	}
}
