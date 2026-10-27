package com.orbitalis.client.screen;

import java.util.List;

import com.orbitalis.core.registry.ModFluids;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Jauge de fluide d'une interface : une zone qui se remplit de bas en haut, à la couleur du fluide.
 *
 * @param x      position dans la fenêtre
 * @param y      position dans la fenêtre
 * @param width  largeur de la zone utile
 * @param height hauteur de la zone utile
 */
public record FluidGauge(int x, int y, int width, int height) {
    /** Dessine le niveau de fluide. {@code left} et {@code top} sont le coin de la fenêtre à l'écran. */
    public void extract(GuiGraphicsExtractor graphics, int left, int top, Fluid fluid, int amount, int capacity) {
        if (fluid == Fluids.EMPTY || amount <= 0 || capacity <= 0) {
            return;
        }
        int filled = Mth.clamp(Mth.ceil(height * (amount / (float) capacity)), 1, height);
        int x0 = left + x;
        int y1 = top + y + height;
        int color = ModFluids.color(fluid);
        graphics.fill(x0, y1 - filled, x0 + width, y1, color);
        // Liseré clair à la surface et reflet sur le bord gauche, pour donner du volume.
        graphics.fill(x0, y1 - filled, x0 + width, y1 - filled + 1, lighten(color));
        graphics.fill(x0, y1 - filled, x0 + 1, y1, lighten(color));
    }

    /** Infobulle : nom du fluide et quantité. */
    public List<Component> tooltip(Fluid fluid, int amount, int capacity) {
        Component name = fluid == Fluids.EMPTY || amount <= 0
                ? Component.translatable("gui.orbitalis.fluid.empty")
                : fluid.getFluidType().getDescription();
        return List.of(name, Component.translatable("gui.orbitalis.fluid.amount", amount, capacity));
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
