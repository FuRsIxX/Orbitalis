package com.orbitalis.phase1.fuel;

import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.phase1.machines.MachineBlockEntity;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

/**
 * Interface d'une machine à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie).
 */
public class FluidMachineMenu extends MachineMenu {
    /**
     * Données communes + progression, durée, consommation + (fluide, quantité) pour chaque réservoir + purge des deux
     * sorties + surcadençage.
     */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 3 + FluidMachineBlockEntity.TANK_COUNT * 2 + 2 + OVERCLOCK_DATA_COUNT;
    private static final int TANK_DATA = BASE_DATA_COUNT + 3;

    private final FluidMachineLayout layout;

    /** Côté client : construit à partir des données envoyées par le serveur. */
    public static FluidMachineMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        FluidMachineLayout layout = buffer.readEnum(FluidMachineLayout.class);
        // Le client applique les mêmes règles de dépôt que le serveur, en interrogeant la machine visée.
        BlockEntity target = playerInventory.player.level().getBlockEntity(pos);
        MachineInventory inventory = new MachineInventory(1,
                (slot, stack) -> layout.hasItemInput()
                        && (!(target instanceof FluidMachineBlockEntity machine) || machine.acceptsItem(stack)),
                slot -> true, () -> {
                });
        return new FluidMachineMenu(containerId, playerInventory, null, inventory,
                MachineBlockEntity.upgradeInventory(() -> {
                }), new SimpleContainerData(DATA_COUNT), layout);
    }

    public FluidMachineMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity,
            MachineInventory inventory, MachineInventory upgrades, ContainerData data, FluidMachineLayout layout) {
        super(ModMenus.FLUID_MACHINE.get(), containerId, blockEntity, data, (layout.hasItemInput() ? 1 : 0) + 1);
        this.layout = layout;

        if (layout.hasItemInput()) {
            // Avec une jauge d'entrée à sa place habituelle, l'emplacement d'objet se range à sa gauche.
            addMachineSlot(inventory, 0, layout.hasFluidInput() ? 22 : 44, SLOT_Y);
        }
        addUpgradeSlot(upgrades, DATA_COUNT - OVERCLOCK_DATA_COUNT);
        addPlayerSlots(playerInventory);
    }

    public FluidMachineLayout getLayout() {
        return layout;
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

    /** Fluide contenu dans un réservoir (0 = entrée, 1 et 2 = sorties). */
    public Fluid getFluid(int tank) {
        return BuiltInRegistries.FLUID.byId(data.get(TANK_DATA + tank * 2));
    }

    /** Purge d'un réservoir de sortie (1 ou 2). */
    public FluidMachineBlockEntity.Purge getPurge(int tank) {
        int ordinal = data.get(TANK_DATA + FluidMachineBlockEntity.TANK_COUNT * 2 + tank - 1);
        return FluidMachineBlockEntity.Purge.values()[Math.clamp(ordinal, 0, FluidMachineBlockEntity.Purge.values().length - 1)];
    }

    /** Numéro du bouton de purge d'une sortie, envoyé au serveur : deux par sortie, clic gauche puis clic droit. */
    public static int purgeButton(int tank, boolean stop) {
        return (tank - 1) * 2 + (stop ? 1 : 0);
    }

    /** Un joueur a cliqué sur un bouton de purge : le serveur change le mode de la sortie. */
    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        if (id > SPEED_BUTTON) {
            return super.clickMenuButton(player, id);
        }
        int tank = 1 + id / 2;
        if (id < 0 || tank > layout.outputCount() || !(blockEntity() instanceof FluidMachineBlockEntity machine)) {
            return false;
        }
        machine.setPurge(tank, id % 2 == 1 ? FluidMachineBlockEntity.Purge.OFF : machine.getPurge(tank).next());
        return true;
    }

    /** Quantité contenue dans un réservoir, en mB. */
    public int getFluidAmount(int tank) {
        return data.get(TANK_DATA + tank * 2 + 1);
    }
}
