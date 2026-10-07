package com.lastsunset;

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
				return ok(ctx, "The sun calms down. Everything is back to normal (scorched land stays scorched).");
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
			.then(Commands.literal("impact").executes(ctx -> {
				ApocalypseData data = ApocalypseManager.data();
				if (data == null) return 0;
				if (!data.stage.isCountdown()) {
					if (data.stage != Stage.IDLE) return fail(ctx, "The flare has already hit.");
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
						return ok(ctx, "Impact rescheduled.");
					})))
			.then(Commands.literal("status").executes(ApocalypseCommand::status))
			.then(Commands.literal("meteor").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ApocalypseData data = ApocalypseManager.data();
				Meteors.launch(player.level(), player, 1.3f, data == null || data.griefing);
				return ok(ctx, "Something is falling out of the sky.");
			}))
			.then(Commands.literal("supplydrop").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Meteors.supplyDrop(player.level(), player);
				return ok(ctx, "A supply crate is on its way down.");
			}))
			.then(config("shielding", FloatArgumentType.floatArg(1, 500), (d, ctx) -> d.requiredShielding = FloatArgumentType.getFloat(ctx, "value")))
			.then(config("aftermath", IntegerArgumentType.integer(10, 7200), (d, ctx) -> d.aftermathSeconds = IntegerArgumentType.getInteger(ctx, "value")))
			.then(config("griefing", BoolArgumentType.bool(), (d, ctx) -> d.griefing = BoolArgumentType.getBool(ctx, "value")))
			.then(config("spectate", BoolArgumentType.bool(), (d, ctx) -> d.spectateOnDeath = BoolArgumentType.getBool(ctx, "value")))
			.then(config("revive", BoolArgumentType.bool(), (d, ctx) -> d.reviveAtDawn = BoolArgumentType.getBool(ctx, "value")))
			.then(config("meteors", BoolArgumentType.bool(), (d, ctx) -> d.meteors = BoolArgumentType.getBool(ctx, "value")))
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
		if (data.stage != Stage.IDLE && data.stage != Stage.WASTELAND) {
			return fail(ctx, "The end has already begun. Use /apocalypse stop first.");
		}
		ApocalypseManager.start(minutes);
		return ok(ctx, "The sun has begun to change. The flare comes in " + minutes + " minutes of play (players are never told).");
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ApocalypseData d = ApocalypseManager.data();
		if (d == null) return 0;
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.literal("Last Sunset — phase: " + d.stage.title + (d.paused ? " (paused)" : "")).withStyle(ChatFormatting.GOLD), false);
		if (d.stage.isCountdown()) {
			src.sendSuccess(() -> Component.literal("  Flare in " + Fx.formatTime(d.ticksRemaining()) + " (" + Math.round(d.progress() * 100) + "% of the build-up; hidden from players)"), false);
		}
		src.sendSuccess(() -> Component.literal("  shielding=" + d.requiredShielding + " aftermath=" + d.aftermathSeconds + "s griefing=" + d.griefing
			+ " spectate=" + d.spectateOnDeath + " revive=" + d.reviveAtDawn + " meteors=" + d.meteors
			+ " autostart=" + d.autoStart + " duration=" + d.defaultMinutes + "min").withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.literal("  fallen=" + d.fallen.size() + " scorched chunks=" + d.scorched.size()).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int shelter(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return fail(ctx, "Only players can check their shelter.");
		ApocalypseData data = ApocalypseManager.data();
		float required = data != null ? data.requiredShielding : 30f;
		Shelter.Result r = Shelter.evaluate(player.level(), BlockPos.containing(player.getEyePosition()), required);
		CommandSourceStack src = ctx.getSource();
		ChatFormatting color = switch (r.status()) {
			case SAFE -> ChatFormatting.GREEN;
			case PARTIAL -> ChatFormatting.YELLOW;
			case EXPOSED -> ChatFormatting.RED;
		};
		src.sendSuccess(() -> Component.literal("━━ SHELTER REPORT ━━").withStyle(color, ChatFormatting.BOLD), false);
		src.sendSuccess(() -> Component.literal("Status: " + r.status() + "   Rating: " + r.percent() + "%").withStyle(color), false);
		src.sendSuccess(() -> Component.literal(String.format("Overhead mass: %.1f / %.0f rock-meters", r.mass(), r.required())).withStyle(ChatFormatting.GRAY), false);
		src.sendSuccess(() -> Component.literal("Sunlight reaching you: " + r.skyLight() + "/15").withStyle(ChatFormatting.GRAY), false);
		String advice = switch (r.status()) {
			case EXPOSED -> "Sunlight reaches this spot, so the flare will too. Go deeper, or seal every opening with solid blocks.";
			case PARTIAL -> String.format("Sealed, but too thin. Add about %.0f more rock-meters overhead (stone = 1 per block, obsidian = 4).",
				Math.max(0, r.required() - r.mass()));
			case SAFE -> "This shelter will hold. Stay here when the flare hits.";
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
