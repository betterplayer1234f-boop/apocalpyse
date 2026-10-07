package com.lastsunset;

import com.lastsunset.network.CuePayload;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;

/** The moment the flare hits: who is exposed burns, who is sheltered holds on. */
public final class ImpactSequence {
	private ImpactSequence() {
	}

	static void begin(MinecraftServer server, ApocalypseData data) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float status;
			if (data.fallen.contains(player.getUUID()) || ApocalypseManager.isObserver(player)) {
				status = 3;
			} else {
				status = ApocalypseManager.shelterOf(player).status().ordinal();
			}
			Fx.send(player, new CuePayload(CuePayload.IMPACT, status, Stage.IMPACT_TICKS));
		}
	}

	static void tick(MinecraftServer server, ApocalypseData data, int t) {
		if (t == Stage.IMPACT_BOOM_TICK) boom(server, data);
	}

	/** The shockwave arrives. Everyone is judged by where they stand right now. */
	private static void boom(MinecraftServer server, ApocalypseData data) {
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (ApocalypseManager.isObserver(player) || data.fallen.contains(player.getUUID()) || !player.isAlive()) continue;
			Shelter.Result result = ApocalypseManager.shelterOf(player);
			switch (result.status()) {
				case EXPOSED -> ApocalypseManager.addTask(new Vaporize(player, data.griefing, ApocalypseManager.RANDOM.nextInt(40)));
				case PARTIAL -> breach(player, result);
				case SAFE -> player.displayClientMessage(
					Component.literal("The walls shake... but your shelter holds.").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), true
				);
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				vaporizeMobs(level, player, 96);
			}
		}
	}

	private static void breach(ServerPlayer player, Shelter.Result result) {
		float leak = 1f - result.rating();
		Fx.send(player, new CuePayload(CuePayload.BREACH, leak, 80));
		player.displayClientMessage(Component.literal("Your shelter is cracking — the heat is pouring in!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		player.igniteForSeconds(2f + leak * 6f);
		ServerLevel level = player.level();
		player.hurtServer(level, level.damageSources().source(LastSunset.SHELTER_BREACH), leak * 40f);
	}

	private static void vaporizeMobs(ServerLevel level, ServerPlayer center, double radius) {
		AABB box = center.getBoundingBox().inflate(radius, 160, radius);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
			e -> !(e instanceof Player) && e.isAlive() && level.canSeeSky(BlockPos.containing(e.getEyePosition())));
		if (!victims.isEmpty()) ApocalypseManager.addTask(new BurnMobs(level, new ArrayList<>(victims)));
	}

	/** Exposed animals and monsters burn a few at a time, so the blast never stalls the server. */
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
				e.hurtServer(level, level.damageSources().source(LastSunset.SOLAR_FLARE), Float.MAX_VALUE);
			}
			return index >= victims.size();
		}
	}

	/** Called when the aftermath starts: count who made it. */
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
			list.broadcastSystemMessage(Component.literal("☀ THE FLARE HAS PASSED. ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal("Extinction. No one survived the last sunset.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), false);
		} else {
			list.broadcastSystemMessage(Component.literal("☀ THE FLARE HAS PASSED. ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal("Survivors (" + survivors.size() + "/" + total + "): " + String.join(", ", survivors)).withStyle(ChatFormatting.GOLD)), false);
		}
	}

	/** An exposed player is lifted by the shockwave, set ablaze and turned to ash. */
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
			player.hurtServer(level, level.damageSources().source(LastSunset.SOLAR_FLARE), Float.MAX_VALUE);
			return true;
		}

		/** Like the shadows burned into walls by a nuclear flash: a black silhouette and a skull. */
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
