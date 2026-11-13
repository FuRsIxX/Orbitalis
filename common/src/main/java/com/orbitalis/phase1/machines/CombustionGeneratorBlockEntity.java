package com.orbitalis.phase1.machines;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandlerUtil;

/**
 * Générateur à combustion : brûle les combustibles de four (charbon, bois...) pour produire de l'énergie,
 * puis la distribue aux machines qui le touchent.
 */
public class CombustionGeneratorBlockEntity extends MachineBlockEntity {
    /** Énergie produite par tick de combustion, en FE. Un charbon (1600 ticks) donne donc 32 000 FE. */
    public static final int ENERGY_PER_TICK = 20;
    private static final int FUEL_SLOT = 0;

    /** Ticks de combustion restants pour le combustible en cours. */
    private int burnTime;
    /** Durée totale de combustion du combustible en cours (pour l'affichage de la flamme). */
    private int burnDuration;

    /** Valeurs synchronisées vers l'interface du joueur : données communes, puis état de la combustion. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index - MachineMenu.BASE_DATA_COUNT) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                default -> baseData(index);
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return CombustionGeneratorMenu.DATA_COUNT;
        }
    };

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMBUSTION_GENERATOR.get(), pos, state);
    }

    @Override
    protected MachineEnergyStorage createEnergy() {
        // Le générateur fournit de l'énergie mais n'en reçoit pas.
        return new MachineEnergyStorage(ENERGY_CAPACITY, 0, ENERGY_TRANSFER, this::setChanged);
    }

    @Override
    protected MachineInventory createInventory() {
        // Un seul emplacement, réservé aux combustibles. L'automatisation n'en retire que les récipients vides (seau).
        return new MachineInventory(1,
                (slot, stack) -> isFuel(stack),
                slot -> !isFuel(getInventory().getStack(slot)),
                this::setChanged);
    }

    private boolean isFuel(ItemStack stack) {
        return level != null && dev.architectury.registry.fuel.FuelRegistry.get(stack) > 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean burning = false;

        // La combustion se met en pause quand la réserve est pleine : aucun combustible n'est gaspillé.
        if (!energy.isFull()) {
            if (burnTime <= 0) {
                startBurning(level);
            }
            if (burnTime > 0) {
                burnTime--;
                energy.generate(ENERGY_PER_TICK);
                burning = true;
                setChanged();
            }
        }
        setLit(burning);

        distributeEnergy(level, pos);
    }

    /** Consomme un combustible et lance sa combustion. */
    private void startBurning(ServerLevel level) {
        ItemStack fuel = inventory.getStack(FUEL_SLOT);
        int duration = dev.architectury.registry.fuel.FuelRegistry.get(fuel);
        if (fuel.isEmpty() || duration <= 0) {
            return;
        }
        // Un seau de lave laisse un seau vide : on lit ce reste avant de consommer le combustible.
        net.minecraft.world.item.Item remainder = fuel.getItem().getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && remainder != null) {
            fuel = new ItemStack(remainder);
        }
        inventory.setStack(FUEL_SLOT, fuel);
        // Les durées sont envoyées à l'interface sur 16 bits : on les plafonne en conséquence.
        burnTime = Math.min(duration, Short.MAX_VALUE);
        burnDuration = burnTime;
    }

    /**
     * Envoie l'énergie aux blocs voisins qui en acceptent, en la partageant équitablement :
     * sans ce partage, la première machine servie remplirait toute sa réserve avant que les autres reçoivent quoi que ce soit.
     */
    private void distributeEnergy(ServerLevel level, BlockPos pos) {
        if (energy.getEnergy() <= 0) {
            return;
        }
        List<EnergyHandler> neighbors = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            EnergyHandler neighbor = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Energy.BLOCK, pos.relative(direction), direction.getOpposite());
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        // Chaque voisin reçoit sa part de ce qui reste ; ce qu'un voisin plein refuse revient aux suivants.
        for (int i = 0; i < neighbors.size(); i++) {
            int remainingNeighbors = neighbors.size() - i;
            int share = Mth.positiveCeilDiv(energy.getEnergy(), remainingNeighbors);
            EnergyHandlerUtil.move(energy, neighbors.get(i), Math.min(share, ENERGY_TRANSFER), null);
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CombustionGeneratorMenu(containerId, playerInventory, this, inventory, data);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        output.putInt("burn_time", burnTime);
        output.putInt("burn_duration", burnDuration);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        burnTime = input.getIntOr("burn_time", 0);
        burnDuration = input.getIntOr("burn_duration", 0);
    }
}
