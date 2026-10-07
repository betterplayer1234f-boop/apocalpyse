package com.zerohour.content;

import com.zerohour.ZeroHour;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The gas mask and hazmat suit: barely armour, but they keep the fallout off your skin and out of your lungs. */
public final class ModArmor {
	/** Worn textures: assets/zerohour/equipment/hazmat.json. */
	public static final ResourceKey<EquipmentAsset> HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, ZeroHour.id("hazmat"));

	public static final ArmorMaterial HAZMAT = new ArmorMaterial(
		8,
		Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 3, ArmorType.HELMET, 1, ArmorType.BODY, 3),
		10,
		SoundEvents.ARMOR_EQUIP_LEATHER,
		0.0F,
		0.0F,
		ItemTags.REPAIRS_LEATHER_ARMOR,
		HAZMAT_ASSET
	);

	private ModArmor() {
	}
}
