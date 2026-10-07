package com.zerohour.test;

import com.zerohour.ApocalypseData;
import com.zerohour.ApocalypseManager;
import com.zerohour.Stage;
import com.zerohour.content.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.gamerules.GameRules;
import org.lwjgl.glfw.GLFW;

/**
 * Plays through the whole apocalypse in a real client and screenshots it:
 * the automatic start and survival kit, the war and the infected, a sealed bunker (survive the blast),
 * radiation on the surface, the evacuation, and standing in the open (vaporized, spectate, dug out again).
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

			// It starts by itself, and everyone gets a civil-defence kit.
			Stage stage = server.computeOnServer(s -> ApocalypseManager.data().stage);
			if (stage != Stage.TENSIONS) throw new AssertionError("The war should begin as soon as the world starts, but stage was " + stage);
			if (!ctx.computeOnClient(mc -> mc.player.getInventory().contains(st -> st.is(ModItems.EMERGENCY_RADIO))
				&& mc.player.getInventory().contains(st -> st.is(ModItems.GEIGER_COUNTER))
				&& mc.player.getInventory().contains(st -> st.is(ModItems.IODINE_TABLETS)))) {
				throw new AssertionError("Players should receive a radio, a Geiger counter and iodine on first join");
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
			ctx.takeScreenshot("00-tensions-geiger");

			// The radio, placed on the platform in front of us.
			server.runCommand("execute as @a at @s run setblock ~-1 ~ ~ zerohour:emergency_radio[facing=east]");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 50");
			ctx.waitTicks(20);
			ctx.takeScreenshot("01-radio-block");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -12");

			// The outbreak.
			server.runCommand("apocalypse time 150");
			ctx.waitTicks(140);
			ctx.takeScreenshot("02-outbreak-broadcast");
			server.runCommand("execute as @a run apocalypse jets");
			ctx.waitTicks(45);
			ctx.takeScreenshot("03-jets");

			// Signal flare.
			server.runCommand("item replace entity @a hotbar.7 with zerohour:flare_gun");
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[7]);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -55");
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(25);
			ctx.takeScreenshot("04-flare-rising");
			ctx.waitTicks(50);
			ctx.takeScreenshot("05-flare-hanging");

			// The infected, below the platform.
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 35");
			server.runCommand("execute as @a run apocalypse horde 6");
			ctx.waitTicks(60);
			ctx.takeScreenshot("06-infected");
			server.runCommand("kill @e[type=minecraft:husk]");
			server.runCommand("kill @e[type=minecraft:zombie]");
			server.runCommand("kill @e[type=minecraft:zombie_villager]");

			// Bitten, then cured with an antiviral.
			server.runCommand("execute as @a run apocalypse infect");
			ctx.waitTicks(40);
			if (server.computeOnServer(s -> ApocalypseManager.data().infection.isEmpty())) throw new AssertionError("The player should be infected");
			ctx.takeScreenshot("07-bitten");
			server.runCommand("item replace entity @a hotbar.8 with zerohour:antiviral");
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[8]);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(40);
			if (server.computeOnServer(s -> !ApocalypseManager.data().infection.isEmpty())) throw new AssertionError("The antiviral should cure the infection");

			// A shell and an airdrop.
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -12");
			server.runCommand("execute as @a run apocalypse supplydrop");
			ctx.waitTicks(60);
			ctx.takeScreenshot("08-airdrop-radio");
			server.runCommand("execute as @a run apocalypse shell");
			ctx.waitTicks(70);
			ctx.takeScreenshot("09-shell");

			// Launch detected: missiles climbing on the horizon, a city going up somewhere else.
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			server.runCommand("apocalypse time 70");
			ctx.waitTicks(100);
			server.runCommand("execute as @a run apocalypse missile");
			server.runCommand("execute as @a run apocalypse missile");
			ctx.waitTicks(60);
			ctx.takeScreenshot("10-launch-missiles");
			server.runCommand("execute as @a run apocalypse flash");
			ctx.waitTicks(4);
			ctx.takeScreenshot("11-distant-flash");

			server.runCommand("apocalypse time 30");
			ctx.waitTicks(60);
			// Seal ourselves in: 9 layers of obsidian overhead (36 rock-meters), lit by a lantern.
			server.runCommand("execute as @a at @s run fill ~-3 ~-1 ~-3 ~3 ~12 ~3 obsidian");
			server.runCommand("execute as @a at @s run fill ~-2 ~ ~-2 ~2 ~3 ~2 air");
			server.runCommand("execute as @a at @s run setblock ~2 ~ ~2 lantern");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
			ctx.waitTicks(80);
			ctx.takeScreenshot("12-in-bunker");
			ctx.getInput().holdKey(options -> options.keyPlayerList);
			ctx.waitTicks(2);
			ctx.takeScreenshot("13-tab-survivors");
			ctx.getInput().releaseKey(options -> options.keyPlayerList);
			// The strike comes 600 ticks after "time 30"; the flash 60 ticks later, the blast wave after that.
			ctx.waitTicks(558);
			ctx.takeScreenshot("14-detonation-sheltered");
			ctx.waitTicks(520);
			stage = server.computeOnServer(s -> ApocalypseManager.data().stage);
			if (stage != Stage.FALLOUT) throw new AssertionError("The fallout should have begun, but stage was " + stage);
			if (ctx.computeOnClient(mc -> mc.player == null || mc.player.isSpectator())) throw new AssertionError("A deep enough bunker should survive the blast");
			ctx.takeScreenshot("15-fallout-bunker");

			// Step outside into the fallout: the Geiger counter screams and the dose climbs.
			server.runCommand("execute as @a at @s run tp @s ~ ~14 ~ 90 25");
			ctx.waitTicks(100);
			float dose = server.computeOnServer(s -> {
				ApocalypseData d = ApocalypseManager.data();
				return d.doseOf(s.getPlayerList().getPlayers().get(0).getUUID());
			});
			if (dose <= 0f) throw new AssertionError("Standing in the fallout should irradiate you");
			ctx.takeScreenshot("16-fallout-surface");
			server.runCommand("item replace entity @a armor.head with zerohour:gas_mask");
			server.runCommand("item replace entity @a armor.chest with zerohour:hazmat_suit");
			server.runCommand("item replace entity @a armor.legs with zerohour:hazmat_trousers");
			server.runCommand("item replace entity @a armor.feet with zerohour:hazmat_boots");
			server.runCommand("execute as @a run apocalypse cure");

			// The evacuation: run to the landing zone.
			server.runCommand("apocalypse skip");
			ctx.waitTicks(120);
			ctx.takeScreenshot("17-evacuation");
			int[] lz = server.computeOnServer(s -> {
				ApocalypseData d = ApocalypseManager.data();
				if (!d.hasLz) throw new AssertionError("The evacuation should open a landing zone");
				return new int[]{d.lzX, d.lzZ};
			});
			server.runCommand("spreadplayers " + lz[0] + " " + lz[1] + " 0 1 false @a");
			ctx.waitTicks(40);
			ctx.takeScreenshot("18-landing-zone");
			ctx.waitTicks(200);
			boolean evacuated = server.computeOnServer(s -> ApocalypseManager.data().evacuated.contains(s.getPlayerList().getPlayers().get(0).getUUID()));
			if (!evacuated) throw new AssertionError("Reaching the landing zone should evacuate the player");
			ctx.takeScreenshot("19-safe-zone");

			// Second run: stand in the open when the bomb goes off.
			server.runCommand("apocalypse stop");
			server.runCommand("effect clear @a");
			server.runCommand("clear @a zerohour:gas_mask");
			server.runCommand("clear @a zerohour:hazmat_suit");
			server.runCommand("execute as @a at @s run fill ~-1 ~-1 ~-1 ~1 ~-1 ~1 stone");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -10");
			ctx.waitTicks(40);
			server.runCommand("apocalypse strike");
			ctx.waitTicks(250);
			ctx.takeScreenshot("20-exposed-warhead");
			ctx.waitTicks(40);
			ctx.takeScreenshot("21-exposed-flash");
			ctx.waitTicks(120);
			ctx.takeScreenshot("22-death-screen");

			// The fallen watch as spectators until the evacuation, then are dug out.
			ctx.clickScreenButton("deathScreen.respawn");
			ctx.waitTicks(60);
			if (!ctx.computeOnClient(mc -> mc.player != null && mc.player.isSpectator())) {
				throw new AssertionError("A player vaporized by the blast should respawn as a spectator");
			}
			ctx.takeScreenshot("23-fallen-spectator");
			server.runCommand("apocalypse skip");
			ctx.waitTicks(40);
			server.runCommand("apocalypse skip");
			ctx.waitTicks(120);
			if (!ctx.computeOnClient(mc -> mc.player != null && !mc.player.isSpectator())) {
				throw new AssertionError("The fallen should be revived when the evacuation begins");
			}
			ctx.takeScreenshot("24-revived");
		}
	}
}
