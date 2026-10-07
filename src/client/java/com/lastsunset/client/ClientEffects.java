package com.lastsunset.client;

import com.lastsunset.Stage;
import com.lastsunset.content.ModItems;
import com.lastsunset.content.ModSounds;
import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.network.CuePayload;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Client-side atmosphere: falling ash, embers, boiling water, dust, and the whole sound design. */
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

	static void playAt(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, SoundInstance.createUnseededRandom(), x, y, z));
	}

	static void later(int delay, Runnable action) {
		schedule.add(new Scheduled(ClientState.ticks + delay, action));
	}

	// ------------------------------------------------------------------ events from the server

	static void onStageChange(Stage from, Stage to) {
		switch (to) {
			case OMENS -> {
				for (int i = 0; i < 3; i++) later(i * 34, () -> play(SoundEvents.BELL_BLOCK, 1f, 0.5f));
				later(110, () -> play(SoundEvents.AMBIENT_CAVE, 1f, 0.7f));
			}
			case COLLAPSE -> play(SoundEvents.WITHER_SPAWN, 0.5f, 0.55f);
			case FINAL_MINUTES -> play(SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 0.5f);
			case AFTERMATH -> {
				play(SoundEvents.BELL_RESONATE, 1f, 0.5f);
				play(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f);
			}
			case WASTELAND -> {
				play(SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
				float[] chord = {0.5f, 0.63f, 0.75f, 1f};
				for (int i = 0; i < chord.length; i++) {
					float p = chord[i];
					later(10 + i * 6, () -> play(SoundEvents.NOTE_BLOCK_CHIME, 0.8f, p));
				}
			}
			default -> {
			}
		}
	}

	static void onCue(CuePayload cue) {
		Minecraft mc = Minecraft.getInstance();
		switch (cue.cue()) {
			case CuePayload.QUAKE -> {
				float s = cue.strength();
				play(ModSounds.RUMBLE, 0.4f + 0.6f * s, 0.8f + random.nextFloat() * 0.3f);
				for (int i = 0; i < cue.duration() / 10; i++) {
					later(random.nextInt(Math.max(1, cue.duration())), () -> play(SoundEvents.DEEPSLATE_BREAK, 0.7f * s, 0.4f + random.nextFloat() * 0.2f));
				}
			}
			case CuePayload.SIREN -> {
				// A civil-defence siren wailing somewhere in the distance.
				boolean finalSiren = cue.strength() >= 1f;
				play(ModSounds.SIREN, finalSiren ? 0.9f : 0.6f, finalSiren ? 0.94f : 1f);
				if (finalSiren) later(190, () -> play(ModSounds.SIREN, 0.8f, 0.9f));
			}
			case CuePayload.IMPACT -> {
				mc.getMusicManager().stopPlaying();
				boolean sheltered = cue.strength() < 2f;
				float v = sheltered ? 0.7f : 1f;
				float p = sheltered ? 0.8f : 1f;
				play(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
				play(SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
				later(25, () -> play(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.65f));
				later(Stage.IMPACT_BOOM_TICK, () -> {
					play(ModSounds.SOLAR_ROAR, v, p);
					play(SoundEvents.LIGHTNING_BOLT_THUNDER, v, 0.5f * p);
					play(SoundEvents.END_PORTAL_SPAWN, v, 0.6f * p);
					play(SoundEvents.WARDEN_SONIC_BOOM, v, 0.5f * p);
					play(SoundEvents.ENDER_DRAGON_DEATH, 0.6f * v, 0.7f * p);
				});
				for (int i = 0; i < 30; i++) {
					int at = Stage.IMPACT_BOOM_TICK + 10 + i * 11 + random.nextInt(8);
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
				play(SoundEvents.FIRE_AMBIENT, 1f, 0.6f);
				play(SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
			}
			case CuePayload.SUPPLY_DROP -> play(SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2), 0.6f, 1f);
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
			case BroadcastPayload.DAWN -> {
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
		sounds(mc, level, overworld);
		tickLoops(mc);
	}

	private static int ashCount() {
		return switch (ClientState.stage) {
			case EVACUATION -> 1 + Math.round(ClientState.stageFraction() * 2);
			case COLLAPSE -> 5;
			case FINAL_MINUTES -> ClientState.ticksRemaining() > 160 ? 9 : 3;
			case IMPACT -> ClientState.impactAge > Stage.IMPACT_BOOM_TICK ? 26 : 6;
			case AFTERMATH -> 16;
			case WASTELAND -> Math.max(1, 6 - ClientState.stageTicks / 2400);
			default -> 0;
		};
	}

	private static void particles(Minecraft mc, ClientLevel level) {
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		int ash = ashCount();
		boolean embers = ClientState.stage == Stage.COLLAPSE || ClientState.stage == Stage.FINAL_MINUTES
			|| ClientState.stage == Stage.IMPACT || ClientState.stage == Stage.AFTERMATH;
		for (int i = 0; i < ash; i++) {
			double x = cam.x + (random.nextDouble() - 0.5) * 44;
			double z = cam.z + (random.nextDouble() - 0.5) * 44;
			double y = cam.y - 2 + random.nextDouble() * 18;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) continue;
			if (embers && random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.FLAME, x, y + 4, z, (random.nextDouble() - 0.5) * 0.04, -0.05 - random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.04);
			} else {
				level.addParticle(random.nextFloat() < 0.6f ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, x, y, z, 0, -0.02, 0);
			}
		}

		// The oceans are boiling.
		boolean boiling = ClientState.stage == Stage.COLLAPSE || ClientState.stage == Stage.FINAL_MINUTES || ClientState.stage == Stage.IMPACT
			|| ClientState.stage == Stage.AFTERMATH && ClientState.stageFraction() < 0.7f;
		if (boiling) {
			for (int i = 0; i < 4; i++) {
				int x = Mth.floor(cam.x) + random.nextInt(29) - 14;
				int z = Mth.floor(cam.z) + random.nextInt(29) - 14;
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
				if (!level.getFluidState(top).is(FluidTags.WATER)) continue;
				double px = x + random.nextDouble();
				double pz = z + random.nextDouble();
				level.addParticle(ParticleTypes.BUBBLE_POP, px, top.getY() + 1.0, pz, 0, 0.04, 0);
				level.addParticle(ParticleTypes.CLOUD, px, top.getY() + 1.1, pz, 0, 0.03 + random.nextDouble() * 0.03, 0);
				if (random.nextInt(25) == 0) playAt(SoundEvents.FIRE_EXTINGUISH, px, top.getY() + 1, pz, 0.15f, 0.6f + random.nextFloat() * 0.4f);
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

	private static void sounds(Minecraft mc, ClientLevel level, boolean overworld) {
		Stage stage = ClientState.stage;
		boolean exposed = ClientState.exposedToSky && overworld;
		float d = ClientState.doom(0f);
		long remaining = ClientState.ticksRemaining();
		boolean silence = stage == Stage.FINAL_MINUTES && remaining <= 160;

		// Wind
		float w = switch (stage) {
			case COLLAPSE -> exposed ? 0.2f + 0.3f * ClientState.stageFraction() : 0.04f;
			case FINAL_MINUTES -> silence ? 0f : exposed ? 0.6f : 0.08f;
			case IMPACT -> ClientState.impactAge > Stage.IMPACT_BOOM_TICK ? (exposed || ClientState.impactStatus >= 2 ? 1f : 0.35f) : 0f;
			case AFTERMATH -> exposed ? 0.65f : 0.12f;
			case WASTELAND -> exposed ? 0.12f : 0f;
			default -> 0f;
		};
		windTarget = overworld ? w : 0f;
		rumbleTarget = silence ? 0f : Math.min(1f, CameraShake.intensity(0f) * 0.9f);

		// Your own heartbeat, quickening as the end nears. No bells, no numbers.
		if (stage == Stage.FINAL_MINUTES && remaining > 160 && remaining <= 1200 && !ClientState.paused) {
			if (--nextHeartbeat <= 0) {
				play(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f);
				nextHeartbeat = Math.round(Mth.lerp(1f - remaining / 1200f, 26f, 12f));
			}
		}

		// A heliometer in your hand clicks like a Geiger counter, faster as the sun destabilizes.
		if (mc.player != null && (mc.player.getMainHandItem().is(ModItems.HELIOMETER) || mc.player.getOffhandItem().is(ModItems.HELIOMETER))) {
			float rate = switch (stage) {
				case IDLE, WASTELAND -> 0.02f;
				case IMPACT, AFTERMATH -> 0.7f;
				default -> 0.03f + 0.55f * d * d;
			};
			if (random.nextFloat() < rate) play(ModSounds.HELIOMETER_CLICK, 0.25f + random.nextFloat() * 0.15f, 0.85f + random.nextFloat() * 0.3f);
		}

		// Ambient dread
		if (!silence) {
			switch (stage) {
				case OMENS -> {
					if (random.nextInt(1200) == 0) play(SoundEvents.AMBIENT_CAVE, 0.8f, 0.8f);
				}
				case EVACUATION -> {
					if (random.nextInt(700) == 0) play(SoundEvents.AMBIENT_CAVE, 0.8f, 0.7f);
					if (random.nextInt(900) == 0) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.2f, 0.5f);
				}
				case COLLAPSE, FINAL_MINUTES -> {
					if (random.nextInt(300) == 0) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 0.5f);
					if (exposed && random.nextInt(50) == 0) play(SoundEvents.FIRE_AMBIENT, 0.6f, 0.7f);
					if (random.nextInt(500) == 0) play(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 1f, 0.6f);
				}
				case AFTERMATH -> {
					if (random.nextInt(400) == 0) play(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f);
					if (exposed && random.nextInt(25) == 0) play(SoundEvents.FIRE_AMBIENT, 1f, 0.6f);
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
