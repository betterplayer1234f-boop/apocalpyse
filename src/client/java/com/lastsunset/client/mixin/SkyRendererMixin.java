package com.lastsunset.client.mixin;

import com.lastsunset.client.DyingSunRenderer;
import com.lastsunset.client.SkyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	/** Replace the sun with the swollen, burning one while the world is ending. */
	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void lastsunset$dyingSun(float alpha, PoseStack poseStack, CallbackInfo ci) {
		if (DyingSunRenderer.render(alpha, poseStack)) ci.cancel();
	}

	/** Blood moon. */
	@ModifyArg(
		method = "renderMoon",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		),
		index = 1
	)
	private Vector4fc lastsunset$bloodMoon(Vector4fc color) {
		return SkyVisuals.moonColor(color);
	}
}
