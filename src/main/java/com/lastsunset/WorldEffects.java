package com.lastsunset;

import com.lastsunset.content.ModBlocks;
import com.lastsunset.network.CuePayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Everything the dying world does to itself, stage by stage. */
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
			case OMENS -> {
				if (chance(2400)) quake(level, 0.25f, 80, 0f, grief);
				if (chance(260)) panicAnimals(level, players);
				if (level.isDarkOutside() && chance(260)) streak(level, players);
			}
			case EVACUATION -> {
				if (chance(1400)) quake(level, 0.45f, 120, 0f, grief);
				if (data.meteors && chance(900)) meteor(level, players, 0.8f, grief);
				if (chance(160)) panicAnimals(level, players);
				if (level.isDarkOutside() && chance(400)) streak(level, players);
				if (grief) decay(level, players, 1, 0.3f);
				supplyDrops(level, data, players);
			}
			case COLLAPSE -> {
				if (chance(700)) quake(level, 0.7f, 160, 0.35f, grief);
				if (data.meteors && chance(360)) meteor(level, players, 1.1f, grief);
				if (chance(220)) lightning(level, players, grief);
				if (chance(200)) panicAnimals(level, players);
				if (grief) {
					decay(level, players, 3, 0.6f);
					if (chance(60)) surfaceFires(level, players, 1);
					if (time % 6 == 0) ashfall(level, players, 1, 2);
				}
				if (time % 40 == 0) heat(level, players, false);
			}
			case FINAL_MINUTES -> {
				// The last eight seconds are silent: the world holds its breath.
				if (data.ticksRemaining() > 160) {
					if (chance(300)) quake(level, 0.9f, 200, 0.5f, grief);
					if (data.meteors && chance(140)) meteor(level, players, 1.6f, grief);
					if (chance(90)) lightning(level, players, grief);
					if (grief) {
						decay(level, players, 5, 0.8f);
						if (chance(30)) surfaceFires(level, players, 1);
						ashfall(level, players, 1, 3);
					}
					if (time % 20 == 0) heat(level, players, true);
				}
			}
			case IMPACT -> {
				if (data.stageTicks > Stage.IMPACT_BOOM_TICK && grief) {
					Scorcher.tick(level, data, 96, 8);
					surfaceFires(level, players, 3);
					ashfall(level, players, 2, 4);
				}
			}
			case AFTERMATH -> {
				if (time % 20 == 0) firestorm(level);
				if (grief) {
					Scorcher.tick(level, data, 48, 8);
					if (chance(20)) surfaceFires(level, players, 1);
					ashfall(level, players, 3, 5);
				}
				if (chance(600)) quake(level, 0.3f, 100, 0f, false);
			}
			case WASTELAND -> {
				if (grief) {
					Scorcher.tick(level, data, 16, 6);
					if (data.stageTicks < 12000 && time % 4 == 0) ashfall(level, players, 1, 5);
				}
			}
			default -> {
			}
		}
	}

	private static ServerPlayer randomPlayer(List<ServerPlayer> players) {
		return players.get(ApocalypseManager.RANDOM.nextInt(players.size()));
	}

	// ------------------------------------------------------------------ quakes

	static void quake(ServerLevel level, float intensity, int duration, float fissureChance, boolean grief) {
		Fx.sendLevel(level, new CuePayload(CuePayload.QUAKE, intensity, duration));
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || Fx.exposedToSky(player)) continue;
			// Underground: dust and loose rock rain from the ceiling.
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
		if (grief && fissureChance > 0 && r.nextFloat() < fissureChance) {
			List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
			if (!players.isEmpty()) fissure(level, randomPlayer(players));
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

	static void fissure(ServerLevel level, ServerPlayer near) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 18 + r.nextDouble() * 24;
		double sx = near.getX() + Math.cos(angle) * dist;
		double sz = near.getZ() + Math.sin(angle) * dist;
		double dir = r.nextDouble() * Math.PI * 2;
		ApocalypseManager.addTask(new Fissure(level, sx, sz, Math.cos(dir), Math.sin(dir), 14 + r.nextInt(18)));
	}

	/** The ground tears open over a couple of seconds, glowing with magma at the bottom. */
	static final class Fissure implements ServerTask {
		private final ServerLevel level;
		private final double sx;
		private final double sz;
		private final double dx;
		private final double dz;
		private final int length;
		private int step;

		Fissure(ServerLevel level, double sx, double sz, double dx, double dz, int length) {
			this.level = level;
			this.sx = sx;
			this.sz = sz;
			this.dx = dx;
			this.dz = dz;
			this.length = length;
		}

		@Override
		public boolean tick() {
			if (step >= length) return true;
			float t = (float) step / length;
			float shape = Mth.sin(t * Mth.PI);
			int width = 1 + Math.round(shape * 2);
			int depth = 3 + Math.round(shape * 9);
			double cx = sx + dx * step;
			double cz = sz + dz * step;
			for (int w = -width / 2; w <= width / 2; w++) {
				int x = Mth.floor(cx - dz * w);
				int z = Mth.floor(cz + dx * w);
				carve(x, z, depth - Math.abs(w) * 2, w == 0);
			}
			if (step % 3 == 0) {
				BlockPos top = Fx.surface(level, Mth.floor(cx), Mth.floor(cz));
				Fx.sound(level, top.getX(), top.getY(), top.getZ(), SoundEvents.DEEPSLATE_BREAK, 4f, 0.5f);
				if (step % 6 == 0) Fx.sound(level, top.getX(), top.getY(), top.getZ(), SoundEvents.GENERIC_EXPLODE, 2f, 0.4f);
			}
			step++;
			return false;
		}

		private void carve(int x, int z, int depth, boolean center) {
			if (depth <= 0) return;
			BlockPos topPos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
			if (!level.hasChunkAt(topPos)) return;
			for (ServerPlayer p : level.players()) {
				if (p.blockPosition().distSqr(topPos) < 64) return;
			}
			if (!level.getFluidState(topPos).isEmpty()) return;
			BlockState topState = level.getBlockState(topPos);
			for (int i = 0; i < depth; i++) {
				BlockPos p = topPos.below(i);
				BlockState s = level.getBlockState(p);
				if (level.getBlockEntity(p) != null || s.getDestroySpeed(level, p) < 0 || !level.getFluidState(p).isEmpty()) return;
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			BlockPos bottom = topPos.below(depth);
			if (level.getBlockEntity(bottom) == null && level.getBlockState(bottom).getDestroySpeed(level, bottom) >= 0) {
				boolean lava = center && depth >= 8 && ApocalypseManager.RANDOM.nextFloat() < 0.35f;
				level.setBlock(bottom, (lava ? Blocks.LAVA : Blocks.MAGMA_BLOCK).defaultBlockState(), Block.UPDATE_ALL);
			}
			double px = x + 0.5;
			double py = topPos.getY() + 0.5;
			double pz = z + 0.5;
			if (!topState.isAir()) Fx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, topState), px, py, pz, 20, 0.5, 0.3, 0.5, 0.1);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, px, py, pz, 4, 0.4, 0.05);
			if (center) Fx.particles(level, ParticleTypes.LAVA, px, py - 1, pz, 2, 0.3, 0.0);
		}
	}

	// ------------------------------------------------------------------ sky

	private static void meteor(ServerLevel level, List<ServerPlayer> players, float size, boolean grief) {
		if (players.isEmpty()) return;
		float s = size * (0.7f + ApocalypseManager.RANDOM.nextFloat() * 0.6f);
		Meteors.launch(level, randomPlayer(players), s, grief);
	}

	private static void streak(ServerLevel level, List<ServerPlayer> players) {
		if (!players.isEmpty()) Meteors.streak(level, randomPlayer(players));
	}

	private static void lightning(ServerLevel level, List<ServerPlayer> players, boolean grief) {
		if (players.isEmpty()) return;
		ServerPlayer near = randomPlayer(players);
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 20 + r.nextDouble() * 60;
		BlockPos pos = Fx.surface(level, Mth.floor(near.getX() + Math.cos(angle) * dist), Mth.floor(near.getZ() + Math.sin(angle) * dist));
		if (!level.hasChunkAt(pos)) return;
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) return;
		bolt.snapTo(Vec3.atBottomCenterOf(pos));
		bolt.setVisualOnly(!grief);
		level.addFreshEntity(bolt);
	}

	private static void supplyDrops(ServerLevel level, ApocalypseData data, List<ServerPlayer> players) {
		if (players.isEmpty()) return;
		float f = data.stageFraction();
		float[] marks = {0.12f, 0.45f, 0.78f};
		for (int i = 0; i < marks.length; i++) {
			if (f >= marks[i] && data.fired.add("drop" + i)) {
				data.setDirty();
				Meteors.supplyDrop(level, randomPlayer(players));
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

	private static void decay(ServerLevel level, List<ServerPlayer> players, int perPlayer, float chance) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : players) {
			for (int i = 0; i < perPlayer; i++) {
				int x = Mth.floor(player.getX()) + r.nextInt(97) - 48;
				int z = Mth.floor(player.getZ()) + r.nextInt(97) - 48;
				Scorcher.wither(level, x, z, chance);
			}
		}
	}

	/** Ash settles on open ground like grey snow, piling up to {@code maxLayers}. */
	private static void ashfall(ServerLevel level, List<ServerPlayer> players, int perPlayer, int maxLayers) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : players) {
			for (int i = 0; i < perPlayer; i++) {
				int x = Mth.floor(player.getX()) + r.nextInt(81) - 40;
				int z = Mth.floor(player.getZ()) + r.nextInt(81) - 40;
				BlockPos pos = Fx.surface(level, x, z);
				if (!level.hasChunkAt(pos)) continue;
				BlockState here = level.getBlockState(pos);
				BlockPos target = here.is(ModBlocks.ASH_LAYER) ? pos : level.getBlockState(pos.below()).is(ModBlocks.ASH_LAYER) ? pos.below() : null;
				if (target != null) {
					BlockState ash = level.getBlockState(target);
					int layers = ash.getValue(SnowLayerBlock.LAYERS);
					if (layers < maxLayers) level.setBlock(target, ash.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
				} else if (here.isAir() && level.canSeeSky(pos) && ModBlocks.ASH_LAYER.defaultBlockState().canSurvive(level, pos)) {
					level.setBlock(pos, ModBlocks.ASH_LAYER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void surfaceFires(ServerLevel level, List<ServerPlayer> players, int perPlayer) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : players) {
			for (int i = 0; i < perPlayer; i++) {
				int x = Mth.floor(player.getX()) + r.nextInt(81) - 40;
				int z = Mth.floor(player.getZ()) + r.nextInt(81) - 40;
				BlockPos pos = Fx.surface(level, x, z);
				if (!level.hasChunkAt(pos) || pos.distSqr(player.blockPosition()) < 25) continue;
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender()) {
					level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
				}
			}
		}
	}

	/** Pre-impact heat: standing in the sun becomes dangerous. */
	private static void heat(ServerLevel level, List<ServerPlayer> players, boolean severe) {
		if (!level.isBrightOutside()) return;
		for (ServerPlayer player : players) {
			if (ApocalypseManager.isObserver(player) || !Fx.exposedToSky(player)) continue;
			player.causeFoodExhaustion(severe ? 1.5f : 2.5f);
			if (severe && ApocalypseManager.RANDOM.nextFloat() < 0.12f) {
				player.igniteForSeconds(3f);
				player.displayClientMessage(Component.literal("Your skin is blistering.").withStyle(ChatFormatting.RED), true);
			} else if (level.getGameTime() % 400 == 0) {
				player.displayClientMessage(Component.literal("The sun is burning hot.").withStyle(ChatFormatting.GOLD), true);
			}
		}
	}

	/** Post-impact: anything that can see the sky burns. */
	private static void firestorm(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (ApocalypseManager.isObserver(player)) continue;
			if (Fx.exposedToSky(player)) {
				player.igniteForSeconds(4f);
				player.hurtServer(level, level.damageSources().source(LastSunset.FIRESTORM), 3f);
				player.displayClientMessage(Component.literal("The surface is burning. Get back underground.").withStyle(ChatFormatting.RED), true);
			}
			int n = 0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48),
				e -> !(e instanceof Player) && level.canSeeSky(BlockPos.containing(e.getEyePosition())))) {
				if (n++ > 50) break;
				e.igniteForSeconds(4f);
				e.hurtServer(level, level.damageSources().source(LastSunset.FIRESTORM), 3f);
			}
		}
	}
}
