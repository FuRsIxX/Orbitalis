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

    // Onglet de surcadençage, collé à gauche de la fenêtre : l'emplacement du module, puis le bouton de vitesse.
    private static final int TAB_X = -26;
    private static final int TAB_Y = 5;
    private static final int TAB_W = 26;
    private static final int TAB_H = 44;
    private static final int SPEED_X = -24;
    private static final int SPEED_Y = 32;
    private static final int SPEED_W = 21;
    private static final int SPEED_H = 12;

    // Écran d'état : zone de texte.
    private static final int STATUS_LEFT = 12;
    private static final int STATUS_RIGHT = 146;
    private static final int STATUS_Y = 58;

    private final Identifier texture;
    /** Vrai si le bandeau n'a pas tout montré (état coupé ou détail effacé) : tout s'affiche alors au survol. */
    private boolean statusCut;

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
        if (menu.hasOverclock()) {
            extractOverclockTab(graphics, mouseX, mouseY);
        }
    }

    /**
     * Onglet de surcadençage, dans le gris de la fenêtre : le creux de l'emplacement du module, puis la vitesse. Le
     * bouton reste éteint sans module ; il s'allume dès que la machine va plus vite que d'ordinaire.
     */
    private void extractOverclockTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos + TAB_X;
        int y = topPos + TAB_Y;
        // Contour noir aux coins arrondis, ouvert du côté de la fenêtre, puis le relief clair en haut et sombre en bas.
        graphics.fill(x + 1, y, x + TAB_W, y + 1, 0xFF000000);
        graphics.fill(x + 1, y + TAB_H - 1, x + TAB_W, y + TAB_H, 0xFF000000);
        graphics.fill(x, y + 1, x + 1, y + TAB_H - 1, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + TAB_W, y + TAB_H - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + TAB_W, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 3, y + TAB_H - 2, 0xFFFFFFFF);
        graphics.fill(x + 3, y + TAB_H - 3, x + TAB_W, y + TAB_H - 1, 0xFF555555);
        // Le creux de l'emplacement, comme ceux de la fenêtre.
        int sx = leftPos + MachineMenu.UPGRADE_X - 1;
        int sy = topPos + MachineMenu.UPGRADE_Y - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, 0xFFFFFFFF);
        graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);

        int speed = menu.getSpeed();
        boolean enabled = menu.getMaxSpeed() > 1;
        Ui.button(graphics, font, Component.literal("×" + speed), leftPos + SPEED_X, topPos + SPEED_Y, SPEED_W, SPEED_H,
                enabled && overSpeed(mouseX, mouseY), enabled, speed > 1, speed >= 8 ? Ui.BAD : Ui.WARN);
    }

    private boolean overSpeed(double mouseX, double mouseY) {
        return menu.hasOverclock() && isHovering(SPEED_X, SPEED_Y, SPEED_W, SPEED_H, mouseX, mouseY);
    }

    /** Clic gauche sur la vitesse : la suivante, puis retour à ×1 ; clic droit : la précédente. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if ((event.button() == 0 || event.button() == 1) && overSpeed(event.x(), event.y()) && menu.getMaxSpeed() > 1
                && minecraft != null && minecraft.gameMode != null) {
            int speed = menu.getSpeed();
            int next = event.button() == 0 ? (speed >= menu.getMaxSpeed() ? 1 : speed * 2) : Math.max(1, speed / 2);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.SPEED_BUTTON + next);
            Ui.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Un clic dans l'onglet n'est pas un clic hors de la fenêtre : il ne jette pas l'objet tenu. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        if (menu.hasOverclock() && isHovering(TAB_X, TAB_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top);
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
        // L'état et le détail partagent le bandeau, sans jamais se chevaucher. L'état passe d'abord : s'ils ne tiennent
        // pas ensemble, le détail s'efface (il reste au survol du bandeau), et l'état n'est coupé qu'en dernier recours.
        Component detail = getStatusDetail();
        int detailWidth = font.width(detail);
        String status = getStatus().getString();
        int full = STATUS_RIGHT - STATUS_LEFT;
        boolean both = detailWidth == 0 || font.width(status) + 6 + detailWidth <= full;
        String shown = Ui.fit(font, status, full);
        statusCut = !both || !shown.equals(status);
        graphics.text(font, shown, STATUS_LEFT, STATUS_Y, getStatusColor(), false);
        if (both) {
            graphics.text(font, detail, STATUS_RIGHT - detailWidth, STATUS_Y, COLOR_IDLE, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (statusCut && isHovering(STATUS_LEFT, STATUS_Y - 2, STATUS_RIGHT - STATUS_LEFT, 12, mouseX, mouseY)) {
            Component detail = getStatusDetail();
            graphics.setComponentTooltipForNextFrame(font, detail.getString().isEmpty() ? java.util.List.of(getStatus())
                    : java.util.List.of(getStatus(), detail.copy().withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
        if (overSpeed(mouseX, mouseY)) {
            int speed = menu.getSpeed();
            graphics.setComponentTooltipForNextFrame(font, menu.getMaxSpeed() <= 1
                    ? java.util.List.of(Component.translatable("gui.orbitalis.overclock.title"),
                            Component.translatable("gui.orbitalis.overclock.none").withStyle(net.minecraft.ChatFormatting.GRAY))
                    : java.util.List.of(Component.translatable("gui.orbitalis.overclock.speed", speed),
                            Component.translatable("gui.orbitalis.overclock.cost", speed * speed).withStyle(net.minecraft.ChatFormatting.GRAY),
                            Component.translatable("gui.orbitalis.overclock.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        }
        if (menu.hasOverclock() && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()
                && isHovering(MachineMenu.UPGRADE_X, MachineMenu.UPGRADE_Y, 16, 16, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, java.util.List.of(Component.translatable("gui.orbitalis.overclock.title"),
                    Component.translatable("gui.orbitalis.overclock.slot").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("gui.orbitalis.energy", menu.getEnergy(), menu.getEnergyCapacity()),
                    mouseX, mouseY);
        }
    }
}
