package com.zerohour;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent per-world state of the apocalypse, stored with the overworld. */
public class ApocalypseData extends SavedData {
	public static final SavedDataType<ApocalypseData> TYPE = new SavedDataType<>(
		"zerohour_apocalypse",
		ApocalypseData::new,
		CompoundTag.CODEC.xmap(ApocalypseData::fromTag, ApocalypseData::toTag),
		null
	);

	/** Nothing irradiated, no infected, no scorched land within this many blocks of the safe zone. */
	public static final int SAFE_RADIUS = 96;

	public Stage stage = Stage.IDLE;
	/** Ticks elapsed in the pre-strike build-up. */
	public long elapsed;
	/** Total length of the build-up in ticks (never shown to players). */
	public long duration = 20L * 60 * 60;
	/** Ticks spent in the current stage. */
	public int stageTicks;
	public boolean paused;

	// Configuration (changed with /apocalypse config)
	public float requiredShielding = 24f;
	public int falloutSeconds = 360;
	public int evacMinutes = 12;
	public int infectionMinutes = 6;
	/** How far from the players the warhead comes down. */
	public int strikeDistance = 140;
	public boolean griefing = true;
	public boolean spectateOnDeath = true;
	public boolean reviveOnEvacuation = true;
	public boolean shelling = true;
	public boolean infected = true;
	/** Start by itself the first time the server starts. */
	public boolean autoStart = true;
	/** Length of an automatically started run, in minutes of play. */
	public int defaultMinutes = 60;
	/** Whether a run has ever been started on this world (so auto-start only happens once). */
	public boolean started;

	/** Whether we turned off the advance_time gamerule and must restore it. */
	public boolean timeLocked;
	public boolean savedAdvanceTime = true;

	/** Players who died during the detonation or fallout and are waiting to be found again. */
	public final Set<UUID> fallen = new HashSet<>();
	/** Number of players who were alive and sheltered when the bomb went off. */
	public int survivorCount;
	public int victimCount;
	/** One-shot timeline events already fired (broadcasts etc.). */
	public final Set<String> fired = new HashSet<>();
	/** Chunks already burned by the thermal pulse. */
	public final LongOpenHashSet scorched = new LongOpenHashSet();
	/** Chunks of the crater already blasted out. */
	public final LongOpenHashSet cratered = new LongOpenHashSet();
	/** Players who already received their survival kit. */
	public final Set<UUID> kitted = new HashSet<>();
	/** The last emergency broadcast, so radios can replay it. */
	public int lastStyle = -1;
	public String lastHeader = "";
	public String lastBody = "";

	/** Ground zero. */
	public boolean hasStrike;
	public int strikeX;
	public int strikeY;
	public int strikeZ;
	/** Ticks since the bomb went off; drives how fast the radiation decays. */
	public long postTicks;

	/** The evacuation landing zone. */
	public boolean hasLz;
	public int lzX;
	public int lzY;
	public int lzZ;
	public boolean lzBuilt;
	/** Ticks the evacuation window has been open. */
	public int evacTicks;
	public boolean evacClosed;

	/** Where the helicopters take people: clean land, far from the blast. */
	public boolean hasSafeZone;
	public int safeX;
	public int safeY;
	public int safeZ;
	public boolean safeBuilt;
	public final Set<UUID> evacuated = new HashSet<>();

	/** Absorbed radiation in rads, per player. */
	public final Map<UUID, Float> dose = new HashMap<>();
	/** Ticks since each infected player was bitten. */
	public final Map<UUID, Integer> infection = new HashMap<>();
	/** Ticks of iodine protection left, per player. */
	public final Map<UUID, Integer> iodine = new HashMap<>();

	public float progress() {
		return duration <= 0 ? 1f : Math.min(1f, (float) elapsed / duration);
	}

	/** Ticks since the current stage began. Countdown stages derive this from the clock so jumps stay consistent. */
	public int ticksInStage() {
		if (stage.isCountdown()) return (int) Math.max(0, elapsed - (long) (stage.start * duration));
		return stageTicks;
	}

	/** How far through the current stage we are, 0..1. */
	public float stageFraction() {
		if (stage.isCountdown()) {
			float len = (stage.end - stage.start) * duration;
			return len <= 0 ? 1f : Math.min(1f, ticksInStage() / len);
		}
		if (stage == Stage.DETONATION) return stageTicks / (float) Stage.DETONATION_TICKS;
		if (stage == Stage.FALLOUT) return Math.min(1f, stageTicks / (float) Math.max(1, falloutSeconds * 20));
		if (stage == Stage.EVACUATION) return Math.min(1f, evacTicks / (float) Math.max(1, evacMinutes * 1200));
		return 0f;
	}

	public long ticksRemaining() {
		return Math.max(0, duration - elapsed);
	}

	public int evacTicksLeft() {
		return Math.max(0, evacMinutes * 1200 - evacTicks);
	}

	public boolean evacOpen() {
		return stage == Stage.EVACUATION && hasLz && !evacClosed;
	}

	public boolean inSafeZone(double x, double z) {
		if (!hasSafeZone) return false;
		double dx = x - safeX;
		double dz = z - safeZ;
		return dx * dx + dz * dz < (double) SAFE_RADIUS * SAFE_RADIUS;
	}

	public float doseOf(UUID id) {
		return dose.getOrDefault(id, 0f);
	}

	public boolean isInfected(UUID id) {
		return infection.containsKey(id);
	}

	/** How far along an infection is, 0..1, or -1 if the player is not infected. */
	public float infectionProgress(UUID id) {
		Integer ticks = infection.get(id);
		if (ticks == null) return -1f;
		return Math.min(1f, ticks / (float) Math.max(1, infectionMinutes * 1200));
	}

	/** Forget everything a player carried in their body (on death, cure-all, or a new run). */
	public void clearBody(UUID id) {
		dose.remove(id);
		infection.remove(id);
		iodine.remove(id);
		setDirty();
	}

	public void resetRun() {
		stage = Stage.IDLE;
		elapsed = 0;
		stageTicks = 0;
		paused = false;
		fallen.clear();
		fired.clear();
		survivorCount = 0;
		victimCount = 0;
		lastStyle = -1;
		lastHeader = "";
		lastBody = "";
		hasStrike = false;
		postTicks = 0;
		hasLz = false;
		lzBuilt = false;
		evacTicks = 0;
		evacClosed = false;
		hasSafeZone = false;
		safeBuilt = false;
		evacuated.clear();
		dose.clear();
		infection.clear();
		iodine.clear();
		setDirty();
	}

	private static Set<UUID> readUuids(CompoundTag tag, String key, Set<UUID> into) {
		for (Tag t : tag.getListOrEmpty(key)) {
			t.asString().ifPresent(s -> {
				try {
					into.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
				}
			});
		}
		return into;
	}

	private static ListTag writeUuids(Set<UUID> ids) {
		ListTag list = new ListTag();
		for (UUID id : ids) list.add(StringTag.valueOf(id.toString()));
		return list;
	}

	private static UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static ApocalypseData fromTag(CompoundTag tag) {
		ApocalypseData data = new ApocalypseData();
		data.stage = Stage.byId(tag.getIntOr("stage", 0));
		data.elapsed = tag.getLongOr("elapsed", 0);
		data.duration = tag.getLongOr("duration", data.duration);
		data.stageTicks = tag.getIntOr("stageTicks", 0);
		data.paused = tag.getBooleanOr("paused", false);
		data.requiredShielding = tag.getFloatOr("requiredShielding", 24f);
		data.falloutSeconds = tag.getIntOr("falloutSeconds", 360);
		data.evacMinutes = tag.getIntOr("evacMinutes", 12);
		data.infectionMinutes = tag.getIntOr("infectionMinutes", 6);
		data.strikeDistance = tag.getIntOr("strikeDistance", 140);
		data.griefing = tag.getBooleanOr("griefing", true);
		data.spectateOnDeath = tag.getBooleanOr("spectateOnDeath", true);
		data.reviveOnEvacuation = tag.getBooleanOr("reviveOnEvacuation", true);
		data.shelling = tag.getBooleanOr("shelling", true);
		data.infected = tag.getBooleanOr("infected", true);
		data.autoStart = tag.getBooleanOr("autoStart", true);
		data.defaultMinutes = tag.getIntOr("defaultMinutes", 60);
		data.started = tag.getBooleanOr("started", false);
		data.lastStyle = tag.getIntOr("lastStyle", -1);
		data.lastHeader = tag.getStringOr("lastHeader", "");
		data.lastBody = tag.getStringOr("lastBody", "");
		data.timeLocked = tag.getBooleanOr("timeLocked", false);
		data.savedAdvanceTime = tag.getBooleanOr("savedAdvanceTime", true);
		data.survivorCount = tag.getIntOr("survivorCount", 0);
		data.victimCount = tag.getIntOr("victimCount", 0);
		readUuids(tag, "kitted", data.kitted);
		readUuids(tag, "fallen", data.fallen);
		readUuids(tag, "evacuated", data.evacuated);
		for (Tag t : tag.getListOrEmpty("fired")) {
			t.asString().ifPresent(data.fired::add);
		}
		tag.getLongArray("scorched").ifPresent(arr -> {
			for (long l : arr) data.scorched.add(l);
		});
		tag.getLongArray("cratered").ifPresent(arr -> {
			for (long l : arr) data.cratered.add(l);
		});

		data.hasStrike = tag.getBooleanOr("hasStrike", false);
		data.strikeX = tag.getIntOr("strikeX", 0);
		data.strikeY = tag.getIntOr("strikeY", 64);
		data.strikeZ = tag.getIntOr("strikeZ", 0);
		data.postTicks = tag.getLongOr("postTicks", 0);
		data.hasLz = tag.getBooleanOr("hasLz", false);
		data.lzX = tag.getIntOr("lzX", 0);
		data.lzY = tag.getIntOr("lzY", 64);
		data.lzZ = tag.getIntOr("lzZ", 0);
		data.lzBuilt = tag.getBooleanOr("lzBuilt", false);
		data.evacTicks = tag.getIntOr("evacTicks", 0);
		data.evacClosed = tag.getBooleanOr("evacClosed", false);
		data.hasSafeZone = tag.getBooleanOr("hasSafeZone", false);
		data.safeX = tag.getIntOr("safeX", 0);
		data.safeY = tag.getIntOr("safeY", 64);
		data.safeZ = tag.getIntOr("safeZ", 0);
		data.safeBuilt = tag.getBooleanOr("safeBuilt", false);

		// Per-player body state: a list of players plus parallel arrays (dose in thousandths of a rad, -1 = none).
		List<UUID> bodies = new ArrayList<>();
		for (Tag t : tag.getListOrEmpty("bodies")) {
			t.asString().ifPresent(s -> bodies.add(parse(s)));
		}
		long[] doses = tag.getLongArray("bodyDose").orElse(new long[0]);
		long[] infections = tag.getLongArray("bodyInfection").orElse(new long[0]);
		long[] iodines = tag.getLongArray("bodyIodine").orElse(new long[0]);
		for (int i = 0; i < bodies.size(); i++) {
			UUID id = bodies.get(i);
			if (id == null) continue;
			if (i < doses.length && doses[i] >= 0) data.dose.put(id, doses[i] / 1000f);
			if (i < infections.length && infections[i] >= 0) data.infection.put(id, (int) infections[i]);
			if (i < iodines.length && iodines[i] >= 0) data.iodine.put(id, (int) iodines[i]);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("stage", stage.ordinal());
		tag.putLong("elapsed", elapsed);
		tag.putLong("duration", duration);
		tag.putInt("stageTicks", stageTicks);
		tag.putBoolean("paused", paused);
		tag.putFloat("requiredShielding", requiredShielding);
		tag.putInt("falloutSeconds", falloutSeconds);
		tag.putInt("evacMinutes", evacMinutes);
		tag.putInt("infectionMinutes", infectionMinutes);
		tag.putInt("strikeDistance", strikeDistance);
		tag.putBoolean("griefing", griefing);
		tag.putBoolean("spectateOnDeath", spectateOnDeath);
		tag.putBoolean("reviveOnEvacuation", reviveOnEvacuation);
		tag.putBoolean("shelling", shelling);
		tag.putBoolean("infected", infected);
		tag.putBoolean("autoStart", autoStart);
		tag.putInt("defaultMinutes", defaultMinutes);
		tag.putBoolean("started", started);
		tag.putInt("lastStyle", lastStyle);
		tag.putString("lastHeader", lastHeader);
		tag.putString("lastBody", lastBody);
		tag.putBoolean("timeLocked", timeLocked);
		tag.putBoolean("savedAdvanceTime", savedAdvanceTime);
		tag.putInt("survivorCount", survivorCount);
		tag.putInt("victimCount", victimCount);
		tag.put("kitted", writeUuids(kitted));
		tag.put("fallen", writeUuids(fallen));
		tag.put("evacuated", writeUuids(evacuated));
		ListTag firedList = new ListTag();
		for (String s : fired) firedList.add(StringTag.valueOf(s));
		tag.put("fired", firedList);
		tag.putLongArray("scorched", scorched.toLongArray());
		tag.putLongArray("cratered", cratered.toLongArray());

		tag.putBoolean("hasStrike", hasStrike);
		tag.putInt("strikeX", strikeX);
		tag.putInt("strikeY", strikeY);
		tag.putInt("strikeZ", strikeZ);
		tag.putLong("postTicks", postTicks);
		tag.putBoolean("hasLz", hasLz);
		tag.putInt("lzX", lzX);
		tag.putInt("lzY", lzY);
		tag.putInt("lzZ", lzZ);
		tag.putBoolean("lzBuilt", lzBuilt);
		tag.putInt("evacTicks", evacTicks);
		tag.putBoolean("evacClosed", evacClosed);
		tag.putBoolean("hasSafeZone", hasSafeZone);
		tag.putInt("safeX", safeX);
		tag.putInt("safeY", safeY);
		tag.putInt("safeZ", safeZ);
		tag.putBoolean("safeBuilt", safeBuilt);

		List<UUID> bodies = new ArrayList<>(dose.keySet());
		for (UUID id : infection.keySet()) if (!bodies.contains(id)) bodies.add(id);
		for (UUID id : iodine.keySet()) if (!bodies.contains(id)) bodies.add(id);
		ListTag bodyList = new ListTag();
		long[] doses = new long[bodies.size()];
		long[] infections = new long[bodies.size()];
		long[] iodines = new long[bodies.size()];
		for (int i = 0; i < bodies.size(); i++) {
			UUID id = bodies.get(i);
			bodyList.add(StringTag.valueOf(id.toString()));
			Float d = dose.get(id);
			Integer inf = infection.get(id);
			Integer io = iodine.get(id);
			doses[i] = d == null ? -1 : Math.round(d * 1000.0);
			infections[i] = inf == null ? -1 : inf;
			iodines[i] = io == null ? -1 : io;
		}
		tag.put("bodies", bodyList);
		tag.putLongArray("bodyDose", doses);
		tag.putLongArray("bodyInfection", infections);
		tag.putLongArray("bodyIodine", iodines);
		return tag;
	}
}
