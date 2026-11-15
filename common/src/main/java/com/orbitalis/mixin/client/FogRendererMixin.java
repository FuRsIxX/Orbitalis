package com.orbitalis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.orbitalis.client.port.event.ClientEvents;
import com.orbitalis.core.registry.ModFluids;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;

/**
 * Le brouillard, sur les deux chargeurs : sa couleur revue par le mod (le ciel qui bleuit puis fonce en montant vers
 * l'espace), et, sous le méthane liquide de Titan, une brume brune, épaisse, où l'on ne voit qu'à quelques blocs.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    private static boolean orbitalis$inMethane(Camera camera) {
        if (camera.getEntity() == null) {
            return false;
        }
        FluidState fluid = camera.getEntity().level().getFluidState(BlockPos.containing(camera.getPosition()));
        return !fluid.isEmpty() && fluid.getType().isSame(ModFluids.LIQUID_METHANE.source().get());
    }

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void orbitalis$color(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken,
            CallbackInfo ci) {
        if (orbitalis$inMethane(camera)) {
            fogRed = 0.26F;
            fogGreen = 0.16F;
            fogBlue = 0.07F;
        } else {
            float[] color = ClientEvents.fogColor(camera, partialTick, fogRed, fogGreen, fogBlue);
            fogRed = color[0];
            fogGreen = color[1];
            fogBlue = color[2];
        }
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void orbitalis$distance(Camera camera, FogRenderer.FogMode mode, float farPlane, boolean thick, float partialTick,
            CallbackInfo ci) {
        if (orbitalis$inMethane(camera)) {
            RenderSystem.setShaderFogStart(0.0F);
            RenderSystem.setShaderFogEnd(7.0F);
        }
    }
}
