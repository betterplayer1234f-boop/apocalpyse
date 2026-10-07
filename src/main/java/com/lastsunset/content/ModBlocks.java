package com.lastsunset.content;

import com.lastsunset.LastSunset;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	/** Grey ash that drifts down and piles up on the surface, like snow that never melts. */
	public static final Block ASH_LAYER = register("ash_layer", AshLayerBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_GRAY)
		.replaceable()
		.forceSolidOff()
		.strength(0.1F)
		.sound(SoundType.SAND)
		.isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
		.pushReaction(PushReaction.DESTROY));

	/** What is left of a tree after the flare: charcoal with embers still glowing in the cracks. */
	public static final Block CHARRED_LOG = register("charred_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(1.5F)
		.sound(SoundType.BASALT));

	public static final Block SCORCHED_SOIL = register("scorched_soil", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BROWN)
		.strength(0.6F)
		.sound(SoundType.ROOTED_DIRT));

	/** Glowing crystal from the heart of a meteorite. */
	public static final Block SOLARITE_ORE = register("solarite_ore", props -> new DropExperienceBlock(UniformInt.of(3, 7), props),
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE)
			.strength(4.5F, 9.0F)
			.requiresCorrectToolForDrops()
			.sound(SoundType.DEEPSLATE)
			.lightLevel(state -> 9));

	/** Obsidian plating laced with solarite: the densest shelter material there is. */
	public static final Block FLARE_SHIELDING = register("flare_shielding", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.METAL)
		.strength(30.0F, 1200.0F)
		.requiresCorrectToolForDrops()
		.sound(SoundType.NETHERITE_BLOCK));

	public static final Block EMERGENCY_RADIO = register("emergency_radio", RadioBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_GREEN)
		.strength(1.0F)
		.sound(SoundType.LANTERN)
		.noOcclusion());

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return Blocks.register(ResourceKey.create(Registries.BLOCK, LastSunset.id(name)), factory, properties);
	}

	public static void init() {
	}
}
