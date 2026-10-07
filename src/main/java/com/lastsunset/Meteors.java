package com.lastsunset;

import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.content.ModSounds;
import com.lastsunset.content.ModItems;
import com.lastsunset.content.ModBlocks;
import com.lastsunset.network.CuePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Things that fall from the sky: meteors, harmless shooting stars and emergency supply drops. */
public final class Meteors {
	private Meteors() {
	}

	/** Launch a meteor at a point somewhere around the player. */
	public static void launch(ServerLevel level, ServerPlayer near, float size, boolean griefing) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 22 + r.nextDouble() * 70;
		int tx = Mth.floor(near.getX() + Math.cos(angle) * dist);
		int tz = Mth.floor(near.getZ() + Math.sin(angle) * dist);
		if (!level.hasChunkAt(new BlockPos(tx, 0, tz))) return;
		BlockPos ground = Fx.surface(level, tx, tz);
		double from = r.nextDouble() * Math.PI * 2;
		Vec3 target = Vec3.atCenterOf(ground);
		Vec3 start = target.add(Math.cos(from) * 160, 190 + r.nextDouble() * 40, Math.sin(from) * 160);
		ApocalypseManager.addTask(new Meteor(level, start, target.subtract(start).normalize().scale(2.6), size, griefing));
		// The whistle grows louder as it falls toward where it will land.
		Fx.sound(level, target.x, target.y, target.z, ModSounds.METEOR_WHISTLE, 10f, 0.9f + r.nextFloat() * 0.2f);
	}

	/** A shooting star that burns up high in the sky. */
	public static void streak(ServerLevel level, ServerPlayer near) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		Vec3 start = near.position().add(Math.cos(angle) * 90, 150 + r.nextDouble() * 40, Math.sin(angle) * 90);
		double dir = angle + Math.PI + (r.nextDouble() - 0.5);
		Vec3 vel = new Vec3(Math.cos(dir) * 3.2, -0.6 - r.nextDouble() * 0.5, Math.sin(dir) * 3.2);
		ApocalypseManager.addTask(new Streak(level, start, vel, 40 + r.nextInt(20)));
	}

	public static void supplyDrop(ServerLevel level, ServerPlayer near) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 30 + r.nextDouble() * 40;
		int x = Mth.floor(near.getX() + Math.cos(angle) * dist);
		int z = Mth.floor(near.getZ() + Math.sin(angle) * dist);
		if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
		BlockPos ground = Fx.surface(level, x, z);
		if (!level.getFluidState(ground.below()).isEmpty()) return;
		ApocalypseManager.addTask(new SupplyDrop(level, ground));
		ApocalypseData data = ApocalypseManager.data();
		if (data != null) {
			Broadcasts.air(level.getServer(), data, BroadcastPayload.BULLETIN, "CIVIL DEFENSE",
				"Supply crate dropping near X " + x + ", Z " + z + ". Follow the smoke.", 220);
		}
		Fx.sendLevel(level, new CuePayload(CuePayload.SUPPLY_DROP, 1f, 0, x, ground.getY(), z));
	}

	static final class Meteor implements ServerTask {
		private final ServerLevel level;
		private Vec3 pos;
		private final Vec3 vel;
		private final float size;
		private final boolean griefing;
		private int age;

		Meteor(ServerLevel level, Vec3 pos, Vec3 vel, float size, boolean griefing) {
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.size = size;
			this.griefing = griefing;
		}

		@Override
		public boolean tick() {
			age++;
			if (age > 400) return true;
			// Move in small steps so we never tunnel through the ground.
			for (int step = 0; step < 3; step++) {
				pos = pos.add(vel.scale(1 / 3.0));
				BlockPos bp = BlockPos.containing(pos);
				if (bp.getY() < level.getMinY()) return true;
				if (bp.getY() <= level.getMaxY()) {
					if (!level.hasChunkAt(bp)) return true;
					BlockState state = level.getBlockState(bp);
					if (!state.isAir() || !level.getFluidState(bp).isEmpty()) {
						impact(bp);
						return true;
					}
				}
			}
			double s = size;
			Fx.particles(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, (int) (10 * s), 0.5 * s, 0.04);
			Fx.particles(level, ParticleTypes.LAVA, pos.x, pos.y, pos.z, 2, 0.4 * s, 0.0);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, (int) (4 * s), 0.4 * s, 0.02);
			Fx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 2, 0.3 * s, 0.01);
			if (age % 2 == 0) Fx.particles(level, ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0.2, 0.0);
			if (age % 15 == 1) Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, 10f, 0.4f + ApocalypseManager.RANDOM.nextFloat() * 0.2f);
			return false;
		}

		private void impact(BlockPos at) {
			float power = 3.0f + size * 1.6f;
			level.explode(null, pos.x, pos.y, pos.z, power, griefing, griefing ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
			Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 1.0, 0.0);
			Fx.particles(level, ParticleTypes.LAVA, pos.x, pos.y, pos.z, 60, 2.0, 0.0);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, 80, 3.0, 0.08);
			Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.x, pos.y + 1, pos.z, 20, 2.0, 0.02);
			Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, 12f, 0.5f);
			Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, 10f, 0.6f);
			for (ServerPlayer p : level.players()) {
				double d = p.position().distanceTo(pos);
				if (d < 120) Fx.send(p, new CuePayload(CuePayload.METEOR_IMPACT, (float) (1 - d / 120), 30, pos.x, pos.y, pos.z));
			}
			if (griefing) core(BlockPos.containing(pos));
		}

		/** A glowing meteorite left in the crater. Sometimes it carries something valuable. */
		private void core(BlockPos center) {
			RandomSource r = ApocalypseManager.RANDOM;
			BlockPos c = center;
			while (c.getY() > level.getMinY() && level.getBlockState(c.below()).isAir()) c = c.below();
			float roll = r.nextFloat();
			Block heart = roll < 0.4f ? ModBlocks.SOLARITE_ORE : roll < 0.48f ? Blocks.ANCIENT_DEBRIS : roll < 0.68f ? Blocks.RAW_IRON_BLOCK
				: roll < 0.75f ? Blocks.RAW_GOLD_BLOCK : Blocks.MAGMA_BLOCK;
			int radius = size >= 1.5f ? 2 : 1;
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -radius, -radius), c.offset(radius, radius, radius))) {
				if (p.distSqr(c) > radius * radius + 0.5) continue;
				if (level.getBlockEntity(p) != null || level.getBlockState(p).getDestroySpeed(level, p) < 0) continue;
				float f = r.nextFloat();
				BlockState shell = (f < 0.4f ? Blocks.MAGMA_BLOCK : f < 0.72f ? Blocks.BLACKSTONE : f < 0.88f ? Blocks.OBSIDIAN
					: f < 0.94f ? ModBlocks.SOLARITE_ORE : Blocks.CRYING_OBSIDIAN).defaultBlockState();
				level.setBlockAndUpdate(p, shell);
			}
			level.setBlockAndUpdate(c, heart.defaultBlockState());
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos f = c.relative(dir, radius + 1).above();
				if (level.getBlockState(f).isAir() && level.getBlockState(f.below()).isSolidRender()) {
					level.setBlockAndUpdate(f, Blocks.FIRE.defaultBlockState());
				}
			}
		}
	}

	static final class Streak implements ServerTask {
		private final ServerLevel level;
		private Vec3 pos;
		private final Vec3 vel;
		private final int life;
		private int age;

		Streak(ServerLevel level, Vec3 pos, Vec3 vel, int life) {
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.life = life;
		}

		@Override
		public boolean tick() {
			pos = pos.add(vel);
			Fx.particles(level, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 3, 0.15, 0.0);
			Fx.particles(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.2, 0.0);
			if (age % 3 == 0) Fx.particles(level, ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 2, 0.1, 0.02);
			return ++age >= life;
		}
	}

	/** A crate falls on a column of smoke and lands as a barrel full of supplies. */
	static final class SupplyDrop implements ServerTask {
		private final ServerLevel level;
		private final BlockPos ground;
		private double y;
		private int smokeTicks = -1;

		SupplyDrop(ServerLevel level, BlockPos ground) {
			this.level = level;
			this.ground = ground;
			this.y = ground.getY() + 140;
		}

		@Override
		public boolean tick() {
			double x = ground.getX() + 0.5;
			double z = ground.getZ() + 0.5;
			if (smokeTicks < 0) {
				y -= 1.4;
				Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1, z, 2, 0.2, 0.01);
				Fx.particles(level, ParticleTypes.FIREWORK, x, y, z, 3, 0.3, 0.02);
				Fx.particles(level, ParticleTypes.FLAME, x, y - 0.5, z, 3, 0.2, 0.01);
				if (y <= ground.getY()) {
					land();
					smokeTicks = 0;
				}
				return false;
			}
			if (smokeTicks % 3 == 0) Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, ground.getY() + 1.2, z, 2, 0.15, 0.01);
			return ++smokeTicks > 20 * 150;
		}

		private void land() {
			RandomSource r = ApocalypseManager.RANDOM;
			BlockState existing = level.getBlockState(ground);
			if (!existing.isAir() && !existing.canBeReplaced()) return;
			level.setBlockAndUpdate(ground, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			Fx.sound(level, ground.getX(), ground.getY(), ground.getZ(), SoundEvents.ANVIL_LAND, 2f, 0.6f);
			Fx.particles(level, ParticleTypes.CLOUD, ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5, 30, 1.0, 0.05);
			if (!(level.getBlockEntity(ground) instanceof BarrelBlockEntity barrel)) return;
			int slot = 0;
			barrel.setItem(slot++, new ItemStack(ModItems.EMERGENCY_RATION, 3 + r.nextInt(4)));
			barrel.setItem(slot++, new ItemStack(Items.TORCH, 16 + r.nextInt(17)));
			barrel.setItem(slot++, new ItemStack(r.nextFloat() < 0.15f ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE));
			if (r.nextFloat() < 0.7f) barrel.setItem(slot++, new ItemStack(Items.OBSIDIAN, 4 + r.nextInt(9)));
			if (r.nextFloat() < 0.5f) barrel.setItem(slot++, new ItemStack(Items.IRON_SHOVEL));
			if (r.nextFloat() < 0.5f) barrel.setItem(slot++, new ItemStack(Items.WATER_BUCKET));
			if (r.nextFloat() < 0.35f) barrel.setItem(slot++, new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
			if (r.nextFloat() < 0.25f) barrel.setItem(slot++, PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE));
			if (r.nextFloat() < 0.6f) barrel.setItem(slot++, new ItemStack(Items.OAK_LOG, 16));
			if (r.nextFloat() < 0.35f) barrel.setItem(slot++, new ItemStack(ModItems.FLARE_GUN));
			if (r.nextFloat() < 0.3f) barrel.setItem(slot++, new ItemStack(ModItems.FLARE_SHIELDING, 4 + r.nextInt(5)));
			if (r.nextFloat() < 0.25f) barrel.setItem(slot++, new ItemStack(ModItems.SOLARITE_SHARD, 1 + r.nextInt(2)));
			barrel.setItem(slot, new ItemStack(Items.COBBLED_DEEPSLATE, 32 + r.nextInt(33)));
			barrel.setChanged();
		}
	}
}
