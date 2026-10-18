package com.loaffie.titanfall.item;

import com.loaffie.titanfall.TitanfallMovement;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class TitanfallItems {
	/**
	 * Titanfall 2 gravity while the kit is worn: {@code sv_gravity} 750 u/s^2 x the pilot's gravity scale 0.8
	 * = 600 u/s^2 = 15.24 blocks/s^2, against Minecraft's 0.08 blocks/tick^2 = 32 blocks/s^2. The kit scales
	 * the player's gravity attribute by 15.24 / 32 = 0.476, so 0.08 becomes 0.0381 blocks/tick^2.
	 */
	public static final double GRAVITY_SCALE = 15.24 / 32.0;
	/**
	 * Jump impulse with the kit, blocks/tick. Titanfall's datamined pilot jump height is 60 u = 1.52 blocks;
	 * under the kit's gravity (and vanilla's 0.98 air drag) an impulse of 0.337 b/t peaks at 1.52 blocks
	 * and takes 18 ticks to land, against vanilla's 1.25 blocks and 12 ticks.
	 */
	public static final double JUMP_VELOCITY = 0.337;
	private static final double VANILLA_JUMP_VELOCITY = 0.42;

	/**
	 * Single stack, rare, survives lava like a netherite piece: losing your jumpkit to a fall into lava is no
	 * fun. Wearing it lowers gravity and retunes the jump through attribute modifiers on the chest slot, so
	 * the floatier Titanfall arc applies on the server and the client alike, in every movement state.
	 */
	public static final Item PILOT_JUMPKIT = register("pilot_jumpkit",
			new PilotJumpkitItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE).fireproof().attributeModifiers(jumpkitAttributes())));

	private TitanfallItems() {
	}

	private static AttributeModifiersComponent jumpkitAttributes() {
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_GRAVITY,
						new EntityAttributeModifier(Identifier.of(TitanfallMovement.MOD_ID, "jumpkit_gravity"), GRAVITY_SCALE - 1.0,
								EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
						AttributeModifierSlot.CHEST)
				.add(EntityAttributes.GENERIC_JUMP_STRENGTH,
						new EntityAttributeModifier(Identifier.of(TitanfallMovement.MOD_ID, "jumpkit_jump"), JUMP_VELOCITY - VANILLA_JUMP_VELOCITY,
								EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.CHEST)
				.build();
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(TitanfallMovement.MOD_ID, name), item);
	}

	/**
	 * Forces class loading (and so registration) from the mod initializer, and lists the kit in the Combat
	 * tab. The crafting recipe is data-driven: {@code data/titanfall_movement/recipe/pilot_jumpkit.json}
	 * resolves its result by this item's registry id, {@code titanfall_movement:pilot_jumpkit}.
	 */
	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(PILOT_JUMPKIT));
	}
}
