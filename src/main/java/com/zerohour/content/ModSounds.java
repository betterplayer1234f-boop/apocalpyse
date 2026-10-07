package com.zerohour.content;

import com.zerohour.ZeroHour;
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
	public static final SoundEvent NUKE_BLAST = register("nuke_blast");
	public static final SoundEvent GEIGER_CLICK = register("geiger_click");
	public static final SoundEvent FLARE_GUN = register("flare_gun");
	public static final SoundEvent SHELL_WHISTLE = register("shell_whistle");
	public static final SoundEvent WIND = register("wind");
	public static final SoundEvent JET_FLYBY = register("jet_flyby");
	public static final SoundEvent HELICOPTER = register("helicopter");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = ZeroHour.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
