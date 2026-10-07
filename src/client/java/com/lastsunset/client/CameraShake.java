package com.lastsunset.client;

import com.lastsunset.Stage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Earthquakes and shockwaves: layered sine "noise" applied to the view. */
public final class CameraShake {
	private record Quake(int start, int duration, float strength) {
	}

	private static final List<Quake> quakes = new ArrayList<>();

	private CameraShake() {
	}

	public static void add(float strength, int duration) {
		quakes.add(new Quake(ClientState.ticks, Math.max(1, duration), strength));
	}

	public static void clear() {
		quakes.clear();
	}

	public static float intensity(float partialTick) {
		float now = ClientState.ticks + partialTick;
		float total = 0f;
		quakes.removeIf(q -> now - q.start > q.duration);
		for (Quake q : quakes) {
			float f = (now - q.start) / q.duration;
			float envelope = f < 0.15f ? f / 0.15f : 1f - (f - 0.15f) / 0.85f;
			total = Math.max(total, q.strength * envelope * envelope);
		}
		// A constant tremor in the final minutes, and the shockwave itself.
		if (ClientState.stage == Stage.FINAL_MINUTES && ClientState.ticksRemaining() > 160) {
			total = Math.max(total, 0.06f + 0.12f * ClientState.stageFraction());
		}
		if (ClientState.impactAge >= 0) {
			float a = ClientState.impactAge + partialTick;
			if (a < Stage.IMPACT_BOOM_TICK) {
				total = Math.max(total, 0.25f * (a / Stage.IMPACT_BOOM_TICK));
			} else {
				float after = a - Stage.IMPACT_BOOM_TICK;
				float peak = ClientState.impactStatus == 0 || ClientState.impactStatus == 1 ? 1.3f : 1.0f;
				total = Math.max(total, peak * (float) Math.exp(-after / 110f));
			}
		}
		if (ClientState.vaporizeAge >= 0) total = Math.max(total, 0.5f);
		return total;
	}

	private static float noise(float x) {
		return (Mth.sin(x) + 0.6f * Mth.sin(x * 2.31f + 1.7f) + 0.3f * Mth.sin(x * 4.73f + 0.3f)) / 1.9f;
	}

	public static void apply(PoseStack poseStack, float partialTick) {
		if (!ClientState.active() && quakes.isEmpty()) return;
		float i = intensity(partialTick);
		if (i <= 0.001f) return;
		i *= Minecraft.getInstance().options.screenEffectScale().get().floatValue();
		if (i <= 0.001f) return;
		float t = (ClientState.ticks + partialTick) * (1.1f + i);
		poseStack.translate(noise(t * 2.1f + 30f) * 0.05f * i, noise(t * 2.4f + 40f) * 0.06f * i, 0f);
		poseStack.mulPose(Axis.ZP.rotationDegrees(noise(t * 0.9f) * 2.6f * i));
		poseStack.mulPose(Axis.XP.rotationDegrees(noise(t * 1.3f + 10f) * 1.6f * i));
		poseStack.mulPose(Axis.YP.rotationDegrees(noise(t * 1.1f + 20f) * 1.2f * i));
	}
}
