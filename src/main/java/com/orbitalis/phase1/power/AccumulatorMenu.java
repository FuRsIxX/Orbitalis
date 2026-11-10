package com.orbitalis.phase1.power;

import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Écran d'un accumulateur : rien à poser, un seul réglage (charge ou décharge), sa charge et ses échanges, synchronisés
 * à chaque tick (l'énergie et la capacité, la génération, l'entrée et la sortie moyennes, le réglage).
 */
public class AccumulatorMenu extends MachineMenu {
    public static AccumulatorMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        buffer.readBlockPos();
        return new AccumulatorMenu(containerId, null, new SimpleContainerData(AccumulatorBlockEntity.DATA_COUNT));
    }

    public AccumulatorMenu(int containerId, BlockEntity blockEntity, ContainerData data) {
        super(ModMenus.ACCUMULATOR.get(), containerId, blockEntity, data, 0);
    }

    public AccumulatorBlock.Tier tier() {
        AccumulatorBlock.Tier[] tiers = AccumulatorBlock.Tier.values();
        return tiers[Math.clamp(data.get(4), 0, tiers.length - 1)];
    }

    /** Entrée moyenne de la dernière seconde, en FE par tick. */
    public int averageIn() {
        return data.get(MachineMenu.BASE_DATA_COUNT) & 0xFFFF;
    }

    /** Sortie moyenne de la dernière seconde, en FE par tick. */
    public int averageOut() {
        return data.get(MachineMenu.BASE_DATA_COUNT + 1) & 0xFFFF;
    }

    /** Le réglage de l'accumulateur. */
    public AccumulatorBlockEntity.Mode mode() {
        return data.get(MachineMenu.BASE_DATA_COUNT + 2) == 1 ? AccumulatorBlockEntity.Mode.DISCHARGE : AccumulatorBlockEntity.Mode.CHARGE;
    }

    /** Les boutons du réglage : 0 pour la charge, 1 pour la décharge. */
    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        if ((id == 0 || id == 1) && blockEntity() instanceof AccumulatorBlockEntity accumulator) {
            accumulator.setMode(id == 0 ? AccumulatorBlockEntity.Mode.CHARGE : AccumulatorBlockEntity.Mode.DISCHARGE);
            return true;
        }
        return super.clickMenuButton(player, id);
    }
}
