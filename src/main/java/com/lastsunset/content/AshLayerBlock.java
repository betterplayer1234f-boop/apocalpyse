package com.lastsunset.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SnowLayerBlock;

/** Behaves like snow layers (stacking, falling apart without support) but never melts. */
public class AshLayerBlock extends SnowLayerBlock {
	public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(AshLayerBlock::new);

	public AshLayerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<SnowLayerBlock> codec() {
		return CODEC;
	}
}
