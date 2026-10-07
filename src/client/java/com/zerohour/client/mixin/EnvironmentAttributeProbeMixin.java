package com.zerohour.client.mixin;

import com.zerohour.client.SkyVisuals;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The camera reads sky, fog, light and music settings through this probe; we grade them for the apocalypse. */
@Mixin(EnvironmentAttributeProbe.class)
public class EnvironmentAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void zerohour$gradeAttribute(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
		Object original = cir.getReturnValue();
		Object graded = SkyVisuals.modify(attribute, original, partialTick);
		if (graded != original) cir.setReturnValue(graded);
	}
}
