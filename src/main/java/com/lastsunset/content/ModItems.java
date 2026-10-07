package com.lastsunset.content;

import com.lastsunset.LastSunset;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	public static final Item HELIOMETER = register("heliometer", HeliometerItem::new, new Item.Properties()
		.stacksTo(1)
		.component(DataComponents.LORE, lore("Measures the sun's output. Right-click for a reading.")));
	public static final Item FLARE_GUN = register("flare_gun", FlareGunItem::new, new Item.Properties()
		.durability(16)
		.component(DataComponents.LORE, lore("Fires a red signal flare high into the sky.")));
	public static final Item SOLARITE_SHARD = register("solarite_shard", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item ASH = register("ash", Item::new, new Item.Properties());
	public static final Item EMERGENCY_RATION = register("emergency_ration", Item::new, new Item.Properties()
		.food(new FoodProperties(7, 0.9F, false)));

	public static final Item EMERGENCY_RADIO = block(ModBlocks.EMERGENCY_RADIO, RadioItem::new, new Item.Properties()
		.component(DataComponents.LORE, lore("Carry it, or stay near it, to hear emergency broadcasts.")));
	public static final Item ASH_LAYER = block(ModBlocks.ASH_LAYER, BlockItem::new, new Item.Properties());
	public static final Item CHARRED_LOG = block(ModBlocks.CHARRED_LOG, BlockItem::new, new Item.Properties());
	public static final Item SCORCHED_SOIL = block(ModBlocks.SCORCHED_SOIL, BlockItem::new, new Item.Properties());
	public static final Item SOLARITE_ORE = block(ModBlocks.SOLARITE_ORE, BlockItem::new, new Item.Properties());
	public static final Item FLARE_SHIELDING = block(ModBlocks.FLARE_SHIELDING, BlockItem::new, new Item.Properties()
		.component(DataComponents.LORE, lore("Worth eight blocks of stone in a shelter roof.")));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, LastSunset.id("last_sunset"),
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.lastsunset"))
			.icon(() -> new ItemStack(HELIOMETER))
			.displayItems((params, output) -> {
				output.accept(EMERGENCY_RADIO);
				output.accept(HELIOMETER);
				output.accept(FLARE_GUN);
				output.accept(EMERGENCY_RATION);
				output.accept(SOLARITE_SHARD);
				output.accept(ASH);
				output.accept(SOLARITE_ORE);
				output.accept(FLARE_SHIELDING);
				output.accept(ASH_LAYER);
				output.accept(CHARRED_LOG);
				output.accept(SCORCHED_SOIL);
			})
			.build());

	private ModItems() {
	}

	private static ItemLore lore(String text) {
		return new ItemLore(List.of(Component.literal(text).withStyle(ChatFormatting.GRAY)));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		return Items.registerItem(ResourceKey.create(Registries.ITEM, LastSunset.id(name)), factory, properties);
	}

	private static Item block(Block block, java.util.function.BiFunction<Block, Item.Properties, Item> factory, Item.Properties properties) {
		return Items.registerBlock(block, factory, properties);
	}

	public static void init() {
	}
}
