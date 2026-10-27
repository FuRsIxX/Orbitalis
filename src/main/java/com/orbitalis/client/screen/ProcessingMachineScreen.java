package com.orbitalis.client.screen;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.machines.ProcessingMachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran des machines de transformation (fonderie à arc, laminoir).
 */
public class ProcessingMachineScreen extends MachineScreen<ProcessingMachineMenu> {
    private static final Identifier ONE_INPUT = Orbitalis.id("textures/gui/processing_machine_1.png");
    private static final Identifier TWO_INPUTS = Orbitalis.id("textures/gui/processing_machine_2.png");

    public ProcessingMachineScreen(ProcessingMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.getInputCount() == 1 ? ONE_INPUT : TWO_INPUTS);
    }

    @Override
    protected void extractMachineBackground(GuiGraphicsExtractor graphics) {
        extractBar(graphics, menu.getProgress(), BAR_PROGRESS_V);
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
