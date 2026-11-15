package com.orbitalis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.client.WarpFx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;

/**
 * Pendant un saut en distorsion, le joueur ne voit pas « Chargement du terrain » : l'écran du changement de monde ne dessine
 * que l'éclair du départ, et l'on voit à travers le tunnel et l'onde de choc (voir {@link WarpFx}) le temps que le monde
 * d'arrivée se charge. Hors d'un saut, c'est l'écran ordinaire du jeu.
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void orbitalis$warpRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (WarpFx.active()) {
            float flash = WarpFx.flashNow();
            if (flash > 0.004F) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int) (flash * 240) << 24 | WarpFx.glow());
            }
            ci.cancel();
        }
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void orbitalis$warpBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (WarpFx.active()) {
            ci.cancel();
        }
    }
}
