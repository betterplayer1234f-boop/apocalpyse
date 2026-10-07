package com.lastsunset;

import com.lastsunset.content.ModItems;
import com.lastsunset.network.CuePayload;
import com.lastsunset.network.RosterPayload;
import com.lastsunset.network.SyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** Runs the apocalypse on the server: the clock, stage changes, time-of-day control, deaths and revivals. */
public final class ApocalypseManager {
	public static final RandomSource RANDOM = RandomSource.create();

	private static @Nullable MinecraftServer server;
	private static @Nullable ApocalypseData data;
	private static final List<ServerTask> tasks = new ArrayList<>();
	private static final List<ServerTask> pendingTasks = new ArrayList<>();
	private static final Map<UUID, Shelter.Result> lastShelter = new HashMap<>();
	private static int lapseSpeed;

	private ApocalypseManager() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			server = srv;
			data = srv.overworld().getDataStorage().computeIfAbsent(ApocalypseData.TYPE);
			// The end begins the moment the world does. No announcement, no timer: only signs.
			if (data.autoStart && !data.started && data.stage == Stage.IDLE) {
				LastSunset.LOGGER.info("The sun has begun to change ({} minutes of play until the flare).", data.defaultMinutes);
				start(data.defaultMinutes);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			Scorcher.reset();
			tasks.clear();
			pendingTasks.clear();
			lastShelter.clear();
			server = null;
			data = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(ApocalypseManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> onJoin(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register(ApocalypseManager::onDeath);
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
		float required = data != null ? data.requiredShielding : 30f;
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
			case OMENS, EVACUATION, COLLAPSE, FINAL_MINUTES -> {
				if (running) {
					data.elapsed++;
					data.stageTicks++;
				}
				Stage target = data.elapsed >= data.duration ? Stage.IMPACT : Stage.forProgress(data.progress());
				if (target != stage) enterStage(target);
			}
			case IMPACT -> {
				// The flare does not wait for anyone.
				data.stageTicks++;
				ImpactSequence.tick(srv, data, data.stageTicks);
				if (data.stageTicks >= Stage.IMPACT_TICKS) enterStage(Stage.AFTERMATH);
			}
			case AFTERMATH -> {
				if (running) data.stageTicks++;
				if (data.stageTicks >= data.aftermathSeconds * 20) enterStage(Stage.WASTELAND);
			}
			case WASTELAND -> {
				if (data.stageTicks < 1_000_000_000) data.stageTicks++;
			}
			default -> {
			}
		}

		stage = data.stage;
		ServerLevel overworld = srv.overworld();
		if (running || stage == Stage.IMPACT) {
			WorldEffects.tick(srv, overworld, data, stage);
			Broadcasts.tick(srv, data);
		}
		tickTime(srv, overworld, stage);

		if (srv.getTickCount() % 20 == 0) {
			updateShelters(srv);
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
			case IMPACT -> ImpactSequence.begin(server, data);
			case AFTERMATH -> ImpactSequence.summarize(server, data);
			case WASTELAND -> dawn(server, data);
			default -> {
			}
		}
		syncAll();
		Broadcasts.onStage(server, data, next);
		if (next == Stage.EVACUATION || next == Stage.FINAL_MINUTES) {
			Fx.sendAll(server, new CuePayload(CuePayload.SIREN, next == Stage.FINAL_MINUTES ? 1f : 0.6f, 0));
		}
	}

	// ------------------------------------------------------------------ time of day

	private static void tickTime(MinecraftServer srv, ServerLevel level, Stage stage) {
		if (data == null) return;
		long target = switch (stage) {
			// The sun freezes low in the western sky: the last sunset.
			case COLLAPSE, FINAL_MINUTES, IMPACT -> 11000L;
			// Ash blots out the sky.
			case AFTERMATH -> 15500L;
			// Fast-forward to a sunrise, then let time run again.
			case WASTELAND -> data.timeLocked ? 23300L : -1L;
			default -> -1L;
		};
		if (target < 0) {
			// e.g. a new run started while Day One's sunrise was still fast-forwarding.
			unlockTime(srv);
			return;
		}
		lockTime(srv, level);
		long now = level.getDayTime();
		long delta = Math.floorMod(target - Math.floorMod(now, 24000L), 24000L);
		if (delta == 0) {
			if (stage == Stage.WASTELAND) unlockTime(srv);
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

	private static void updateShelters(MinecraftServer srv) {
		if (data == null) return;
		List<RosterPayload.Entry> entries = new ArrayList<>();
		for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
			int status;
			int percent = 0;
			if (data.fallen.contains(player.getUUID())) {
				status = RosterPayload.FALLEN;
			} else if (isObserver(player)) {
				status = RosterPayload.OBSERVER;
			} else {
				Shelter.Result result = shelterOf(player);
				lastShelter.put(player.getUUID(), result);
				status = result.status().ordinal();
				percent = result.percent();
			}
			entries.add(new RosterPayload.Entry(player.getName().getString(), status, percent));
		}
		Fx.sendAll(srv, new RosterPayload(entries));
	}

	private static SyncPayload syncPayload() {
		ApocalypseData d = data;
		if (d == null) return new SyncPayload(0, 0, 1, 0, 0, false, 30f);
		int stageLength = switch (d.stage) {
			case OMENS, EVACUATION, COLLAPSE, FINAL_MINUTES -> (int) ((d.stage.end - d.stage.start) * d.duration);
			case IMPACT -> Stage.IMPACT_TICKS;
			case AFTERMATH -> d.aftermathSeconds * 20;
			default -> 0;
		};
		return new SyncPayload(d.stage.ordinal(), d.elapsed, d.duration, d.ticksInStage(), stageLength, d.paused, d.requiredShielding);
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
			if (data.stage == Stage.WASTELAND && data.reviveAtDawn) {
				revive(player);
			} else if (data.stage == Stage.IMPACT || data.stage == Stage.AFTERMATH) {
				player.setGameMode(GameType.SPECTATOR);
			}
		}
	}

	/** Everyone starts with an old emergency radio, a heliometer and a couple of rations. */
	private static void giveKit(ServerPlayer player) {
		if (data == null || !data.kitted.add(player.getUUID())) return;
		data.setDirty();
		for (ItemStack stack : List.of(new ItemStack(ModItems.EMERGENCY_RADIO), new ItemStack(ModItems.HELIOMETER), new ItemStack(ModItems.EMERGENCY_RATION, 2))) {
			if (!player.getInventory().add(stack)) player.drop(stack, false);
		}
		player.sendSystemMessage(Component.literal("You find an old survival kit in your pack: a radio, a heliometer, and two ration tins.")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (data == null || !(entity instanceof ServerPlayer player)) return;
		if ((data.stage == Stage.IMPACT || data.stage == Stage.AFTERMATH) && data.spectateOnDeath) {
			if (data.fallen.add(player.getUUID())) {
				data.setDirty();
				player.level().getServer().getPlayerList().broadcastSystemMessage(
					Component.literal("✝ " + player.getName().getString() + " will not see the dawn.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
					false
				);
			}
		}
	}

	private static void onRespawn(ServerPlayer player) {
		if (data == null || !data.fallen.contains(player.getUUID())) return;
		if (data.stage == Stage.IMPACT || data.stage == Stage.AFTERMATH) {
			player.setGameMode(GameType.SPECTATOR);
			ServerPlayer survivor = randomSurvivor(player);
			if (survivor != null) teleportTo(player, survivor);
		} else if (data.stage == Stage.WASTELAND && data.reviveAtDawn) {
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

	private static void teleportTo(ServerPlayer player, ServerPlayer target) {
		player.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), target.getYRot(), target.getXRot(), false);
	}

	public static void revive(ServerPlayer player) {
		if (data == null) return;
		data.fallen.remove(player.getUUID());
		data.setDirty();
		if (player.isSpectator()) {
			player.setGameMode(GameType.SURVIVAL);
			ServerPlayer survivor = randomSurvivor(player);
			if (survivor != null) teleportTo(player, survivor);
		}
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
		Fx.sound(player.level(), player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, 1f, 0.8f);
		player.sendSystemMessage(Component.literal("You rise from the ashes.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private static void dawn(MinecraftServer srv, ApocalypseData d) {
		Fx.sendAll(srv, new CuePayload(CuePayload.DAWN, 1f, 0));
		if (d.reviveAtDawn) {
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
				if (d.fallen.contains(p.getUUID())) revive(p);
			}
		}
	}

	// ------------------------------------------------------------------ commands

	public static void start(int minutes) {
		if (data == null) return;
		data.resetRun();
		data.started = true;
		data.duration = Math.max(1, minutes) * 1200L;
		enterStage(Stage.OMENS);
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
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (data.fallen.contains(p.getUUID()) && p.isSpectator()) p.setGameMode(GameType.SURVIVAL);
		}
		tasks.clear();
		pendingTasks.clear();
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
			case OMENS, EVACUATION, COLLAPSE, FINAL_MINUTES -> {
				data.elapsed = Math.max(data.elapsed, (long) Math.ceil(data.stage.end * data.duration));
			}
			case IMPACT -> data.stageTicks = Stage.IMPACT_TICKS;
			case AFTERMATH -> data.stageTicks = data.aftermathSeconds * 20;
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
