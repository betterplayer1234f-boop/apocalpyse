package com.zerohour.content;

import com.zerohour.ApocalypseData;
import com.zerohour.ApocalypseManager;
import com.zerohour.Fx;
import com.zerohour.Infection;
import com.zerohour.Radiation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Iodine tablets, anti-rad serum and antiviral syringes: right-click to take one. */
public class MedicineItem extends Item {
	public enum Kind {
		IODINE, ANTI_RAD, ANTIVIRAL
	}

	/** Five minutes of iodine. */
	public static final int IODINE_TICKS = 6000;
	public static final float SERUM_RADS = 300f;

	private final Kind kind;

	public MedicineItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			ApocalypseData data = ApocalypseManager.data();
			if (data != null && apply(serverPlayer, data) && !player.isCreative()) stack.shrink(1);
		}
		player.getCooldowns().addCooldown(stack, 30);
		return InteractionResult.SUCCESS;
	}

	/** @return whether the dose was used up. */
	private boolean apply(ServerPlayer player, ApocalypseData data) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		switch (kind) {
			case IODINE -> {
				data.iodine.put(player.getUUID(), IODINE_TICKS);
				data.setDirty();
				Fx.sound(player.level(), x, y, z, SoundEvents.GENERIC_EAT, 0.8f, 1.2f);
				message(player, "You swallow an iodine tablet. For the next five minutes your body takes in half the radiation.", ChatFormatting.YELLOW);
				return true;
			}
			case ANTI_RAD -> {
				float before = data.doseOf(player.getUUID());
				Radiation.addDose(player, data, -SERUM_RADS);
				Fx.sound(player.level(), x, y, z, SoundEvents.HONEY_DRINK, 0.8f, 0.8f);
				message(player, before <= 0f
					? "The serum burns going in. You weren't irradiated, but it can't hurt."
					: "The serum burns going in. Absorbed dose: " + Math.round(before) + " → " + Math.round(data.doseOf(player.getUUID())) + " rad.", ChatFormatting.AQUA);
				return true;
			}
			case ANTIVIRAL -> {
				if (!Infection.cure(player, data)) {
					message(player, "You're not infected. Save it for when you are.", ChatFormatting.GRAY);
					return false;
				}
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
				Fx.sound(player.level(), x, y, z, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.7f, 1.4f);
				message(player, "☣ The fever breaks. The infection is gone.", ChatFormatting.GREEN);
				return true;
			}
		}
		return false;
	}

	private static void message(ServerPlayer player, String text, ChatFormatting color) {
		player.displayClientMessage(Component.literal(text).withStyle(color), true);
	}
}
