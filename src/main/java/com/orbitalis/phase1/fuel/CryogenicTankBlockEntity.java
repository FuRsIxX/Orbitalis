package com.orbitalis.phase1.fuel;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.conduit.ConduitNetwork;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.phase1.machines.MachineBlockEntity;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Réservoir cryogénique : stocke un seul fluide à la fois.
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

    /**
     * Ravitaillement du pas de tir : le réservoir envoie son contenu à la tour de service ou aux bras ombilicaux
     * qu'il touche, ou qu'un réseau de tuyaux relie à lui. Un seul tuyau jusqu'au pied de la tour suffit pour tout
     * un parc de réservoirs, quels que soient leurs ergols. Il ne se vide jamais dans une autre machine ni dans un autre réservoir :
     * le bras ne prend que ce dont la fusée a besoin.
     */
    private void feedLaunchPad(ServerLevel level, BlockPos pos) {
        FluidStack content = tank.getFluid(0);
        if (content.isEmpty()) {
            return;
        }
        Fluid fluid = content.getFluid();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            BlockState neighbourState = level.getBlockState(neighbour);
            if (isLaunchPadInlet(neighbourState)) {
                feed(level, neighbour, direction.getOpposite(), fluid);
            } else if (neighbourState.is(ModBlocks.FLUID_PIPE.get())) {
                int sent = 0;
                for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(level, neighbour, neighbourState.getBlock(),
                        Capabilities.Fluid.BLOCK)) {
                    if (isLaunchPadInlet(level.getBlockState(endpoint.pos()))) {
                        sent += feed(level, endpoint.pos(), endpoint.side(), fluid);
                    }
                }
                if (sent > 0) {
                    // Les tuyaux traversés s'illuminent, comme pour tout transfert.
                    ConduitNetwork.markActive(level, neighbour, neighbourState.getBlock(), Capabilities.Fluid.BLOCK,
                            neighbourState.getBlock() instanceof ConduitBlock conduit ? conduit.contentOf(fluid) : 0);
                }
            }
        }
    }

    /** Vrai si le bloc est une prise d'ergols du pas de tir : un bloc de ravitaillement, au pied de la tour. */
    private static boolean isLaunchPadInlet(BlockState state) {
        return state.is(ModBlocks.FUEL_PORT.get());
    }

    /** Envoie le fluide du réservoir à une prise du pas de tir. Renvoie la quantité transférée, en mB. */
    private int feed(ServerLevel level, BlockPos armPos, Direction side, Fluid fluid) {
        ResourceHandler<FluidResource> arm = level.getCapability(Capabilities.Fluid.BLOCK, armPos, side);
        if (arm == null) {
            return 0;
        }
        return ResourceHandlerUtil.move(tank.automationView(), arm, resource -> resource.getFluid() == fluid, FEED_PER_TICK, null);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CryogenicTankMenu(containerId, playerInventory, this, data);
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
