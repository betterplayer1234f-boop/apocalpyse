package com.zerohour;

import com.zerohour.network.CuePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The moment the bomb goes off: who is exposed burns, who is sheltered holds on, and a mushroom cloud rises over ground zero. */
public final class Detonation {
	private Detonation() {
	}

	static void begin(MinecraftServer server, ApocalypseData data) {
		ServerLevel level = server.overworld();
		chooseGroundZero(level, data);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float status;
			if (data.fallen.contains(player.getUUID()) || ApocalypseManager.isObserver(player) || player.level().dimension() != Level.OVERWORLD) {
				status = 3;
			} else {
				status = ApocalypseManager.shelterOf(player).status().ordinal();
			}
			Fx.send(player, new CuePayload(CuePayload.DETONATION, status, Stage.DETONATION_TICKS, data.strikeX + 0.5, data.strikeY, data.strikeZ + 0.5));
		}
		ApocalypseManager.addTask(new Warhead(level, new Vec3(data.strikeX + 0.5, data.strikeY + 30, data.strikeZ + 0.5)));
	}

	/** Ground zero: a few hundred metres from where the survivors are, in a random direction. */
	private static void chooseGroundZero(ServerLevel level, ApocalypseData data) {
		double sx = 0;
		double sz = 0;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) continue;
			sx += p.getX();
			sz += p.getZ();
			n++;
		}
		if (n == 0) {
			for (ServerPlayer p : level.players()) {
				sx += p.getX();
				sz += p.getZ();
				n++;
			}
		}
		double cx = n > 0 ? sx / n : 0;
		double cz = n > 0 ? sz / n : 0;
		double angle = ApocalypseManager.RANDOM.nextDouble() * Math.PI * 2;
		int x = Mth.floor(cx + Math.cos(angle) * data.strikeDistance);
		int z = Mth.floor(cz + Math.sin(angle) * data.strikeDistance);
		data.strikeX = x;
		data.strikeZ = z;
		data.strikeY = level.hasChunkAt(new BlockPos(x, 0, z)) ? Fx.surface(level, x, z).getY() : level.getSeaLevel();
		data.hasStrike = true;
		data.postTicks = 0;
		data.setDirty();
	}

	static void tick(MinecraftServer server, ApocalypseData data, int t) {
		// Checked with >= so that skipping ahead never skips the judgement.
		if (t >= Stage.FLASH_TICK && data.fired.add("flash")) flash(server, data);
		if (t >= Stage.SHOCK_TICK && data.fired.add("shock")) shock(server, data);
	}

	/** The flash. Everyone is judged by where they stand right now. */
	private static void flash(MinecraftServer server, ApocalypseData data) {
		ServerLevel overworld = server.overworld();
		if (overworld.hasChunkAt(new BlockPos(data.strikeX, 0, data.strikeZ))) {
			data.strikeY = Fx.surface(overworld, data.strikeX, data.strikeZ).getY();
		}
		ApocalypseManager.addTask(new MushroomCloud(overworld, data.strikeX + 0.5, data.strikeY, data.strikeZ + 0.5));
		Fx.sound(overworld, data.strikeX, data.strikeY + 20, data.strikeZ, SoundEvents.GENERIC_EXPLODE, 16f, 0.3f);

		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (ApocalypseManager.isObserver(player) || data.fallen.contains(player.getUUID()) || !player.isAlive()) continue;
			if (player.level().dimension() != Level.OVERWORLD) continue;
			Shelter.Result result = ApocalypseManager.shelterOf(player);
			switch (result.status()) {
				case EXPOSED -> ApocalypseManager.addTask(new Vaporize(player, data.griefing, ApocalypseManager.RANDOM.nextInt(40)));
				case PARTIAL -> breach(player, result, data);
				case SAFE -> player.displayClientMessage(
					Component.literal("The ground heaves... but your shelter holds.").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), true
				);
			}
		}
		for (ServerPlayer player : overworld.players()) {
			vaporizeMobs(overworld, player, 96);
		}
		ApocalypseManager.syncAll();
	}

	/** The blast wave rolls over the survivors' homes: every window facing the outside shatters. */
	private static void shock(MinecraftServer server, ApocalypseData data) {
		if (!data.griefing) return;
		ServerLevel overworld = server.overworld();
		for (ServerPlayer player : overworld.players()) {
			ApocalypseManager.addTask(new Shatter(overworld, player.blockPosition()));
		}
	}

	private static void breach(ServerPlayer player, Shelter.Result result, ApocalypseData data) {
		float leak = 1f - result.rating();
		Fx.send(player, new CuePayload(CuePayload.BREACH, leak, 80));
		player.displayClientMessage(Component.literal("Your shelter is caving in — the blast is tearing through!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		player.igniteForSeconds(2f + leak * 6f);
		ServerLevel level = player.level();
		player.hurtServer(level, level.damageSources().source(ZeroHour.SHELTER_BREACH), leak * 40f);
		// The initial burst of radiation gets through a thin roof too.
		Radiation.addDose(player, data, leak * 350f);
	}

	private static void vaporizeMobs(ServerLevel level, ServerPlayer center, double radius) {
		AABB box = center.getBoundingBox().inflate(radius, 160, radius);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
			e -> !(e instanceof Player) && e.isAlive() && level.canSeeSky(BlockPos.containing(e.getEyePosition())));
		if (!victims.isEmpty()) ApocalypseManager.addTask(new BurnMobs(level, new ArrayList<>(victims)));
	}

	/** Exposed animals and infected burn a few at a time, so the blast never stalls the server. */
	static final class BurnMobs implements ServerTask {
		private static final long TIME_BUDGET_NANOS = 2_000_000L;
		private final ServerLevel level;
		private final List<LivingEntity> victims;
		private int index;

		BurnMobs(ServerLevel level, List<LivingEntity> victims) {
			this.level = level;
			this.victims = victims;
		}

		@Override
		public boolean tick() {
			long deadline = System.nanoTime() + TIME_BUDGET_NANOS;
			while (index < victims.size() && System.nanoTime() < deadline) {
				LivingEntity e = victims.get(index++);
				if (!e.isAlive()) continue;
				if (index < 40) {
					Fx.particles(level, ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.4, 0.05);
					Fx.particles(level, ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 6, 0.3, 0.05);
				}
				e.igniteForSeconds(5f);
				e.hurtServer(level, level.damageSources().source(ZeroHour.NUCLEAR_BLAST), Float.MAX_VALUE);
			}
			return index >= victims.size();
		}
	}

	/** Called when the fallout starts: count who made it. */
	static void summarize(MinecraftServer server, ApocalypseData data) {
		List<String> survivors = new ArrayList<>();
		int total = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || (player.isSpectator() && !data.fallen.contains(player.getUUID()))) continue;
			total++;
			if (player.isAlive() && !player.isSpectator() && !data.fallen.contains(player.getUUID())) {
				survivors.add(player.getName().getString());
			}
		}
		data.survivorCount = survivors.size();
		data.victimCount = total - survivors.size();
		data.setDirty();
		var list = server.getPlayerList();
		if (total > 0 && survivors.isEmpty()) {
			list.broadcastSystemMessage(Component.literal("☢ THE BOMB HAS FALLEN. ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal("No one survived the blast.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), false);
		} else {
			list.broadcastSystemMessage(Component.literal("☢ THE BOMB HAS FALLEN. ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal("Survivors (" + survivors.size() + "/" + total + "): " + String.join(", ", survivors)).withStyle(ChatFormatting.GOLD)), false);
		}
	}

	/** The warhead's last seconds: a white-hot streak falling out of the sky toward ground zero. */
	static final class Warhead implements ServerTask {
		private final ServerLevel level;
		private final Vec3 start;
		private final Vec3 end;
		private int age;

		Warhead(ServerLevel level, Vec3 burst) {
			this.level = level;
			double from = ApocalypseManager.RANDOM.nextDouble() * Math.PI * 2;
			this.end = burst;
			this.start = burst.add(Math.cos(from) * 90, 260, Math.sin(from) * 90);
		}

		@Override
		public boolean tick() {
			age++;
			if (age > Stage.FLASH_TICK) return true;
			Vec3 pos = start.lerp(end, age / (double) Stage.FLASH_TICK);
			Fx.particles(level, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, 0.2, 0.0);
			Fx.particles(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.3, 0.01);
			Fx.particles(level, ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 3, 0.4, 0.01);
			if (age % 3 == 0) Fx.particles(level, ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 2, 0.1, 0.02);
			return false;
		}
	}

	/** Fireball, then a column of smoke and a spreading cap: the mushroom cloud, visible from far away. */
	static final class MushroomCloud implements ServerTask {
		private static final int LIFE = 900;
		private final ServerLevel level;
		private final double x;
		private final double y;
		private final double z;
		private int age;

		MushroomCloud(ServerLevel level, double x, double y, double z) {
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public boolean tick() {
			age++;
			if (age > LIFE) return true;
			double rise = Math.min(1.0, age / 400.0);
			double capY = y + 18 + 92 * (1 - (1 - rise) * (1 - rise));
			double capR = 6 + 28 * Math.min(1.0, age / 500.0);
			double stemTop = capY - capR * 0.3;

			if (age <= 70) {
				// The fireball, climbing and swelling.
				double fr = 3 + age * 0.22;
				double fy = y + 10 + age * 0.35;
				Fx.particles(level, ParticleTypes.FLAME, x, fy, z, 30, fr, fr, fr, 0.02);
				Fx.particles(level, ParticleTypes.LAVA, x, fy, z, 6, fr, fr, fr, 0.0);
				if (age % 3 == 0) Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, x, fy, z, 2, fr * 0.6, fr * 0.6, fr * 0.6, 0.0);
			}
			if (age % 2 == 0 && age < 640) {
				double h = stemTop - y;
				double w = 2.5 + capR * 0.08;
				Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + h * 0.5, z, 8, w, h * 0.3, w, 0.01);
				if (age < 200) Fx.particles(level, ParticleTypes.FLAME, x, y + h * 0.4, z, 4, 1.5, h * 0.25, 1.5, 0.01);
			}
			if (age > 15 && age < 720) {
				Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, capY, z, 16, capR * 0.42, capR * 0.16, capR * 0.42, 0.006);
				if (age % 3 == 0) Fx.particles(level, ParticleTypes.LARGE_SMOKE, x, capY - capR * 0.15, z, 10, capR * 0.4, capR * 0.12, capR * 0.4, 0.01);
				if (age < 260) Fx.particles(level, ParticleTypes.FLAME, x, capY - capR * 0.1, z, 6, capR * 0.25, capR * 0.08, capR * 0.25, 0.0);
			}
			if (age < 160 && age % 2 == 0) {
				// The base surge: a ring of dust racing outward along the ground.
				double ring = 6 + age * 0.45;
				for (int i = 0; i < 12; i++) {
					double a = i / 12.0 * Math.PI * 2 + age * 0.05;
					Fx.particles(level, ParticleTypes.CLOUD, x + Math.cos(a) * ring, y + 1.5, z + Math.sin(a) * ring, 2, 1.5, 0.6, 1.5, 0.02);
				}
			}
			return false;
		}
	}

	/** Glass that daylight reaches is blown in by the blast wave, a little at a time. */
	static final class Shatter implements ServerTask {
		private static final long TIME_BUDGET_NANOS = 2_000_000L;
		private static final int R = 20;
		private static final int H = 10;
		private static final int SIZE = (2 * R + 1) * (2 * R + 1) * (2 * H + 1);
		private final ServerLevel level;
		private final BlockPos center;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		private int index;

		Shatter(ServerLevel level, BlockPos center) {
			this.level = level;
			this.center = center;
		}

		@Override
		public boolean tick() {
			long deadline = System.nanoTime() + TIME_BUDGET_NANOS;
			RandomSource r = ApocalypseManager.RANDOM;
			while (index < SIZE && System.nanoTime() < deadline) {
				int i = index++;
				int dx = i % (2 * R + 1) - R;
				int rest = i / (2 * R + 1);
				int dz = rest % (2 * R + 1) - R;
				int dy = rest / (2 * R + 1) - H;
				pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
				if (!level.hasChunkAt(pos)) continue;
				BlockState state = level.getBlockState(pos);
				if (!isGlass(state) || level.getBrightness(LightLayer.SKY, pos) < 8) continue;
				if (r.nextFloat() < 0.75f) level.destroyBlock(pos.immutable(), false);
			}
			return index >= SIZE;
		}

		private static boolean isGlass(BlockState state) {
			return state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof StainedGlassPaneBlock;
		}
	}

	/** An exposed player is caught by the flash, lifted by the blast and turned to ash. */
	static final class Vaporize implements ServerTask {
		private static final int BURN_TICKS = 34;
		private final ServerPlayer player;
		private final boolean griefing;
		private int delay;
		private int age = -1;
		private BlockPos origin = BlockPos.ZERO;
		private float yaw;

		Vaporize(ServerPlayer player, boolean griefing, int delay) {
			this.player = player;
			this.griefing = griefing;
			this.delay = delay;
		}

		@Override
		public boolean tick() {
			if (player.isRemoved() || player.hasDisconnected() || !player.isAlive() || ApocalypseManager.isObserver(player)) return true;
			if (delay-- > 0) return false;
			ServerLevel level = player.level();
			age++;
			double x = player.getX();
			double y = player.getY() + 1;
			double z = player.getZ();
			if (age == 0) {
				origin = player.blockPosition();
				yaw = player.getYRot();
				Fx.send(player, new CuePayload(CuePayload.VAPORIZE, 1f, BURN_TICKS));
				player.igniteForSeconds(10f);
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, BURN_TICKS, 0, false, false));
				Fx.sound(level, x, y, z, SoundEvents.PLAYER_HURT_ON_FIRE, 2f, 0.6f);
				Fx.sound(level, x, y, z, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
			}
			Fx.particles(level, ParticleTypes.FLAME, x, y, z, 10, 0.4, 0.06);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, x, y, z, 4, 0.3, 0.04);
			Fx.particles(level, ParticleTypes.WHITE_ASH, x, y, z, 30, 0.6, 0.1);
			if (age % 4 == 0) Fx.particles(level, ParticleTypes.LAVA, x, y, z, 2, 0.3, 0.0);
			if (age < BURN_TICKS) return false;

			if (griefing) leaveShadow(level);
			Fx.particles(level, ParticleTypes.EXPLOSION, x, y, z, 3, 0.4, 0.0);
			Fx.particles(level, ParticleTypes.ASH, x, y, z, 120, 0.8, 0.2);
			Fx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 12, 0.4, 0.02);
			Fx.sound(level, x, y, z, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.4f);
			player.hurtServer(level, level.damageSources().source(ZeroHour.NUCLEAR_BLAST), Float.MAX_VALUE);
			return true;
		}

		/** Like the shadows burned into the pavement at Hiroshima: a black silhouette and a skull. */
		private void leaveShadow(ServerLevel level) {
			BlockPos ground = null;
			for (int i = 0; i < 6; i++) {
				BlockPos p = origin.below(i + 1);
				BlockState s = level.getBlockState(p);
				if (s.isSolidRender() && level.getBlockEntity(p) == null) {
					ground = p;
					break;
				}
				if (!s.isAir()) break;
			}
			if (ground == null) return;
			level.setBlockAndUpdate(ground, Blocks.BLACK_CONCRETE.defaultBlockState());
			for (BlockPos n : new BlockPos[]{ground.north(), ground.south(), ground.east(), ground.west()}) {
				if (ApocalypseManager.RANDOM.nextFloat() < 0.6f && level.getBlockState(n).isSolidRender() && level.getBlockEntity(n) == null
					&& level.getBlockState(n.above()).isAir()) {
					level.setBlockAndUpdate(n, (ApocalypseManager.RANDOM.nextBoolean() ? Blocks.BLACK_CONCRETE : Blocks.COAL_BLOCK).defaultBlockState());
				}
			}
			BlockPos skull = ground.above();
			if (level.getBlockState(skull).isAir()) {
				int rotation = RotationSegment.convertToSegment(Mth.wrapDegrees(yaw));
				level.setBlockAndUpdate(skull, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rotation));
			}
		}
	}
}
