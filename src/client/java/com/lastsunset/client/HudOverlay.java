package com.lastsunset.client;

import com.lastsunset.LastSunset;
import com.lastsunset.Shelter;
import com.lastsunset.Stage;
import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.network.RosterPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * The on-screen layer. Deliberately small: a timer line, a shelter line and a radio box in the corners,
 * a survivor list behind the Tab key, and edge vignettes instead of full-screen overlays.
 */
public final class HudOverlay {
	public static final float TYPE_SPEED = 1.6f;
	private static final Identifier HEAT_VIGNETTE = LastSunset.id("textures/gui/heat_vignette.png");
	private static final int GREEN = 0x6EE08A;
	private static final int YELLOW = 0xF0C850;
	private static final int RED = 0xF05050;
	private static final String GLITCH_CHARS = "#%&@$*!?/\\|=+~";

	private HudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, LastSunset.id("overlays"), HudOverlay::renderOverlays);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LastSunset.id("panels"), HudOverlay::renderPanels);
		HudElementRegistry.addLast(LastSunset.id("flash"), HudOverlay::renderFlash);
	}

	private static int argb(float alpha, int rgb) {
		return ARGB.color(Mth.clamp(alpha, 0f, 1f), rgb);
	}

	private static boolean overworld() {
		var level = Minecraft.getInstance().level;
		return level != null && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
	}

	private static int hash(int x) {
		x ^= x >>> 16;
		x *= 0x7feb352d;
		x ^= x >>> 15;
		x *= 0x846ca68b;
		x ^= x >>> 16;
		return x & 0x7fffffff;
	}

	private static void centered(GuiGraphics g, Font font, String text, float cx, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		g.drawString(font, text, -font.width(text) / 2, 0, color, true);
		g.pose().popMatrix();
	}

	private static void vignette(GuiGraphics g, float alpha, int rgb) {
		if (alpha <= 0.01f) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.blit(RenderPipelines.GUI_TEXTURED, HEAT_VIGNETTE, 0, 0, 0f, 0f, w, h, w, h, argb(alpha, rgb));
	}

	// ------------------------------------------------------------------ edge effects (under the HUD)

	private static void renderOverlays(GuiGraphics g, DeltaTracker delta) {
		if (!ClientState.active()) return;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		float t = ClientState.ticks + pt;

		// Heat creeping in at the edges of your vision while you stand in the sun.
		if (ClientState.exposedToSky && overworld() && !ClientState.isObserver()) {
			float heat = switch (ClientState.stage) {
				case EVACUATION -> 0.08f * ClientState.stageFraction();
				case COLLAPSE -> 0.1f + 0.15f * ClientState.stageFraction();
				case FINAL_MINUTES -> 0.3f;
				case AFTERMATH -> 0.45f;
				default -> 0f;
			};
			vignette(g, heat * (0.85f + 0.15f * Mth.sin(t * 0.21f)), 0xFFFFFF);
		}

		if (ClientState.breachAge >= 0) {
			vignette(g, ClientState.breachStrength * 0.9f * (1f - (ClientState.breachAge + pt) / 200f), 0xFF6040);
		}

		// The lights in the shelter stutter as the shockwave passes overhead.
		int age = ClientState.impactAge;
		if (age >= Stage.IMPACT_BOOM_TICK && age < 300 && ClientState.impactStatus <= 1) {
			float fade = 1f - (age - Stage.IMPACT_BOOM_TICK) / 240f;
			int r = hash(ClientState.ticks * 31 + 7) % 100;
			if (r < 18) g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb((0.15f + (r % 10) * 0.025f) * fade, 0x000000));
		}
	}

	// ------------------------------------------------------------------ corner panels

	private static void renderPanels(GuiGraphics g, DeltaTracker delta) {
		if (!ClientState.active()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();

		// No timer anywhere: the sky, the heliometer and the radio are the only clocks.
		int y = renderShelter(g, font, 4);
		renderRadio(g, font, pt, 4, y + 2);
		if (mc.options.keyPlayerList.isDown()) renderManifest(g, font);

		if (ClientState.selfFallen() && mc.player != null && mc.player.isSpectator()) {
			centered(g, font, "✝ You did not survive the flare. The fallen rise again at dawn.", w / 2f, g.guiHeight() - 52, 1f, 0xB0A0A0A0);
		}
	}

	/** Top left: one line telling you whether you would survive right here. */
	private static int renderShelter(GuiGraphics g, Font font, int y) {
		Stage stage = ClientState.stage;
		boolean show = stage == Stage.EVACUATION || stage == Stage.COLLAPSE || stage == Stage.FINAL_MINUTES || stage == Stage.IMPACT || stage == Stage.AFTERMATH;
		Shelter.Result r = ClientState.shelter;
		if (!show || r == null || ClientState.isObserver() || ClientState.selfFallen()) return y;
		int color = switch (r.status()) {
			case SAFE -> GREEN;
			case PARTIAL -> YELLOW;
			case EXPOSED -> RED;
		};
		String hint = switch (r.status()) {
			case SAFE -> "will hold";
			case PARTIAL -> "needs +" + Math.max(1, Math.round(r.required() - r.mass())) + " rock overhead";
			case EXPOSED -> "sunlight gets in";
		};
		boolean urgent = r.status() == Shelter.Status.EXPOSED && (stage == Stage.FINAL_MINUTES || stage == Stage.AFTERMATH);
		float alpha = r.status() == Shelter.Status.SAFE ? 0.6f : 0.9f;
		int dotColor = urgent && (ClientState.ticks / 6) % 2 == 0 ? 0xFFFFFF : color;
		int x = 4;
		g.drawString(font, "●", x, y, argb(alpha, dotColor), true);
		x += 9;
		String label = "Shelter ";
		g.drawString(font, label, x, y, argb(alpha, 0xC8C8C8), true);
		x += font.width(label);
		String pct = r.percent() + "%";
		g.drawString(font, pct, x, y, argb(alpha, color), true);
		x += font.width(pct);
		g.drawString(font, "  " + hint, x, y, argb(alpha * 0.8f, 0x9A9A9A), true);
		return y + 11;
	}

	/** Hold Tab to see who made it underground. */
	private static void renderManifest(GuiGraphics g, Font font) {
		Stage stage = ClientState.stage;
		List<RosterPayload.Entry> roster = ClientState.roster;
		if (stage == Stage.OMENS || roster.isEmpty()) return;
		int rows = Math.min(16, roster.size());
		int width = 110;
		for (int i = 0; i < rows; i++) width = Math.max(width, font.width(roster.get(i).name()) + 76);
		int x = g.guiWidth() - width - 4;
		int y = g.guiHeight() / 2 - (rows * 10 + 12) / 2;
		g.fill(x - 3, y - 3, x + width + 1, y + 11 + rows * 10, 0x55000000);
		g.drawString(font, "Survivors", x, y, 0xC0B0B0B0, true);
		for (int i = 0; i < rows; i++) {
			RosterPayload.Entry e = roster.get(i);
			int ry = y + 12 + i * 10;
			int status = stage == Stage.WASTELAND && e.status() <= RosterPayload.EXPOSED ? -1 : e.status();
			String label;
			int color;
			switch (status) {
				case -1 -> {
					label = "alive";
					color = 0xE8C080;
				}
				case RosterPayload.SAFE -> {
					label = "safe";
					color = GREEN;
				}
				case RosterPayload.PARTIAL -> {
					label = e.percent() + "%";
					color = YELLOW;
				}
				case RosterPayload.EXPOSED -> {
					label = "exposed";
					color = RED;
				}
				case RosterPayload.FALLEN -> {
					label = "fallen";
					color = 0x707070;
				}
				default -> {
					label = "watching";
					color = 0x909090;
				}
			}
			Component name = e.status() == RosterPayload.FALLEN
				? Component.literal(e.name()).withStyle(ChatFormatting.STRIKETHROUGH)
				: Component.literal(e.name());
			g.drawString(font, "●", x, ry, 0xFF000000 | color, true);
			g.drawString(font, name, x + 9, ry, e.status() == RosterPayload.FALLEN ? 0xFF808080 : 0xFFE0E0E0, true);
			g.drawString(font, label, x + width - font.width(label), ry, 0xFF000000 | color, true);
		}
	}

	/** A small radio box in the corner; the text types itself out. */
	private static void renderRadio(GuiGraphics g, Font font, float pt, int x, int y) {
		BroadcastPayload b = ClientState.broadcast;
		if (b == null || ClientState.finalCountdown()) return;
		float age = ClientState.broadcastAge + pt;
		float alpha = Math.min(1f, age / 8f) * Math.min(1f, (b.duration() + 30 - age) / 20f);
		if (alpha <= 0.03f) return;
		boolean glitch = b.style() == BroadcastPayload.GLITCH;
		if (glitch && hash(ClientState.ticks * 13) % 100 < 12) alpha *= 0.4f;
		int accent = switch (b.style()) {
			case BroadcastPayload.EMERGENCY, BroadcastPayload.FINAL -> 0xE04040;
			case BroadcastPayload.GLITCH -> 0x808080;
			case BroadcastPayload.DAWN -> 0xE0A850;
			default -> 0x6A9FE0;
		};
		int width = Mth.clamp(g.guiWidth() / 3, 140, 200);
		List<String> lines = new ArrayList<>();
		for (FormattedText line : font.getSplitter().splitLines(b.body(), width, Style.EMPTY)) lines.add(line.getString());
		int height = 11 + lines.size() * 9;
		g.fill(x - 2, y - 2, x + width + 3, y + height + 1, argb(alpha * 0.33f, 0x000000));
		g.fill(x - 2, y - 2, x - 1, y + height + 1, argb(alpha * 0.9f, accent));
		boolean live = (ClientState.ticks / 10) % 2 == 0;
		String header = (glitch ? "" : live ? "● " : "  ") + b.header();
		g.drawString(font, header, x + 2, y, argb(alpha, accent), true);

		int reveal = (int) (age * TYPE_SPEED);
		int shown = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int n = Mth.clamp(reveal - shown, 0, line.length());
			shown += line.length();
			if (n <= 0) break;
			String visible = line.substring(0, n);
			if (glitch) visible = glitch(visible, i);
			g.drawString(font, visible, x + 2, y + 11 + i * 9, argb(alpha * 0.9f, 0xDCDCDC), true);
		}
	}

	private static String glitch(String s, int line) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			int r = hash(ClientState.ticks / 2 * 977 + line * 131 + i);
			char c = s.charAt(i);
			b.append(c != ' ' && r % 100 < 14 ? GLITCH_CHARS.charAt(r % GLITCH_CHARS.length()) : c);
		}
		return b.toString();
	}

	// ------------------------------------------------------------------ the flare itself

	/** Only the impact gets to fill the screen, and only for those caught in the open. */
	private static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		if (!ClientState.active()) return;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();

		if (ClientState.impactAge >= 0) {
			float a = ClientState.impactAge + pt;
			float rise = Mth.square(Mth.clamp(a / Stage.IMPACT_BOOM_TICK, 0f, 1f));
			float flash = switch (ClientState.impactStatus) {
				case 2 -> rise * 0.92f;
				case 3 -> a < 120 ? rise * 0.7f : 0.7f * (1f - Mth.clamp((a - 120) / 80f, 0f, 1f));
				case 1 -> a < Stage.IMPACT_BOOM_TICK ? 0f : 0.3f * (float) Math.exp(-(a - Stage.IMPACT_BOOM_TICK) / 14f);
				default -> a < Stage.IMPACT_BOOM_TICK ? 0f : 0.12f * (float) Math.exp(-(a - Stage.IMPACT_BOOM_TICK) / 6f);
			};
			if (flash > 0.003f) g.fill(0, 0, w, h, argb(flash, 0xFFFFFF));
		}
		if (ClientState.vaporizeAge >= 0) {
			// Burning away from the edges inward; the world stays visible behind the fire.
			float v = Mth.clamp((ClientState.vaporizeAge + pt) / 34f, 0f, 1f);
			vignette(g, 0.6f + 0.4f * v, v < 0.5f ? 0xFFD090 : 0xFF6A30);
			g.fill(0, 0, w, h, argb(0.35f * v, 0x5A1000));
		}
		if (ClientState.dawnAge >= 0 && ClientState.dawnAge < 40) {
			g.fill(0, 0, w, h, argb(0.6f * (1f - (ClientState.dawnAge + pt) / 40f), 0x000000));
		}
	}
}
