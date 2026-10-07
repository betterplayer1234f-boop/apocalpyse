package com.zerohour.client;

import com.zerohour.Stage;
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
	private static final int WHITE = 0xFFFFFF;
	/** War smoke drifting over from the front. */
	private static final int HAZE = 0xA8A49C;
	/** Burning cities on the horizon. */
	private static final int SMOKE = 0x8A6E5C;

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
		l.moonTint.set(1f, 1f, 1f, 1f);
		l.starMin = 0f;
		l.fogScale = 1f;
		l.light = WHITE;
		l.music = 1f;
		l.sunsetBlend = 0f;
		switch (stage) {
			case TENSIONS, OUTBREAK, LAUNCH, INCOMING -> {
				float d = ClientState.doom(pt);
				// Clear blue gives way to a grey war haze, then to the brown smoke of burning cities.
				if (d < 0.55f) {
					l.sky = HAZE;
					l.skyBlend = 0.5f * smooth(0.05f, 0.55f, d);
					l.fog = 0xB4AEA4;
				} else {
					float k = smooth(0.55f, 1f, d);
					l.sky = mix(HAZE, SMOKE, k);
					l.skyBlend = 0.5f + 0.35f * k;
					l.fog = mix(0xB4AEA4, 0x7A6050, k);
				}
				l.fogBlend = l.skyBlend * 0.9f;
				l.fogScale = 1f - 0.4f * smooth(0.3f, 1f, d);
				l.light = mix(WHITE, 0xF0D8C0, smooth(0.4f, 1f, d));
				l.clouds = mix(0x9A968E, 0x4A3E36, smooth(0.2f, 1f, d));
				l.cloudBlend = 0.75f * smooth(0.05f, 1f, d);
				l.sunset = 0xFF5A20;
				l.sunsetBlend = 0.7f * smooth(0.55f, 0.9f, d);
				l.music = 1f - smooth(0.82f, 0.9f, d);
				l.moonTint.set(1f, 1f - 0.2f * d, 1f - 0.3f * d, 1f);
			}
			case DETONATION -> {
				float a = ClientState.sinceFlash(pt);
				l.music = 0f;
				if (a < 0f) {
					// The warhead's glow growing in the sky.
					float w = 0.35f * smooth(-60f, 0f, a);
					l.sky = mix(SMOKE, WHITE, w);
					l.skyBlend = 0.85f;
					l.fog = mix(0x7A6050, WHITE, w);
					l.fogBlend = 0.8f;
					l.fogScale = 0.6f;
					l.light = 0xF0D8C0;
					l.clouds = 0x4A3E36;
					l.cloudBlend = 0.75f;
				} else {
					// White, then the orange glow of the fireball, then the dust closes in.
					float w = (float) Math.exp(-a / 50f);
					float burn = smooth(0f, 300f, a);
					int after = mix(mix(0xFFB060, 0xC0602A, burn), 0x3A2E26, smooth(200f, 540f, a));
					l.sky = mix(after, WHITE, w);
					l.skyBlend = 1f;
					l.fog = mix(mix(0xC07040, 0x3E3228, smooth(150f, 540f, a)), WHITE, w);
					l.fogBlend = 1f;
					l.fogScale = Mth.lerp(smooth(0f, 400f, a), 0.8f, 0.3f);
					l.light = mix(mix(0xFFD8B0, 0xC09070, burn), WHITE, w);
					l.clouds = l.sky;
					l.cloudBlend = 1f;
					l.sunset = 0xFF7030;
					l.sunsetBlend = 0.9f * (1f - smooth(100f, 500f, a));
				}
			}
			case FALLOUT -> {
				// Nuclear winter: a low, sick, olive-grey sky and fog that swallows everything.
				float f = ClientState.stageFraction();
				l.sky = mix(0x2E3028, 0x45463C, f);
				l.skyBlend = 1f;
				l.fog = mix(0x3A3C32, 0x56564A, f);
				l.fogBlend = 1f;
				l.fogScale = 0.28f + 0.2f * f;
				l.light = 0xB0B8A0;
				l.clouds = 0x26261F;
				l.cloudBlend = 1f;
				l.music = 0f;
				l.moonTint.set(0.6f, 0.8f, 0.5f, 1f);
			}
			case EVACUATION -> {
				// The smoke slowly clears over the next half hour.
				float ash = 1f - smooth(0f, 30f * 60f * 20f, ClientState.stageTicks + pt);
				l.sky = 0x8C877C;
				l.skyBlend = 0.8f * ash;
				l.fog = 0x868276;
				l.fogBlend = 0.85f * ash;
				l.fogScale = 1f - 0.5f * ash;
				l.light = mix(WHITE, 0xE8E0C8, ash);
				l.clouds = 0x6E6A62;
				l.cloudBlend = 0.8f * ash;
				l.sunset = 0xFF7A40;
				l.sunsetBlend = 0.5f * ash;
				l.music = 1f - 0.6f * ash;
				l.moonTint.set(0.9f, 0.95f, 0.8f, 1f);
			}
			default -> {
				return false;
			}
		}
		// Another city, over the horizon: the whole sky lights up for a moment.
		if (ClientState.distantFlashAge >= 0 && stage.isCountdown()) {
			float k = ClientState.distantFlashStrength * (float) Math.exp(-(ClientState.distantFlashAge + pt) / 18f);
			l.sky = mix(l.sky, 0xFFE6C0, k);
			l.skyBlend += (1f - l.skyBlend) * k;
			l.fog = mix(l.fog, 0xFFD8B0, k);
			l.fogBlend += (1f - l.fogBlend) * k;
			l.light = mix(l.light, 0xFFF0E0, k);
		}
		return true;
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

	/** Camera field-of-view punch: the blast wave, being vaporized. */
	public static float fovMultiplier(float partialTick) {
		float m = 1f;
		if (ClientState.detonationAge >= 0) {
			float a = ClientState.sinceShock(partialTick);
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
