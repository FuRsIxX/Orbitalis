package com.orbitalis.phase1.fuel;

import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

/**
 * Interface du réservoir cryogénique.
 */
public class CryogenicTankMenu extends MachineMenu {
    /** Données communes + fluide, quantité et état du stockage. */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 3;

    /** Côté client : construit à partir des données envoyées par le serveur. */
    public static CryogenicTankMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        buffer.readBlockPos();
        return new CryogenicTankMenu(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public CryogenicTankMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(ModMenus.CRYOGENIC_TANK.get(), containerId, blockEntity, data, 0);
        addPlayerSlots(playerInventory);
    }

    public Fluid getFluid() {
        return BuiltInRegistries.FLUID.byId(data.get(BASE_DATA_COUNT));
    }

    /** Quantité stockée, en mB. */
    public int getFluidAmount() {
        return data.get(BASE_DATA_COUNT + 1);
    }

    /** État du stockage : une des constantes STATE_* de {@link CryogenicTankBlockEntity}. */
    public int getStorageState() {
        return data.get(BASE_DATA_COUNT + 2);
    }
}
