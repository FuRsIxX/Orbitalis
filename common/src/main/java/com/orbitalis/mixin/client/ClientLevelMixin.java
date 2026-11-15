package com.orbitalis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.orbitalis.client.SpaceTransition;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Le ciel de la Terre fonce et s'étoile à mesure qu'on monte vers l'espace (voir {@link SpaceTransition}). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void orbitalis$skyColor(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(SpaceTransition.skyColor(cir.getReturnValue()));
    }

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void orbitalis$stars(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SpaceTransition.starBrightness(cir.getReturnValueF()));
    }
}
