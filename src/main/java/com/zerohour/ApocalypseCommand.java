package com.zerohour;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /apocalypse (operators) and /shelter (everyone). */
public final class ApocalypseCommand {
	private ApocalypseCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("apocalypse")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("start")
				.executes(ctx -> start(ctx, ApocalypseManager.data() != null ? ApocalypseManager.data().defaultMinutes : 60))
				.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 600))
					.executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))
			.then(Commands.literal("stop").executes(ctx -> {
				ApocalypseManager.stop();
				return ok(ctx, "A ceasefire holds. Everything is back to normal (burned land and craters stay).");
			}))
			.then(Commands.literal("pause").executes(ctx -> {
				ApocalypseManager.setPaused(true);
				return ok(ctx, "Countdown paused.");
			}))
			.then(Commands.literal("resume").executes(ctx -> {
				ApocalypseManager.setPaused(false);
				return ok(ctx, "Countdown resumed.");
			}))
			.then(Commands.literal("skip").executes(ctx -> {
				if (!running(ctx)) return 0;
				ApocalypseManager.skip();
				return ok(ctx, "Skipping to the next phase...");
			}))
			.then(Commands.literal("strike").executes(ctx -> {
				ApocalypseData data = ApocalypseManager.data();
				if (data == null) return 0;
				if (!data.stage.isCountdown()) {
					if (data.stage != Stage.IDLE) return fail(ctx, "The bomb has already fallen.");
					ApocalypseManager.start(1);
				}
				ApocalypseManager.setRemaining(10);
				return ok(ctx, "Ten seconds. Hope you're underground.");
			}))
			.then(Commands.literal("time")
				.then(Commands.argument("seconds", IntegerArgumentType.integer(0))
					.executes(ctx -> {
						if (!running(ctx)) return 0;
						ApocalypseManager.setRemaining(IntegerArgumentType.getInteger(ctx, "seconds"));
						return ok(ctx, "Strike rescheduled.");
					})))
			.then(Commands.literal("status").executes(ApocalypseCommand::status))
			.then(Commands.literal("shell").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ApocalypseData data = ApocalypseManager.data();
				Strikes.shell(player.level(), player, data == null || data.griefing);
				return ok(ctx, "Incoming shell.");
			}))
			.then(Commands.literal("jets").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Strikes.jets(player.level(), player);
				return ok(ctx, "Fighter jets inbound.");
			}))
			.then(Commands.literal("missile").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Strikes.icbm(player.level(), player);
				return ok(ctx, "A missile launches somewhere on the horizon.");
			}))
			.then(Commands.literal("flash").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Strikes.distantFlash(player.level());
				return ok(ctx, "A city over the horizon is gone.");
			}))
			.then(Commands.literal("supplydrop").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Strikes.supplyDrop(player.level(), player);
				return ok(ctx, "An airdrop is on its way down.");
			}))
			.then(Commands.literal("horde")
				.executes(ctx -> horde(ctx, 5))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
					.executes(ctx -> horde(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
			.then(Commands.literal("infect").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ApocalypseData data = ApocalypseManager.data();
				if (data == null) return 0;
				Infection.infect(player, data);
				return ok(ctx, "You've been bitten.");
			}))
			.then(Commands.literal("irradiate")
				.then(Commands.argument("rads", IntegerArgumentType.integer(0, 2000))
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						ApocalypseData data = ApocalypseManager.data();
						if (data == null) return 0;
						Radiation.addDose(player, data, IntegerArgumentType.getInteger(ctx, "rads"));
						return ok(ctx, "Absorbed dose is now " + Math.round(data.doseOf(player.getUUID())) + " rad.");
					})))
			.then(Commands.literal("cure").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ApocalypseData data = ApocalypseManager.data();
				if (data == null) return 0;
				data.clearBody(player.getUUID());
				return ok(ctx, "No radiation, no infection. Good as new.");
			}))
			.then(config("shielding", FloatArgumentType.floatArg(1, 500), (d, ctx) -> d.requiredShielding = FloatArgumentType.getFloat(ctx, "value")))
			.then(config("fallout", IntegerArgumentType.integer(10, 7200), (d, ctx) -> d.falloutSeconds = IntegerArgumentType.getInteger(ctx, "value")))
			.then(config("evacuation", IntegerArgumentType.integer(1, 120), (d, ctx) -> d.evacMinutes = IntegerArgumentType.getInteger(ctx, "value")))
			.then(config("infection", IntegerArgumentType.integer(1, 120), (d, ctx) -> d.infectionMinutes = IntegerArgumentType.getInteger(ctx, "value")))
			.then(config("distance", IntegerArgumentType.integer(32, 480), (d, ctx) -> d.strikeDistance = IntegerArgumentType.getInteger(ctx, "value")))
			.then(config("griefing", BoolArgumentType.bool(), (d, ctx) -> d.griefing = BoolArgumentType.getBool(ctx, "value")))
			.then(config("spectate", BoolArgumentType.bool(), (d, ctx) -> d.spectateOnDeath = BoolArgumentType.getBool(ctx, "value")))
			.then(config("revive", BoolArgumentType.bool(), (d, ctx) -> d.reviveOnEvacuation = BoolArgumentType.getBool(ctx, "value")))
			.then(config("shelling", BoolArgumentType.bool(), (d, ctx) -> d.shelling = BoolArgumentType.getBool(ctx, "value")))
			.then(config("infected", BoolArgumentType.bool(), (d, ctx) -> d.infected = BoolArgumentType.getBool(ctx, "value")))
			.then(config("autostart", BoolArgumentType.bool(), (d, ctx) -> d.autoStart = BoolArgumentType.getBool(ctx, "value")))
			.then(config("duration", IntegerArgumentType.integer(1, 600), (d, ctx) -> ApocalypseManager.setDuration(IntegerArgumentType.getInteger(ctx, "value")))));

		dispatcher.register(Commands.literal("shelter").executes(ApocalypseCommand::shelter));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> config(
		String key, com.mojang.brigadier.arguments.ArgumentType<?> type, BiConsumer<ApocalypseData, CommandContext<CommandSourceStack>> setter
	) {
		return Commands.literal("config").then(Commands.literal(key).then(Commands.argument("value", type).executes(ctx -> {
			ApocalypseData data = ApocalypseManager.data();
			if (data == null) return 0;
			setter.accept(data, ctx);
			data.setDirty();
			ApocalypseManager.syncAll();
			return ok(ctx, "Set " + key + ".");
		})));
	}

	private static boolean running(CommandContext<CommandSourceStack> ctx) {
		ApocalypseData data = ApocalypseManager.data();
		if (data == null || data.stage == Stage.IDLE) {
			fail(ctx, "Nothing is happening. Use /apocalypse start [minutes].");
			return false;
		}
		return true;
	}

	private static int start(CommandContext<CommandSourceStack> ctx, int minutes) {
		ApocalypseData data = ApocalypseManager.data();
		if (data == null) return 0;
		if (data.stage != Stage.IDLE && data.stage != Stage.EVACUATION) {
			return fail(ctx, "The end has already begun. Use /apocalypse stop first.");
		}
		ApocalypseManager.start(minutes);
		return ok(ctx, "Tensions are rising. The bomb falls in " + minutes + " minutes of play (players are never told).");
	}

	private static int horde(CommandContext<CommandSourceStack> ctx, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ApocalypseData data = ApocalypseManager.data();
		if (data == null) return 0;
		int spawned = Horde.spawnGroup(player.level(), data, player, count, data.stage.isPostStrike());
		return spawned > 0 ? ok(ctx, spawned + " infected are coming.") : fail(ctx, "No room for the infected around here.");
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ApocalypseData d = ApocalypseManager.data();
		if (d == null) return 0;
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.literal("Zero Hour — phase: " + d.stage.title + (d.paused ? " (paused)" : "")).withStyle(ChatFormatting.GOLD), false);
		if (d.stage.isCountdown()) {
			src.sendSuccess(() -> Component.literal("  Strike in " + Fx.formatTime(d.ticksRemaining()) + " (" + Math.round(d.progress() * 100) + "% of the build-up; hidden from players)"), false);
		}
		if (d.hasStrike) {
			src.sendSuccess(() -> Component.literal(String.format("  Ground zero X %d, Z %d · surface radiation %.2f rad/s", d.strikeX, d.strikeZ, Radiation.surfaceRate(d))), false);
		}
		if (d.hasLz) {
			src.sendSuccess(() -> Component.literal("  Landing zone X " + d.lzX + ", Z " + d.lzZ + (d.evacClosed ? " (closed)" : " (closes in " + Fx.formatTime(d.evacTicksLeft()) + ")")
				+ " · safe zone X " + d.safeX + ", Z " + d.safeZ + " · evacuated " + d.evacuated.size()), false);
		}
		src.sendSuccess(() -> Component.literal("  shielding=" + d.requiredShielding + " fallout=" + d.falloutSeconds + "s evacuation=" + d.evacMinutes + "min infection="
			+ d.infectionMinutes + "min distance=" + d.strikeDistance + " griefing=" + d.griefing + " spectate=" + d.spectateOnDeath + " revive=" + d.reviveOnEvacuation
			+ " shelling=" + d.shelling + " infected=" + d.infected + " autostart=" + d.autoStart + " duration=" + d.defaultMinutes + "min").withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.literal("  fallen=" + d.fallen.size() + " infected players=" + d.infection.size() + " scorched chunks=" + d.scorched.size()).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int shelter(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return fail(ctx, "Only players can check their shelter.");
		ApocalypseData data = ApocalypseManager.data();
		float required = data != null ? data.requiredShielding : 24f;
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		Shelter.Result r = Shelter.evaluate(player.level(), eye, required);
		CommandSourceStack src = ctx.getSource();
		ChatFormatting color = switch (r.status()) {
			case SAFE -> ChatFormatting.GREEN;
			case PARTIAL -> ChatFormatting.YELLOW;
			case EXPOSED -> ChatFormatting.RED;
		};
		float exposure = Radiation.exposure(player.level(), eye);
		src.sendSuccess(() -> Component.literal("━━ SHELTER REPORT ━━").withStyle(color, ChatFormatting.BOLD), false);
		src.sendSuccess(() -> Component.literal("Blast: " + r.status() + "   Rating: " + r.percent() + "%").withStyle(color), false);
		src.sendSuccess(() -> Component.literal(String.format("Overhead mass: %.1f / %.0f rock-meters", r.mass(), r.required())).withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.literal("Daylight reaching you: " + r.skyLight() + "/15").withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.literal(String.format("Fallout getting through: %.1f%%", exposure * 100f)).withStyle(ChatFormatting.GRAY), false);
		String advice = switch (r.status()) {
			case EXPOSED -> "Daylight reaches this spot, so the flash and the blast will too. Go deeper, or seal every opening with solid blocks.";
			case PARTIAL -> String.format("Sealed, but too thin. Add about %.0f more rock-meters overhead (stone = 1 per block, obsidian = 4, blast shielding = 8).",
				Math.max(0, r.required() - r.mass()));
			case SAFE -> "This shelter will hold. Stay here when the bomb falls, and stay here while the fallout comes down.";
		};
		src.sendSuccess(() -> Component.literal(advice).withStyle(ChatFormatting.ITALIC), false);
		return 1;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GOLD), true);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendFailure(Component.literal(message));
		return 0;
	}
}
