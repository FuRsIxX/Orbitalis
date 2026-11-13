package com.orbitalis.phase1.machines;

import com.orbitalis.core.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Interface du générateur à combustion.
 */
public class CombustionGeneratorMenu extends MachineMenu {
    /** Données communes + combustion restante + durée totale du combustible. */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 2;

    /** Côté client : construit à partir des données envoyées par le serveur. */
    public static CombustionGeneratorMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        buffer.readBlockPos();
        var fuelValues = playerInventory.player.level().fuelValues();
        MachineInventory inventory = new MachineInventory(1,
                (slot, stack) -> stack.getBurnTime(null, fuelValues) > 0, slot -> true, () -> {
                });
        return new CombustionGeneratorMenu(containerId, playerInventory, null, inventory, new SimpleContainerData(DATA_COUNT));
    }

    public CombustionGeneratorMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity,
            MachineInventory inventory, ContainerData data) {
        super(ModMenus.COMBUSTION_GENERATOR.get(), containerId, blockEntity, data, 1);

        // Combustible
        addMachineSlot(inventory, 0, 44, SLOT_Y);

        addPlayerSlots(playerInventory);
    }

    /** Vrai s'il reste du combustible en cours de combustion (même si la combustion est en pause). */
    public boolean hasFuelBurning() {
        return data.get(BASE_DATA_COUNT) > 0;
    }

    /** Secondes de combustion restantes pour le combustible en cours. */
    public int getBurnSecondsLeft() {
        return (data.get(BASE_DATA_COUNT) + 19) / 20;
    }

    /** Part du combustible en cours qui reste à brûler, entre 0 et 1. */
    public float getBurnProgress() {
        int duration = data.get(BASE_DATA_COUNT + 1);
        return duration <= 0 ? 0.0F : Math.min(1.0F, data.get(BASE_DATA_COUNT) / (float) duration);
    }
}
