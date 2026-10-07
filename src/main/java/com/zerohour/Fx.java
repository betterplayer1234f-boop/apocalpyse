package com.zerohour;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small helpers for server-side particles, sounds and packets. */
public final class Fx {
	private Fx() {
	}

	/** Long-range particles, visible up to 512 blocks away. */
	public static void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double spread, double speed) {
		level.sendParticles(particle, true, true, x, y, z, count, spread, spread, spread, speed);
	}

	public static void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		level.sendParticles(particle, true, true, x, y, z, count, dx, dy, dz, speed);
	}

	public static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, x, y, z, sound, SoundSource.AMBIENT, volume, pitch);
	}

	public static void sound(ServerLevel level, double x, double y, double z, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, x, y, z, sound, SoundSource.AMBIENT, volume, pitch);
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void sendAll(MinecraftServer server, CustomPacketPayload payload) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, payload);
		}
	}

	public static void sendLevel(ServerLevel level, CustomPacketPayload payload) {
		for (ServerPlayer player : level.players()) {
			send(player, payload);
		}
	}

	/** First non-air position above the motion-blocking surface. */
	public static BlockPos surface(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}

	public static boolean exposedToSky(ServerPlayer player) {
		return player.level().canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	public static String formatTime(long ticks) {
		long seconds = Math.max(0, (ticks + 19) / 20);
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	public static String humanTime(long ticks) {
		long seconds = Math.max(0, ticks / 20);
		if (seconds >= 120) return (seconds / 60) + " minutes";
		if (seconds >= 60) return "1 minute " + (seconds % 60) + " seconds";
		return seconds + " seconds";
	}
}
