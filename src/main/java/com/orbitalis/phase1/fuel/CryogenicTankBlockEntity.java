package com.orbitalis.phase1.fuel;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModDataComponents;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.machines.MachineBlockEntity;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Réservoir cryogénique : stocke un seul fluide à la fois. Cassé, il garde son contenu.
 * <p>
 * Les ergols cryogéniques (oxygène et hydrogène liquides) doivent être maintenus au froid : alimenté en
 * énergie, le réservoir les conserve sans perte ; sinon ils s'évaporent peu à peu. Les fluides stockables
 * à température ambiante (RP-1, eau) ne demandent aucune énergie.
 * <p>
 * Relié à un bras ombilical, directement ou par des tuyaux, il ravitaille la fusée du pas de tir.
 */
public class CryogenicTankBlockEntity extends MachineBlockEntity {
    /** Capacité du réservoir, en mB. */
    public static final int CAPACITY = 32_000;
    /** Énergie consommée par tick pour maintenir un ergol cryogénique au froid, en FE. */
    public static final int COOLING_PER_TICK = 10;
    /** Sans refroidissement, 1 mB s'évapore tous les 4 ticks, soit 5 mB par seconde. */
    private static final int EVAPORATION_INTERVAL = 4;
    /** Débit de ravitaillement d'une fusée, en mB par tick. */
    private static final int FEED_PER_TICK = 100;

    /** État du stockage, affiché dans l'interface. */
    public static final int STATE_EMPTY = 0;
    public static final int STATE_STABLE = 1;
    public static final int STATE_COOLING = 2;
    public static final int STATE_EVAPORATING = 3;

    private final MachineTanks tank;
    private int storageState = STATE_EMPTY;
    private int evaporationTimer;

    /** Valeurs synchronisées vers l'interface : données communes, puis fluide, quantité et état. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index - MachineMenu.BASE_DATA_COUNT) {
                case 0 -> BuiltInRegistries.FLUID.getId(tank.getFluid(0).getFluid());
                case 1 -> tank.getFluid(0).getAmount();
                case 2 -> storageState;
                default -> baseData(index);
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return CryogenicTankMenu.DATA_COUNT;
        }
    };

    public CryogenicTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYOGENIC_TANK.get(), pos, state);
        // Tout fluide est accepté, et l'automatisation peut le reprendre.
        this.tank = new MachineTanks(1, CAPACITY, (index, fluid) -> true, index -> true, this::setChanged);
    }

    @Override
    protected MachineEnergyStorage createEnergy() {
        return new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    }

    @Override
    protected MachineInventory createInventory() {
        // Le réservoir ne contient aucun objet.
        return new MachineInventory(1, (slot, stack) -> false, slot -> false, this::setChanged);
    }

    public MachineTanks getTank() {
        return tank;
    }

    /** Un seau peut être vidé ou rempli à la main sur le réservoir. */
    @Override
    public ResourceHandler<FluidResource> getBucketFluidHandler() {
        return tank;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        FluidStack content = tank.getFluid(0);
        boolean cooling = false;

        if (content.isEmpty()) {
            storageState = STATE_EMPTY;
        } else if (!ModFluids.boilsOff(content.getFluid(), level)) {
            storageState = STATE_STABLE;
        } else if (energy.consume(COOLING_PER_TICK)) {
            storageState = STATE_COOLING;
            cooling = true;
        } else {
            storageState = STATE_EVAPORATING;
            evaporationTimer++;
            if (evaporationTimer >= EVAPORATION_INTERVAL) {
                evaporationTimer = 0;
                int remaining = content.getAmount() - 1;
                tank.setFluid(0, remaining <= 0 ? FluidStack.EMPTY : new FluidStack(content.getFluid(), remaining));
            }
        }
        setLit(cooling);
        feedLaunchPad(level, pos);
    }

    /** Ravitaillement du pas de tir, directement ou par des tuyaux (voir {@link PadFeed}). */
    private void feedLaunchPad(ServerLevel level, BlockPos pos) {
        FluidStack content = tank.getFluid(0);
        if (!content.isEmpty()) {
            PadFeed.feed(level, pos, tank.automationView(), content.getFluid(), FEED_PER_TICK);
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CryogenicTankMenu(containerId, playerInventory, this, data);
    }

    /** Cassé, le réservoir garde son contenu : l'objet qui tombe l'emporte. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        FluidStack content = tank.getFluid(0);
        if (!content.isEmpty()) {
            components.set(ModDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(content));
        }
    }

    /** Reposé, il retrouve ce contenu. */
    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent content = components.get(ModDataComponents.FLUID_CONTENT.get());
        if (content != null && !content.isEmpty()) {
            tank.setFluid(0, content.copy());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
    }
}
