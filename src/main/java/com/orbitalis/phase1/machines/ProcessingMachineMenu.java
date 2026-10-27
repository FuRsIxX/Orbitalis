package com.orbitalis.phase1.machines;

import com.orbitalis.core.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Interface d'une machine de transformation (fonderie à arc, laminoir).
 */
public class ProcessingMachineMenu extends MachineMenu {
    /** Données communes + progression + durée de la recette + consommation par tick. */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 3;

    private final int inputCount;

    /** Côté client : construit à partir des données envoyées par le serveur. */
    public static ProcessingMachineMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int inputCount = buffer.readVarInt();
        // Le client applique les mêmes règles de dépôt que le serveur, en interrogeant la machine visée.
        BlockEntity target = playerInventory.player.level().getBlockEntity(pos);
        MachineInventory inventory = new MachineInventory(3,
                (slot, stack) -> slot < inputCount
                        && (!(target instanceof ProcessingMachineBlockEntity machine) || machine.acceptsInput(stack)),
                slot -> true, () -> {
                });
        return new ProcessingMachineMenu(containerId, playerInventory, null, inventory, new SimpleContainerData(DATA_COUNT), inputCount);
    }

    public ProcessingMachineMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity,
            MachineInventory inventory, ContainerData data, int inputCount) {
        super(ModMenus.PROCESSING_MACHINE.get(), containerId, blockEntity, data, inputCount + 1);
        this.inputCount = inputCount;

        // Entrées : une seule centrée, ou deux côte à côte.
        if (inputCount == 1) {
            addMachineSlot(inventory, 0, 44, SLOT_Y);
        } else {
            addMachineSlot(inventory, 0, 35, SLOT_Y);
            addMachineSlot(inventory, 1, 53, SLOT_Y);
        }
        // Sortie
        addMachineSlot(inventory, inputCount, 120, SLOT_Y);

        addPlayerSlots(playerInventory);
    }

    public int getInputCount() {
        return inputCount;
    }

    /** Avancement de la recette en cours, entre 0 et 1. */
    public float getProgress() {
        int max = data.get(BASE_DATA_COUNT + 1);
        return max <= 0 ? 0.0F : Math.min(1.0F, data.get(BASE_DATA_COUNT) / (float) max);
    }

    /** Vrai si le contenu des entrées correspond à une recette. */
    public boolean hasRecipe() {
        return data.get(BASE_DATA_COUNT + 1) > 0;
    }

    /** Énergie consommée par tick par la recette en cours (0 sans recette). */
    public int getEnergyPerTick() {
        return data.get(BASE_DATA_COUNT + 2);
    }
}
