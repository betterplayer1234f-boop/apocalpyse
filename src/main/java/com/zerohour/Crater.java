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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Blasts a crater at ground zero once its chunks are loaded: a scorched bowl floored with slag and green
 * "trinitite" glass, ringed by flattened, burned-out trees. Works column by column, like the {@link Scorcher}.
 */
public final class Crater {
	public static final int RADIUS = 26;
	private static final int DEPTH = 12;
	/** Beyond the bowl, trees are stripped and burned out to this distance. */
	private static final int RING = 14;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
	private static final long TIME_BUDGET_NANOS = 2_000_000L;

	private static long jobChunk = Long.MIN_VALUE;
	private static int jobColumn;

	private Crater() {
	}

	public static void reset() {
		jobChunk = Long.MIN_VALUE;
	}

	static void tick(ServerLevel level, ApocalypseData d) {
		if (!d.hasStrike || !d.griefing || !d.stage.isPostStrike()) return;
		if (d.stage == Stage.DETONATION && d.stageTicks < Stage.FLASH_TICK) return;
		long deadline = System.nanoTime() + TIME_BUDGET_NANOS;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (System.nanoTime() < deadline) {
			if (jobChunk == Long.MIN_VALUE || d.cratered.contains(jobChunk)) {
				jobChunk = nextChunk(level, d);
				jobColumn = 0;
				if (jobChunk == Long.MIN_VALUE) return;
			}
			int cx = ChunkPos.getX(jobChunk);
			int cz = ChunkPos.getZ(jobChunk);
			if (level.getChunkSource().getChunkNow(cx, cz) == null) {
				jobChunk = Long.MIN_VALUE;
				return;
			}
			while (jobColumn < 256 && System.nanoTime() < deadline) {
				carve(level, d, (cx << 4) + (jobColumn & 15), (cz << 4) + (jobColumn >> 4), pos);
				jobColumn++;
			}
			if (jobColumn >= 256) {
				d.cratered.add(jobChunk);
				d.setDirty();
				jobChunk = Long.MIN_VALUE;
			}
		}
	}

	private static long nextChunk(ServerLevel level, ApocalypseData d) {
		int r = RADIUS + RING;
		for (int cx = (d.strikeX - r) >> 4; cx <= (d.strikeX + r) >> 4; cx++) {
			for (int cz = (d.strikeZ - r) >> 4; cz <= (d.strikeZ + r) >> 4; cz++) {
				long key = ChunkPos.asLong(cx, cz);
				if (!d.cratered.contains(key) && level.getChunkSource().getChunkNow(cx, cz) != null) return key;
			}
		}
		return Long.MIN_VALUE;
	}

	private static boolean isDebris(BlockState state) {
		return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || Scorcher.isPlant(state) || state.is(ModBlocks.FALLOUT);
	}

	private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) == null && state.getDestroySpeed(level, pos) >= 0;
	}

	private static void carve(ServerLevel level, ApocalypseData d, int x, int z, BlockPos.MutableBlockPos pos) {
		double dx = x - d.strikeX;
		double dz = z - d.strikeZ;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > RADIUS + RING) return;
		RandomSource r = ApocalypseManager.RANDOM;
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

		// Strip whatever stands on the ground (trees, plants, fallout).
		int y = top;
		for (int i = 0; i < 40 && y > level.getMinY(); i++, y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (!isDebris(state)) break;
			if (state.isAir()) continue;
			BlockState replacement = Blocks.AIR.defaultBlockState();
			if (state.is(BlockTags.LOGS) && dist > RADIUS && r.nextFloat() < 0.35f) {
				// A few blackened stumps survive out in the ring.
				replacement = ModBlocks.CHARRED_LOG.defaultBlockState();
				if (state.hasProperty(RotatedPillarBlock.AXIS)) replacement = replacement.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
			}
			level.setBlock(pos, replacement, FLAGS);
		}
		if (dist > RADIUS) return;

		pos.set(x, y, z);
		// Leave lakes and the sea alone.
		if (!level.getFluidState(pos).isEmpty()) return;
		float k = (float) (dist / RADIUS);
		int depth = Math.round(DEPTH * (1f - k * k)) + 1;
		int floor = y - depth;
		for (int yy = y; yy > floor && yy > level.getMinY(); yy--) {
			pos.set(x, yy, z);
			BlockState state = level.getBlockState(pos);
			if (!level.getFluidState(pos).isEmpty() || !breakable(level, pos, state)) return;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		}
		pos.set(x, floor, z);
		BlockState under = level.getBlockState(pos);
		if (floor <= level.getMinY() || !breakable(level, pos, under)) return;
		float roll = r.nextFloat();
		// Sand fused into green glass near the centre, slag and basalt further out.
		float glass = 0.45f * (1f - k);
		Block block = roll < glass ? Blocks.GREEN_STAINED_GLASS
			: roll < glass + 0.3f ? Blocks.BLACKSTONE
			: roll < glass + 0.5f ? Blocks.BASALT
			: roll < glass + 0.62f ? Blocks.MAGMA_BLOCK
			: roll < glass + 0.7f ? Blocks.OBSIDIAN
			: Blocks.TUFF;
		level.setBlock(pos, block.defaultBlockState(), FLAGS);
	}
}
