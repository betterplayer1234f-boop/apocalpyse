package com.zerohour.content;

import com.zerohour.ApocalypseManager;
import com.zerohour.Fx;
import com.zerohour.ServerTask;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Fires a red signal flare that climbs high, bursts, and drifts down slowly: "we're here". */
public class FlareGunItem extends Item {
	public FlareGunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			Vec3 look = player.getLookAngle();
			Vec3 velocity = new Vec3(look.x * 0.4, 1.7, look.z * 0.4);
			ApocalypseManager.addTask(new SignalFlare(serverLevel, player.getEyePosition(), velocity));
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FLARE_GUN, SoundSource.PLAYERS, 3.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			stack.hurtAndBreak(1, player, hand);
		}
		player.getCooldowns().addCooldown(stack, 60);
		return InteractionResult.SUCCESS;
	}

	/** A flare in flight. Also fired by the military from the evacuation landing zone. */
	public static final class SignalFlare implements ServerTask {
		private static final DustParticleOptions RED = new DustParticleOptions(0xFF2A1A, 3.0F);
		private static final DustParticleOptions GLOW = new DustParticleOptions(0xFFB070, 1.5F);
		private final ServerLevel level;
		private Vec3 pos;
		private Vec3 velocity;
		private int age;
		private int hanging = -1;

		public SignalFlare(ServerLevel level, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.pos = pos;
			this.velocity = velocity;
		}

		@Override
		public boolean tick() {
			age++;
			if (hanging < 0) {
				pos = pos.add(velocity);
				velocity = velocity.add(0, -0.045, 0).scale(0.985);
				Fx.particles(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.05, 0.01);
				Fx.particles(level, RED, pos.x, pos.y, pos.z, 2, 0.1, 0.0);
				Fx.particles(level, ParticleTypes.SMOKE, pos.x, pos.y - 0.3, pos.z, 1, 0.05, 0.01);
				if (velocity.y <= 0.05 || age > 80) {
					hanging = 0;
					Fx.particles(level, ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.12);
					Fx.particles(level, RED, pos.x, pos.y, pos.z, 30, 0.8, 0.0);
					level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.PLAYERS, 8.0F, 0.7F);
				}
				return false;
			}
			// Hangs in the sky under its little parachute, burning red, sinking slowly.
			hanging++;
			pos = pos.add(Math.sin(hanging * 0.05) * 0.02, -0.05, Math.cos(hanging * 0.05) * 0.02);
			Fx.particles(level, RED, pos.x, pos.y, pos.z, 4, 0.25, 0.0);
			Fx.particles(level, GLOW, pos.x, pos.y, pos.z, 1, 0.05, 0.0);
			if (hanging % 3 == 0) Fx.particles(level, ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 1, 0.05, 0.0);
			if (hanging % 12 == 0) Fx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 0.5, pos.z, 1, 0.1, 0.005);
			return hanging > 260 || pos.y < level.getMinY();
		}
	}
}
