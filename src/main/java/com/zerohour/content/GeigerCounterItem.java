package com.zerohour.content;

import com.zerohour.ApocalypseData;
import com.zerohour.ApocalypseManager;
import com.zerohour.Radiation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A yellow civil-defence Geiger counter. In your hand it clicks with the radiation around you (the client
 * drives the clicks and the needle from the dose rate the server sends); right-click for an exact reading.
 */
public class GeigerCounterItem extends Item {
	public GeigerCounterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			ApocalypseData data = ApocalypseManager.data();
			Component reading = data == null
				? Component.literal("☢ 0.00 rad/s — background").withStyle(ChatFormatting.GREEN)
				: Radiation.reading(serverPlayer, data);
			serverPlayer.displayClientMessage(reading, true);
			for (int i = 0; i < 3; i++) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GEIGER_CLICK, SoundSource.PLAYERS, 0.6F, 0.9F + i * 0.1F);
			}
		}
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
		return InteractionResult.SUCCESS;
	}
}
