package com.orbitalis.phase1.quarry;

import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Interface de la foreuse, du même genre que celles des machines : les dix-huit cases du butin, la case du bloc de
 * remplacement, l'inventaire du joueur, et les jauges (énergie, eau, chaleur) synchronisées à chaque tick. Les réglages,
 * la carte et les filtres arrivent à part (voir {@code DerrickStatusPayload}).
 */
public class DerrickMenu extends MachineMenu {
    /** Données communes des machines, puis l'eau, la chaleur et l'état. */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 3;
    /** Placement des cases, en pixels de l'écran. */
    public static final int OUTPUT_X = 62;
    public static final int OUTPUT_Y = 18;
    public static final int REPLACE_X = 26;
    public static final int REPLACE_Y = 80;
    public static final int PLAYER_Y = 112;

    private final BlockPos pos;

    public static DerrickMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        MachineInventory output = new MachineInventory(DerrickBlockEntity.OUTPUT_SLOTS, (slot, stack) -> false, slot -> true, () -> {
        });
        MachineInventory replacement = new MachineInventory(1, (slot, stack) -> stack.getItem() instanceof BlockItem, slot -> true, () -> {
        });
        DerrickMenu menu = new DerrickMenu(containerId, playerInventory, null, output, replacement, new SimpleContainerData(DATA_COUNT));
        return menu.at(pos);
    }

    private BlockPos at;

    private DerrickMenu at(BlockPos pos) {
        this.at = pos;
        return this;
    }

    public DerrickMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, MachineInventory output, MachineInventory replacement,
            ContainerData data) {
        super(ModMenus.DRILL_DERRICK.get(), containerId, blockEntity, data, DerrickBlockEntity.OUTPUT_SLOTS + 1);
        this.pos = blockEntity == null ? null : blockEntity.getBlockPos();
        for (int slot = 0; slot < DerrickBlockEntity.OUTPUT_SLOTS; slot++) {
            addMachineSlot(output, slot, OUTPUT_X + (slot % 6) * 18, OUTPUT_Y + (slot / 6) * 18);
        }
        addMachineSlot(replacement, 0, REPLACE_X, REPLACE_Y);
        com.orbitalis.platform.Compat.addStandardInventorySlots(this::addSlot, playerInventory, 8, PLAYER_Y);
    }

    /** La foreuse de cet écran. */
    public BlockPos pos() {
        return pos != null ? pos : at;
    }

    public int getWater() {
        return data.get(BASE_DATA_COUNT);
    }

    public int getHeat() {
        return data.get(BASE_DATA_COUNT + 1);
    }

    public int getStatus() {
        return data.get(BASE_DATA_COUNT + 2);
    }
}
