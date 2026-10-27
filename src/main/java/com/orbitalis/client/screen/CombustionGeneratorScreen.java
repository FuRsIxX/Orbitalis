package com.orbitalis.client.screen;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.machines.CombustionGeneratorBlockEntity;
import com.orbitalis.phase1.machines.CombustionGeneratorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran du générateur à combustion.
 */
public class CombustionGeneratorScreen extends MachineScreen<CombustionGeneratorMenu> {
    private static final Identifier TEXTURE = Orbitalis.id("textures/gui/combustion_generator.png");

    // Flamme à droite de la barre de combustion.
    private static final int FLAME_X = 119;
    private static final int FLAME_Y = 28;
    private static final int FLAME_SIZE = 14;

    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }

    @Override
    protected void extractMachineBackground(GuiGraphicsExtractor graphics) {
        // La barre montre ce qu'il reste du combustible en cours ; la flamme s'allume quand le générateur produit.
        extractBar(graphics, menu.getBurnProgress(), BAR_FUEL_V);
        if (menu.isWorking()) {
            blitPart(graphics, FLAME_X, FLAME_Y, 176, 17, FLAME_SIZE, FLAME_SIZE);
        }
    }

    @Override
    protected Component getStatus() {
        if (menu.isWorking()) {
            return Component.translatable("gui.orbitalis.status.generating",
                    CombustionGeneratorBlockEntity.ENERGY_PER_TICK);
        }
        // Du combustible est entamé mais rien n'est produit : la réserve est pleine, la combustion est en pause.
        return menu.hasFuelBurning()
                ? Component.translatable("gui.orbitalis.status.buffer_full")
                : Component.translatable("gui.orbitalis.status.no_fuel");
    }

    @Override
    protected int getStatusColor() {
        if (menu.isWorking()) {
            return COLOR_OK;
        }
        return menu.hasFuelBurning() ? COLOR_WARNING : COLOR_IDLE;
    }

    @Override
    protected Component getStatusDetail() {
        return menu.hasFuelBurning()
                ? Component.translatable("gui.orbitalis.seconds", menu.getBurnSecondsLeft())
                : Component.empty();
    }
}
