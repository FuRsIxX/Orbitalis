package com.orbitalis.client.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Un écran d'inventaire à la façon de Minecraft 26 (fond, étiquettes et bulles d'aide « extraits », clics en événements),
 * sur celui de 1.21.1. Comme en 26, la bulle d'aide de l'emplacement survolé vient d'elle-même, après le reste.
 */
public abstract class ContainerScreen26<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private GuiGraphicsExtractor frame;

    protected ContainerScreen26(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** Avec la taille de la fenêtre, comme le permettait 26. */
    protected ContainerScreen26(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.inventoryLabelY = height - 94;
    }

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
        extractTooltip(extractor, mouseX, mouseY);
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

    /** Le fond est dessiné par {@link #extractBackground}, après l'assombrissement du jeu. */
    @Override
    protected final void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected final void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        extractLabels(wrap(graphics), mouseX, mouseY);
    }

    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics.vanilla(), mouseX, mouseY);
    }

    @Override
    protected final void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        extractTooltip(wrap(graphics), mouseX, mouseY);
    }

    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics.vanilla(), mouseX, mouseY);
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
}
