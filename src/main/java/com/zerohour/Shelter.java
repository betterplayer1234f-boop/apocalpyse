package com.zerohour;

import com.zerohour.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Decides whether a position is protected from the blast.
 * Rule one: if daylight can reach you, the flash and the blast wave can too (sky light must be 0).
 * Rule two: there must be enough mass overhead to take the blast ("rock-meters").
 * The same overhead mass is what shields you from fallout afterwards (see {@link Radiation}).
 */
public final class Shelter {
	public enum Status {
		SAFE, PARTIAL, EXPOSED;
	}

	public record Result(Status status, int skyLight, float mass, float required) {
		public float rating() {
			if (status == Status.EXPOSED) return 0f;
			return Mth.clamp(mass / required, 0f, 1f);
		}

		public int percent() {
			return Math.round(rating() * 100f);
		}
	}

	private Shelter() {
	}

	public static Result evaluate(Level level, BlockPos eye, float required) {
		int sky = level.dimensionType().hasSkyLight() ? level.getBrightness(LightLayer.SKY, eye) : 0;
		float mass = overheadMass(level, eye, required * 2f);
		Status status;
		if (sky > 0) {
			status = Status.EXPOSED;
		} else if (mass >= required) {
			status = Status.SAFE;
		} else {
			status = Status.PARTIAL;
		}
		return new Result(status, sky, mass, required);
	}

	/** Sum of block weights above the position, stopping early once {@code cap} is reached. */
	public static float overheadMass(Level level, BlockPos eye, float cap) {
		float mass = 0f;
		BlockPos.MutableBlockPos pos = eye.mutable();
		int top = level.getMaxY();
		for (int y = eye.getY() + 1; y <= top && mass < cap; y++) {
			pos.setY(y);
			mass += weight(level.getBlockState(pos));
		}
		return mass;
	}

	public static float weight(BlockState state) {
		if (state.isAir()) return 0f;
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty() && !state.canOcclude()) {
			return fluid.is(FluidTags.LAVA) ? 1f : 0.5f;
		}
		if (!state.canOcclude()) return 0f;
		if (state.is(ModBlocks.BLAST_SHIELDING)) return 8f;
		return Mth.clamp(state.getBlock().getExplosionResistance() / 6f, 0.25f, 4f);
	}
}
