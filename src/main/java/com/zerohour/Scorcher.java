package com.zerohour;

import com.zerohour.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The thermal pulse turns the surface into a wasteland: burnt trees, dead grass, glassed sand, fallout drifts.
 * Works chunk by chunk around players and remembers which chunks are done. The safe zone is spared.
 */
public final class Scorcher {
	/** Plain block swaps: tell clients, skip neighbour/shape updates and other side effects. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
	/** Never spend more than this per tick burning the land, however slow the server. */
	private static final long TIME_BUDGET_NANOS = 3_000_000L;

	private Scorcher() {
	}

	/** The chunk currently being burned, column by column. */
	private static long jobChunk = Long.MIN_VALUE;
	private static int jobColumn;

	/**
	 * Scorch up to {@code columns} block columns this tick, working outward from players one chunk at a time.
	 * Spreading the work per column keeps the server from stalling when a whole forest burns at once.
	 */
	public static void tick(ServerLevel level, ApocalypseData data, int columns, int radius) {
		long deadline = System.nanoTime() + TIME_BUDGET_NANOS;
		while (columns > 0 && System.nanoTime() < deadline) {
			if (jobChunk == Long.MIN_VALUE || data.scorched.contains(jobChunk)) {
				jobChunk = findChunk(level, data, radius);
				jobColumn = 0;
				if (jobChunk == Long.MIN_VALUE) return;
			}
			int cx = ChunkPos.getX(jobChunk);
			int cz = ChunkPos.getZ(jobChunk);
			if (level.getChunkSource().getChunkNow(cx, cz) == null) {
				jobChunk = Long.MIN_VALUE;
				continue;
			}
			RandomSource r = ApocalypseManager.RANDOM;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (columns > 0 && jobColumn < 256 && System.nanoTime() < deadline) {
				scorchColumn(level, (cx << 4) + (jobColumn & 15), (cz << 4) + (jobColumn >> 4), pos, r);
				jobColumn++;
				columns--;
			}
			if (jobColumn >= 256) {
				data.scorched.add(jobChunk);
				jobChunk = Long.MIN_VALUE;
			}
		}
	}

	public static void reset() {
		jobChunk = Long.MIN_VALUE;
	}

	/** Nearest loaded, unscorched chunk around a random player (spiralling outward), or Long.MIN_VALUE. */
	private static long findChunk(ServerLevel level, ApocalypseData data, int radius) {
		var players = level.players();
		if (players.isEmpty()) return Long.MIN_VALUE;
		int start = ApocalypseManager.RANDOM.nextInt(players.size());
		for (int i = 0; i < players.size(); i++) {
			ChunkPos center = players.get((start + i) % players.size()).chunkPosition();
			for (int ring = 0; ring <= radius; ring++) {
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
						long key = ChunkPos.asLong(center.x + dx, center.z + dz);
						if (data.scorched.contains(key) || level.getChunkSource().getChunkNow(center.x + dx, center.z + dz) == null) continue;
						if (data.inSafeZone(((center.x + dx) << 4) + 8, ((center.z + dz) << 4) + 8)) {
							// Never burned; remember it so we don't look at it again.
							data.scorched.add(key);
							continue;
						}
						return key;
					}
				}
			}
		}
		return Long.MIN_VALUE;
	}

	private static void scorchColumn(ServerLevel level, int wx, int wz, BlockPos.MutableBlockPos pos, RandomSource r) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
		for (int y = top; y > top - 32 && y > level.getMinY(); y--) {
			pos.set(wx, y, wz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) continue;
			if (state.is(BlockTags.LEAVES)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
				continue;
			}
			if (state.is(BlockTags.LOGS)) {
				BlockState charred = ModBlocks.CHARRED_LOG.defaultBlockState();
				if (state.hasProperty(RotatedPillarBlock.AXIS)) charred = charred.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
				level.setBlock(pos, charred, FLAGS);
				continue;
			}
			if (isPlant(state)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
				continue;
			}
			if (!level.getFluidState(pos).isEmpty()) break;
			scorchGround(level, pos, state, r);
			break;
		}
	}

	static boolean isPlant(BlockState state) {
		Block b = state.getBlock();
		return b instanceof VegetationBlock || b instanceof VineBlock || b == Blocks.SNOW
			|| b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO || state.is(BlockTags.CROPS);
	}

	private static void scorchGround(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState state, RandomSource r) {
		Block b = state.getBlock();
		BlockState result = null;
		if (b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.MYCELIUM || b == Blocks.MOSS_BLOCK || b == Blocks.FARMLAND) {
			float f = r.nextFloat();
			result = (f < 0.55f ? ModBlocks.SCORCHED_SOIL : f < 0.9f ? Blocks.COARSE_DIRT : f < 0.97f ? Blocks.DIRT : Blocks.MAGMA_BLOCK).defaultBlockState();
		} else if (b == Blocks.SAND) {
			if (r.nextFloat() < 0.18f) result = Blocks.GLASS.defaultBlockState();
		} else if (b == Blocks.RED_SAND) {
			if (r.nextFloat() < 0.18f) result = Blocks.TERRACOTTA.defaultBlockState();
		} else if (b == Blocks.ICE || b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW) {
			result = Blocks.AIR.defaultBlockState();
		} else if (state.ignitedByLava() && level.getBlockEntity(pos) == null) {
			// Wooden roofs, wool, hay: it all goes up in flames.
			result = r.nextFloat() < 0.6f ? Blocks.FIRE.defaultBlockState() : null;
		}
		// Fire needs its normal placement update so it actually burns (and burns out).
		if (result != null) level.setBlock(pos, result, result.is(Blocks.FIRE) ? Block.UPDATE_ALL : FLAGS);
		if (result == null || !result.isAir()) {
			BlockPos above = pos.above();
			if (level.getBlockState(above).isAir() && level.getBlockState(pos).isSolidRender()) {
				float roll = r.nextFloat();
				if (roll < 0.03f) {
					level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
				} else if (roll < 0.33f) {
					// A drift of fallout where the flames passed.
					level.setBlock(above, ModBlocks.FALLOUT.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r.nextInt(2)), FLAGS);
				}
			}
		}
	}
}
