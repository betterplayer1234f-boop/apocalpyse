package com.zerohour;

import com.zerohour.content.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The infected: plain zombies, husks and zombie villagers, dressed as the civilians and soldiers they used to be.
 * They come in small groups around the survivors, more of them as things get worse. After the bomb some of them
 * are irradiated: faster, tougher, glowing faintly green, and radioactive to stand next to.
 */
public final class Horde {
	private static final DustParticleOptions GLOW = new DustParticleOptions(0x7CFF3A, 1.3F);
	/** Infected spawned after the bomb (not saved: after a restart they are ordinary infected). */
	private static final Set<UUID> irradiated = new HashSet<>();

	private Horde() {
	}

	public static void reset() {
		irradiated.clear();
	}

	public static void forget(Entity entity) {
		irradiated.remove(entity.getUUID());
	}

	public static boolean isIrradiated(Entity entity) {
		return irradiated.contains(entity.getUUID());
	}

	static void tick(ServerLevel level, ApocalypseData d, Stage stage) {
		long time = level.getGameTime();
		if (time % 20 == 0 && !irradiated.isEmpty()) glow(level);
		if (time % 40 != 0 || !d.infected) return;
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) return;
		boolean dark = level.isDarkOutside();
		int cap;
		float chance;
		switch (stage) {
			case TENSIONS -> {
				// Only rumours so far: the odd one, at night.
				cap = dark ? 2 : 0;
				chance = 0.12f;
			}
			case OUTBREAK -> {
				cap = dark ? 8 : 5;
				chance = 0.3f + 0.3f * d.stageFraction();
			}
			case LAUNCH -> {
				cap = 8;
				chance = 0.45f;
			}
			case INCOMING -> {
				cap = 6;
				chance = 0.35f;
			}
			case FALLOUT -> {
				cap = 8;
				chance = 0.45f;
			}
			case EVACUATION -> {
				cap = d.evacClosed ? 6 : 10;
				chance = 0.5f;
			}
			default -> {
				return;
			}
		}
		if (cap <= 0) return;
		boolean postStrike = stage.isPostStrike();
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : level.players()) {
			if (ApocalypseManager.isObserver(player) || !player.isAlive() || d.inSafeZone(player.getX(), player.getZ())) continue;
			if (r.nextFloat() >= chance) continue;
			int near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48, 24, 48), Infection::isInfectedKind).size();
			if (near >= cap) continue;
			int group = 1 + r.nextInt(stage == Stage.TENSIONS ? 1 : 3);
			spawnGroup(level, d, player, Math.min(group, cap - near), postStrike);
		}
	}

	/** Spawns a small group of infected somewhere 22-42 blocks from the player. */
	public static int spawnGroup(ServerLevel level, ApocalypseData d, ServerPlayer near, int count, boolean irradiate) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 22 + r.nextDouble() * 20;
		int cx = Mth.floor(near.getX() + Math.cos(angle) * dist);
		int cz = Mth.floor(near.getZ() + Math.sin(angle) * dist);
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			int x = cx + r.nextInt(5) - 2;
			int z = cz + r.nextInt(5) - 2;
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (!level.hasChunkAt(pos) || d.inSafeZone(x, z) || !canStand(level, pos)) continue;
			if (spawnOne(level, pos, irradiate, r) != null) spawned++;
		}
		return spawned;
	}

	private static boolean canStand(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
			&& level.getBlockState(pos.below()).isSolidRender() && level.getFluidState(pos.below()).isEmpty();
	}

	private static @Nullable Mob spawnOne(ServerLevel level, BlockPos pos, boolean irradiate, RandomSource r) {
		// Husks don't burn in daylight, so they are the ones walking around at noon.
		boolean daylight = level.isBrightOutside() && level.canSeeSky(pos);
		float roll = r.nextFloat();
		Mob mob;
		if (daylight || roll < 0.25f) {
			mob = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
		} else if (roll < 0.37f) {
			mob = EntityType.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.EVENT);
		} else {
			mob = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		}
		if (mob == null) return null;
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		dress(mob, irradiate, r);
		if (irradiate) {
			var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.2);
			var health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(30.0);
				mob.setHealth(30f);
			}
			irradiated.add(mob.getUUID());
		}
		level.addFreshEntity(mob);
		return mob;
	}

	/** Who they were: mostly civilians, some soldiers, and after the bomb, the odd hazmat crew member. */
	private static void dress(Mob mob, boolean irradiate, RandomSource r) {
		if (mob.getType() == EntityType.ZOMBIE_VILLAGER) return;
		float roll = r.nextFloat();
		if (roll < 0.25f) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			if (r.nextFloat() < 0.3f) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		} else if (irradiate && roll < 0.4f) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.GAS_MASK));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_SUIT));
			if (r.nextFloat() < 0.5f) mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_TROUSERS));
		}
	}

	/** A player who died infected gets back up, still wearing their name. */
	public static void spawnTurned(ServerPlayer player) {
		ServerLevel level = player.level();
		Mob mob = EntityType.ZOMBIE.create(level, EntitySpawnReason.CONVERSION);
		if (mob == null) return;
		mob.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
		mob.setCustomName(Component.literal(player.getName().getString()));
		mob.setCustomNameVisible(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		Fx.sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.2f, 0.8f);
		Fx.particles(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.02);
	}

	/** Extra rad/s from irradiated infected within a few blocks. */
	public static float radiationNear(ServerLevel level, ServerPlayer player) {
		if (irradiated.isEmpty()) return 0f;
		float total = 0f;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6), Horde::isIrradiated)) {
			float k = 1f - mob.distanceTo(player) / 7f;
			if (k > 0f) total += 0.6f * k;
		}
		return Math.min(3f, total);
	}

	private static void glow(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), Horde::isIrradiated)) {
				Fx.particles(level, GLOW, mob.getX(), mob.getY() + 1, mob.getZ(), 4, 0.35, 0.0);
			}
		}
	}
}
