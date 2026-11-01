package com.orbitalis.client.screen;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.FluidMachineBlockEntity;
import com.orbitalis.phase1.fuel.FluidMachineLayout;
import com.orbitalis.phase1.fuel.FluidMachineMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran des machines à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie).
 */
public class FluidMachineScreen extends MachineScreen<FluidMachineMenu> {
    // Jauges : entrée à gauche de la barre de progression, sorties à droite.
    private static final FluidGauge INPUT_GAUGE = new FluidGauge(48, 18, 12, 32);
    private static final FluidGauge[] OUTPUT_GAUGES = {new FluidGauge(118, 18, 12, 32), new FluidGauge(134, 18, 12, 32)};
    // Boutons de purge : sous le bandeau d'état, à l'aplomb de chaque jauge de sortie.
    private static final int PURGE_Y = 72;
    private static final int PURGE_W = 12;
    private static final int PURGE_H = 10;

    public FluidMachineScreen(FluidMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, Orbitalis.id("textures/gui/" + menu.getLayout().id() + ".png"));
    }

    @Override
    protected void extractMachineBackground(GuiGraphicsExtractor graphics) {
        extractBar(graphics, menu.getProgress(), BAR_PROGRESS_V);

        FluidMachineLayout layout = menu.getLayout();
        if (layout.hasFluidInput()) {
            extractGauge(graphics, INPUT_GAUGE, 0);
        }
        for (int i = 0; i < layout.outputCount(); i++) {
            extractGauge(graphics, OUTPUT_GAUGES[i], 1 + i);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (int tank = 1; tank <= menu.getLayout().outputCount(); tank++) {
            extractPurgeButton(graphics, tank, overPurge(tank, mouseX, mouseY));
        }
    }

    /**
     * Bouton de purge d'une sortie : un petit réservoir dessiné, vide de contour quand la purge est arrêtée, rempli à
     * moitié pour la purge à 50 %, vidé par le bas pour la purge totale. Sa couleur dit le mode d'un coup d'œil.
     */
    private void extractPurgeButton(GuiGraphicsExtractor graphics, int tank, boolean hovered) {
        FluidMachineBlockEntity.Purge purge = menu.getPurge(tank);
        int x = leftPos + OUTPUT_GAUGES[tank - 1].x();
        int y = topPos + PURGE_Y;
        int color = switch (purge) {
            case OFF -> Ui.MUTED;
            case HALF -> Ui.WARN;
            case FULL -> Ui.BAD;
        };
        Ui.round(graphics, x, y, PURGE_W, PURGE_H, hovered ? Ui.alpha(color, 0.35F) : Ui.SURFACE);
        Ui.frame(graphics, x, y, PURGE_W, PURGE_H, purge == FluidMachineBlockEntity.Purge.OFF && !hovered ? Ui.LINE_HI : color);
        // Le petit réservoir : 6 pixels sur 6, au centre.
        int tx = x + 3;
        int ty = y + 2;
        Ui.frame(graphics, tx, ty, 6, 6, color);
        if (purge == FluidMachineBlockEntity.Purge.HALF) {
            graphics.fill(tx + 1, ty + 3, tx + 5, ty + 5, color);
        } else if (purge == FluidMachineBlockEntity.Purge.FULL) {
            graphics.fill(tx + 2, ty + 5, tx + 4, ty + 8, color);
        }
    }

    private boolean overPurge(int tank, double mouseX, double mouseY) {
        return isHovering(OUTPUT_GAUGES[tank - 1].x(), PURGE_Y, PURGE_W, PURGE_H, mouseX, mouseY);
    }

    /** Clic gauche : la purge suivante (arrêt, 50 %, totale) ; clic droit : la purge s'arrête. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int tank = 1; tank <= menu.getLayout().outputCount(); tank++) {
            if ((event.button() == 0 || event.button() == 1) && overPurge(tank, event.x(), event.y()) && minecraft != null
                    && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FluidMachineMenu.purgeButton(tank, event.button() == 1));
                Ui.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void extractGauge(GuiGraphicsExtractor graphics, FluidGauge gauge, int tank) {
        gauge.extract(graphics, leftPos, topPos, menu.getFluid(tank), menu.getFluidAmount(tank), FluidMachineBlockEntity.TANK_CAPACITY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        FluidMachineLayout layout = menu.getLayout();
        if (layout.hasFluidInput()) {
            extractGaugeTooltip(graphics, INPUT_GAUGE, 0, mouseX, mouseY);
        }
        for (int i = 0; i < layout.outputCount(); i++) {
            extractGaugeTooltip(graphics, OUTPUT_GAUGES[i], 1 + i, mouseX, mouseY);
            if (overPurge(1 + i, mouseX, mouseY)) {
                // Le mode, ce qu'il fait, puis comment le changer.
                String mode = menu.getPurge(1 + i).name().toLowerCase(java.util.Locale.ROOT);
                graphics.setComponentTooltipForNextFrame(font, java.util.List.of(
                        Component.translatable("gui.orbitalis.purge." + mode),
                        Component.translatable("gui.orbitalis.purge." + mode + ".desc").withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.orbitalis.purge.hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            }
        }
    }

    private void extractGaugeTooltip(GuiGraphicsExtractor graphics, FluidGauge gauge, int tank, int mouseX, int mouseY) {
        if (isHovering(gauge.x(), gauge.y(), gauge.width(), gauge.height(), mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                    gauge.tooltip(menu.getFluid(tank), menu.getFluidAmount(tank), FluidMachineBlockEntity.TANK_CAPACITY),
                    mouseX, mouseY);
        }
    }

    @Override
    protected Component getStatus() {
        if (menu.isWorking()) {
            return Component.translatable("gui.orbitalis.status.working");
        }
        if (!menu.hasRecipe()) {
            return Component.translatable("gui.orbitalis.status.idle");
        }
        // Une recette est prête mais la machine ne tourne pas : il manque de l'énergie ou de la place en sortie.
        return menu.getEnergy() < menu.getEnergyPerTick()
                ? Component.translatable("gui.orbitalis.status.no_energy")
                : Component.translatable("gui.orbitalis.status.output_full");
    }

    @Override
    protected int getStatusColor() {
        if (menu.isWorking()) {
            return COLOR_OK;
        }
        return menu.hasRecipe() ? COLOR_WARNING : COLOR_IDLE;
    }

    @Override
    protected Component getStatusDetail() {
        return menu.hasRecipe()
                ? Component.translatable("gui.orbitalis.energy_use", menu.getEnergyPerTick())
                : Component.empty();
    }
}
