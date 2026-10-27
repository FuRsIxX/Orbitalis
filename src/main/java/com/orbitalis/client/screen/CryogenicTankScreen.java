package com.orbitalis.client.screen;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.CryogenicTankBlockEntity;
import com.orbitalis.phase1.fuel.CryogenicTankMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran du réservoir cryogénique : une grande jauge et l'état du stockage.
 */
public class CryogenicTankScreen extends MachineScreen<CryogenicTankMenu> {
    private static final Identifier TEXTURE = Orbitalis.id("textures/gui/cryogenic_tank.png");
    private static final FluidGauge GAUGE = new FluidGauge(44, 18, 88, 32);

    public CryogenicTankScreen(CryogenicTankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }

    @Override
    protected void extractMachineBackground(GuiGraphicsExtractor graphics) {
        GAUGE.extract(graphics, leftPos, topPos, menu.getFluid(), menu.getFluidAmount(), CryogenicTankBlockEntity.CAPACITY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isHovering(GAUGE.x(), GAUGE.y(), GAUGE.width(), GAUGE.height(), mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font,
                    GAUGE.tooltip(menu.getFluid(), menu.getFluidAmount(), CryogenicTankBlockEntity.CAPACITY), mouseX, mouseY);
        }
    }

    @Override
    protected Component getStatus() {
        return switch (menu.getStorageState()) {
            case CryogenicTankBlockEntity.STATE_STABLE -> Component.translatable("gui.orbitalis.status.stable");
            case CryogenicTankBlockEntity.STATE_COOLING -> Component.translatable("gui.orbitalis.status.cooling");
            case CryogenicTankBlockEntity.STATE_EVAPORATING -> Component.translatable("gui.orbitalis.status.evaporating");
            default -> Component.translatable("gui.orbitalis.status.empty");
        };
    }

    @Override
    protected int getStatusColor() {
        return switch (menu.getStorageState()) {
            case CryogenicTankBlockEntity.STATE_STABLE, CryogenicTankBlockEntity.STATE_COOLING -> COLOR_OK;
            case CryogenicTankBlockEntity.STATE_EVAPORATING -> COLOR_WARNING;
            default -> COLOR_IDLE;
        };
    }

    @Override
    protected Component getStatusDetail() {
        return menu.getStorageState() == CryogenicTankBlockEntity.STATE_COOLING
                ? Component.translatable("gui.orbitalis.energy_use", CryogenicTankBlockEntity.COOLING_PER_TICK)
                : Component.empty();
    }
}
