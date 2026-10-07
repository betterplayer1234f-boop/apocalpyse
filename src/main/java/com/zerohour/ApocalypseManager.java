package com.zerohour;

import com.zerohour.content.ModItems;
import com.zerohour.network.CuePayload;
import com.zerohour.network.RosterPayload;
import com.zerohour.network.SyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/** Runs the apocalypse on the server: the clock, stage changes, time of day, weather, deaths and revivals. */
public final class ApocalypseManager {
	public static final RandomSource RANDOM = RandomSource.create();

	private static @Nullable MinecraftServer server;
	private static @Nullable ApocalypseData data;
	private static final List<ServerTask> tasks = new ArrayList<>();
	private static final List<ServerTask> pendingTasks = new ArrayList<>();
	private static int lapseSpeed;

	private ApocalypseManager() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			server = srv;
			data = srv.overworld().getDataStorage().computeIfAbsent(ApocalypseData.TYPE);
			// The end begins the moment the world does. No announcement, no timer: only the news.
			if (data.autoStart && !data.started && data.stage == Stage.IDLE) {
				ZeroHour.LOGGER.info("Tensions are rising ({} minutes of play until the strike).", data.defaultMinutes);
				start(data.defaultMinutes);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			Scorcher.reset();
			Crater.reset();
			Horde.reset();
			Radiation.reset();
			Evacuation.reset();
			tasks.clear();
			pendingTasks.clear();
			server = null;
			data = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(ApocalypseManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> onJoin(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register(ApocalypseManager::onDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (data != null && !blocked && damage > 0f) Infection.onDamage(entity, source, data);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(newPlayer));
	}

	public static @Nullable ApocalypseData data() {
		return data;
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	public static void addTask(ServerTask task) {
		pendingTasks.add(task);
	}

	public static Shelter.Result shelterOf(ServerPlayer player) {
		float required = data != null ? data.requiredShielding : 24f;
		return Shelter.evaluate(player.level(), BlockPos.containing(player.getEyePosition()), required);
	}

	public static boolean isObserver(ServerPlayer player) {
		return player.isCreative() || player.isSpectator();
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer srv) {
		if (data == null) return;
		tasks.addAll(pendingTasks);
		pendingTasks.clear();
		tasks.removeIf(ServerTask::tick);

		Stage stage = data.stage;
		if (stage == Stage.IDLE) return;
		boolean anyone = !srv.getPlayerList().getPlayers().isEmpty();
		boolean running = anyone && !data.paused;
		switch (stage) {
			case TENSIONS, OUTBREAK, LAUNCH, INCOMING -> {
				if (running) {
					data.elapsed++;
					data.stageTicks++;
				}
				Stage target = data.elapsed >= data.duration ? Stage.DETONATION : Stage.forProgress(data.progress());
				if (target != stage) enterStage(target);
			}
			case DETONATION -> {
				// The bomb does not wait for anyone.
				data.stageTicks++;
				if (data.stageTicks > Stage.FLASH_TICK) data.postTicks++;
				Detonation.tick(srv, data, data.stageTicks);
				if (data.stageTicks >= Stage.DETONATION_TICKS) enterStage(Stage.FALLOUT);
			}
			case FALLOUT -> {
				if (running) {
					data.stageTicks++;
					data.postTicks++;
				}
				if (data.stageTicks >= data.falloutSeconds * 20) enterStage(Stage.EVACUATION);
			}
			case EVACUATION -> {
				if (running) {
					if (data.stageTicks < 1_000_000_000) data.stageTicks++;
					data.postTicks++;
				}
				Evacuation.tick(srv, data, running);
			}
			default -> {
			}
		}

		stage = data.stage;
		ServerLevel overworld = srv.overworld();
		boolean live = running || stage == Stage.DETONATION;
		if (live) {
			WorldEffects.tick(srv, overworld, data, stage);
			Broadcasts.tick(srv, data);
			Horde.tick(overworld, data, stage);
			Crater.tick(overworld, data);
		}
		tickTime(srv, overworld, stage);

		if (srv.getTickCount() % 20 == 0) {
			Radiation.tick(srv, data, live);
			if (live) Infection.tick(srv, data);
			updateRoster(srv);
			syncAll();
			data.setDirty();
		}
	}

	public static void enterStage(Stage next) {
		if (data == null || server == null) return;
		data.stage = next;
		data.stageTicks = 0;
		data.setDirty();
		lapseSpeed = 0;
		switch (next) {
			case DETONATION -> Detonation.begin(server, data);
			case FALLOUT -> {
				Detonation.summarize(server, data);
				// Black rain: the fallout comes down with it.
				setWeather(server.overworld(), true, data.falloutSeconds * 20 + 2400);
			}
			case EVACUATION -> {
				setWeather(server.overworld(), false, 12000);
				reviveFallen(server, data);
				Evacuation.begin(server, data);
			}
			default -> {
			}
		}
		syncAll();
		Broadcasts.onStage(server, data, next);
		if (next == Stage.LAUNCH || next == Stage.INCOMING) {
			Fx.sendAll(server, new CuePayload(CuePayload.SIREN, next == Stage.INCOMING ? 1f : 0.6f, 0));
		}
	}

	private static void setWeather(ServerLevel level, boolean rain, int ticks) {
		level.setWeatherParameters(rain ? 0 : ticks, rain ? ticks : 0, rain, false);
	}

	// ------------------------------------------------------------------ time of day

	private static void tickTime(MinecraftServer srv, ServerLevel level, Stage stage) {
		if (data == null) return;
		long target = switch (stage) {
			// Smoke and fallout blot out the sun.
			case FALLOUT -> 14500L;
			// Fast-forward to a grey sunrise for the evacuation, then let time run again.
			case EVACUATION -> data.timeLocked ? 23300L : -1L;
			default -> -1L;
		};
		if (target < 0) {
			// e.g. a new run started while the evacuation sunrise was still fast-forwarding.
			unlockTime(srv);
			return;
		}
		lockTime(srv, level);
		long now = level.getDayTime();
		long delta = Math.floorMod(target - Math.floorMod(now, 24000L), 24000L);
		if (delta == 0) {
			if (stage == Stage.EVACUATION) unlockTime(srv);
			return;
		}
		if (lapseSpeed <= 0) lapseSpeed = (int) Math.max(8, delta / 160);
		level.setDayTime(now + Math.min(delta, lapseSpeed));
		srv.getPlayerList().broadcastAll(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), false), Level.OVERWORLD);
	}

	private static void lockTime(MinecraftServer srv, ServerLevel level) {
		if (data == null || data.timeLocked) return;
		data.savedAdvanceTime = level.getGameRules().get(GameRules.ADVANCE_TIME);
		level.getGameRules().set(GameRules.ADVANCE_TIME, false, srv);
		data.timeLocked = true;
		data.setDirty();
	}

	private static void unlockTime(MinecraftServer srv) {
		if (data == null || !data.timeLocked) return;
		srv.overworld().getGameRules().set(GameRules.ADVANCE_TIME, data.savedAdvanceTime, srv);
		data.timeLocked = false;
		data.setDirty();
	}

	// ------------------------------------------------------------------ syncing

	private static void updateRoster(MinecraftServer srv) {
		if (data == null) return;
		List<RosterPayload.Entry> entries = new ArrayList<>();
		boolean blastPhase = data.stage.isCountdown() || data.stage == Stage.DETONATION && data.stageTicks <= Stage.FLASH_TICK;
		for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
			int status;
			int value = 0;
			if (data.fallen.contains(player.getUUID())) {
				status = RosterPayload.FALLEN;
			} else if (isObserver(player)) {
				status = RosterPayload.OBSERVER;
			} else if (data.evacuated.contains(player.getUUID())) {
				status = RosterPayload.EVACUATED;
			} else if (data.isInfected(player.getUUID())) {
				status = RosterPayload.INFECTED;
				value = Math.round(data.infectionProgress(player.getUUID()) * 100f);
			} else if (blastPhase) {
				Shelter.Result result = shelterOf(player);
				status = result.status().ordinal();
				value = result.percent();
			} else {
				status = RosterPayload.ALIVE;
				value = Math.round(data.doseOf(player.getUUID()));
			}
			entries.add(new RosterPayload.Entry(player.getName().getString(), status, value));
		}
		Fx.sendAll(srv, new RosterPayload(entries));
	}

	private static SyncPayload syncPayload() {
		ApocalypseData d = data;
		if (d == null) return SyncPayload.EMPTY;
		int stageLength = switch (d.stage) {
			case TENSIONS, OUTBREAK, LAUNCH, INCOMING -> (int) ((d.stage.end - d.stage.start) * d.duration);
			case DETONATION -> Stage.DETONATION_TICKS;
			case FALLOUT -> d.falloutSeconds * 20;
			default -> 0;
		};
		return new SyncPayload(d.stage.ordinal(), d.elapsed, d.duration, d.ticksInStage(), stageLength, d.paused, d.requiredShielding,
			d.hasStrike, d.strikeX, d.strikeY, d.strikeZ,
			d.hasLz, d.lzX, d.lzY, d.lzZ, d.evacOpen(), d.evacTicksLeft(),
			d.hasSafeZone, d.safeX, d.safeZ);
	}

	public static void syncAll() {
		if (server != null) Fx.sendAll(server, syncPayload());
	}

	// ------------------------------------------------------------------ players

	private static void onJoin(ServerPlayer player) {
		if (data == null) return;
		Fx.send(player, syncPayload());
		giveKit(player);
		if (data.fallen.contains(player.getUUID())) {
			if (data.stage == Stage.EVACUATION && data.reviveOnEvacuation) {
				revive(player);
			} else if (data.stage == Stage.DETONATION || data.stage == Stage.FALLOUT) {
				player.setGameMode(GameType.SPECTATOR);
			}
		}
	}

	/** Everyone starts with an old civil-defence kit. */
	private static void giveKit(ServerPlayer player) {
		if (data == null || !data.kitted.add(player.getUUID())) return;
		data.setDirty();
		for (ItemStack stack : List.of(new ItemStack(ModItems.EMERGENCY_RADIO), new ItemStack(ModItems.GEIGER_COUNTER),
			new ItemStack(ModItems.IODINE_TABLETS, 3), new ItemStack(ModItems.EMERGENCY_RATION, 2))) {
			if (!player.getInventory().add(stack)) player.drop(stack, false);
		}
		player.sendSystemMessage(Component.literal("You find an old civil-defence kit in your pack: a radio, a Geiger counter, iodine tablets and two ration tins.")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (data == null) return;
		if (!(entity instanceof ServerPlayer player)) {
			Horde.forget(entity);
			return;
		}
		// Bitten, or killed by one of them: they get back up.
		Infection.onPlayerDeath(player, source, data);
		data.clearBody(player.getUUID());
		if ((data.stage == Stage.DETONATION || data.stage == Stage.FALLOUT) && data.spectateOnDeath) {
			if (data.fallen.add(player.getUUID())) {
				data.setDirty();
				player.level().getServer().getPlayerList().broadcastSystemMessage(
					Component.literal("✝ " + player.getName().getString() + " is lost in the fallout.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
					false
				);
			}
		}
	}

	private static void onRespawn(ServerPlayer player) {
		if (data == null || !data.fallen.contains(player.getUUID())) return;
		if (data.stage == Stage.DETONATION || data.stage == Stage.FALLOUT) {
			player.setGameMode(GameType.SPECTATOR);
			ServerPlayer survivor = randomSurvivor(player);
			if (survivor != null) teleportTo(player, survivor);
		} else if (data.stage == Stage.EVACUATION && data.reviveOnEvacuation) {
			revive(player);
		}
	}

	public static @Nullable ServerPlayer randomSurvivor(@Nullable ServerPlayer exclude) {
		if (server == null || data == null) return null;
		List<ServerPlayer> alive = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p != exclude && p.isAlive() && !p.isSpectator() && !data.fallen.contains(p.getUUID())) alive.add(p);
		}
		return alive.isEmpty() ? null : alive.get(RANDOM.nextInt(alive.size()));
	}

	public static void teleportTo(ServerPlayer player, ServerPlayer target) {
		player.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), target.getYRot(), target.getXRot(), false);
	}

	public static void revive(ServerPlayer player) {
		if (data == null) return;
		data.fallen.remove(player.getUUID());
		data.clearBody(player.getUUID());
		if (player.isSpectator()) {
			player.setGameMode(GameType.SURVIVAL);
			ServerPlayer survivor = randomSurvivor(player);
			if (survivor != null) teleportTo(player, survivor);
		}
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		Fx.sound(player.level(), player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, 1f, 0.8f);
		player.sendSystemMessage(Component.literal("Survivors dig you out of the rubble. You're alive.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private static void reviveFallen(MinecraftServer srv, ApocalypseData d) {
		if (!d.reviveOnEvacuation) return;
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
			if (d.fallen.contains(p.getUUID())) revive(p);
		}
	}

	// ------------------------------------------------------------------ commands

	public static void start(int minutes) {
		if (data == null) return;
		Horde.reset();
		data.resetRun();
		data.started = true;
		data.duration = Math.max(1, minutes) * 1200L;
		enterStage(Stage.TENSIONS);
	}

	/** Change the length of the build-up, keeping how far along it already is. */
	public static void setDuration(int minutes) {
		if (data == null) return;
		data.defaultMinutes = Math.max(1, minutes);
		if (data.stage.isCountdown()) {
			float progress = data.progress();
			data.duration = data.defaultMinutes * 1200L;
			data.elapsed = (long) (progress * data.duration);
		}
		data.setDirty();
		syncAll();
	}

	public static void stop() {
		if (data == null || server == null) return;
		unlockTime(server);
		if (data.stage == Stage.FALLOUT) setWeather(server.overworld(), false, 6000);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (data.fallen.contains(p.getUUID()) && p.isSpectator()) p.setGameMode(GameType.SURVIVAL);
		}
		tasks.clear();
		pendingTasks.clear();
		Horde.reset();
		data.resetRun();
		// An operator called it off: don't start again by itself on the next restart.
		data.autoStart = false;
		syncAll();
	}

	public static void setPaused(boolean paused) {
		if (data == null) return;
		data.paused = paused;
		data.setDirty();
		syncAll();
	}

	public static void skip() {
		if (data == null) return;
		switch (data.stage) {
			case TENSIONS, OUTBREAK, LAUNCH, INCOMING -> {
				data.elapsed = Math.max(data.elapsed, (long) Math.ceil(data.stage.end * data.duration));
			}
			case DETONATION -> data.stageTicks = Math.max(data.stageTicks, Stage.DETONATION_TICKS - 1);
			case FALLOUT -> data.stageTicks = data.falloutSeconds * 20;
			case EVACUATION -> data.evacTicks = Math.max(data.evacTicks, data.evacMinutes * 1200);
			default -> {
			}
		}
		data.setDirty();
	}

	public static void setRemaining(int seconds) {
		if (data == null || !data.stage.isCountdown()) return;
		data.elapsed = Math.max(0, data.duration - seconds * 20L);
		data.setDirty();
		syncAll();
	}
}
