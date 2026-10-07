package com.lastsunset.content;

import com.lastsunset.LastSunset;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Custom sounds, synthesized by tools/gen_sounds.py. */
public final class ModSounds {
	public static final SoundEvent SIREN = register("siren");
	public static final SoundEvent EAS_TONE = register("eas_tone");
	public static final SoundEvent RADIO_STATIC = register("radio_static");
	public static final SoundEvent RUMBLE = register("rumble");
	public static final SoundEvent SOLAR_ROAR = register("solar_roar");
	public static final SoundEvent HELIOMETER_CLICK = register("heliometer_click");
	public static final SoundEvent FLARE_GUN = register("flare_gun");
	public static final SoundEvent METEOR_WHISTLE = register("meteor_whistle");
	public static final SoundEvent WIND = register("wind");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = LastSunset.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
