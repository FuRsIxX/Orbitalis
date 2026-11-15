package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.client.port.event.ClientEvents;
import com.orbitalis.client.port.event.ClientHooks.VanillaGuiLayers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PlayerRideableJumping;

/**
 * Sous Fabric : les couches de l'interface du jeu que le mod cache (dans la cabine, la place est aux instruments), comme
 * le fait NeoForge pour ses couches nommées.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    private static void orbitalis$layer(ResourceLocation name, CallbackInfo ci) {
        if (!ClientEvents.guiLayer(name)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void orbitalis$crosshair(GuiGraphics graphics, DeltaTracker tick, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.CROSSHAIR, ci);
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void orbitalis$hotbar(GuiGraphics graphics, DeltaTracker tick, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.HOTBAR, ci);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void orbitalis$health(GuiGraphics graphics, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.PLAYER_HEALTH, ci);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void orbitalis$vehicle(GuiGraphics graphics, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.VEHICLE_HEALTH, ci);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void orbitalis$experienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, ci);
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void orbitalis$jumpMeter(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, ci);
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void orbitalis$experienceLevel(GuiGraphics graphics, DeltaTracker tick, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.EXPERIENCE_LEVEL, ci);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void orbitalis$itemName(GuiGraphics graphics, CallbackInfo ci) {
        orbitalis$layer(VanillaGuiLayers.SELECTED_ITEM_NAME, ci);
    }
}
