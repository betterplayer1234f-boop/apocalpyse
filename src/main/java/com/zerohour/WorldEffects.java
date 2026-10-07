package com.zerohour;

import com.zerohour.content.ModBlocks;
import com.zerohour.network.CuePayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Everything the war does to the world, stage by stage. */
public final class WorldEffects {
	private WorldEffects() {
	}

	private static boolean chance(int oneIn) {
		return ApocalypseManager.RANDOM.nextInt(Math.max(1, oneIn)) == 0;
	}

	static void tick(MinecraftServer server, ServerLevel level, ApocalypseData data, Stage stage) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		boolean grief = data.griefing;
		long time = level.getGameTime();
		switch (stage) {
			case TENSIONS -> {
				if (chance(900)) jets(level, players);
				// Artillery on the front line, far enough away that it's only a tremor.
				if (chance(700)) quake(level, 0.12f, 60, false);
			}
			case OUTBREAK -> {
				if (chance(700)) jets(level, players);
				if (chance(1400)) quake(level, 0.3f, 100, grief);
				if (data.shelling && chance(1500)) shell(level, players, grief);
				if (chance(400)) panicAnimals(level, players);
				supplyDrops(level, data, players, "drop", 0.15f, 0.5f, 0.85f);
			}
			case LAUNCH -> {
				if (chance(260)) icbm(level, players);
				if (chance(500)) jets(level, players);
				if (chance(900)) quake(level, 0.45f, 120, grief);
				if (data.shelling && chance(800)) shell(level, players, grief);
				if (chance(300)) panicAnimals(level, players);
				// Other cities are hit first. At least once, everyone sees it.
				if (chance(1800) || data.stageFraction() > 0.6f && data.fired.add("cityflash")) Strikes.distantFlash(level);
				supplyDrops(level, data, players, "launchdrop", 0.3f);
				// Ash from the burning cities starts drifting in.
				if (grief && data.stageFraction() > 0.5f && time % 12 == 0) falloutfall(level, data, players, 1, 1);
			}
			case INCOMING -> {
				// The last eight seconds are silent: the world holds its breath.
				if (data.ticksRemaining() > 160) {
					if (chance(120)) icbm(level, players);
					if (chance(700)) Strikes.distantFlash(level);
					if (chance(400)) quake(level, 0.5f, 140, grief);
					if (grief && time % 8 == 0) falloutfall(level, data, players, 1, 1);
				}
			}
			case DETONATION -> {
				if (data.stageTicks > Stage.SHOCK_TICK && grief) {
					Scorcher.tick(level, data, 96, 8);
					if (data.stageTicks % 2 == 0) surfaceFires(level, data, players, 2);
					if (data.stageTicks > Stage.SHOCK_TICK + 100) falloutfall(level, data, players, 2, 3);
				}
			}
			case FALLOUT -> {
				if (grief) {
					Scorcher.tick(level, data, 48, 8);
					if (chance(30)) surfaceFires(level, data, players, 1);
					falloutfall(level, data, players, 2, 5);
				}
				// Buildings and tunnels settling after the blast.
				if (chance(900)) quake(level, 0.25f, 100, false);
			}
			case EVACUATION -> {
				if (grief) {
					Scorcher.tick(level, data, 16, 6);
					if (data.stageTicks < 12000 && time % 5 == 0) falloutfall(level, data, players, 1, 5);
				}
				if (!data.evacClosed) {
					// Rescue flights.
					if (chance(1400)) jets(level, players);
					supplyDrops(level, data, players, "evacdrop", 0.2f, 0.6f);
				}
			}
			default -> {
			}
		}
	}

	private static ServerPlayer randomPlayer(List<ServerPlayer> players) {
		return players.get(ApocalypseManager.RANDOM.nextInt(players.size()));
	}

	private static void jets(ServerLevel level, List<ServerPlayer> players) {
		if (!players.isEmpty()) Strikes.jets(level, randomPlayer(players));
	}

	private static void icbm(ServerLevel level, List<ServerPlayer> players) {
		if (!players.isEmpty()) Strikes.icbm(level, randomPlayer(players));
	}

	private static void shell(ServerLevel level, List<ServerPlayer> players, boolean grief) {
		if (!players.isEmpty()) Strikes.shell(level, randomPlayer(players), grief);
	}

	// ------------------------------------------------------------------ tremors

	/** Bombardment and collapsing buildings: the ground shakes, and underground, dust and loose rock come down. */
	static void quake(ServerLevel level, float intensity, int duration, boolean grief) {
		Fx.sendLevel(level, new CuePayload(CuePayload.TREMOR, intensity, duration));
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || Fx.exposedToSky(player)) continue;
			for (int i = 0; i < 6; i++) {
				BlockPos ceiling = findCeiling(level, player.blockPosition().offset(r.nextInt(11) - 5, 1, r.nextInt(11) - 5));
				if (ceiling == null) continue;
				BlockState state = level.getBlockState(ceiling);
				Fx.particles(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, state), ceiling.getX() + 0.5, ceiling.getY() - 0.1, ceiling.getZ() + 0.5,
					8, 0.6, 0.0, 0.6, 0.0);
				if (grief && i < 2 && r.nextFloat() < intensity * 0.5f && isLooseRock(state) && level.getBlockState(ceiling.below()).isAir()) {
					FallingBlockEntity.fall(level, ceiling, state);
				}
			}
		}
	}

	private static BlockPos findCeiling(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i < 10; i++) {
			pos.move(0, 1, 0);
			if (!level.hasChunkAt(pos)) return null;
			if (!level.getBlockState(pos).isAir()) return pos.immutable();
		}
		return null;
	}

	private static boolean isLooseRock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND)
			|| state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE);
	}

	// ------------------------------------------------------------------ supplies

	private static void supplyDrops(ServerLevel level, ApocalypseData data, List<ServerPlayer> players, String key, float... marks) {
		if (players.isEmpty()) return;
		float f = data.stageFraction();
		for (int i = 0; i < marks.length; i++) {
			if (f >= marks[i] && data.fired.add(key + i)) {
				data.setDirty();
				Strikes.supplyDrop(level, randomPlayer(players));
			}
		}
	}

	// ------------------------------------------------------------------ life

	private static void panicAnimals(ServerLevel level, List<ServerPlayer> players) {
		if (players.isEmpty()) return;
		ServerPlayer near = randomPlayer(players);
		RandomSource r = ApocalypseManager.RANDOM;
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, near.getBoundingBox().inflate(40), a -> true);
		int n = 0;
		for (Animal animal : animals) {
			if (n++ >= 10) break;
			double angle = r.nextDouble() * Math.PI * 2;
			animal.getNavigation().moveTo(animal.getX() + Math.cos(angle) * 16, animal.getY(), animal.getZ() + Math.sin(angle) * 16, 1.8);
			if (r.nextFloat() < 0.3f) animal.playAmbientSound();
		}
	}

	/** Radioactive fallout settles on open ground like grey snow, piling up to {@code maxLayers}. */
	private static void falloutfall(ServerLevel level, ApocalypseData data, List<ServerPlayer> players, int perPlayer, int maxLayers) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : players) {
			for (int i = 0; i < perPlayer; i++) {
				int x = Mth.floor(player.getX()) + r.nextInt(81) - 40;
				int z = Mth.floor(player.getZ()) + r.nextInt(81) - 40;
				if (data.inSafeZone(x, z)) continue;
				BlockPos pos = Fx.surface(level, x, z);
				if (!level.hasChunkAt(pos)) continue;
				BlockState here = level.getBlockState(pos);
				BlockPos target = here.is(ModBlocks.FALLOUT) ? pos : level.getBlockState(pos.below()).is(ModBlocks.FALLOUT) ? pos.below() : null;
				if (target != null) {
					BlockState layer = level.getBlockState(target);
					int layers = layer.getValue(SnowLayerBlock.LAYERS);
					if (layers < maxLayers) level.setBlock(target, layer.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
				} else if (here.isAir() && level.canSeeSky(pos) && ModBlocks.FALLOUT.defaultBlockState().canSurvive(level, pos)) {
					level.setBlock(pos, ModBlocks.FALLOUT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void surfaceFires(ServerLevel level, ApocalypseData data, List<ServerPlayer> players, int perPlayer) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : players) {
			for (int i = 0; i < perPlayer; i++) {
				int x = Mth.floor(player.getX()) + r.nextInt(81) - 40;
				int z = Mth.floor(player.getZ()) + r.nextInt(81) - 40;
				if (data.inSafeZone(x, z)) continue;
				BlockPos pos = Fx.surface(level, x, z);
				if (!level.hasChunkAt(pos) || pos.distSqr(player.blockPosition()) < 25) continue;
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender()) {
					level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
				}
			}
		}
	}
}
