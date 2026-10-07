package com.zerohour.content;

import com.zerohour.ZeroHour;
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
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	public static final Item GEIGER_COUNTER = register("geiger_counter", GeigerCounterItem::new, new Item.Properties()
		.stacksTo(1)
		.component(DataComponents.LORE, lore("Clicks faster the more radiation there is. Right-click for a reading.")));
	public static final Item FLARE_GUN = register("flare_gun", FlareGunItem::new, new Item.Properties()
		.durability(16)
		.component(DataComponents.LORE, lore("Fires a red signal flare high into the sky.")));
	public static final Item IODINE_TABLETS = register("iodine_tablets", p -> new MedicineItem(MedicineItem.Kind.IODINE, p), new Item.Properties()
		.stacksTo(16)
		.component(DataComponents.LORE, lore("Halves the radiation you take in, for five minutes.")));
	public static final Item ANTI_RAD_SERUM = register("anti_rad_serum", p -> new MedicineItem(MedicineItem.Kind.ANTI_RAD, p), new Item.Properties()
		.stacksTo(8)
		.component(DataComponents.LORE, lore("Flushes 300 rad of radiation out of your body.")));
	public static final Item ANTIVIRAL = register("antiviral", p -> new MedicineItem(MedicineItem.Kind.ANTIVIRAL, p), new Item.Properties()
		.stacksTo(8)
		.rarity(Rarity.UNCOMMON)
		.component(DataComponents.LORE, lore("Stops the infection, if it isn't too late.")));
	public static final Item GAS_MASK = register("gas_mask", Item::new, new Item.Properties()
		.humanoidArmor(ModArmor.HAZMAT, ArmorType.HELMET)
		.component(DataComponents.LORE, lore("Stops 25% of the radiation. A full hazmat outfit stops 80%.")));
	public static final Item HAZMAT_SUIT = register("hazmat_suit", Item::new, new Item.Properties()
		.humanoidArmor(ModArmor.HAZMAT, ArmorType.CHESTPLATE)
		.component(DataComponents.LORE, lore("Stops 30% of the radiation, and most bites.")));
	public static final Item HAZMAT_TROUSERS = register("hazmat_trousers", Item::new, new Item.Properties()
		.humanoidArmor(ModArmor.HAZMAT, ArmorType.LEGGINGS)
		.component(DataComponents.LORE, lore("Stops 15% of the radiation.")));
	public static final Item HAZMAT_BOOTS = register("hazmat_boots", Item::new, new Item.Properties()
		.humanoidArmor(ModArmor.HAZMAT, ArmorType.BOOTS)
		.component(DataComponents.LORE, lore("Stops 10% of the radiation.")));
	public static final Item FALLOUT_DUST = register("fallout_dust", Item::new, new Item.Properties()
		.component(DataComponents.LORE, lore("Faintly warm.")));
	public static final Item EMERGENCY_RATION = register("emergency_ration", Item::new, new Item.Properties()
		.food(new FoodProperties(7, 0.9F, false)));

	public static final Item EMERGENCY_RADIO = block(ModBlocks.EMERGENCY_RADIO, RadioItem::new, new Item.Properties()
		.component(DataComponents.LORE, lore("Carry it, or stay near it, to hear emergency broadcasts.")));
	public static final Item FALLOUT = block(ModBlocks.FALLOUT, BlockItem::new, new Item.Properties());
	public static final Item CHARRED_LOG = block(ModBlocks.CHARRED_LOG, BlockItem::new, new Item.Properties());
	public static final Item SCORCHED_SOIL = block(ModBlocks.SCORCHED_SOIL, BlockItem::new, new Item.Properties());
	public static final Item BLAST_SHIELDING = block(ModBlocks.BLAST_SHIELDING, BlockItem::new, new Item.Properties()
		.component(DataComponents.LORE, lore("Worth eight blocks of stone in a shelter roof.")));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ZeroHour.id("zero_hour"),
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.zerohour"))
			.icon(() -> new ItemStack(GEIGER_COUNTER))
			.displayItems((params, output) -> {
				output.accept(EMERGENCY_RADIO);
				output.accept(GEIGER_COUNTER);
				output.accept(FLARE_GUN);
				output.accept(GAS_MASK);
				output.accept(HAZMAT_SUIT);
				output.accept(HAZMAT_TROUSERS);
				output.accept(HAZMAT_BOOTS);
				output.accept(IODINE_TABLETS);
				output.accept(ANTI_RAD_SERUM);
				output.accept(ANTIVIRAL);
				output.accept(EMERGENCY_RATION);
				output.accept(BLAST_SHIELDING);
				output.accept(FALLOUT_DUST);
				output.accept(FALLOUT);
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
		return Items.registerItem(ResourceKey.create(Registries.ITEM, ZeroHour.id(name)), factory, properties);
	}

	private static Item block(Block block, java.util.function.BiFunction<Block, Item.Properties, Item> factory, Item.Properties properties) {
		return Items.registerBlock(block, factory, properties);
	}

	public static void init() {
	}
}
