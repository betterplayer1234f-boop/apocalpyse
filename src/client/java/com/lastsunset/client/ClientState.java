package com.lastsunset.client;

import com.lastsunset.Shelter;
import com.lastsunset.Stage;
import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.network.CuePayload;
import com.lastsunset.network.RosterPayload;
import com.lastsunset.network.SyncPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Client-side mirror of the apocalypse, plus the timers that drive cinematic effects. */
public final class ClientState {
	public static Stage stage = Stage.IDLE;
	public static long elapsed;
	public static long duration = 1;
	public static int stageTicks;
	public static int stageLength;
	public static boolean paused;
	public static float requiredShielding = 30f;
	public static boolean synced;

	public static List<RosterPayload.Entry> roster = List.of();
	public static Shelter.@Nullable Result shelter;
	public static boolean exposedToSky;

	public static int ticks;
	/** Ticks since the impact cue, -1 if none. */
	public static int impactAge = -1;
	/** Shelter status sent with the impact cue: 0 safe, 1 partial, 2 exposed, 3 observer. */
	public static int impactStatus = 3;
	public static int vaporizeAge = -1;
	public static int breachAge = -1;
	public static float breachStrength;
	public static int dawnAge = -1;
	/** Stage title card. */
	public static @Nullable Stage cardStage;
	public static int cardAge = -1;

	public static @Nullable BroadcastPayload broadcast;
	public static int broadcastAge;
	private static final Deque<BroadcastPayload> broadcastQueue = new ArrayDeque<>();

	private ClientState() {
	}

	public static void onSync(SyncPayload p) {
		Stage next = Stage.byId(p.stage());
		Stage previous = stage;
		stage = next;
		elapsed = p.elapsed();
		duration = Math.max(1, p.duration());
		stageTicks = p.stageTicks();
		stageLength = p.stageLength();
		paused = p.paused();
		requiredShielding = p.requiredShielding();
		if (synced && next != previous) onStageChange(previous, next);
		synced = true;
		if (next == Stage.IDLE) {
			impactAge = -1;
			vaporizeAge = -1;
			dawnAge = -1;
			cardAge = -1;
			broadcast = null;
			broadcastQueue.clear();
		}
	}

	private static void onStageChange(Stage from, Stage to) {
		if (to != Stage.IMPACT && to != Stage.AFTERMATH) {
			vaporizeAge = -1;
		}
		if (to == Stage.AFTERMATH) {
			impactAge = -1;
		}
		// Old news is dropped: whatever was still on air fades out.
		broadcastQueue.clear();
		if (broadcast != null) broadcastAge = Math.max(broadcastAge, broadcast.duration() + 10);
		ClientEffects.onStageChange(from, to);
	}

	public static void onCue(CuePayload cue) {
		switch (cue.cue()) {
			case CuePayload.QUAKE -> CameraShake.add(cue.strength(), cue.duration());
			case CuePayload.IMPACT -> {
				impactAge = 0;
				impactStatus = Math.round(cue.strength());
			}
			case CuePayload.VAPORIZE -> vaporizeAge = 0;
			case CuePayload.BREACH -> {
				breachAge = 0;
				breachStrength = cue.strength();
				CameraShake.add(0.6f + cue.strength(), 60);
			}
			case CuePayload.METEOR_IMPACT -> CameraShake.add(cue.strength() * 0.9f, cue.duration());
			case CuePayload.DAWN -> dawnAge = 0;
			default -> {
			}
		}
		ClientEffects.onCue(cue);
	}

	public static void onRoster(RosterPayload payload) {
		roster = payload.entries();
	}

	public static void onBroadcast(BroadcastPayload payload) {
		if (broadcast != null || cardShowing()) {
			broadcastQueue.add(payload);
		} else {
			showBroadcast(payload);
		}
	}

	/** Broadcasts wait until the stage title card has faded out. */
	public static boolean cardShowing() {
		return cardAge >= 0 && cardAge < 110;
	}

	/** The last ten seconds: giant numbers, nothing else. */
	public static boolean finalCountdown() {
		return stage == Stage.FINAL_MINUTES && ticksRemaining() <= 200;
	}

	private static void showBroadcast(BroadcastPayload payload) {
		broadcast = payload;
		broadcastAge = 0;
		ClientEffects.onBroadcast(payload);
	}

	public static void tick(Minecraft mc) {
		ticks++;
		if (stage.isCountdown() && !paused) {
			elapsed = Math.min(duration, elapsed + 1);
			stageTicks++;
		} else if (stage == Stage.IMPACT || stage == Stage.AFTERMATH && !paused || stage == Stage.WASTELAND) {
			stageTicks++;
		}
		if (impactAge >= 0) impactAge++;
		if (vaporizeAge >= 0 && ++vaporizeAge > 60) {
			// The burn ends with death; whoever respawns watches the rest like an observer.
			vaporizeAge = -1;
			impactStatus = 3;
		}
		if (breachAge >= 0 && ++breachAge > 200) breachAge = -1;
		if (dawnAge >= 0 && ++dawnAge > 400) dawnAge = -1;
		if (cardAge >= 0 && ++cardAge > 130) cardAge = -1;
		if (broadcast != null && ++broadcastAge > broadcast.duration() + 30) broadcast = null;
		if (broadcast == null && !broadcastQueue.isEmpty() && !cardShowing() && !finalCountdown()) showBroadcast(broadcastQueue.poll());

		if (mc.player != null && mc.level != null && ticks % 5 == 0) {
			BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
			shelter = Shelter.evaluate(mc.level, eye, requiredShielding);
			exposedToSky = mc.level.canSeeSky(eye);
		}
	}

	public static void reset() {
		stage = Stage.IDLE;
		synced = false;
		roster = List.of();
		shelter = null;
		impactAge = -1;
		vaporizeAge = -1;
		breachAge = -1;
		dawnAge = -1;
		cardAge = -1;
		broadcast = null;
		broadcastQueue.clear();
		CameraShake.clear();
	}

	// ------------------------------------------------------------------ derived values

	public static boolean active() {
		return stage != Stage.IDLE;
	}

	/** Countdown progress, 0..1, smoothed with partial ticks. */
	public static float doom(float partialTick) {
		if (stage == Stage.IDLE) return 0f;
		if (!stage.isCountdown()) return 1f;
		float e = elapsed + (paused ? 0 : partialTick);
		return Mth.clamp(e / duration, 0f, 1f);
	}

	public static long ticksRemaining() {
		return Math.max(0, duration - elapsed);
	}

	public static float stageFraction() {
		if (stageLength <= 0) return 0f;
		return Mth.clamp(stageTicks / (float) stageLength, 0f, 1f);
	}

	public static boolean isObserver() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || mc.player.isSpectator() || mc.player.isCreative();
	}

	public static boolean selfFallen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		String name = mc.player.getName().getString();
		for (RosterPayload.Entry e : roster) {
			if (e.name().equals(name)) return e.status() == RosterPayload.FALLEN;
		}
		return false;
	}
}
