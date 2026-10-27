package com.orbitalis.client.screen;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.FluidMachineBlockEntity;
import com.orbitalis.phase1.fuel.FluidMachineLayout;
import com.orbitalis.phase1.fuel.FluidMachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran des machines à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie).
 */
public class FluidMachineScreen extends MachineScreen<FluidMachineMenu> {
    // Jauges : entrée à gauche de la barre de progression, sorties à droite.
    private static final FluidGauge INPUT_GAUGE = new FluidGauge(48, 18, 12, 32);
    private static final FluidGauge[] OUTPUT_GAUGES = {new FluidGauge(118, 18, 12, 32), new FluidGauge(134, 18, 12, 32)};

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
