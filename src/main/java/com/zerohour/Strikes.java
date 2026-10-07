package com.zerohour;

import com.zerohour.content.ModItems;
import com.zerohour.content.ModSounds;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Things that come out of the sky in a war: stray artillery shells, fighter jets, missiles, and military airdrops. */
public final class Strikes {
	private Strikes() {
	}

	/** A stray artillery shell screams in and lands somewhere around the player. */
	public static void shell(ServerLevel level, ServerPlayer near, boolean griefing) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 30 + r.nextDouble() * 60;
		int tx = Mth.floor(near.getX() + Math.cos(angle) * dist);
		int tz = Mth.floor(near.getZ() + Math.sin(angle) * dist);
		if (!level.hasChunkAt(new BlockPos(tx, 0, tz))) return;
		BlockPos ground = Fx.surface(level, tx, tz);
		double from = r.nextDouble() * Math.PI * 2;
		Vec3 target = Vec3.atCenterOf(ground);
		Vec3 start = target.add(Math.cos(from) * 110, 170, Math.sin(from) * 110);
		ApocalypseManager.addTask(new Shell(level, start, target.subtract(start).normalize().scale(3.4), griefing));
		// The whistle grows louder as it falls toward where it will land.
		Fx.sound(level, target.x, target.y, target.z, ModSounds.SHELL_WHISTLE, 10f, 0.9f + r.nextFloat() * 0.2f);
	}

	/** A flight of fighter jets tears across the sky, low, right over the player. */
	public static void jets(ServerLevel level, ServerPlayer near) {
		RandomSource r = ApocalypseManager.RANDOM;
		double heading = r.nextDouble() * Math.PI * 2;
		Vec3 dir = new Vec3(Math.cos(heading), 0, Math.sin(heading));
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double altitude = Math.max(near.getY() + 55, level.getSeaLevel() + 70) + r.nextDouble() * 25;
		Vec3 over = new Vec3(near.getX() + side.x * (r.nextDouble() - 0.5) * 40, altitude, near.getZ() + side.z * (r.nextDouble() - 0.5) * 40);
		Vec3 start = over.subtract(dir.scale(200));
		int count = 2 + r.nextInt(2);
		for (int i = 0; i < count; i++) {
			Vec3 offset = side.scale((i - (count - 1) / 2.0) * 7).add(dir.scale(-Math.abs(i - (count - 1) / 2.0) * 9));
			ApocalypseManager.addTask(new Jet(level, start.add(offset), dir.scale(3.6)));
		}
		Fx.sound(level, over.x, near.getY() + 20, over.z, ModSounds.JET_FLYBY, 14f, 0.9f + r.nextFloat() * 0.2f);
	}

	/** A missile launching far away: a bright point climbing on a pillar of smoke. */
	public static void icbm(ServerLevel level, ServerPlayer near) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		double dist = 260 + r.nextDouble() * 160;
		Vec3 start = new Vec3(near.getX() + Math.cos(angle) * dist, level.getSeaLevel() + 20, near.getZ() + Math.sin(angle) * dist);
		double heading = angle + Math.PI / 2 + (r.nextDouble() - 0.5);
		Vec3 vel = new Vec3(Math.cos(heading) * 0.8, 2.6, Math.sin(heading) * 0.8);
		ApocalypseManager.addTask(new Missile(level, start, vel, 140 + r.nextInt(40)));
	}

	/** Another city, somewhere over the horizon. Everyone sees the sky light up; the rumble comes later. */
	public static void distantFlash(ServerLevel level) {
		RandomSource r = ApocalypseManager.RANDOM;
		double angle = r.nextDouble() * Math.PI * 2;
		float strength = 0.5f + r.nextFloat() * 0.5f;
		for (ServerPlayer p : level.players()) {
			Fx.send(p, new CuePayload(CuePayload.DISTANT_FLASH, strength, 60 + r.nextInt(80),
				p.getX() + Math.cos(angle) * 4000, p.getY(), p.getZ() + Math.sin(angle) * 4000));
		}
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
			Broadcasts.air(level.getServer(), data, BroadcastPayload.BULLETIN, "MILITARY AIRDROP",
				"Supplies are being dropped near X " + x + ", Z " + z + ". Follow the smoke. Watch for the infected.", 220);
		}
		Fx.sendLevel(level, new CuePayload(CuePayload.SUPPLY_DROP, 1f, 0, x, ground.getY(), z));
	}

	static final class Shell implements ServerTask {
		private final ServerLevel level;
		private Vec3 pos;
		private final Vec3 vel;
		private final boolean griefing;
		private int age;

		Shell(ServerLevel level, Vec3 pos, Vec3 vel, boolean griefing) {
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.griefing = griefing;
		}

		@Override
		public boolean tick() {
			age++;
			if (age > 300) return true;
			// Move in small steps so we never tunnel through the ground.
			for (int step = 0; step < 3; step++) {
				pos = pos.add(vel.scale(1 / 3.0));
				BlockPos bp = BlockPos.containing(pos);
				if (bp.getY() < level.getMinY()) return true;
				if (bp.getY() <= level.getMaxY()) {
					if (!level.hasChunkAt(bp)) return true;
					BlockState state = level.getBlockState(bp);
					if (!state.isAir() || !level.getFluidState(bp).isEmpty()) {
						impact();
						return true;
					}
				}
			}
			Fx.particles(level, ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 4, 0.1, 0.01);
			Fx.particles(level, ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 2, 0.05, 0.0);
			return false;
		}

		private void impact() {
			level.explode(null, pos.x, pos.y, pos.z, 3.4f, griefing, griefing ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
			Fx.particles(level, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.5, 0.0);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, 50, 2.0, 0.06);
			Fx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 1, pos.z, 14, 1.5, 0.02);
			Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, 10f, 0.6f);
			for (ServerPlayer p : level.players()) {
				double d = p.position().distanceTo(pos);
				if (d < 100) Fx.send(p, new CuePayload(CuePayload.SHELL_IMPACT, (float) (1 - d / 100), 24, pos.x, pos.y, pos.z));
			}
		}
	}

	static final class Jet implements ServerTask {
		private static final int LIFE = 115;
		private final ServerLevel level;
		private Vec3 pos;
		private final Vec3 vel;
		private int age;

		Jet(ServerLevel level, Vec3 pos, Vec3 vel) {
			this.level = level;
			this.pos = pos;
			this.vel = vel;
		}

		@Override
		public boolean tick() {
			pos = pos.add(vel);
			Fx.particles(level, ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 2, 0.15, 0.0);
			Vec3 tail = pos.subtract(vel.scale(0.5));
			Fx.particles(level, ParticleTypes.CLOUD, tail.x, tail.y, tail.z, 3, 0.15, 0.01);
			if (age % 2 == 0) Fx.particles(level, ParticleTypes.SMALL_FLAME, tail.x, tail.y, tail.z, 1, 0.05, 0.0);
			return ++age >= LIFE;
		}
	}

	static final class Missile implements ServerTask {
		private final ServerLevel level;
		private Vec3 pos;
		private Vec3 vel;
		private final int life;
		private int age;

		Missile(ServerLevel level, Vec3 pos, Vec3 vel, int life) {
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.life = life;
		}

		@Override
		public boolean tick() {
			pos = pos.add(vel);
			// The trajectory slowly bends over toward its target.
			vel = vel.add(vel.x * 0.006, -0.012, vel.z * 0.006);
			Fx.particles(level, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.1, 0.0);
			Fx.particles(level, ParticleTypes.FLAME, pos.x, pos.y - 0.5, pos.z, 2, 0.15, 0.0);
			Fx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y - 1.5, pos.z, 2, 0.4, 0.005);
			return ++age >= life;
		}
	}

	/** A crate falls on a column of smoke and lands as a barrel full of military supplies. */
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
				y -= 1.0;
				Fx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1, z, 2, 0.2, 0.01);
				Fx.particles(level, ParticleTypes.FIREWORK, x, y, z, 2, 0.3, 0.02);
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
			barrel.setItem(slot++, new ItemStack(ModItems.IODINE_TABLETS, 2 + r.nextInt(3)));
			barrel.setItem(slot++, new ItemStack(Items.TORCH, 16 + r.nextInt(17)));
			barrel.setItem(slot++, new ItemStack(r.nextFloat() < 0.15f ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE));
			if (r.nextFloat() < 0.6f) barrel.setItem(slot++, new ItemStack(ModItems.ANTI_RAD_SERUM, 1 + r.nextInt(2)));
			if (r.nextFloat() < 0.35f) barrel.setItem(slot++, new ItemStack(ModItems.ANTIVIRAL));
			if (r.nextFloat() < 0.4f) barrel.setItem(slot++, new ItemStack(ModItems.GAS_MASK));
			if (r.nextFloat() < 0.35f) {
				Item[] suit = {ModItems.HAZMAT_SUIT, ModItems.HAZMAT_TROUSERS, ModItems.HAZMAT_BOOTS};
				barrel.setItem(slot++, new ItemStack(suit[r.nextInt(suit.length)]));
			}
			if (r.nextFloat() < 0.4f) barrel.setItem(slot++, new ItemStack(Items.IRON_SWORD));
			if (r.nextFloat() < 0.3f) {
				barrel.setItem(slot++, new ItemStack(Items.BOW));
				barrel.setItem(slot++, new ItemStack(Items.ARROW, 16 + r.nextInt(17)));
			}
			if (r.nextFloat() < 0.5f) barrel.setItem(slot++, new ItemStack(Items.OBSIDIAN, 4 + r.nextInt(9)));
			if (r.nextFloat() < 0.3f) barrel.setItem(slot++, new ItemStack(ModItems.BLAST_SHIELDING, 4 + r.nextInt(5)));
			if (r.nextFloat() < 0.35f) barrel.setItem(slot++, new ItemStack(ModItems.FLARE_GUN));
			if (r.nextFloat() < 0.5f) barrel.setItem(slot++, new ItemStack(Items.WATER_BUCKET));
			if (r.nextFloat() < 0.25f) barrel.setItem(slot++, new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
			barrel.setItem(slot, new ItemStack(Items.COBBLED_DEEPSLATE, 32 + r.nextInt(33)));
			barrel.setChanged();
		}
	}
}
