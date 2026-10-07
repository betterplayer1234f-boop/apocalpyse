package com.zerohour.client;

import com.zerohour.Stage;
import com.zerohour.content.ModSounds;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Client-side atmosphere: falling fallout, embers, dust, the Geiger counter, and the whole sound design. */
public final class ClientEffects {
	private record Scheduled(int at, Runnable action) {
	}

	private static final List<Scheduled> schedule = new ArrayList<>();
	private static final RandomSource random = RandomSource.create();
	private static @Nullable LoopSound wind;
	private static @Nullable LoopSound rumble;
	private static float windTarget;
	private static float rumbleTarget;
	private static int nextHeartbeat;
	private static int nextSiren;
	private static int typedChars;

	private ClientEffects() {
	}

	// ------------------------------------------------------------------ sound helpers

	static void play(SoundEvent sound, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
			sound.location(), SoundSource.AMBIENT, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
			SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	static void play(Holder<SoundEvent> sound, float volume, float pitch) {
		play(sound.value(), volume, pitch);
	}

	static void later(int delay, Runnable action) {
		schedule.add(new Scheduled(ClientState.ticks + delay, action));
	}

	// ------------------------------------------------------------------ events from the server

	static void onStageChange(Stage from, Stage to) {
		switch (to) {
			case TENSIONS -> {
				play(SoundEvents.AMBIENT_CAVE, 1f, 0.7f);
				later(60, () -> play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.25f, 0.4f));
			}
			case OUTBREAK -> {
				play(SoundEvents.ZOMBIE_AMBIENT, 0.8f, 0.6f);
				later(40, () -> play(SoundEvents.AMBIENT_CAVE, 1f, 0.6f));
			}
			case LAUNCH -> play(SoundEvents.WITHER_SPAWN, 0.5f, 0.55f);
			case INCOMING -> {
				play(SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 0.5f);
				nextSiren = 220;
			}
			case FALLOUT -> {
				play(SoundEvents.BELL_RESONATE, 1f, 0.5f);
				play(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f);
			}
			case EVACUATION -> {
				play(SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
				float[] chord = {0.5f, 0.63f, 0.75f, 1f};
				for (int i = 0; i < chord.length; i++) {
					float p = chord[i];
					later(10 + i * 6, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.8f, p));
				}
				later(80, () -> play(ModSounds.HELICOPTER, 0.5f, 1f));
			}
			default -> {
			}
		}
	}

	static void onCue(CuePayload cue) {
		Minecraft mc = Minecraft.getInstance();
		switch (cue.cue()) {
			case CuePayload.TREMOR -> {
				float s = cue.strength();
				play(ModSounds.RUMBLE, 0.4f + 0.6f * s, 0.8f + random.nextFloat() * 0.3f);
				for (int i = 0; i < cue.duration() / 10; i++) {
					later(random.nextInt(Math.max(1, cue.duration())), () -> play(SoundEvents.DEEPSLATE_BREAK, 0.7f * s, 0.4f + random.nextFloat() * 0.2f));
				}
			}
			case CuePayload.SIREN -> {
				// An air-raid siren wailing over the town.
				boolean finalSiren = cue.strength() >= 1f;
				play(ModSounds.SIREN, finalSiren ? 0.9f : 0.6f, finalSiren ? 0.94f : 1f);
			}
			case CuePayload.DETONATION -> {
				mc.getMusicManager().stopPlaying();
				boolean sheltered = cue.strength() < 2f;
				float v = sheltered ? 0.7f : 1f;
				float p = sheltered ? 0.8f : 1f;
				// The warhead screaming down.
				play(ModSounds.SHELL_WHISTLE, 0.8f, 0.5f);
				play(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
				later(25, () -> play(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.65f));
				// The flash itself is silent. Then a ringing in your ears...
				later(Stage.FLASH_TICK, () -> {
					play(SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
					play(SoundEvents.BEACON_POWER_SELECT, 0.5f, 2f);
				});
				// ...and then the blast wave arrives.
				int shock = Stage.FLASH_TICK + ClientState.shockDelay;
				later(shock, () -> {
					play(ModSounds.NUKE_BLAST, v, p);
					play(SoundEvents.LIGHTNING_BOLT_THUNDER, v, 0.4f * p);
					play(SoundEvents.WARDEN_SONIC_BOOM, v, 0.5f * p);
					play(SoundEvents.GENERIC_EXPLODE, v, 0.3f * p);
					play(SoundEvents.ENDER_DRAGON_DEATH, 0.5f * v, 0.6f * p);
				});
				for (int i = 0; i < 30; i++) {
					int at = shock + 10 + i * 11 + random.nextInt(8);
					float fade = 1f - i / 30f;
					later(at, () -> play(random.nextBoolean() ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.LIGHTNING_BOLT_THUNDER,
						0.6f * fade * v, (0.3f + random.nextFloat() * 0.3f) * p));
				}
			}
			case CuePayload.VAPORIZE -> {
				play(SoundEvents.PLAYER_HURT_ON_FIRE, 1f, 0.5f);
				play(SoundEvents.FIRECHARGE_USE, 1f, 0.5f);
				play(SoundEvents.BEACON_AMBIENT, 1f, 2f);
			}
			case CuePayload.BREACH -> {
				play(SoundEvents.GENERIC_EXPLODE, 0.7f, 0.4f);
				play(SoundEvents.DEEPSLATE_BREAK, 1f, 0.5f);
				play(SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
			}
			case CuePayload.SUPPLY_DROP -> play(SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2), 0.6f, 1f);
			case CuePayload.DISTANT_FLASH -> {
				float s = cue.strength();
				later(cue.duration(), () -> {
					play(ModSounds.RUMBLE, 0.6f * s, 0.6f);
					play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f * s, 0.35f);
					CameraShake.add(0.25f * s, 90);
				});
			}
			case CuePayload.EXTRACTION -> {
				mc.getMusicManager().stopPlaying();
				play(ModSounds.HELICOPTER, 1f, 1f);
				later(70, () -> play(ModSounds.HELICOPTER, 0.6f, 1f));
				later(100, () -> {
					play(SoundEvents.BEACON_ACTIVATE, 0.8f, 0.8f);
					play(SoundEvents.NOTE_BLOCK_CHIME, 0.7f, 1f);
					later(6, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.7f, 1.26f));
					later(12, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.7f, 1.5f));
				});
			}
			case CuePayload.BITTEN -> {
				play(SoundEvents.WARDEN_HEARTBEAT, 1f, 1.3f);
				later(8, () -> play(SoundEvents.WARDEN_HEARTBEAT, 1f, 1.3f));
			}
			default -> {
			}
		}
	}

	static void onBroadcast(BroadcastPayload b) {
		typedChars = 0;
		switch (b.style()) {
			// The real Emergency Alert System header bursts and two-tone attention signal.
			case BroadcastPayload.EMERGENCY, BroadcastPayload.FINAL -> play(ModSounds.EAS_TONE, 0.55f, 1f);
			case BroadcastPayload.GLITCH -> {
				play(ModSounds.RADIO_STATIC, 0.7f, 0.9f);
				later(30, () -> play(ModSounds.RADIO_STATIC, 0.5f, 0.75f));
			}
			case BroadcastPayload.RESCUE -> {
				play(ModSounds.RADIO_STATIC, 0.4f, 1.1f);
				later(30, () -> play(SoundEvents.NOTE_BLOCK_BELL, 0.7f, 1f));
				later(36, () -> play(SoundEvents.NOTE_BLOCK_BELL, 0.7f, 1.26f));
			}
			default -> {
				play(ModSounds.RADIO_STATIC, 0.35f, 1.1f);
				later(10, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.2f));
				later(16, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.5f));
			}
		}
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(Minecraft mc) {
		if (!schedule.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			schedule.removeIf(s -> {
				if (s.at <= ClientState.ticks) {
					due.add(s);
					return true;
				}
				return false;
			});
			due.forEach(s -> s.action.run());
		}
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			windTarget = 0f;
			rumbleTarget = 0f;
			return;
		}
		if (!ClientState.active()) {
			windTarget = 0f;
			rumbleTarget = Math.min(1f, CameraShake.intensity(0f) * 0.9f);
			tickLoops(mc);
			return;
		}
		boolean overworld = level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
		if (overworld) particles(mc, level);
		sounds(mc, overworld);
		tickLoops(mc);
	}

	private static int falloutCount() {
		float shock = ClientState.sinceShock(0f);
		return switch (ClientState.stage) {
			case LAUNCH -> ClientState.stageFraction() > 0.5f ? 1 : 0;
			case INCOMING -> ClientState.silence() ? 1 : 3;
			case DETONATION -> shock > 0f ? 24 : 2;
			case FALLOUT -> 18;
			case EVACUATION -> Math.max(1, 8 - ClientState.stageTicks / 2000);
			default -> 0;
		};
	}

	private static void particles(Minecraft mc, ClientLevel level) {
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		int count = ClientState.inSafeZone(cam.x, cam.z) ? 0 : falloutCount();
		boolean embers = ClientState.stage == Stage.DETONATION && ClientState.sinceShock(0f) > 0f
			|| ClientState.stage == Stage.FALLOUT && ClientState.stageFraction() < 0.3f;
		for (int i = 0; i < count; i++) {
			double x = cam.x + (random.nextDouble() - 0.5) * 44;
			double z = cam.z + (random.nextDouble() - 0.5) * 44;
			double y = cam.y - 2 + random.nextDouble() * 18;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) continue;
			if (embers && random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.FLAME, x, y + 4, z, (random.nextDouble() - 0.5) * 0.04, -0.05 - random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.04);
			} else {
				level.addParticle(random.nextFloat() < 0.6f ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, x, y, z, 0, -0.02, 0);
			}
		}

		// Dust shaken loose from the ceiling of your shelter.
		float shake = CameraShake.intensity(0f);
		if (shake > 0.25f && !ClientState.exposedToSky) {
			int tries = Math.round(shake * 4);
			for (int i = 0; i < tries; i++) {
				BlockPos from = BlockPos.containing(cam).offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
				for (int up = 1; up <= 7; up++) {
					BlockPos p = from.above(up);
					BlockState state = level.getBlockState(p);
					if (!state.isAir()) {
						level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
							p.getX() + random.nextDouble(), p.getY() - 0.05, p.getZ() + random.nextDouble(), 0, 0, 0);
						break;
					}
				}
			}
		}
	}

	private static void sounds(Minecraft mc, boolean overworld) {
		Stage stage = ClientState.stage;
		boolean exposed = ClientState.exposedToSky && overworld;
		long remaining = ClientState.ticksRemaining();
		boolean silence = ClientState.silence();
		float shock = ClientState.sinceShock(0f);

		// Wind
		float w = switch (stage) {
			case LAUNCH, INCOMING -> silence ? 0f : exposed ? 0.12f : 0f;
			case DETONATION -> shock > 0f ? (exposed || ClientState.impactStatus >= 2 ? 1f : 0.35f) : 0f;
			case FALLOUT -> exposed ? 0.55f : 0.1f;
			case EVACUATION -> exposed ? 0.15f : 0f;
			default -> 0f;
		};
		windTarget = overworld ? w : 0f;
		rumbleTarget = silence ? 0f : Math.min(1f, CameraShake.intensity(0f) * 0.9f);

		// Air-raid sirens, over and over, until the very end.
		if (stage == Stage.INCOMING && !silence && !ClientState.paused && --nextSiren <= 0) {
			play(ModSounds.SIREN, 0.7f, 0.94f);
			nextSiren = 220;
		}

		// Your own heartbeat: in the last minute before the strike, or when the infection is taking you.
		boolean dread = stage == Stage.INCOMING && remaining > 160 && remaining <= 1200 && !ClientState.paused;
		boolean turning = ClientState.infection >= 0.75f;
		if (dread || turning) {
			if (--nextHeartbeat <= 0) {
				play(SoundEvents.WARDEN_HEARTBEAT, 0.8f, turning ? 1.15f : 1f);
				nextHeartbeat = turning ? Math.round(Mth.lerp((ClientState.infection - 0.75f) * 4f, 18f, 9f))
					: Math.round(Mth.lerp(1f - remaining / 1200f, 26f, 12f));
			}
		}

		// A Geiger counter in your hand clicks with the radiation around you.
		if (ClientState.holdsGeiger()) {
			float rate = ClientState.shownRate;
			float p = Math.min(0.95f, 0.025f + rate * 0.12f);
			int bursts = rate > 8f ? 3 : rate > 3f ? 2 : 1;
			for (int i = 0; i < bursts; i++) {
				if (random.nextFloat() < p) play(ModSounds.GEIGER_CLICK, 0.25f + random.nextFloat() * 0.15f, 0.85f + random.nextFloat() * 0.3f);
			}
		}

		// Ambient dread
		if (!silence) {
			switch (stage) {
				case TENSIONS -> {
					// Guns on the front, far away.
					if (random.nextInt(500) == 0) play(SoundEvents.GENERIC_EXPLODE, 0.15f, 0.35f);
					if (random.nextInt(900) == 0) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.2f, 0.4f);
				}
				case OUTBREAK -> {
					if (random.nextInt(600) == 0) play(SoundEvents.ZOMBIE_AMBIENT, 0.2f, 0.7f);
					if (random.nextInt(700) == 0) play(SoundEvents.GENERIC_EXPLODE, 0.2f, 0.35f);
					if (random.nextInt(1200) == 0) play(SoundEvents.AMBIENT_CAVE, 0.8f, 0.7f);
				}
				case LAUNCH, INCOMING -> {
					if (random.nextInt(300) == 0) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 0.5f);
					if (random.nextInt(500) == 0) play(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 1f, 0.6f);
				}
				case FALLOUT -> {
					if (random.nextInt(400) == 0) play(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f);
					if (exposed && random.nextInt(60) == 0) play(SoundEvents.FIRE_AMBIENT, 0.5f, 0.6f);
				}
				default -> {
				}
			}
		}

		// Teletype clicks while a broadcast is being typed out.
		BroadcastPayload b = ClientState.broadcast;
		if (b != null) {
			int visible = Math.min(b.body().length(), (int) (ClientState.broadcastAge * HudOverlay.TYPE_SPEED));
			if (visible / 3 > typedChars / 3 && visible < b.body().length()) {
				play(SoundEvents.NOTE_BLOCK_HAT, 0.07f, 1.7f + random.nextFloat() * 0.3f);
			}
			typedChars = visible;
		}
	}

	private static void tickLoops(Minecraft mc) {
		var sounds = mc.getSoundManager();
		if (windTarget > 0f && (wind == null || wind.isStopped() || !sounds.isActive(wind))) {
			wind = new LoopSound(ModSounds.WIND, 1.0f, () -> windTarget);
			sounds.play(wind);
		}
		if (rumbleTarget > 0.02f && (rumble == null || rumble.isStopped() || !sounds.isActive(rumble))) {
			rumble = new LoopSound(SoundEvents.ELYTRA_FLYING, 0.3f, () -> rumbleTarget);
			sounds.play(rumble);
		}
	}

	public static void reset() {
		schedule.clear();
		windTarget = 0f;
		rumbleTarget = 0f;
		wind = null;
		rumble = null;
	}
}
