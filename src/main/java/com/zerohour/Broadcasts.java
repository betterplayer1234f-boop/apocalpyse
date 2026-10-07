package com.zerohour;

import com.zerohour.content.ModBlocks;
import com.zerohour.content.ModItems;
import com.zerohour.network.BroadcastPayload;
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

	private static String lz(ApocalypseData data) {
		return "X " + data.lzX + ", Z " + data.lzZ;
	}

	static void onStage(MinecraftServer server, ApocalypseData data, Stage stage) {
		String eta = eta(data.ticksRemaining());
		switch (stage) {
			case OUTBREAK -> send(server, data, "outbreak1", BroadcastPayload.EMERGENCY, "EMERGENCY ALERT SYSTEM",
				"A highly contagious infection is spreading through the cities. The infected are violent and feel no pain. A single bite can pass it on. Stay indoors, barricade your doors and avoid all contact with the sick.", 340);
			case LAUNCH -> send(server, data, "launch1", BroadcastPayload.EMERGENCY, "EMERGENCY ALERT SYSTEM",
				"BALLISTIC MISSILE THREAT. Russian strategic forces have launched nuclear missiles. Impact is expected " + eta
					+ ". Seek deep underground shelter immediately. This is not a drill.", 320);
			case INCOMING -> send(server, data, "incoming1", BroadcastPayload.FINAL, "FINAL WARNING",
				"Missiles are inbound to this region. If you are on the surface, take cover now. Do not look at the flash. Stay below ground until the fallout warning is lifted.", 280);
			case EVACUATION -> send(server, data, "evac1", BroadcastPayload.RESCUE, "MILITARY EVACUATION",
				"Fallout levels are dropping. Helicopters are landing at " + lz(data) + ". The window closes in " + Fx.humanTime(data.evacTicksLeft())
					+ ". Wear protective gear: the ground is still hot. Stay away from the crater. Move fast, and move together.", 360);
			default -> {
			}
		}
	}

	static void tick(MinecraftServer server, ApocalypseData data) {
		float f = data.stageFraction();
		switch (data.stage) {
			case TENSIONS -> {
				if (f >= 0.1f) send(server, data, "tensions1", BroadcastPayload.BULLETIN, "WORLD NEWS",
					"Peace talks have collapsed. Russian forces are massing on the border and NATO has raised its alert level. The government urges calm: there is no immediate threat to the homeland.", 300);
				if (f >= 0.55f) send(server, data, "tensions2", BroadcastPayload.BULLETIN, "WORLD NEWS",
					"Field hospitals near the front report soldiers with a strange fever. Patients have become violent and attacked medical staff. Officials deny any link to the fighting.", 300);
				if (f >= 0.85f) send(server, data, "tensions3", BroadcastPayload.BULLETIN, "CIVIL DEFENSE",
					"Check your survival kit. Keep your radio on. Iodine tablets cut the radiation your body takes in. Know where you would shelter if the sirens sound.", 280);
			}
			case OUTBREAK -> {
				if (f >= 0.3f) send(server, data, "outbreak2", BroadcastPayload.EMERGENCY, "INFECTION GUIDANCE",
					"The infection spreads through bites. Fever comes first, then delirium. An antiviral can stop it if given in time. Those who die infected will rise again. Do not hesitate.", 320);
				if (f >= 0.6f) send(server, data, "outbreak3", BroadcastPayload.BULLETIN, "CIVIL DEFENSE",
					"Begin building a shelter now. Every metre of rock overhead stops more of the blast and more of the fallout. Seal every opening. Blast shielding and obsidian are the strongest materials known.", 320);
				if (f >= 0.9f) send(server, data, "outbreak4", BroadcastPayload.BULLETIN, "WORLD NEWS",
					"Russia has issued a final ultimatum. Embassies are being evacuated. Strategic bombers are in the air.", 260);
			}
			case LAUNCH -> {
				if (f >= 0.35f) send(server, data, "launch2", BroadcastPayload.EMERGENCY, "SHELTER GUIDANCE",
					"If daylight can reach you, the blast can too. Get underground and seal the entrance. After the blast, stay below: the fallout makes the surface lethal. If you must go out, wear a gas mask and a hazmat suit.", 340);
				if (f >= 0.75f) send(server, data, "launch3", BroadcastPayload.GLITCH, "...WORLD NEWS...",
					"...flashes reported on the horizon... cities in the east are no longer responding... stay off the roads...", 240);
			}
			case INCOMING -> {
				if (data.ticksRemaining() <= 1200 && data.ticksRemaining() > 200) send(server, data, "incoming2", BroadcastPayload.FINAL, "LAST TRANSMISSION",
					"This is the final broadcast. To anyone still out there: close the door, stay together, hold on. When it's over, keep listening. We will come for you. Goodbye.", 300);
			}
			case FALLOUT -> {
				if (data.stageTicks >= 240) send(server, data, "fallout1", BroadcastPayload.GLITCH, "...SIGNAL LOST...",
					"...anyone receiving... the fallout is... lethal... do not go outside... stay below... take your iodine...", 260);
				if (f >= 0.6f) send(server, data, "fallout2", BroadcastPayload.GLITCH, "...WEAK SIGNAL...",
					"...radiation is falling... an evacuation... is being organised... hold on... a little longer...", 240);
			}
			case EVACUATION -> {
				if (data.evacClosed || !data.hasLz) return;
				if (f >= 0.5f) send(server, data, "evac2", BroadcastPayload.EMERGENCY, "MILITARY EVACUATION",
					"Helicopters are still landing at " + lz(data) + ". Look for the red flares. The window closes in " + Fx.humanTime(data.evacTicksLeft()) + ".", 280);
				if (data.evacTicksLeft() <= 2400 && data.evacTicksLeft() > 200) send(server, data, "evac3", BroadcastPayload.FINAL, "LAST CALL",
					"Last call for the evacuation at " + lz(data) + ". The last helicopter leaves in two minutes. We can't wait any longer than that.", 260);
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
