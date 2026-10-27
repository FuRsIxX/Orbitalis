package com.orbitalis.client.screen;

import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Base des écrans de machines : fond, jauge d'énergie sur le bord droit, barre horizontale
 * (progression ou combustion) et écran d'état sous les emplacements.
 * <p>
 * Chaque écran utilise une image de 256x256 : le fond occupe la zone 176x166 en haut à gauche,
 * les éléments animés (barres pleines, flamme, jauge pleine) sont rangés à droite, à partir de x = 176.
 */
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
    // Couleurs du texte de l'écran d'état.
    protected static final int COLOR_OK = 0xFF7CE08A;
    protected static final int COLOR_IDLE = 0xFF9DB5A4;
    protected static final int COLOR_WARNING = 0xFFFFC857;

    // Jauge d'énergie : position dans la fenêtre et position de la jauge pleine dans l'image.
    private static final int ENERGY_X = 158;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = 8;
    private static final int ENERGY_HEIGHT = 52;
    private static final int ENERGY_U = 176;
    private static final int ENERGY_V = 31;

    // Barre horizontale (progression ou combustion).
    protected static final int BAR_X = 77;
    protected static final int BAR_Y = 31;
    protected static final int BAR_WIDTH = 32;
    protected static final int BAR_HEIGHT = 8;
    protected static final int BAR_PROGRESS_V = 0;
    protected static final int BAR_FUEL_V = 8;

    // Écran d'état : zone de texte.
    private static final int STATUS_LEFT = 12;
    private static final int STATUS_RIGHT = 146;
    private static final int STATUS_Y = 58;

    private final Identifier texture;

    protected MachineScreen(T menu, Inventory inventory, Component title, Identifier texture) {
        super(menu, inventory, title);
        this.texture = texture;
    }

    @Override
    protected void init() {
        super.init();
        // Titre centré, comme sur les fours vanilla.
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        blitPart(graphics, 0, 0, 0, 0, imageWidth, imageHeight);

        // Jauge d'énergie : elle se remplit de bas en haut.
        int capacity = menu.getEnergyCapacity();
        if (capacity > 0) {
            int filled = Mth.clamp(Mth.ceil(ENERGY_HEIGHT * (menu.getEnergy() / (float) capacity)), 0, ENERGY_HEIGHT);
            int empty = ENERGY_HEIGHT - filled;
            blitPart(graphics, ENERGY_X, ENERGY_Y + empty, ENERGY_U, ENERGY_V + empty, ENERGY_WIDTH, filled);
        }

        extractMachineBackground(graphics);
    }

    /** Éléments animés propres à la machine (barre de progression, flamme...). */
    protected abstract void extractMachineBackground(GuiGraphicsExtractor graphics);

    /** Texte de gauche de l'écran d'état : ce que fait la machine. */
    protected abstract Component getStatus();

    /** Couleur du texte d'état. */
    protected abstract int getStatusColor();

    /** Texte de droite de l'écran d'état (puissance, temps restant...) ; vide s'il n'y a rien à afficher. */
    protected abstract Component getStatusDetail();

    /** Remplit la barre horizontale de gauche à droite, avec la barre pleine située à la hauteur {@code v} de l'image. */
    protected void extractBar(GuiGraphicsExtractor graphics, float ratio, int v) {
        int filled = Mth.clamp(Mth.ceil(ratio * BAR_WIDTH), 0, BAR_WIDTH);
        blitPart(graphics, BAR_X, BAR_Y, 176, v, filled, BAR_HEIGHT);
    }

    /** Dessine une portion de l'image de l'écran, à une position relative au coin de la fenêtre. */
    protected void blitPart(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height) {
        if (width > 0 && height > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos + x, topPos + y, u, v, width, height, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, getStatus(), STATUS_LEFT, STATUS_Y, getStatusColor(), false);
        Component detail = getStatusDetail();
        graphics.text(font, detail, STATUS_RIGHT - font.width(detail), STATUS_Y, COLOR_IDLE, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("gui.orbitalis.energy", menu.getEnergy(), menu.getEnergyCapacity()),
                    mouseX, mouseY);
        }
    }
}
