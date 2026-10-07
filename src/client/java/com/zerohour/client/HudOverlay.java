package com.zerohour.client;

import com.zerohour.Infection;
import com.zerohour.Radiation;
import com.zerohour.Shelter;
import com.zerohour.Stage;
import com.zerohour.ZeroHour;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.RosterPayload;
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
 * The on-screen layer. Deliberately small: a few status lines and a radio box in the corner,
 * a survivor list behind the Tab key, and edge vignettes instead of full-screen overlays.
 */
public final class HudOverlay {
	public static final float TYPE_SPEED = 1.6f;
	private static final Identifier VIGNETTE = ZeroHour.id("textures/gui/vignette.png");
	private static final int GREEN = 0x6EE08A;
	private static final int YELLOW = 0xF0C850;
	private static final int RED = 0xF05050;
	private static final int RAD_GREEN = 0x9CFF5A;
	private static final String GLITCH_CHARS = "#%&@$*!?/\\|=+~";
	private static final String ARROWS = "↑↗→↘↓↙←↖";

	private HudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ZeroHour.id("overlays"), HudOverlay::renderOverlays);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ZeroHour.id("panels"), HudOverlay::renderPanels);
		HudElementRegistry.addLast(ZeroHour.id("flash"), HudOverlay::renderFlash);
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
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, w, h, argb(alpha, rgb));
	}

	// ------------------------------------------------------------------ edge effects (under the HUD)

	private static void renderOverlays(GuiGraphics g, DeltaTracker delta) {
		if (!ClientState.active()) return;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		float t = ClientState.ticks + pt;
		boolean observer = ClientState.isObserver();

		// Radiation glowing green at the edges of your vision.
		float rate = ClientState.shownRate;
		if (rate > 0.15f && !observer) {
			float a = Math.min(0.55f, 0.08f + rate / 14f);
			vignette(g, a * (0.8f + 0.2f * Mth.sin(t * 0.5f)), RAD_GREEN);
		}

		// The infection: your vision darkens and throbs as you turn.
		float infection = ClientState.infection;
		if (infection >= 0.5f && !observer) {
			float a = 0.15f + 0.35f * (infection - 0.5f) * 2f;
			vignette(g, a * (0.6f + 0.4f * Mth.sin(t * (0.25f + 0.25f * infection))), 0x6A0C0C);
		}
		if (ClientState.bittenAge >= 0) {
			vignette(g, 0.7f * (1f - (ClientState.bittenAge + pt) / 40f), 0xC01818);
		}

		if (ClientState.breachAge >= 0) {
			vignette(g, ClientState.breachStrength * 0.9f * (1f - (ClientState.breachAge + pt) / 200f), 0xFF6040);
		}

		// The lights in the shelter stutter as the blast wave passes overhead.
		float shock = ClientState.sinceShock(pt);
		if (ClientState.detonationAge >= 0 && shock >= 0f && shock < 240f && ClientState.impactStatus <= 1) {
			float fade = 1f - shock / 240f;
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

		// No timer anywhere: the sky, the radio and the Geiger counter are the only clocks.
		int y = 4;
		if (!ClientState.isObserver() && !ClientState.selfFallen()) {
			y = renderShelter(g, font, y);
			y = renderRadiation(g, font, y);
			y = renderInfection(g, font, y);
			y = renderEvacuation(g, font, y);
		}
		renderRadio(g, font, pt, 4, y + 2);
		if (mc.options.keyPlayerList.isDown()) renderManifest(g, font);

		if (ClientState.selfFallen() && mc.player != null && mc.player.isSpectator()) {
			centered(g, font, "✝ You did not survive the blast. When the evacuation begins, survivors will dig you out.", w / 2f, g.guiHeight() - 52, 1f, 0xB0A0A0A0);
		}
	}

	private static int line(GuiGraphics g, Font font, int y, String icon, int iconColor, String label, String value, int valueColor, String hint, float alpha) {
		int x = 4;
		g.drawString(font, icon, x, y, argb(alpha, iconColor), true);
		x += font.width(icon) + 3;
		g.drawString(font, label, x, y, argb(alpha, 0xC8C8C8), true);
		x += font.width(label);
		g.drawString(font, value, x, y, argb(alpha, valueColor), true);
		x += font.width(value);
		if (!hint.isEmpty()) g.drawString(font, "  " + hint, x, y, argb(alpha * 0.8f, 0x9A9A9A), true);
		return y + 11;
	}

	/** Before the strike: would this spot survive the blast? */
	private static int renderShelter(GuiGraphics g, Font font, int y) {
		Shelter.Result r = ClientState.shelter;
		if (!ClientState.blastPhase() || r == null) return y;
		int color = switch (r.status()) {
			case SAFE -> GREEN;
			case PARTIAL -> YELLOW;
			case EXPOSED -> RED;
		};
		String hint = switch (r.status()) {
			case SAFE -> "will hold";
			case PARTIAL -> "needs +" + Math.max(1, Math.round(r.required() - r.mass())) + " rock overhead";
			case EXPOSED -> "daylight gets in";
		};
		boolean urgent = r.status() == Shelter.Status.EXPOSED && (ClientState.stage == Stage.INCOMING || ClientState.stage == Stage.DETONATION);
		float alpha = r.status() == Shelter.Status.SAFE ? 0.6f : 0.9f;
		int dotColor = urgent && (ClientState.ticks / 6) % 2 == 0 ? 0xFFFFFF : color;
		return line(g, font, y, "●", dotColor, "Shelter ", r.percent() + "%", color, hint, alpha);
	}

	/** After the strike, with a Geiger counter in your pack: the dose rate and what you've absorbed. */
	private static int renderRadiation(GuiGraphics g, Font font, int y) {
		if (ClientState.blastPhase() || !ClientState.stage.isPostStrike() || !ClientState.carriesGeiger()) return y;
		float rate = ClientState.shownRate;
		int color = rate < 0.05f ? GREEN : rate < 2f ? YELLOW : RED;
		boolean urgent = rate >= 6f && (ClientState.ticks / 6) % 2 == 0;
		String sickness = Radiation.sicknessLabel(ClientState.dose);
		String hint = sickness.isEmpty() ? Radiation.rateLabel(rate) : sickness;
		if (ClientState.iodineTicks > 0) hint += " · iodine";
		String value = String.format("%.2f rad/s", rate) + "  ·  " + Math.round(ClientState.dose) + " rad";
		return line(g, font, y, "☢", urgent ? 0xFFFFFF : color, "", value, color, hint, rate < 0.05f && ClientState.dose < 1f ? 0.6f : 0.9f);
	}

	private static int renderInfection(GuiGraphics g, Font font, int y) {
		float f = ClientState.infection;
		if (f < 0f) return y;
		int color = f < 0.5f ? 0xC0E070 : f < 0.75f ? 0xE09040 : 0xE03030;
		boolean urgent = f >= 0.75f && (ClientState.ticks / 8) % 2 == 0;
		return line(g, font, y, "☣", urgent ? 0xFFFFFF : color, "Infected — ", Infection.phaseLabel(f), color, "find an antiviral", 0.9f);
	}

	/** Which way to the landing zone (or the safe zone, once the last flight has gone). */
	private static int renderEvacuation(GuiGraphics g, Font font, int y) {
		Minecraft mc = Minecraft.getInstance();
		if (ClientState.stage != Stage.EVACUATION || ClientState.evacuated || mc.player == null || !overworld()) return y;
		boolean toLz = ClientState.evacOpen && ClientState.hasLz;
		if (!toLz && !ClientState.hasSafeZone) return y;
		double tx = toLz ? ClientState.lzX + 0.5 : ClientState.safeX + 0.5;
		double tz = toLz ? ClientState.lzZ + 0.5 : ClientState.safeZ + 0.5;
		double dx = tx - mc.player.getX();
		double dz = tz - mc.player.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float relative = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
		int index = Math.floorMod(Math.round(relative / 45f), 8);
		String arrow = String.valueOf(ARROWS.charAt(index));
		String hint = toLz ? "window closes in " + Math.max(1, (ClientState.evacTicksLeft + 1199) / 1200) + " min" : "walk";
		int color = toLz ? 0xF0C060 : 0xC0C0A0;
		return line(g, font, y, toLz ? "✈" : "⌂", color, toLz ? "Landing zone " : "Safe zone ", dist + " m " + arrow, color, hint, 0.9f);
	}

	/** Hold Tab to see who made it. */
	private static void renderManifest(GuiGraphics g, Font font) {
		List<RosterPayload.Entry> roster = ClientState.roster;
		if (ClientState.stage == Stage.TENSIONS || roster.isEmpty()) return;
		int rows = Math.min(16, roster.size());
		int width = 120;
		for (int i = 0; i < rows; i++) width = Math.max(width, font.width(roster.get(i).name()) + 86);
		int x = g.guiWidth() - width - 4;
		int y = g.guiHeight() / 2 - (rows * 10 + 12) / 2;
		g.fill(x - 3, y - 3, x + width + 1, y + 11 + rows * 10, 0x55000000);
		g.drawString(font, "Survivors", x, y, 0xC0B0B0B0, true);
		for (int i = 0; i < rows; i++) {
			RosterPayload.Entry e = roster.get(i);
			int ry = y + 12 + i * 10;
			String label;
			int color;
			switch (e.status()) {
				case RosterPayload.SAFE -> {
					label = "sheltered";
					color = GREEN;
				}
				case RosterPayload.PARTIAL -> {
					label = e.value() + "%";
					color = YELLOW;
				}
				case RosterPayload.EXPOSED -> {
					label = "exposed";
					color = RED;
				}
				case RosterPayload.FALLEN -> {
					label = "lost";
					color = 0x707070;
				}
				case RosterPayload.EVACUATED -> {
					label = "evacuated";
					color = 0xF0C060;
				}
				case RosterPayload.INFECTED -> {
					label = "infected";
					color = 0xC0E070;
				}
				case RosterPayload.ALIVE -> {
					label = e.value() > 0 ? e.value() + " rad" : "alive";
					color = e.value() >= 600 ? RED : e.value() >= 200 ? YELLOW : 0xE8C080;
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
		if (b == null || ClientState.silence()) return;
		float age = ClientState.broadcastAge + pt;
		float alpha = Math.min(1f, age / 8f) * Math.min(1f, (b.duration() + 30 - age) / 20f);
		if (alpha <= 0.03f) return;
		boolean glitch = b.style() == BroadcastPayload.GLITCH;
		if (glitch && hash(ClientState.ticks * 13) % 100 < 12) alpha *= 0.4f;
		int accent = switch (b.style()) {
			case BroadcastPayload.EMERGENCY, BroadcastPayload.FINAL -> 0xE04040;
			case BroadcastPayload.GLITCH -> 0x808080;
			case BroadcastPayload.RESCUE -> 0xE0A850;
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

	// ------------------------------------------------------------------ the flash, and the way out

	/** Only the flash and the helicopter get to fill the screen. */
	private static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		if (!ClientState.active()) return;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();

		if (ClientState.detonationAge >= 0) {
			float a = ClientState.sinceFlash(pt);
			float flash = 0f;
			if (a >= 0f) {
				flash = switch (ClientState.impactStatus) {
					// Caught in the open: the world is nothing but white.
					case 2 -> 0.95f;
					case 3 -> 0.8f * (1f - Mth.clamp((a - 30f) / 90f, 0f, 1f));
					case 1 -> 0.35f * (float) Math.exp(-a / 14f);
					default -> 0.12f * (float) Math.exp(-a / 6f);
				};
			} else if (ClientState.impactStatus >= 2) {
				// The warhead's glow, swelling in the last second.
				flash = 0.25f * Mth.square(Mth.clamp((a + 20f) / 20f, 0f, 1f));
			}
			if (flash > 0.003f) g.fill(0, 0, w, h, argb(flash, 0xFFFFFF));
		}
		if (ClientState.distantFlashAge >= 0 && ClientState.exposedToSky && overworld()) {
			float f = 0.18f * ClientState.distantFlashStrength * (float) Math.exp(-(ClientState.distantFlashAge + pt) / 10f);
			if (f > 0.003f) g.fill(0, 0, w, h, argb(f, 0xFFF0D8));
		}
		if (ClientState.vaporizeAge >= 0) {
			// Burning away from the edges inward; the world stays visible behind the fire.
			float v = Mth.clamp((ClientState.vaporizeAge + pt) / 34f, 0f, 1f);
			vignette(g, 0.6f + 0.4f * v, v < 0.5f ? 0xFFD090 : 0xFF6A30);
			g.fill(0, 0, w, h, argb(0.35f * v, 0x5A1000));
		}
		if (ClientState.extractionAge >= 0) {
			// Fade to black while the helicopter lifts you out, then open your eyes at the safe zone.
			float e = ClientState.extractionAge + pt;
			float black = e < 60f ? e / 60f : e < 100f ? 1f : 1f - (e - 100f) / 70f;
			if (black > 0.003f) g.fill(0, 0, w, h, argb(black, 0x000000));
			float text = e > 55f && e < 150f ? Math.min(1f, (e - 55f) / 15f) * Math.min(1f, (150f - e) / 25f) : 0f;
			if (text > 0.03f) {
				centered(g, Minecraft.getInstance().font, "EVACUATED", w / 2f, h / 2f - 12, 3f, argb(text, 0xF0C060));
				centered(g, Minecraft.getInstance().font, "You made it out.", w / 2f, h / 2f + 22, 1f, argb(text * 0.8f, 0xC8C8C8));
			}
		}
		if (ClientState.evacStartAge >= 0 && ClientState.evacStartAge < 40) {
			g.fill(0, 0, w, h, argb(0.6f * (1f - (ClientState.evacStartAge + pt) / 40f), 0x000000));
		}
	}
}
