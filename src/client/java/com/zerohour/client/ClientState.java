package com.zerohour.client;

import com.zerohour.ApocalypseData;
import com.zerohour.Shelter;
import com.zerohour.Stage;
import com.zerohour.content.ModItems;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import com.zerohour.network.RosterPayload;
import com.zerohour.network.StatusPayload;
import com.zerohour.network.SyncPayload;
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
	public static float requiredShielding = 24f;
	public static boolean synced;

	// Places that matter.
	public static boolean hasStrike;
	public static int strikeX;
	public static int strikeY;
	public static int strikeZ;
	public static boolean hasLz;
	public static int lzX;
	public static int lzY;
	public static int lzZ;
	public static boolean evacOpen;
	public static int evacTicksLeft;
	public static boolean hasSafeZone;
	public static int safeX;
	public static int safeZ;

	// What is going on in your own body (sent by the server once a second).
	public static float dose;
	public static float rate;
	/** The dose rate, smoothed for the needle and the clicks. */
	public static float shownRate;
	/** 0..1, or -1 if you're not infected. */
	public static float infection = -1f;
	public static int iodineTicks;
	public static boolean evacuated;

	public static List<RosterPayload.Entry> roster = List.of();
	public static Shelter.@Nullable Result shelter;
	public static boolean exposedToSky;

	public static int ticks;
	/** Ticks since the detonation cue, -1 if none. The flash comes at {@link Stage#FLASH_TICK}. */
	public static int detonationAge = -1;
	/** Shelter status sent with the detonation cue: 0 safe, 1 partial, 2 exposed, 3 observer. */
	public static int impactStatus = 3;
	/** Ticks between the flash and the blast wave reaching you: the further from ground zero, the longer. */
	public static int shockDelay = 30;
	public static int vaporizeAge = -1;
	public static int breachAge = -1;
	public static float breachStrength;
	public static int distantFlashAge = -1;
	public static float distantFlashStrength;
	public static int extractionAge = -1;
	public static int bittenAge = -1;
	public static int evacStartAge = -1;

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
		hasStrike = p.hasStrike();
		strikeX = p.strikeX();
		strikeY = p.strikeY();
		strikeZ = p.strikeZ();
		hasLz = p.hasLz();
		lzX = p.lzX();
		lzY = p.lzY();
		lzZ = p.lzZ();
		evacOpen = p.evacOpen();
		evacTicksLeft = p.evacTicksLeft();
		hasSafeZone = p.hasSafeZone();
		safeX = p.safeX();
		safeZ = p.safeZ();
		if (synced && next != previous) onStageChange(previous, next);
		synced = true;
		if (next == Stage.IDLE) {
			detonationAge = -1;
			vaporizeAge = -1;
			extractionAge = -1;
			evacStartAge = -1;
			broadcast = null;
			broadcastQueue.clear();
		}
	}

	private static void onStageChange(Stage from, Stage to) {
		if (to != Stage.DETONATION && to != Stage.FALLOUT) {
			vaporizeAge = -1;
		}
		if (to == Stage.FALLOUT) {
			detonationAge = -1;
		}
		if (to == Stage.EVACUATION) evacStartAge = 0;
		// Old news is dropped: whatever was still on air fades out.
		broadcastQueue.clear();
		if (broadcast != null) broadcastAge = Math.max(broadcastAge, broadcast.duration() + 10);
		ClientEffects.onStageChange(from, to);
	}

	public static void onStatus(StatusPayload p) {
		dose = p.dose();
		rate = p.rate();
		infection = p.infection();
		iodineTicks = p.iodineTicks();
		evacuated = p.evacuated();
	}

	public static void onCue(CuePayload cue) {
		switch (cue.cue()) {
			case CuePayload.TREMOR -> CameraShake.add(cue.strength(), cue.duration());
			case CuePayload.DETONATION -> {
				detonationAge = 0;
				impactStatus = Math.round(cue.strength());
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					double dx = mc.player.getX() - cue.x();
					double dz = mc.player.getZ() - cue.z();
					shockDelay = Mth.clamp((int) (Math.sqrt(dx * dx + dz * dz) / 8.0), 8, 100);
				}
			}
			case CuePayload.VAPORIZE -> vaporizeAge = 0;
			case CuePayload.BREACH -> {
				breachAge = 0;
				breachStrength = cue.strength();
				CameraShake.add(0.6f + cue.strength(), 60);
			}
			case CuePayload.SHELL_IMPACT -> CameraShake.add(cue.strength() * 0.9f, cue.duration());
			case CuePayload.DISTANT_FLASH -> {
				distantFlashAge = 0;
				distantFlashStrength = cue.strength();
			}
			case CuePayload.EXTRACTION -> extractionAge = 0;
			case CuePayload.BITTEN -> bittenAge = 0;
			default -> {
			}
		}
		ClientEffects.onCue(cue);
	}

	public static void onRoster(RosterPayload payload) {
		roster = payload.entries();
	}

	public static void onBroadcast(BroadcastPayload payload) {
		if (broadcast != null || silence()) {
			broadcastQueue.add(payload);
		} else {
			showBroadcast(payload);
		}
	}

	/** The last eight seconds before the strike: no voices, no sirens, nothing. */
	public static boolean silence() {
		return stage == Stage.INCOMING && ticksRemaining() <= 160;
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
		} else if (stage == Stage.DETONATION || (stage == Stage.FALLOUT || stage == Stage.EVACUATION) && !paused) {
			stageTicks++;
		}
		if (evacOpen && !paused && evacTicksLeft > 0) evacTicksLeft--;
		shownRate += (rate - shownRate) * 0.1f;
		if (detonationAge >= 0) detonationAge++;
		if (vaporizeAge >= 0 && ++vaporizeAge > 60) {
			// The burn ends with death; whoever respawns watches the rest like an observer.
			vaporizeAge = -1;
			impactStatus = 3;
		}
		if (breachAge >= 0 && ++breachAge > 200) breachAge = -1;
		if (distantFlashAge >= 0 && ++distantFlashAge > 120) distantFlashAge = -1;
		if (extractionAge >= 0 && ++extractionAge > 170) extractionAge = -1;
		if (bittenAge >= 0 && ++bittenAge > 40) bittenAge = -1;
		if (evacStartAge >= 0 && ++evacStartAge > 400) evacStartAge = -1;
		if (broadcast != null && ++broadcastAge > broadcast.duration() + 30) broadcast = null;
		if (broadcast == null && !broadcastQueue.isEmpty() && !silence()) showBroadcast(broadcastQueue.poll());

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
		hasStrike = false;
		hasLz = false;
		evacOpen = false;
		hasSafeZone = false;
		dose = 0f;
		rate = 0f;
		shownRate = 0f;
		infection = -1f;
		iodineTicks = 0;
		evacuated = false;
		detonationAge = -1;
		vaporizeAge = -1;
		breachAge = -1;
		distantFlashAge = -1;
		extractionAge = -1;
		bittenAge = -1;
		evacStartAge = -1;
		broadcast = null;
		broadcastQueue.clear();
		CameraShake.clear();
	}

	// ------------------------------------------------------------------ derived values

	public static boolean active() {
		return stage != Stage.IDLE;
	}

	/** Build-up progress, 0..1, smoothed with partial ticks. */
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

	/** Ticks since the flash (negative before it), or a large number if there was no detonation this session. */
	public static float sinceFlash(float partialTick) {
		if (detonationAge < 0) return stage == Stage.DETONATION ? stageTicks - Stage.FLASH_TICK + partialTick : 100000f;
		return detonationAge - Stage.FLASH_TICK + partialTick;
	}

	/** Ticks since the blast wave reached you (negative before it). */
	public static float sinceShock(float partialTick) {
		return sinceFlash(partialTick) - shockDelay;
	}

	/** Before the flash, the shelter rating is what matters; after it, the radiation. */
	public static boolean blastPhase() {
		return stage == Stage.OUTBREAK || stage == Stage.LAUNCH || stage == Stage.INCOMING || stage == Stage.DETONATION && sinceFlash(0f) < 0f;
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

	public static boolean carriesGeiger() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getInventory().contains(stack -> stack.is(ModItems.GEIGER_COUNTER));
	}

	public static boolean holdsGeiger() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && (mc.player.getMainHandItem().is(ModItems.GEIGER_COUNTER) || mc.player.getOffhandItem().is(ModItems.GEIGER_COUNTER));
	}

	public static boolean inSafeZone(double x, double z) {
		if (!hasSafeZone) return false;
		double dx = x - safeX;
		double dz = z - safeZ;
		return dx * dx + dz * dz < (double) ApocalypseData.SAFE_RADIUS * ApocalypseData.SAFE_RADIUS;
	}
}
