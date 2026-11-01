package com.orbitalis.client.screen;

import java.util.List;

import com.orbitalis.core.registry.ModFluids;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Jauge de fluide d'une interface : une zone qui se remplit de bas en haut, de la texture de l'eau de Minecraft
 * teintée à la couleur du fluide (voir {@link com.orbitalis.client.render.FluidSprites}).
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
        int color = com.orbitalis.client.render.FluidSprites.tint(fluid);
        // La texture est posée en carreaux de sa taille, du fond vers la surface, sans être étirée.
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = com.orbitalis.client.render.FluidSprites.still();
        int tileW = sprite.contents().width();
        int tileH = sprite.contents().height();
        int atlasW = Math.round(tileW / (sprite.getU1() - sprite.getU0()));
        int atlasH = Math.round(tileH / (sprite.getV1() - sprite.getV0()));
        float u0 = sprite.getU0() * atlasW;
        float v0 = sprite.getV0() * atlasH;
        for (int dy = 0; dy < filled; dy += tileH) {
            int h = Math.min(tileH, filled - dy);
            for (int dx = 0; dx < width; dx += tileW) {
                int w = Math.min(tileW, width - dx);
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite.atlasLocation(), x0 + dx, y1 - dy - h,
                        u0, v0 + tileH - h, w, h, w, h, atlasW, atlasH, color);
            }
        }
        // Liseré clair à la surface, pour donner du volume.
        graphics.fill(x0, y1 - filled, x0 + width, y1 - filled + 1, 0x60000000 | lighten(color) & 0xFFFFFF);
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
