package com.zerohour;

import com.zerohour.content.FalloutLayerBlock;
import com.zerohour.content.FlareGunItem;
import com.zerohour.content.ModItems;
import com.zerohour.content.ModSounds;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The way out. When the fallout thins, the military opens a landing zone a couple of hundred metres away
 * (away from the crater) for a limited time. Reach it and a helicopter flies you to the safe zone,
 * far beyond the blast. Miss it and you can still walk there.
 */
public final class Evacuation {
	private static final int LZ_DISTANCE = 220;
	private static final int SAFE_DISTANCE = 1400;
	private static final double PICKUP_RADIUS = 5.0;
	private static final int EXTRACTION_TICKS = 60;
	private static final Set<UUID> extracting = new HashSet<>();

	private Evacuation() {
	}

	public static void reset() {
		extracting.clear();
	}

	/** Pick the landing zone and the safe zone, both leading away from ground zero. */
	static void begin(MinecraftServer server, ApocalypseData d) {
		ServerLevel level = server.overworld();
		double sx = 0;
		double sz = 0;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (ApocalypseManager.isObserver(p) || d.fallen.contains(p.getUUID())) continue;
			sx += p.getX();
			sz += p.getZ();
			n++;
		}
		double cx = n > 0 ? sx / n : d.hasStrike ? d.strikeX : 0;
		double cz = n > 0 ? sz / n : d.hasStrike ? d.strikeZ : 0;
		double angle;
		if (d.hasStrike && (cx != d.strikeX || cz != d.strikeZ)) {
			angle = Math.atan2(cz - d.strikeZ, cx - d.strikeX);
		} else {
			angle = ApocalypseManager.RANDOM.nextDouble() * Math.PI * 2;
		}
		angle += (ApocalypseManager.RANDOM.nextDouble() - 0.5) * Math.PI / 2;
		d.lzX = Mth.floor(cx + Math.cos(angle) * LZ_DISTANCE);
		d.lzZ = Mth.floor(cz + Math.sin(angle) * LZ_DISTANCE);
		d.lzY = level.getSeaLevel();
		d.hasLz = true;
		d.lzBuilt = false;
		d.evacTicks = 0;
		d.evacClosed = false;
		d.safeX = Mth.floor(cx + Math.cos(angle) * SAFE_DISTANCE);
		d.safeZ = Mth.floor(cz + Math.sin(angle) * SAFE_DISTANCE);
		d.safeY = level.getSeaLevel();
		d.hasSafeZone = true;
		d.safeBuilt = false;
		extracting.clear();
		d.setDirty();
	}

	static void tick(MinecraftServer server, ApocalypseData d, boolean running) {
		if (!d.hasLz) return;
		ServerLevel level = server.overworld();
		if (!d.lzBuilt) buildLandingZone(level, d);
		long time = level.getGameTime();
		if (time % 20 == 0) checkWalkIns(level, d);
		if (d.evacClosed) return;
		if (running) d.evacTicks++;

		double lx = d.lzX + 0.5;
		double ly = d.lzBuilt ? d.lzY + 1 : 90;
		double lz = d.lzZ + 0.5;
		// A red flare goes up from the landing zone every half minute, visible from far away.
		if (time % 600 == 0) {
			ApocalypseManager.addTask(new FlareGunItem.SignalFlare(level, new Vec3(lx, ly, lz), new Vec3(0, 1.7, 0)));
			Fx.sound(level, lx, ly, lz, ModSounds.FLARE_GUN, 8f, 0.9f);
		}
		if (time % 140 == 0 && anyoneWithin(level, lx, lz, 96)) {
			Fx.sound(level, lx, ly + 16, lz, ModSounds.HELICOPTER, 6f, 1f);
		}
		if (time % 10 == 0) {
			for (ServerPlayer player : level.players()) {
				if (!eligible(player, d) || extracting.contains(player.getUUID())) continue;
				double dx = player.getX() - lx;
				double dz = player.getZ() - lz;
				if (dx * dx + dz * dz > PICKUP_RADIUS * PICKUP_RADIUS) continue;
				if (d.lzBuilt && Math.abs(player.getY() - d.lzY) > 8) continue;
				extracting.add(player.getUUID());
				ApocalypseManager.addTask(new Extraction(player, level, lx, ly, lz));
			}
		}
		if (d.evacTicks >= d.evacMinutes * 1200) close(server, d);
	}

	private static boolean eligible(ServerPlayer player, ApocalypseData d) {
		return player.isAlive() && !ApocalypseManager.isObserver(player) && !d.evacuated.contains(player.getUUID())
			&& player.level().dimension() == Level.OVERWORLD;
	}

	private static boolean anyoneWithin(ServerLevel level, double x, double z, double radius) {
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (dx * dx + dz * dz < radius * radius) return true;
		}
		return false;
	}

	/** Anyone who reaches the safe zone on foot is safe too. */
	private static void checkWalkIns(ServerLevel level, ApocalypseData d) {
		if (!d.hasSafeZone) return;
		for (ServerPlayer player : level.players()) {
			if (eligible(player, d) && d.inSafeZone(player.getX(), player.getZ())) {
				markEvacuated(player, d, false);
			}
		}
	}

	private static void close(MinecraftServer server, ApocalypseData d) {
		d.evacClosed = true;
		d.setDirty();
		List<String> saved = new ArrayList<>();
		List<String> left = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative()) continue;
			(d.evacuated.contains(player.getUUID()) ? saved : left).add(player.getName().getString());
		}
		var list = server.getPlayerList();
		list.broadcastSystemMessage(Component.literal("✈ THE LAST HELICOPTER HAS LEFT. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("Evacuated (" + saved.size() + "/" + (saved.size() + left.size()) + ")"
				+ (saved.isEmpty() ? "" : ": " + String.join(", ", saved))).withStyle(ChatFormatting.YELLOW)), false);
		if (!left.isEmpty()) {
			list.broadcastSystemMessage(Component.literal("Left behind: " + String.join(", ", left)
				+ ". The safe zone is at X " + d.safeX + ", Z " + d.safeZ + ". Walk.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
		}
		Broadcasts.air(server, d, BroadcastPayload.FINAL, "LAST FLIGHT",
			"The last helicopter has left. To anyone still out there: the safe zone is at X " + d.safeX + ", Z " + d.safeZ
				+ ". Head there on foot. Stay away from the crater. Good luck.", 320);
		ApocalypseManager.syncAll();
	}

	/** A helipad with a signal fire, built once the landing zone's chunk is loaded. */
	private static void buildLandingZone(ServerLevel level, ApocalypseData d) {
		for (int ox = -5; ox <= 5; ox += 10) {
			for (int oz = -5; oz <= 5; oz += 10) {
				if (!level.hasChunkAt(new BlockPos(d.lzX + ox, 0, d.lzZ + oz))) return;
			}
		}
		d.lzY = buildPad(level, d.lzX, d.lzZ, 3);
		BlockPos fire = new BlockPos(d.lzX + 5, d.lzY, d.lzZ);
		level.setBlockAndUpdate(fire.below(), Blocks.HAY_BLOCK.defaultBlockState());
		level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
		d.lzBuilt = true;
		d.setDirty();
		ApocalypseManager.syncAll();
	}

	/**
	 * Lays a concrete helipad with a yellow "H" centred on (x, z), clears the air above it and puts lanterns on the corners.
	 * @return the Y the pad's top surface stands on (one above the pad blocks).
	 */
	private static int buildPad(ServerLevel level, int x, int z, int half) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// Land on the ground, not on top of a tree trunk.
		for (int i = 0; i < 24 && y > level.getMinY() + 1; i++) {
			BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
			if (!below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES) && !(below.getBlock() instanceof FalloutLayerBlock)) break;
			y--;
		}
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos p = new BlockPos(x + dx, y - 1, z + dz);
				boolean h = (Math.abs(dx) == 1 && Math.abs(dz) <= 1) || (dx == 0 && dz == 0);
				level.setBlock(p, (h ? Blocks.YELLOW_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState(), Block.UPDATE_ALL);
				for (int up = 0; up < 8; up++) {
					BlockPos a = p.above(up + 1);
					if (!level.getBlockState(a).isAir() && level.getBlockEntity(a) == null) level.setBlock(a, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int dx = -half; dx <= half; dx += 2 * half) {
			for (int dz = -half; dz <= half; dz += 2 * half) {
				level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.LANTERN.defaultBlockState());
			}
		}
		return y;
	}

	/** The safe zone: a camp with a helipad and supplies, on clean ground far from the blast. */
	private static void ensureSafeZone(ServerLevel level, ApocalypseData d) {
		if (d.safeBuilt) return;
		level.getChunk(d.safeX >> 4, d.safeZ >> 4);
		d.safeY = buildPad(level, d.safeX, d.safeZ, 4);
		BlockPos barrelPos = new BlockPos(d.safeX + 6, d.safeY, d.safeZ);
		level.setBlockAndUpdate(barrelPos.below(), Blocks.GRAY_CONCRETE.defaultBlockState());
		level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		if (level.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
			int slot = 0;
			barrel.setItem(slot++, new ItemStack(ModItems.EMERGENCY_RATION, 16));
			barrel.setItem(slot++, new ItemStack(ModItems.ANTI_RAD_SERUM, 4));
			barrel.setItem(slot++, new ItemStack(ModItems.ANTIVIRAL, 2));
			barrel.setItem(slot++, new ItemStack(Items.WHITE_BED, 4));
			barrel.setItem(slot++, new ItemStack(Items.TORCH, 32));
			barrel.setItem(slot, new ItemStack(Items.OAK_SAPLING, 8));
			barrel.setChanged();
		}
		BlockPos fire = new BlockPos(d.safeX - 6, d.safeY, d.safeZ);
		level.setBlockAndUpdate(fire.below(), Blocks.HAY_BLOCK.defaultBlockState());
		level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
		d.safeBuilt = true;
		d.setDirty();
	}

	static void evacuate(ServerPlayer player, ApocalypseData d) {
		ServerLevel level = player.level().getServer().overworld();
		ensureSafeZone(level, d);
		player.removeEffect(MobEffects.LEVITATION);
		double ox = (ApocalypseManager.RANDOM.nextDouble() - 0.5) * 4;
		double oz = (ApocalypseManager.RANDOM.nextDouble() - 0.5) * 4;
		player.teleportTo(level, d.safeX + 0.5 + ox, d.safeY, d.safeZ + 0.5 + oz, Set.of(), player.getYRot(), 0f, false);
		player.fallDistance = 0;
		markEvacuated(player, d, true);
	}

	static void markEvacuated(ServerPlayer player, ApocalypseData d, boolean byAir) {
		if (!d.evacuated.add(player.getUUID())) return;
		d.setDirty();
		// The medics at the camp treat whatever you brought with you.
		Infection.cure(player, d);
		Radiation.addDose(player, d, -d.doseOf(player.getUUID()) * 0.5f);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		for (ItemStack stack : List.of(new ItemStack(ModItems.EMERGENCY_RATION, 3), new ItemStack(ModItems.ANTI_RAD_SERUM))) {
			if (!player.getInventory().add(stack)) player.drop(stack, false);
		}
		Fx.sound(player.level(), player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, 1f, 0.7f);
		player.sendSystemMessage(Component.literal(byAir
				? "✈ EVACUATED. The helicopter sets you down at the safe zone. Medics check you over. You made it."
				: "⌂ You walked out of the wasteland and into the safe zone. You made it.")
			.withStyle(ChatFormatting.GOLD));
		player.level().getServer().getPlayerList().broadcastSystemMessage(
			Component.literal("✈ " + player.getName().getString() + (byAir ? " has been evacuated." : " reached the safe zone on foot."))
				.withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC), false);
		ApocalypseManager.syncAll();
	}

	/** Rotor wash, a few seconds of being hauled aboard, and then you are out. */
	static final class Extraction implements ServerTask {
		private final ServerPlayer player;
		private final ServerLevel level;
		private final double x;
		private final double y;
		private final double z;
		private int age;

		Extraction(ServerPlayer player, ServerLevel level, double x, double y, double z) {
			this.player = player;
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public boolean tick() {
			ApocalypseData d = ApocalypseManager.data();
			if (d == null || player.isRemoved() || player.hasDisconnected() || !player.isAlive()) {
				extracting.remove(player.getUUID());
				return true;
			}
			age++;
			if (age == 1) {
				Fx.send(player, new CuePayload(CuePayload.EXTRACTION, 1f, EXTRACTION_TICKS));
				Fx.sound(level, x, y + 8, z, ModSounds.HELICOPTER, 4f, 1f);
				player.displayClientMessage(Component.literal("The helicopter is coming down. Hold on!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
			}
			if (age % 2 == 0) {
				Fx.particles(level, ParticleTypes.CLOUD, x, y + 0.3, z, 10, 3.0, 0.2, 3.0, 0.15);
				Fx.particles(level, ParticleTypes.POOF, x, y + 0.3, z, 4, 2.0, 0.1, 2.0, 0.1);
			}
			if (age == EXTRACTION_TICKS / 2) {
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, EXTRACTION_TICKS, 2, false, false));
			}
			if (age < EXTRACTION_TICKS) return false;
			extracting.remove(player.getUUID());
			evacuate(player, d);
			return true;
		}
	}
}
