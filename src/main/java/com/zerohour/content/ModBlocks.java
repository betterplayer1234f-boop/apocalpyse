package com.zerohour.content;

import com.zerohour.ZeroHour;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	/** Grey-green radioactive dust that settles on the surface after the bomb, like snow that never melts. */
	public static final Block FALLOUT = register("fallout", FalloutLayerBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_GRAY)
		.replaceable()
		.forceSolidOff()
		.strength(0.1F)
		.sound(SoundType.SAND)
		.isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
		.pushReaction(PushReaction.DESTROY));

	/** What is left of a tree after the thermal pulse: charcoal with embers still glowing in the cracks. */
	public static final Block CHARRED_LOG = register("charred_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK)
		.strength(1.5F)
		.sound(SoundType.BASALT));

	public static final Block SCORCHED_SOIL = register("scorched_soil", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BROWN)
		.strength(0.6F)
		.sound(SoundType.ROOTED_DIRT));

	/** Steel-reinforced concrete with a lead core: the densest shelter material there is. */
	public static final Block BLAST_SHIELDING = register("blast_shielding", Block::new, BlockBehaviour.Properties.of()
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
		return Blocks.register(ResourceKey.create(Registries.BLOCK, ZeroHour.id(name)), factory, properties);
	}

	public static void init() {
	}
}
