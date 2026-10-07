package com.lastsunset.test;

import com.lastsunset.ApocalypseManager;
import com.lastsunset.Stage;
import com.lastsunset.content.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.gamerules.GameRules;
import org.lwjgl.glfw.GLFW;

/**
 * Plays through the whole apocalypse in a real client and screenshots it:
 * the automatic start and survival kit, the instruments, a sealed bunker (survive),
 * and standing in the open (vaporized, spectate, revived at dawn).
 */
public class CinematicTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("8675309");
				s.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
				s.getGameRules().set(GameRules.SPAWN_MONSTERS, false, null);
			})
			.create()) {
			ctx.getInput().resizeWindow(1280, 720);
			sp.getClientWorld().waitForChunksRender();
			TestServerContext server = sp.getServer();

			// It starts by itself, and everyone gets a survival kit.
			Stage stage = server.computeOnServer(s -> ApocalypseManager.data().stage);
			if (stage != Stage.OMENS) throw new AssertionError("The end should begin as soon as the world starts, but stage was " + stage);
			if (!ctx.computeOnClient(mc -> mc.player.getInventory().contains(st -> st.is(ModItems.EMERGENCY_RADIO))
				&& mc.player.getInventory().contains(st -> st.is(ModItems.HELIOMETER)))) {
				throw new AssertionError("Players should receive a radio and a heliometer on first join");
			}

			// Compress the hidden build-up to four minutes for the test.
			server.runCommand("apocalypse config duration 4");
			server.runCommand("gamemode survival @a");
			server.runCommand("time set 1000");
			// A lookout platform above the forest canopy so the sky is visible.
			server.runCommand("execute as @a at @s run fill ~-1 ~16 ~-1 ~1 ~16 ~1 stone");
			server.runCommand("execute as @a at @s run tp @s ~ ~17 ~ 90 -12");
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			ctx.waitTicks(60);
			ctx.takeScreenshot("00-omens-heliometer");

			// The radio, placed on the platform in front of us.
			server.runCommand("execute as @a at @s run setblock ~-1 ~ ~ lastsunset:emergency_radio[facing=east]");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 50");
			ctx.waitTicks(20);
			ctx.takeScreenshot("01-radio-block");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -12");

			server.runCommand("apocalypse time 150");
			ctx.waitTicks(140);
			ctx.takeScreenshot("02-evacuation-broadcast");
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(5);
			ctx.takeScreenshot("03-heliometer-reading");

			// Signal flare.
			server.runCommand("give @a lastsunset:flare_gun");
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[3]);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -55");
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(25);
			ctx.takeScreenshot("04-flare-rising");
			ctx.waitTicks(50);
			ctx.takeScreenshot("05-flare-hanging");

			// A supply crate and a meteor, announced over the radio / screaming overhead.
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -12");
			server.runCommand("execute as @a run apocalypse supplydrop");
			ctx.waitTicks(60);
			ctx.takeScreenshot("06-supply-drop-radio");
			server.runCommand("execute as @a run apocalypse meteor");
			ctx.waitTicks(100);
			ctx.takeScreenshot("07-meteor");

			ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			server.runCommand("apocalypse time 70");
			ctx.waitTicks(220);
			ctx.takeScreenshot("08-collapse-last-sunset");

			server.runCommand("apocalypse time 30");
			ctx.waitTicks(60);
			// Seal ourselves in: 9 layers of obsidian overhead (36 rock-meters), lit by a lantern.
			server.runCommand("execute as @a at @s run fill ~-3 ~-1 ~-3 ~3 ~12 ~3 obsidian");
			server.runCommand("execute as @a at @s run fill ~-2 ~ ~-2 ~2 ~3 ~2 air");
			server.runCommand("execute as @a at @s run setblock ~2 ~ ~2 lantern");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
			ctx.waitTicks(80);
			ctx.takeScreenshot("09-in-bunker");
			ctx.getInput().holdKey(options -> options.keyPlayerList);
			ctx.waitTicks(2);
			ctx.takeScreenshot("10-tab-survivors");
			ctx.getInput().releaseKey(options -> options.keyPlayerList);
			ctx.waitTicks(488);
			ctx.takeScreenshot("11-impact-sheltered");
			ctx.waitTicks(660);
			ctx.takeScreenshot("12-aftermath-bunker");

			// Step outside into the firestorm (with fire resistance so the test survives).
			server.runCommand("effect give @a fire_resistance 120 0 true");
			server.runCommand("execute as @a at @s run tp @s ~ ~14 ~ 90 25");
			ctx.waitTicks(200);
			ctx.takeScreenshot("13-aftermath-ash");
			server.runCommand("apocalypse skip");
			ctx.waitTicks(260);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 15");
			ctx.waitTicks(30);
			ctx.takeScreenshot("14-day-one");

			// Second run: stand in the open when the flare hits.
			server.runCommand("apocalypse stop");
			server.runCommand("effect clear @a");
			server.runCommand("execute as @a at @s run fill ~25 ~-14 ~-1 ~27 ~-14 ~1 stone");
			server.runCommand("execute as @a at @s run tp @s ~26 ~-13 ~ 90 -10");
			ctx.waitTicks(40);
			server.runCommand("apocalypse impact");
			ctx.waitTicks(230);
			ctx.takeScreenshot("15-exposed-whiteout");
			ctx.waitTicks(60);
			ctx.takeScreenshot("16-exposed-vaporizing");
			ctx.waitTicks(100);
			ctx.takeScreenshot("17-death-screen");

			// The fallen watch as spectators until dawn, then rise again.
			ctx.clickScreenButton("deathScreen.respawn");
			ctx.waitTicks(60);
			if (!ctx.computeOnClient(mc -> mc.player != null && mc.player.isSpectator())) {
				throw new AssertionError("A player vaporized by the flare should respawn as a spectator");
			}
			ctx.takeScreenshot("18-fallen-spectator");
			server.runCommand("apocalypse skip");
			ctx.waitTicks(40);
			server.runCommand("apocalypse skip");
			ctx.waitTicks(120);
			if (!ctx.computeOnClient(mc -> mc.player != null && !mc.player.isSpectator())) {
				throw new AssertionError("The fallen should be revived at dawn");
			}
			ctx.takeScreenshot("19-revived");
		}
	}
}
