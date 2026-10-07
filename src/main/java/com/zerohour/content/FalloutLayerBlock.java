package com.zerohour.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SnowLayerBlock;

/** Radioactive fallout: behaves like snow layers (stacking, falling apart without support) but never melts. */
public class FalloutLayerBlock extends SnowLayerBlock {
	public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(FalloutLayerBlock::new);

	public FalloutLayerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<SnowLayerBlock> codec() {
		return CODEC;
	}
}
