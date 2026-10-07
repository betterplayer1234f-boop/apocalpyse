package com.zerohour.client;

import java.util.function.Supplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** A non-positional looping sound whose volume glides toward a target. */
public class LoopSound extends AbstractTickableSoundInstance {
	private final Supplier<Float> target;
	private float current;

	public LoopSound(SoundEvent sound, float pitch, Supplier<Float> target) {
		super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
		this.target = target;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0001f;
		this.pitch = pitch;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		float goal = target.get();
		current += (goal - current) * 0.06f;
		volume = Math.max(0.0001f, current);
		if (goal <= 0f && current < 0.003f) stop();
	}
}
