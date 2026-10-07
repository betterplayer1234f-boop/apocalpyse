package com.lastsunset;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
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
		"lastsunset_apocalypse",
		ApocalypseData::new,
		CompoundTag.CODEC.xmap(ApocalypseData::fromTag, ApocalypseData::toTag),
		null
	);

	public Stage stage = Stage.IDLE;
	/** Ticks elapsed in the pre-impact countdown. */
	public long elapsed;
	/** Total length of the build-up in ticks (never shown to players). */
	public long duration = 20L * 60 * 60;
	/** Ticks spent in the current stage. */
	public int stageTicks;
	public boolean paused;

	// Configuration (changed with /apocalypse config)
	public float requiredShielding = 30f;
	public int aftermathSeconds = 300;
	public boolean griefing = true;
	public boolean spectateOnDeath = true;
	public boolean reviveAtDawn = true;
	public boolean meteors = true;
	/** Start by itself the first time the server starts. */
	public boolean autoStart = true;
	/** Length of an automatically started run, in minutes of play. */
	public int defaultMinutes = 60;
	/** Whether a run has ever been started on this world (so auto-start only happens once). */
	public boolean started;

	/** Whether we turned off the advance_time gamerule and must restore it. */
	public boolean timeLocked;
	public boolean savedAdvanceTime = true;

	/** Players who died during the impact or aftermath and are waiting to be revived. */
	public final Set<UUID> fallen = new HashSet<>();
	/** Number of players who were alive and sheltered when the flare passed. */
	public int survivorCount;
	public int victimCount;
	/** One-shot timeline events already fired (broadcasts etc.). */
	public final Set<String> fired = new HashSet<>();
	/** Chunks already converted to scorched earth. */
	public final LongOpenHashSet scorched = new LongOpenHashSet();
	/** Players who already received their survival kit. */
	public final Set<UUID> kitted = new HashSet<>();
	/** The last emergency broadcast, so radios can replay it. */
	public int lastStyle = -1;
	public String lastHeader = "";
	public String lastBody = "";

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
		if (stage == Stage.IMPACT) return stageTicks / (float) Stage.IMPACT_TICKS;
		if (stage == Stage.AFTERMATH) return Math.min(1f, stageTicks / (float) Math.max(1, aftermathSeconds * 20));
		return 0f;
	}

	public long ticksRemaining() {
		return Math.max(0, duration - elapsed);
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
		setDirty();
	}

	private static ApocalypseData fromTag(CompoundTag tag) {
		ApocalypseData data = new ApocalypseData();
		data.stage = Stage.byId(tag.getIntOr("stage", 0));
		data.elapsed = tag.getLongOr("elapsed", 0);
		data.duration = tag.getLongOr("duration", data.duration);
		data.stageTicks = tag.getIntOr("stageTicks", 0);
		data.paused = tag.getBooleanOr("paused", false);
		data.requiredShielding = tag.getFloatOr("requiredShielding", 30f);
		data.aftermathSeconds = tag.getIntOr("aftermathSeconds", 300);
		data.griefing = tag.getBooleanOr("griefing", true);
		data.spectateOnDeath = tag.getBooleanOr("spectateOnDeath", true);
		data.reviveAtDawn = tag.getBooleanOr("reviveAtDawn", true);
		data.meteors = tag.getBooleanOr("meteors", true);
		data.autoStart = tag.getBooleanOr("autoStart", true);
		data.defaultMinutes = tag.getIntOr("defaultMinutes", 60);
		data.started = tag.getBooleanOr("started", false);
		data.lastStyle = tag.getIntOr("lastStyle", -1);
		data.lastHeader = tag.getStringOr("lastHeader", "");
		data.lastBody = tag.getStringOr("lastBody", "");
		for (Tag t : tag.getListOrEmpty("kitted")) {
			t.asString().ifPresent(s -> {
				try {
					data.kitted.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
				}
			});
		}
		data.timeLocked = tag.getBooleanOr("timeLocked", false);
		data.savedAdvanceTime = tag.getBooleanOr("savedAdvanceTime", true);
		data.survivorCount = tag.getIntOr("survivorCount", 0);
		data.victimCount = tag.getIntOr("victimCount", 0);
		for (Tag t : tag.getListOrEmpty("fallen")) {
			t.asString().ifPresent(s -> {
				try {
					data.fallen.add(UUID.fromString(s));
				} catch (IllegalArgumentException ignored) {
				}
			});
		}
		for (Tag t : tag.getListOrEmpty("fired")) {
			t.asString().ifPresent(data.fired::add);
		}
		tag.getLongArray("scorched").ifPresent(arr -> {
			for (long l : arr) data.scorched.add(l);
		});
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
		tag.putInt("aftermathSeconds", aftermathSeconds);
		tag.putBoolean("griefing", griefing);
		tag.putBoolean("spectateOnDeath", spectateOnDeath);
		tag.putBoolean("reviveAtDawn", reviveAtDawn);
		tag.putBoolean("meteors", meteors);
		tag.putBoolean("autoStart", autoStart);
		tag.putInt("defaultMinutes", defaultMinutes);
		tag.putBoolean("started", started);
		tag.putInt("lastStyle", lastStyle);
		tag.putString("lastHeader", lastHeader);
		tag.putString("lastBody", lastBody);
		ListTag kittedList = new ListTag();
		for (UUID id : kitted) kittedList.add(StringTag.valueOf(id.toString()));
		tag.put("kitted", kittedList);
		tag.putBoolean("timeLocked", timeLocked);
		tag.putBoolean("savedAdvanceTime", savedAdvanceTime);
		tag.putInt("survivorCount", survivorCount);
		tag.putInt("victimCount", victimCount);
		ListTag fallenList = new ListTag();
		for (UUID id : fallen) fallenList.add(StringTag.valueOf(id.toString()));
		tag.put("fallen", fallenList);
		ListTag firedList = new ListTag();
		for (String s : fired) firedList.add(StringTag.valueOf(s));
		tag.put("fired", firedList);
		tag.putLongArray("scorched", scorched.toLongArray());
		return tag;
	}
}
