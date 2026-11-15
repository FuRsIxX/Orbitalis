package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.orbitalis.client.port.event.ClientEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** Sous Fabric : le champ de vision revu par le mod (il s'élargit pendant un saut en distorsion). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void orbitalis$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(ClientEvents.fov(camera, partialTick, cir.getReturnValueD()));
    }
}
