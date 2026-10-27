package com.orbitalis.phase1.machines;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * Base des interfaces de machines : emplacements de la machine, inventaire du joueur,
 * et valeurs synchronisées (énergie, progression).
 * <p>
 * Les premières valeurs synchronisées sont communes à toutes les machines : l'énergie et la capacité
 * (chacune découpée en deux moitiés de 16 bits, limite du protocole de Minecraft), puis l'état « en marche ».
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    /** Nombre de valeurs communes au début des données synchronisées (voir MachineBlockEntity#baseData). */
    public static final int BASE_DATA_COUNT = 5;
    /** Hauteur des emplacements de la machine dans la fenêtre. */
    protected static final int SLOT_Y = 27;

    /** Entité de bloc côté serveur ; {@code null} côté client. */
    private final BlockEntity blockEntity;
    protected final ContainerData data;
    private final int machineSlotCount;

    protected MachineMenu(MenuType<?> type, int containerId, BlockEntity blockEntity, ContainerData data, int machineSlotCount) {
        super(type, containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.machineSlotCount = machineSlotCount;
        addDataSlots(data);
    }

    /** Ajoute un emplacement de la machine. Les règles de dépôt viennent de l'inventaire de la machine. */
    protected void addMachineSlot(MachineInventory inventory, int index, int x, int y) {
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, index, x, y));
    }

    /** Ajoute l'inventaire et la barre d'accès rapide du joueur, à l'emplacement standard. */
    protected void addPlayerSlots(Inventory playerInventory) {
        addStandardInventorySlots(playerInventory, 8, 84);
    }

    public int getEnergy() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    /** Vrai si la machine travaille en ce moment. */
    public boolean isWorking() {
        return data.get(4) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || Container.stillValidBlockEntity(blockEntity, player);
    }

    /** Maj + clic : déplace la pile entre la machine et l'inventaire du joueur. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex < machineSlotCount) {
            // Machine -> joueur
            if (!moveItemStackTo(stack, machineSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlotCount, false)) {
            // Joueur -> machine (les emplacements refusent d'eux-mêmes ce qui ne leur convient pas)
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
