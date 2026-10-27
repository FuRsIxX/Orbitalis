package com.orbitalis.phase1.machines;

import com.orbitalis.core.energy.MachineEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Base de toutes les machines : une réserve d'énergie, un inventaire, la sauvegarde
 * et l'ouverture de l'interface. Chaque machine définit son comportement dans {@link #serverTick}.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    /** Capacité par défaut de la réserve d'énergie d'une machine, en FE. */
    public static final int ENERGY_CAPACITY = 20_000;
    /** Débit maximal d'énergie reçu ou fourni par une machine, en FE par tick : de quoi nourrir la plus gourmande (400 FE/t). */
    public static final int ENERGY_TRANSFER = 400;

    protected final MachineEnergyStorage energy;
    protected final MachineInventory inventory;
    /** Vrai pendant les ticks où la machine travaille réellement. */
    private boolean working;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.energy = createEnergy();
        this.inventory = createInventory();
    }

    protected abstract MachineEnergyStorage createEnergy();

    protected abstract MachineInventory createInventory();

    /** Logique de la machine, exécutée une fois par tick côté serveur uniquement. */
    public abstract void serverTick(ServerLevel level, BlockPos pos, BlockState state);

    /** Données supplémentaires envoyées au client à l'ouverture de l'interface. */
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public MachineInventory getInventory() {
        return inventory;
    }

    /**
     * Réservoir dans lequel un joueur peut vider ou remplir un seau en cliquant sur le bloc ;
     * {@code null} si la machine ne contient pas de fluide.
     */
    public ResourceHandler<FluidResource> getBucketFluidHandler() {
        return null;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /**
     * Valeurs communes à toutes les machines, synchronisées vers l'interface du joueur.
     * Chaque valeur tient sur 16 bits : l'énergie et la capacité sont donc découpées en deux moitiés.
     */
    protected int baseData(int index) {
        return switch (index) {
            case 0 -> energy.getEnergy() & 0xFFFF;
            case 1 -> energy.getEnergy() >>> 16;
            case 2 -> energy.getCapacity() & 0xFFFF;
            case 3 -> energy.getCapacity() >>> 16;
            case 4 -> working ? 1 : 0;
            default -> 0;
        };
    }

    /** Indique si la machine travaille : allume ou éteint sa face avant et sa lumière. */
    protected void setLit(boolean lit) {
        working = lit;
        BlockState state = getBlockState();
        if (level != null && state.getValue(MachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.LIT, lit), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        inventory.serialize(output.child("inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        inventory.deserialize(input.childOrEmpty("inventory"));
    }

    /** Quand le bloc est cassé, son contenu tombe au sol. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, inventory.copyToList());
        }
    }
}
