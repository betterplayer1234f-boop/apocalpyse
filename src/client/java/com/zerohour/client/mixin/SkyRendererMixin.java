package com.zerohour.client.mixin;

import com.zerohour.client.SkyVisuals;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	/** A sickly moon behind the smoke and the fallout. */
	@ModifyArg(
		method = "renderMoon",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		),
		index = 1
	)
	private Vector4fc zerohour$falloutMoon(Vector4fc color) {
		return SkyVisuals.moonColor(color);
	}
}
