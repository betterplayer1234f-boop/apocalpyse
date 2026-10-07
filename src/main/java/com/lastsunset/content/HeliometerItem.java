package com.lastsunset.content;

import com.lastsunset.ApocalypseData;
import com.lastsunset.ApocalypseManager;
import com.lastsunset.Stage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A brass instrument that reads the sun's output. There is no countdown in the world:
 * this needle creeping toward red is the closest thing to one.
 */
public class HeliometerItem extends Item {
	public HeliometerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.displayClientMessage(reading(ApocalypseManager.data(), level.getRandom().nextFloat()), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HELIOMETER_CLICK, SoundSource.PLAYERS, 0.7F, 1.0F);
		}
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
		return InteractionResult.SUCCESS;
	}

	/** Solar flux in percent of normal, as the instrument would show it right now. */
	public static int flux(ApocalypseData data, float jitter) {
		if (data == null || data.stage == Stage.IDLE) return 100;
		if (data.stage == Stage.WASTELAND) return 38;
		float d = data.progress();
		float value = 100 + 1900 * (float) Math.pow(d, 1.7);
		return Math.round(value * (0.97F + 0.06F * jitter));
	}

	public static Component reading(ApocalypseData data, float jitter) {
		Stage stage = data == null ? Stage.IDLE : data.stage;
		if (stage == Stage.IMPACT) {
			return Component.literal("☀ Solar flux: ██████ — the needle is off the scale").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
		}
		if (stage == Stage.AFTERMATH) {
			float f = data.stageFraction();
			int temp = Math.round(20 + 1180 * (float) Math.pow(1 - f, 1.5));
			return Component.literal(String.format("♨ Surface temperature: %,d°C — falling", temp)).withStyle(f < 0.7F ? ChatFormatting.RED : ChatFormatting.GOLD);
		}
		int flux = flux(data, jitter);
		String verdict;
		ChatFormatting color;
		if (stage == Stage.WASTELAND) {
			verdict = "the sun is weakened, but stable";
			color = ChatFormatting.GRAY;
		} else if (flux < 150) {
			verdict = "within normal limits";
			color = ChatFormatting.GREEN;
		} else if (flux < 350) {
			verdict = "elevated, and climbing";
			color = ChatFormatting.YELLOW;
		} else if (flux < 800) {
			verdict = "unstable — this is not normal";
			color = ChatFormatting.GOLD;
		} else if (flux < 1500) {
			verdict = "critical — get underground";
			color = ChatFormatting.RED;
		} else {
			verdict = "CRITICAL — the sun could go at any moment";
			color = ChatFormatting.DARK_RED;
		}
		return Component.literal("☀ Solar flux: " + flux + "% — " + verdict).withStyle(color);
	}

	/** Needle position 0..1 for the item model (client mirrors this from synced progress). */
	public static float needle(Stage stage, float progress) {
		return switch (stage) {
			case IDLE -> 0.04F;
			case IMPACT, AFTERMATH -> 1.0F;
			case WASTELAND -> 0.02F;
			default -> Mth.clamp(0.06F + 0.94F * (float) Math.pow(progress, 1.4), 0F, 1F);
		};
	}
}
