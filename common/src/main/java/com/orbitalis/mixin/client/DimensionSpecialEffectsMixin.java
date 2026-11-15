package com.orbitalis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.orbitalis.client.SpaceTransition;

import net.minecraft.client.renderer.DimensionSpecialEffects;

/** Plus de lever de soleil rougeoyant au-dessus de l'air (voir {@link SpaceTransition}). */
@Mixin(DimensionSpecialEffects.class)
public abstract class DimensionSpecialEffectsMixin {
    @Inject(method = "getSunriseColor", at = @At("RETURN"))
    private void orbitalis$sunrise(float timeOfDay, float partialTick, CallbackInfoReturnable<float[]> cir) {
        float[] color = cir.getReturnValue();
        if (color != null && color.length > 3) {
            color[3] = SpaceTransition.sunriseAlpha(color[3]);
        }
    }
}
