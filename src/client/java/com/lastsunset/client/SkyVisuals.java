package com.lastsunset.client;

import com.lastsunset.Stage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

/**
 * Colour grading for the end of the world. Every visual environment attribute the camera reads
 * (sky, fog, light, clouds, stars, music) passes through {@link #modify}.
 */
public final class SkyVisuals {
	private static final int BLOOD = 0x8A1C10;
	private static final int WHITE = 0xFFFFFF;

	/** Everything that changes about the look of the world, for one frame. */
	public static final class Look {
		int sky;
		float skyBlend;
		int fog;
		float fogBlend;
		float fogScale = 1f;
		int light = WHITE;
		int clouds;
		float cloudBlend;
		int sunset;
		float sunsetBlend;
		float starMin;
		float music = 1f;
		public float sunScale = 1f;
		public final Vector4f sunColor = new Vector4f(1f, 1f, 1f, 1f);
		public float sunSpin;
		final Vector4f moonTint = new Vector4f(1f, 1f, 1f, 1f);
	}

	private static final Look LOOK = new Look();
	private static int cachedTick = Integer.MIN_VALUE;
	private static float cachedPartial = Float.NaN;
	private static boolean cachedActive;

	private SkyVisuals() {
	}

	static float smooth(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	static int mix(int a, int b, float t) {
		return ARGB.srgbLerp(Mth.clamp(t, 0f, 1f), a | 0xFF000000, b | 0xFF000000) & 0xFFFFFF;
	}

	private static boolean overworldSky() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
	}

	/** Returns the look for this frame, or null if the world looks normal. */
	public static @Nullable Look look(float partialTick) {
		if (ClientState.ticks == cachedTick && partialTick == cachedPartial) return cachedActive ? LOOK : null;
		cachedTick = ClientState.ticks;
		cachedPartial = partialTick;
		cachedActive = ClientState.active() && compute(LOOK, partialTick);
		return cachedActive ? LOOK : null;
	}

	private static boolean compute(Look l, float pt) {
		Stage stage = ClientState.stage;
		float t = ClientState.ticks + pt;
		l.sunSpin = t * 0.002f;
		l.moonTint.set(1f, 1f, 1f, 1f);
		l.starMin = 0f;
		switch (stage) {
			case OMENS, EVACUATION, COLLAPSE, FINAL_MINUTES -> {
				float d = ClientState.doom(pt);
				// Blue fades to a pale haze first (blending blue straight into orange turns grey), then gold, orange, blood.
				float grade = smooth(0.04f, 0.9f, d);
				if (d < 0.25f) {
					l.sky = 0xF2DEB8;
					l.skyBlend = 0.85f * smooth(0f, 0.25f, d);
					l.fog = 0xE6D2B0;
				} else if (d < 0.55f) {
					float k = smooth(0.25f, 0.55f, d);
					l.sky = mix(0xF2DEB8, 0xE8803A, k);
					l.skyBlend = 0.85f + 0.1f * k;
					l.fog = mix(0xE6D2B0, 0xC87040, k);
				} else {
					l.sky = mix(0xE8803A, BLOOD, smooth(0.55f, 1f, d));
					l.skyBlend = 0.95f;
					l.fog = mix(0xC87040, 0x5C1A0E, smooth(0.55f, 1f, d));
				}
				l.fogBlend = l.skyBlend * 0.95f;
				l.fogScale = 1f - 0.68f * smooth(0.25f, 1f, d);
				l.light = mix(WHITE, 0xFF8C5A, smooth(0.08f, 1f, d));
				l.clouds = mix(0xB07050, 0x4A1A12, d);
				l.cloudBlend = 0.85f * grade;
				l.sunset = 0xFF4A1A;
				l.sunsetBlend = 0.9f * smooth(0.5f, 0.75f, d);
				l.starMin = 0.35f * smooth(0.85f, 1f, d);
				l.music = 1f - smooth(0.5f, 0.6f, d);
				float pulse = 1f + 0.07f * Mth.sin(t * (0.08f + 0.35f * d)) * smooth(0.5f, 1f, d);
				l.sunScale = (1.15f + 4.6f * (float) Math.pow(d, 1.6)) * pulse;
				lerp3(l.sunColor, d, 1f, 0.93f, 0.78f, 1f, 0.62f, 0.32f, 1f, 0.3f, 0.12f);
				l.sunColor.w = 1f;
				l.sunSpin = t * (0.002f + 0.01f * d);
				l.moonTint.set(1f, 0.45f + 0.55f * (1f - smooth(0f, 0.3f, d)), 0.4f + 0.6f * (1f - smooth(0f, 0.3f, d)), 1f);
			}
			case IMPACT -> {
				float a = (ClientState.impactAge >= 0 ? ClientState.impactAge : ClientState.stageTicks) + pt;
				float w = Mth.square(Mth.clamp(a / Stage.IMPACT_BOOM_TICK, 0f, 1f));
				float out = smooth(140f, 420f, a);
				l.sky = a < 140 ? mix(BLOOD, WHITE, w) : mix(WHITE, 0x1A0705, out);
				l.skyBlend = 1f;
				l.fog = a < 140 ? mix(0x5C1A0E, WHITE, w) : mix(WHITE, 0x2A0C06, out);
				l.fogBlend = 1f;
				l.fogScale = a < 140 ? 0.32f + 0.4f * w : Mth.lerp(out, 0.72f, 0.22f);
				l.light = a < 140 ? mix(0xFF8C5A, WHITE, w) : mix(WHITE, 0xD05A3A, out);
				l.clouds = l.sky;
				l.cloudBlend = 1f;
				l.sunset = 0xFFFFFF;
				l.sunsetBlend = 1f - out;
				l.music = 0f;
				l.sunScale = a < 140 ? 5.8f + 24f * w : Mth.lerp(out, 29.8f, 0.6f);
				l.sunColor.set(1f, Mth.lerp(w, 0.3f, 1f), Mth.lerp(w, 0.12f, 1f), 1f);
				if (a >= 140) l.sunColor.set(Mth.lerp(out, 1f, 0.8f), Mth.lerp(out, 1f, 0.15f), Mth.lerp(out, 1f, 0.05f), 1f - out * 0.5f);
				l.sunSpin = t * 0.03f;
			}
			case AFTERMATH -> {
				float f = ClientState.stageFraction();
				l.sky = mix(0x1C0806, 0x3A1C14, f);
				l.skyBlend = 1f;
				l.fog = mix(0x2A0E08, 0x4A281C, f);
				l.fogBlend = 1f;
				l.fogScale = 0.22f + 0.2f * f;
				l.light = 0xFF7050;
				l.clouds = 0x201010;
				l.cloudBlend = 1f;
				l.sunsetBlend = 0f;
				l.music = 0f;
				l.sunScale = 0.7f;
				l.sunColor.set(0.7f, 0.15f, 0.05f, 0.8f);
				l.moonTint.set(0.55f, 0.12f, 0.08f, 1f);
			}
			case WASTELAND -> {
				float ash = 1f - 0.6f * smooth(0f, 20f * 60f * 20f, ClientState.stageTicks + pt);
				l.sky = 0x8C7A6E;
				l.skyBlend = 0.75f * ash;
				l.fog = 0x86766C;
				l.fogBlend = 0.8f * ash;
				l.fogScale = 1f - 0.5f * ash;
				l.light = mix(WHITE, 0xFFD2B8, ash);
				l.clouds = 0x6E6660;
				l.cloudBlend = 0.8f * ash;
				l.sunset = 0xFF5A2A;
				l.sunsetBlend = 0.6f;
				l.music = 1f - 0.5f * ash;
				l.sunScale = 0.85f;
				l.sunColor.set(1f, 0.5f, 0.3f, 1f);
				l.moonTint.set(0.9f, 0.7f, 0.65f, 1f);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void lerp3(Vector4f out, float d, float r0, float g0, float b0, float r1, float g1, float b1, float r2, float g2, float b2) {
		if (d < 0.5f) {
			float k = d / 0.5f;
			out.set(Mth.lerp(k, r0, r1), Mth.lerp(k, g0, g1), Mth.lerp(k, b0, b1), 1f);
		} else {
			float k = (d - 0.5f) / 0.5f;
			out.set(Mth.lerp(k, r1, r2), Mth.lerp(k, g1, g2), Mth.lerp(k, b1, b2), 1f);
		}
	}

	/** Hook for every environment attribute value read by the camera. */
	public static Object modify(EnvironmentAttribute<?> attribute, Object value, float partialTick) {
		Look l = look(partialTick);
		if (l == null) return value;
		if (attribute == EnvironmentAttributes.MUSIC_VOLUME) {
			return (Float) value * l.music;
		}
		if (!overworldSky()) return value;
		if (attribute == EnvironmentAttributes.SKY_COLOR) {
			return blend((Integer) value, l.sky, l.skyBlend);
		} else if (attribute == EnvironmentAttributes.FOG_COLOR) {
			return blend((Integer) value, l.fog, l.fogBlend);
		} else if (attribute == EnvironmentAttributes.FOG_START_DISTANCE) {
			return (Float) value * l.fogScale;
		} else if (attribute == EnvironmentAttributes.FOG_END_DISTANCE) {
			return Math.max(24f, (Float) value * l.fogScale);
		} else if (attribute == EnvironmentAttributes.SKY_FOG_END_DISTANCE) {
			return Math.max(32f, (Float) value * (0.4f + 0.6f * l.fogScale));
		} else if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR) {
			int v = (Integer) value;
			return (v & 0xFF000000) | (ARGB.multiply(v | 0xFF000000, l.light | 0xFF000000) & 0xFFFFFF);
		} else if (attribute == EnvironmentAttributes.CLOUD_COLOR) {
			int v = (Integer) value;
			return (v & 0xFF000000) | (ARGB.srgbLerp(l.cloudBlend, v | 0xFF000000, l.clouds | 0xFF000000) & 0xFFFFFF);
		} else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
			int v = (Integer) value;
			if (l.sunsetBlend <= 0f) return v;
			float alpha = Math.max(ARGB.alphaFloat(v), l.sunsetBlend * 0.85f);
			int rgb = ARGB.srgbLerp(l.sunsetBlend, v | 0xFF000000, l.sunset | 0xFF000000) & 0xFFFFFF;
			return ARGB.color(alpha, rgb);
		} else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
			return Math.max((Float) value, l.starMin);
		}
		return value;
	}

	private static int blend(int original, int target, float t) {
		if (t <= 0f) return original;
		return (original & 0xFF000000) | (ARGB.srgbLerp(t, original | 0xFF000000, target | 0xFF000000) & 0xFFFFFF);
	}

	public static Vector4fc moonColor(Vector4fc original) {
		Look l = look(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (l == null || !overworldSky()) return original;
		return new Vector4f(original.x() * l.moonTint.x, original.y() * l.moonTint.y, original.z() * l.moonTint.z, original.w());
	}

	/** Camera field-of-view punch: the shockwave, being vaporized. */
	public static float fovMultiplier(float partialTick) {
		float m = 1f;
		if (ClientState.impactAge >= 0) {
			float a = ClientState.impactAge + partialTick - Stage.IMPACT_BOOM_TICK;
			if (a > -8 && a < 60) {
				m *= a < 0 ? 1f - 0.04f * (a + 8) / 8f : 1f + 0.18f * (float) Math.exp(-a / 12f) - 0.04f * (float) Math.exp(-a / 3f);
			}
		}
		if (ClientState.vaporizeAge >= 0) {
			m *= 1f + 0.3f * Mth.clamp((ClientState.vaporizeAge + partialTick) / 34f, 0f, 1f);
		}
		return m;
	}
}
