package com.orbitalis.phase1.fuel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.ResourceHandlerUtil;
import com.orbitalis.platform.transfer.fluid.FluidResource;

/**
 * La pompe à eau au travail. Posée au-dessus d'une étendue d'eau d'au moins {@link #MIN_SOURCES} blocs (une nappe de
 * 4×2), elle pompe un seau par seconde, tant qu'elle a du courant. Comme une source infinie, elle ne vide jamais
 * l'étendue. Comme une vraie pompe, elle a deux raccords : son boîtier électrique, à l'arrière, reçoit le courant, et
 * sa bride, à l'avant, envoie l'eau au tuyau, à la machine ou au réservoir qui s'y branche.
 */
public class WaterPumpBlockEntity extends BlockEntity {
    /** Plus petite étendue d'eau dont la pompe se contente, en blocs d'eau de source qui se touchent. */
    public static final int MIN_SOURCES = 8;
    /** Débit de la pompe, en mB par tick : un seau par seconde. */
    public static final int RATE = 50;
    /** Énergie consommée en pompant, en FE par tick. */
    public static final int ENERGY_PER_TICK = 10;
    private static final int TANK_CAPACITY = 4000;
    private static final int ENERGY_CAPACITY = 10_000;
    private static final int ENERGY_TRANSFER = 400;
    /** Intervalle entre deux relevés de l'étendue d'eau, en ticks. */
    private static final int SCAN_INTERVAL = 40;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    /** L'eau pompée, en attente d'être livrée ; les autres blocs ne peuvent qu'en prendre. */
    private final MachineTanks tank = new MachineTanks(1, TANK_CAPACITY, (index, fluid) -> false, index -> true, this::setChanged);
    /** Blocs d'eau de source relevés sous la pompe (au plus quelques-uns de plus que le minimum). */
    private int sources = -1;
    private int ticks;
    private boolean pumping;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PUMP.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public MachineTanks getTank() {
        return tank;
    }

    public boolean isPumping() {
        return pumping;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % SCAN_INTERVAL == 0 || sources < 0) {
            sources = countSources(level, pos.below());
        }
        FluidStack stored = tank.getFluid(0);
        pumping = sources >= MIN_SOURCES && TANK_CAPACITY - stored.getAmount() >= RATE && energy.consume(ENERGY_PER_TICK);
        if (pumping) {
            tank.setFluid(0, new FluidStack(Fluids.WATER, stored.getAmount() + RATE));
        }
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != pumping) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, pumping), 3);
        }
        deliver(level, pos);
    }

    /** Envoie l'eau par la bride de sortie, à l'avant, au bloc qui s'y branche : tuyau, machine, réservoir. */
    private void deliver(ServerLevel level, BlockPos pos) {
        if (tank.getFluid(0).isEmpty()) {
            return;
        }
        Direction outlet = outlet();
        ResourceHandler<FluidResource> target = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Fluid.BLOCK, pos.relative(outlet), outlet.getOpposite());
        if (target != null) {
            ResourceHandlerUtil.move(tank.automationView(), target, resource -> resource.getFluid() == Fluids.WATER, RATE * 2, null);
        }
    }

    /** Face de la bride de sortie, à l'avant de la pompe. */
    public Direction outlet() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Face du boîtier électrique, à l'arrière de la pompe. */
    public Direction powerInlet() {
        return outlet().getOpposite();
    }

    /**
     * Compte les blocs d'eau de source qui se touchent sous la pompe, à partir de celui juste en dessous, sans remonter
     * au-dessus de lui. On s'arrête un peu après le minimum : il ne s'agit que de savoir s'il est atteint.
     */
    static int countSources(Level level, BlockPos start) {
        if (!isSource(level, start)) {
            return 0;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MIN_SOURCES * 2) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getY() <= start.getY() && !seen.contains(next) && isSource(level, next)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen.size();
    }

    private static boolean isSource(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getFluidState(pos).isSourceOfType(Fluids.WATER);
    }

    /** Ce qu'en dit un clic droit : ce qu'elle pompe, ou ce qui l'en empêche. */
    public Component status() {
        int stored = tank.getFluid(0).getAmount();
        if (sources < MIN_SOURCES) {
            return Component.translatable("pump.orbitalis.water_pump.no_water", MIN_SOURCES, Math.max(0, sources)).withStyle(ChatFormatting.GOLD);
        }
        if (TANK_CAPACITY - stored < RATE) {
            return Component.translatable("pump.orbitalis.water_pump.full", stored, TANK_CAPACITY).withStyle(ChatFormatting.GOLD);
        }
        if (!pumping && energy.getEnergy() < ENERGY_PER_TICK) {
            return Component.translatable("pump.orbitalis.water_pump.no_power", ENERGY_PER_TICK).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("pump.orbitalis.water_pump.pumping", RATE * 20, stored, TANK_CAPACITY).withStyle(ChatFormatting.AQUA);
    }

    /** Cassée, la machine garde son énergie et ses fluides sur son objet ; reposée, elle les retrouve. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
        tank.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components::get);
        tank.restoreFrom(components::get);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
    }
}
