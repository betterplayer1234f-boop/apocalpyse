package com.zerohour.client;

import com.zerohour.Radiation;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Drives the Geiger counter's needle: 0 = background, 1 = pinned in the red. It trembles more as it climbs. */
public class RadiationProperty implements RangeSelectItemModelProperty {
	public static final MapCodec<RadiationProperty> MAP_CODEC = MapCodec.unit(new RadiationProperty());

	@Override
	public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		float needle = Math.max(0.03F, Radiation.needle(ClientState.shownRate));
		float tremble = Mth.sin(ClientState.ticks * 0.9f + seed) * 0.03f * needle;
		return Mth.clamp(needle + tremble, 0f, 0.999f);
	}

	@Override
	public MapCodec<? extends RangeSelectItemModelProperty> type() {
		return MAP_CODEC;
	}
}
