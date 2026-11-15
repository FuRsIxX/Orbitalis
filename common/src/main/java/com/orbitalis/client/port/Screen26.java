package com.orbitalis.client.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Un écran à la façon de Minecraft 26 ({@code extractRenderState}, {@code extractBackground}, clics et touches en
 * événements), sur l'écran de 1.21.1 : les écrans du mod en héritent au lieu de {@link Screen}.
 */
public abstract class Screen26 extends Screen {
    private GuiGraphicsExtractor frame;

    protected Screen26(Component title) {
        super(title);
    }

    /** Le dessin de l'image en cours (un seul par image, pour que les bulles d'aide viennent à la fin). */
    protected GuiGraphicsExtractor wrap(GuiGraphics graphics) {
        if (frame == null || frame.vanilla() != graphics) {
            frame = new GuiGraphicsExtractor(graphics);
        }
        return frame;
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor extractor = wrap(graphics);
        extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.flushTooltips();
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics.vanilla(), mouseX, mouseY, partialTick);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(wrap(graphics), mouseX, mouseY, partialTick);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics.vanilla(), mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------
    // Souris et clavier
    // ------------------------------------------------------------------

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(new MouseButtonEvent(mouseX, mouseY, button), Keys.doubleClick(mouseX, mouseY, button));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleased(new MouseButtonEvent(mouseX, mouseY, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return mouseDragged(new MouseButtonEvent(mouseX, mouseY, button), dragX, dragY);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
    }

    @Override
    public final boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public final boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }
}
