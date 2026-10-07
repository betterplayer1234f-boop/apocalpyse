package com.zerohour;

import com.zerohour.content.ModItems;
import com.zerohour.network.CuePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * The infection. Any zombie's bite can pass it on; it runs its course over a few minutes
 * (fever, delirium, turning) and then you get back up as one of them. An antiviral stops it.
 */
public final class Infection {
	/** Chance that a bite which draws blood infects you. A hazmat suit's thick rubber helps. */
	private static final float BITE_CHANCE = 0.3f;
	/** The last phase each player was told about. */
	private static final Map<UUID, Integer> warned = new HashMap<>();

	private Infection() {
	}

	/** Everything that counts as "infected" in this world: the walking dead, not the Nether's piglins. */
	public static boolean isInfectedKind(Entity e) {
		EntityType<?> type = e.getType();
		return type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.DROWNED;
	}

	/** 0 bitten, 1 fever, 2 delirium, 3 turning. */
	public static int phase(float progress) {
		if (progress < 0.25f) return 0;
		if (progress < 0.5f) return 1;
		if (progress < 0.75f) return 2;
		return 3;
	}

	public static String phaseLabel(float progress) {
		return switch (phase(progress)) {
			case 0 -> "bitten";
			case 1 -> "fever";
			case 2 -> "delirium";
			default -> "turning";
		};
	}

	static void onDamage(LivingEntity entity, DamageSource source, ApocalypseData d) {
		if (!d.stage.isActive() || !(entity instanceof ServerPlayer player) || ApocalypseManager.isObserver(player)) return;
		Entity attacker = source.getEntity();
		if (attacker == null || !isInfectedKind(attacker) || d.isInfected(player.getUUID())) return;
		float chance = BITE_CHANCE * (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.HAZMAT_SUIT) ? 0.4f : 1f);
		if (ApocalypseManager.RANDOM.nextFloat() < chance) infect(player, d);
	}

	public static void infect(ServerPlayer player, ApocalypseData d) {
		if (d.isInfected(player.getUUID())) return;
		d.infection.put(player.getUUID(), 0);
		d.setDirty();
		warned.put(player.getUUID(), 0);
		player.displayClientMessage(Component.literal("☣ You've been bitten. The wound is burning.").withStyle(ChatFormatting.RED), true);
		player.sendSystemMessage(Component.literal("☣ You've been bitten. Find an antiviral before the infection takes you.")
			.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
		Fx.send(player, new CuePayload(CuePayload.BITTEN, 1f, 40));
		Fx.sound(player.level(), player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_INFECT, 0.8f, 0.8f);
	}

	/** @return true if the player was infected and is now cured. */
	public static boolean cure(ServerPlayer player, ApocalypseData d) {
		if (d.infection.remove(player.getUUID()) == null) return false;
		warned.remove(player.getUUID());
		d.setDirty();
		return true;
	}

	private static void effect(ServerPlayer player, Holder<MobEffect> effect, int ticks, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
	}

	/** Once a second: the infection progresses. */
	static void tick(MinecraftServer server, ApocalypseData d) {
		RandomSource r = ApocalypseManager.RANDOM;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Integer ticks = d.infection.get(id);
			if (ticks == null || !player.isAlive() || ApocalypseManager.isObserver(player)) continue;
			d.infection.put(id, ticks + 20);
			float progress = d.infectionProgress(id);
			if (progress >= 1f) {
				turn(player);
				continue;
			}
			int phase = phase(progress);
			if (phase > warned.getOrDefault(id, 0)) {
				String message = switch (phase) {
					case 1 -> "You're burning up with fever.";
					case 2 -> "Your veins are turning black. You can't think straight.";
					default -> "You're turning. You can feel it. An antiviral, now, or it's over.";
				};
				player.displayClientMessage(Component.literal("☣ " + message).withStyle(phase >= 3 ? ChatFormatting.DARK_RED : ChatFormatting.RED), true);
				warned.put(id, phase);
			}
			switch (phase) {
				case 1 -> {
					if (r.nextFloat() < 0.2f) effect(player, MobEffects.HUNGER, 100, 0);
				}
				case 2 -> {
					effect(player, MobEffects.WEAKNESS, 60, 0);
					if (r.nextFloat() < 0.2f) effect(player, MobEffects.HUNGER, 100, 0);
					if (r.nextFloat() < 0.03f) effect(player, MobEffects.BLINDNESS, 40, 0);
				}
				case 3 -> {
					effect(player, MobEffects.WEAKNESS, 60, 1);
					if (r.nextFloat() < 0.15f) effect(player, MobEffects.DARKNESS, 60, 0);
					// Something that isn't quite your voice.
					if (r.nextFloat() < 0.1f) Fx.sound(player.level(), player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_AMBIENT, 0.6f, 1.3f);
				}
				default -> {
				}
			}
		}
	}

	private static void turn(ServerPlayer player) {
		ServerLevel level = player.level();
		player.hurtServer(level, level.damageSources().source(ZeroHour.INFECTION), Float.MAX_VALUE);
	}

	/** Called before the body state is cleared: whoever dies infected, or at the hands of the infected, gets back up. */
	static void onPlayerDeath(ServerPlayer player, DamageSource source, ApocalypseData d) {
		if (!d.stage.isActive()) return;
		Entity killer = source.getEntity();
		boolean infected = d.isInfected(player.getUUID());
		warned.remove(player.getUUID());
		if (!infected && (killer == null || !isInfectedKind(killer))) return;
		Horde.spawnTurned(player);
		player.level().getServer().getPlayerList().broadcastSystemMessage(
			Component.literal("☣ " + player.getName().getString() + " has turned.").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), false);
	}
}
