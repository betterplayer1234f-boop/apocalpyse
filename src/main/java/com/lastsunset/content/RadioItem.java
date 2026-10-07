package com.lastsunset.content;

import com.lastsunset.Broadcasts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The radio places like a block; used in the air, it replays the last broadcast. */
public class RadioItem extends BlockItem {
	public RadioItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RADIO_STATIC, SoundSource.PLAYERS, 0.6F, 1.0F);
			Broadcasts.replay(serverPlayer);
		}
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
		return InteractionResult.SUCCESS;
	}
}
