package com.lastsunset;

import com.lastsunset.content.ModBlocks;
import com.lastsunset.content.ModItems;
import com.lastsunset.network.BroadcastPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The emergency broadcast timeline. Only people with a radio (carried, or within earshot of a placed one)
 * hear these, and they never give an exact time: just how worried the voices on the air are.
 */
public final class Broadcasts {
	private static final int RADIO_RANGE = 12;

	private Broadcasts() {
	}

	/** Vague, human estimates instead of a countdown. */
	static String eta(long ticks) {
		if (ticks > 72000) return "within the next few hours";
		if (ticks > 36000) return "within the hour";
		if (ticks > 18000) return "in under half an hour";
		if (ticks > 6000) return "within minutes";
		return "at any moment";
	}

	static void onStage(MinecraftServer server, ApocalypseData data, Stage stage) {
		String eta = eta(data.ticksRemaining());
		switch (stage) {
			case EVACUATION -> send(server, data, "evac1", BroadcastPayload.EMERGENCY, "EMERGENCY ALERT SYSTEM",
				"The sun is destabilizing. Scientists now expect a lethal solar flare " + eta + ". Evacuate the surface and seek deep underground shelter.", 320);
			case COLLAPSE -> send(server, data, "collapse1", BroadcastPayload.EMERGENCY, "EMERGENCY ALERT SYSTEM",
				"Meteor storms and earthquakes are intensifying and surface temperatures are rising fast. The flare is expected " + eta + ". Do not look at the sun.", 300);
			case FINAL_MINUTES -> send(server, data, "final1", BroadcastPayload.FINAL, "FINAL WARNING",
				"Instruments are failing. The flare is imminent. If you are on the surface, you will not survive. Get underground NOW.", 260);
			case WASTELAND -> send(server, data, "dawn", BroadcastPayload.DAWN, "DAY ONE",
				(data.survivorCount == 1 ? "1 soul" : data.survivorCount + " souls")
					+ " survived the last sunset. The firestorm has passed. The land is scarred and silent, but it is ours. Begin again.", 360);
			default -> {
			}
		}
	}

	static void tick(MinecraftServer server, ApocalypseData data) {
		float f = data.stageFraction();
		switch (data.stage) {
			case OMENS -> {
				if (f >= 0.15f) send(server, data, "omens1", BroadcastPayload.BULLETIN, "SOLAR OBSERVATORY NETWORK",
					"Observatories report unusual activity on the solar surface. The sun appears brighter than normal. There is no cause for alarm.", 260);
				if (f >= 0.6f) send(server, data, "omens2", BroadcastPayload.BULLETIN, "SOLAR OBSERVATORY NETWORK",
					"Reports of panicking animals and minor tremors are coming in from everywhere. Heliometer readings are elevated. Authorities are monitoring the situation.", 260);
			}
			case EVACUATION -> {
				if (f >= 0.3f) send(server, data, "evac2", BroadcastPayload.EMERGENCY, "SHELTER GUIDANCE",
					"Rock absorbs the heat: dig deep. Seal every opening. If sunlight can reach you, the flare will too. Watch your heliometer: when the needle reaches red, it is too late to start digging.", 360);
				if (f >= 0.65f) send(server, data, "evac3", BroadcastPayload.BULLETIN, "CIVIL DEFENSE",
					"Supply crates are being dropped across the region. Follow the smoke columns. Meteorite cores contain solarite: it can be forged into flare shielding.", 280);
			}
			case COLLAPSE -> {
				if (f >= 0.5f) send(server, data, "collapse2", BroadcastPayload.EMERGENCY, "SHELTER GUIDANCE",
					"Flare shielding is the strongest protection known, then obsidian. Deepslate and stone will hold. Glass, wood and leaves will not save you.", 300);
			}
			case FINAL_MINUTES -> {
				if (data.ticksRemaining() <= 1200 && data.ticksRemaining() > 200) send(server, data, "final2", BroadcastPayload.FINAL, "LAST TRANSMISSION",
					"This is the final broadcast. To anyone still out there: close the door, stay together, hold on. It has been an honour. Goodbye.", 300);
			}
			case AFTERMATH -> {
				if (data.stageTicks >= 240) send(server, data, "after1", BroadcastPayload.GLITCH, "...SIGNAL LOST...",
					"...anyone... receiving... surface temperature... over a thousand degrees... do not... go outside... stay below...", 260);
				if (f >= 0.6f) send(server, data, "after2", BroadcastPayload.GLITCH, "...WEAK SIGNAL...",
					"...temperatures... falling... the fires are... dying down... hold on... a little longer...", 240);
			}
			default -> {
			}
		}
	}

	private static void send(MinecraftServer server, ApocalypseData data, String key, int style, String header, String body, int duration) {
		if (!data.fired.add(key)) return;
		air(server, data, style, header, body, duration);
	}

	/** Puts a message on the air for everyone with a radio, and remembers it for replays. */
	public static void air(MinecraftServer server, ApocalypseData data, int style, String header, String body, int duration) {
		data.lastStyle = style;
		data.lastHeader = header;
		data.lastBody = body;
		data.setDirty();
		BroadcastPayload payload = new BroadcastPayload(style, header, body, duration);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (hasRadio(player)) Fx.send(player, payload);
		}
	}

	/** Plays the last broadcast again for one player (radio right-click). */
	public static void replay(ServerPlayer player) {
		ApocalypseData data = ApocalypseManager.data();
		if (data == null || data.lastStyle < 0) {
			Fx.send(player, new BroadcastPayload(BroadcastPayload.GLITCH, "...STATIC...", "...nothing on the air but static...", 80));
			return;
		}
		Fx.send(player, new BroadcastPayload(data.lastStyle, data.lastHeader, data.lastBody, 260));
	}

	public static boolean hasRadio(ServerPlayer player) {
		if (player.getInventory().contains(stack -> stack.is(ModItems.EMERGENCY_RADIO))) return true;
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIO_RANGE, -RADIO_RANGE / 2, -RADIO_RANGE),
			center.offset(RADIO_RANGE, RADIO_RANGE / 2, RADIO_RANGE))) {
			if (player.level().getBlockState(pos).is(ModBlocks.EMERGENCY_RADIO)) return true;
		}
		return false;
	}
}
