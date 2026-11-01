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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * La pompe à eau au travail. Posée au-dessus d'une étendue d'eau d'au moins {@link #MIN_SOURCES} blocs (une nappe de
 * 4×2), elle pompe un seau par seconde, tant qu'elle a du courant. Comme une source infinie, elle ne vide jamais
 * l'étendue. Elle envoie son eau aux tuyaux et aux machines qui la touchent, par toutes ses faces sauf le dessous.
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

    /** Envoie l'eau aux blocs voisins qui en prennent : tuyaux, machines, réservoirs, raccords du pas de tir. */
    private void deliver(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN || tank.getFluid(0).isEmpty()) {
                continue;
            }
            ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(direction), direction.getOpposite());
            if (target != null) {
                ResourceHandlerUtil.move(tank.automationView(), target, resource -> resource.getFluid() == Fluids.WATER, RATE * 2, null);
            }
        }
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
    }
}
