package com.lastsunset.client.mixin;

import com.lastsunset.client.CameraShake;
import com.lastsunset.client.SkyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** Earthquakes and the shockwave shake the view. */
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void lastsunset$shake(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		CameraShake.apply(poseStack, partialTick);
	}

	/** Field-of-view punch when the shockwave hits. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void lastsunset$fovPunch(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		float multiplier = SkyVisuals.fovMultiplier(partialTick);
		if (multiplier != 1f) cir.setReturnValue(cir.getReturnValue() * multiplier);
	}
}
