package com.orbitalis.client.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Le dessin des interfaces à la façon de Minecraft 26 (« extraire » ce qu'il faut dessiner), posé sur le {@link GuiGraphics}
 * de 1.21.1 : les écrans du mod gardent leur code. Les bulles d'aide demandées pendant l'image sont dessinées à la fin,
 * par-dessus tout le reste, comme en 26.
 */
public final class GuiGraphicsExtractor {
    /** Le décalage en profondeur d'une « strate » : de quoi passer au-dessus des objets dessinés avant. */
    private static final float STRATUM = 200.0F;

    private final GuiGraphics graphics;
    private final Matrix3x2fStack pose;
    private final List<Runnable> tooltips = new ArrayList<>();
    private final List<ScreenRectangle> scissors = new ArrayList<>();

    public GuiGraphicsExtractor(GuiGraphics graphics) {
        this.graphics = graphics;
        this.pose = new Matrix3x2fStack(graphics.pose());
    }

    /** Le dessin de 1.21.1 qu'on enveloppe. */
    public GuiGraphics vanilla() {
        return graphics;
    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public Matrix3x2fStack pose() {
        return pose;
    }

    // ------------------------------------------------------------------
    // Formes
    // ------------------------------------------------------------------

    public void fill(int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        graphics.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void hLine(int x1, int x2, int y, int color) {
        graphics.hLine(x1, x2, y, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        graphics.vLine(x, y1, y2, color);
    }

    // ------------------------------------------------------------------
    // Texte
    // ------------------------------------------------------------------

    public void text(Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        if (text != null) {
            graphics.drawString(font, text, x, y, color, shadow);
        }
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, @Nullable String text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    public void centeredText(Font font, String text, int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    // ------------------------------------------------------------------
    // Objets et images
    // ------------------------------------------------------------------

    public void item(ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        graphics.renderItemDecorations(font, stack, x, y);
    }

    /**
     * Une partie d'une image : {@code (u, v)} et {@code uWidth × vHeight} dans l'image de {@code textureWidth ×
     * textureHeight}, étirée sur {@code width × height} à l'écran, teintée de {@code color} (ARGB).
     */
    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int uWidth,
            int vHeight, int textureWidth, int textureHeight, int color) {
        tint(color);
        graphics.blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int uWidth,
            int vHeight, int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
            int textureHeight, int color) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
            int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    private static void tint(int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                (color >>> 24) / 255.0F);
    }

    // ------------------------------------------------------------------
    // Découpe, strates, bulles d'aide
    // ------------------------------------------------------------------

    public void enableScissor(int x1, int y1, int x2, int y2) {
        scissors.add(new ScreenRectangle(x1, y1, x2 - x1, y2 - y1));
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        if (!scissors.isEmpty()) {
            scissors.remove(scissors.size() - 1);
        }
        graphics.disableScissor();
    }

    /** La découpe en cours, s'il y en a une. */
    public @Nullable ScreenRectangle peekScissorStack() {
        return scissors.isEmpty() ? null : scissors.get(scissors.size() - 1);
    }

    /** Tout ce qui suit se dessine au-dessus de ce qui précède, objets compris. */
    public void nextStratum() {
        graphics.flush();
        graphics.pose().translate(0.0F, 0.0F, STRATUM);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        tooltips.add(() -> graphics.renderTooltip(font, text, x, y));
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        tooltips.add(() -> graphics.renderTooltip(font, lines, x, y));
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        tooltips.add(() -> graphics.renderTooltip(font, stack, x, y));
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        tooltips.add(() -> graphics.renderComponentTooltip(font, lines, x, y));
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y, ItemStack stack) {
        tooltips.add(() -> graphics.renderTooltip(font, lines, Optional.empty(), x, y));
    }

    /** Dessine les bulles d'aide demandées pendant l'image, par-dessus tout. */
    public void flushTooltips() {
        if (tooltips.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        for (Runnable tooltip : tooltips) {
            tooltip.run();
        }
        graphics.pose().popPose();
        tooltips.clear();
    }
}
