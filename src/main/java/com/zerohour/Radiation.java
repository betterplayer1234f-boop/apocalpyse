package com.zerohour;

import com.zerohour.content.ModItems;
import com.zerohour.network.StatusPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Fallout. After the bomb, open ground is radioactive: the dose you soak up depends on how much rock is
 * between you and the sky, what you are wearing, and whether you took your iodine. 1000 rads is lethal.
 */
public final class Radiation {
	public static final float LETHAL = 1000f;
	/** Surface rate (rad/s) at the height of the fallout. */
	public static final float FALLOUT_PEAK = 10f;
	/** Surface rate (rad/s) when the evacuation begins; it keeps halving every six minutes after that. */
	public static final float EVACUATION_START = 3f;
	private static final int HOT_RADIUS = 100;

	/** The last sickness level each player was told about. */
	private static final Map<UUID, Integer> warned = new HashMap<>();

	private Radiation() {
	}

	public static void reset() {
		warned.clear();
	}

	private static double sq(double v) {
		return v * v;
	}

	/** Radiation on open ground, in rad/s, before any shielding. */
	public static float surfaceRate(ApocalypseData d) {
		return switch (d.stage) {
			case DETONATION -> {
				// The fallout starts coming down a few seconds after the blast wave.
				int t = d.stageTicks - Stage.SHOCK_TICK;
				yield t <= 0 ? 0f : FALLOUT_PEAK * Mth.clamp(t / 300f, 0f, 1f);
			}
			case FALLOUT -> EVACUATION_START + (FALLOUT_PEAK - EVACUATION_START) * (float) Math.pow(1f - d.stageFraction(), 1.6);
			case EVACUATION -> {
				float r = EVACUATION_START * (float) Math.pow(0.5, d.stageTicks / 7200.0);
				yield r < 0.03f ? 0f : r;
			}
			default -> 0f;
		};
	}

	/** Ground zero stays hot long after the rest of the fallout has decayed. */
	public static float hotZone(ApocalypseData d, double x, double z) {
		if (!d.hasStrike || !d.stage.isPostStrike() || d.postTicks <= 0) return 0f;
		double dist = Math.sqrt(sq(x - d.strikeX) + sq(z - d.strikeZ));
		if (dist >= HOT_RADIUS) return 0f;
		float k = 1f - (float) (dist / HOT_RADIUS);
		return 30f * k * k * (float) Math.pow(0.5, d.postTicks / 36000.0);
	}

	/**
	 * How much of the outside radiation reaches this spot: all of it under open sky, half per 1.5 rock-meters overhead,
	 * and some always leaks in through an open door or window.
	 */
	public static float exposure(Level level, BlockPos eye) {
		if (level.dimension() != Level.OVERWORLD) return 0f;
		if (level.canSeeSky(eye)) return 1f;
		float mass = Shelter.overheadMass(level, eye, 16f);
		float roof = (float) Math.pow(0.5, mass / 1.5);
		float leak = level.getBrightness(LightLayer.SKY, eye) / 15f * 0.4f;
		return Mth.clamp(Math.max(roof, leak), 0f, 1f);
	}

	/** Fraction of incoming radiation stopped by protective gear (a full hazmat outfit stops 80%). */
	public static float gearProtection(LivingEntity e) {
		float p = 0f;
		if (e.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GAS_MASK)) p += 0.25f;
		if (e.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.HAZMAT_SUIT)) p += 0.30f;
		if (e.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.HAZMAT_TROUSERS)) p += 0.15f;
		if (e.getItemBySlot(EquipmentSlot.FEET).is(ModItems.HAZMAT_BOOTS)) p += 0.10f;
		return p;
	}

	/** The dose rate this player is absorbing right now, in rad/s. */
	public static float rateFor(ServerPlayer player, ApocalypseData d) {
		if (ApocalypseManager.isObserver(player) || !player.isAlive()) return 0f;
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD || d.inSafeZone(player.getX(), player.getZ())) return 0f;
		float ambient = surfaceRate(d) + hotZone(d, player.getX(), player.getZ());
		float env = ambient > 0f ? ambient * exposure(level, BlockPos.containing(player.getEyePosition())) : 0f;
		env += Horde.radiationNear(level, player);
		float rate = env * (1f - gearProtection(player));
		if (d.iodine.getOrDefault(player.getUUID(), 0) > 0) rate *= 0.5f;
		return rate;
	}

	/** Once a second: absorb, recover, feel it, and tell the client. */
	static void tick(MinecraftServer server, ApocalypseData d, boolean accumulate) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			float rate = rateFor(player, d);
			float dose = d.doseOf(id);
			if (accumulate && player.isAlive() && !ApocalypseManager.isObserver(player)) {
				if (rate > 0.001f) {
					dose += rate;
				} else if (dose > 0f) {
					// Out of the fallout, the body slowly mends.
					dose = Math.max(0f, dose - 0.1f);
				}
				Integer io = d.iodine.get(id);
				if (io != null) {
					if (io <= 20) d.iodine.remove(id);
					else d.iodine.put(id, io - 20);
				}
				if (dose > 0f) d.dose.put(id, dose);
				else d.dose.remove(id);
				symptoms(player, dose);
			}
			Fx.send(player, new StatusPayload(dose, rate, d.infectionProgress(id), d.iodine.getOrDefault(id, 0), d.evacuated.contains(id)));
		}
	}

	public static void addDose(ServerPlayer player, ApocalypseData d, float rads) {
		float dose = Math.max(0f, d.doseOf(player.getUUID()) + rads);
		if (dose > 0f) d.dose.put(player.getUUID(), dose);
		else d.dose.remove(player.getUUID());
		d.setDirty();
	}

	public static int sicknessLevel(float dose) {
		if (dose >= 800f) return 4;
		if (dose >= 600f) return 3;
		if (dose >= 400f) return 2;
		if (dose >= 200f) return 1;
		return 0;
	}

	public static String sicknessLabel(float dose) {
		return switch (sicknessLevel(dose)) {
			case 1 -> "mild radiation sickness";
			case 2 -> "radiation sickness";
			case 3 -> "severe radiation sickness";
			case 4 -> "lethal dose";
			default -> "";
		};
	}

	/** How dangerous a dose rate is, in words. */
	public static String rateLabel(float rate) {
		if (rate < 0.05f) return "background";
		if (rate < 0.5f) return "elevated";
		if (rate < 2f) return "dangerous";
		if (rate < 6f) return "very dangerous";
		return "LETHAL";
	}

	/** Needle position 0..1 on the Geiger counter's logarithmic dial. */
	public static float needle(float rate) {
		return Mth.clamp((float) (Math.log10(1.0 + rate * 10.0) / Math.log10(401.0)), 0f, 1f);
	}

	private static void effect(ServerPlayer player, Holder<MobEffect> effect, int ticks, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
	}

	private static void symptoms(ServerPlayer player, float dose) {
		if (dose >= LETHAL) {
			ServerLevel level = player.level();
			player.hurtServer(level, level.damageSources().source(ZeroHour.RADIATION), Float.MAX_VALUE);
			return;
		}
		int sickness = sicknessLevel(dose);
		int last = warned.getOrDefault(player.getUUID(), 0);
		if (sickness > last) {
			String message = switch (sickness) {
				case 1 -> "You feel sick to your stomach. That's the radiation.";
				case 2 -> "Your skin is red and blistering. Radiation sickness.";
				case 3 -> "Your hair is coming out in clumps. Get out of the fallout.";
				default -> "You are dying of radiation poisoning. Anti-rad serum. Now.";
			};
			player.displayClientMessage(Component.literal("☢ " + message).withStyle(sickness >= 3 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
		}
		warned.put(player.getUUID(), sickness);

		RandomSource r = ApocalypseManager.RANDOM;
		if (sickness >= 1 && r.nextFloat() < 0.08f) effect(player, MobEffects.HUNGER, 100, 0);
		if (sickness >= 2) effect(player, MobEffects.WEAKNESS, 60, sickness >= 3 ? 1 : 0);
		if (sickness >= 3 && r.nextFloat() < 0.15f) effect(player, MobEffects.HUNGER, 100, 1);
		if (sickness >= 4) {
			if (r.nextFloat() < 0.12f) effect(player, MobEffects.WITHER, 50, 0);
			if (r.nextFloat() < 0.04f) effect(player, MobEffects.DARKNESS, 80, 0);
		}
	}

	/** What a Geiger counter shows when you press the button. */
	public static Component reading(ServerPlayer player, ApocalypseData d) {
		float rate = rateFor(player, d);
		float dose = d.doseOf(player.getUUID());
		ChatFormatting color = rate < 0.05f ? ChatFormatting.GREEN : rate < 0.5f ? ChatFormatting.YELLOW : rate < 2f ? ChatFormatting.GOLD
			: rate < 6f ? ChatFormatting.RED : ChatFormatting.DARK_RED;
		String sickness = sicknessLabel(dose);
		String text = String.format("☢ %.2f rad/s — %s · absorbed %d rad%s", rate, rateLabel(rate), Math.round(dose), sickness.isEmpty() ? "" : " (" + sickness + ")");
		return Component.literal(text).withStyle(color);
	}
}
