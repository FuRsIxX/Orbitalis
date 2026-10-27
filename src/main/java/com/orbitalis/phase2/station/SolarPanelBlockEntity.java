package com.orbitalis.phase2.station;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

/**
 * Élément de panneau solaire : produit de l'électricité quand il voit le ciel. En orbite, hors de l'atmosphère et
 * sans nuit, il donne sa pleine puissance en permanence ; au sol, seulement de jour et quatre fois moins.
 * <p>
 * Les éléments raccordés en un grand panneau mettent leur électricité en commun : chacun connaît toutes les
 * prises du panneau (les machines et les câbles qui touchent l'un de ses éléments) et y envoie sa production.
 * Cette liste n'est relevée que toutes les deux secondes : le reste du temps, un élément ne fait que produire
 * et, s'il a de quoi, livrer.
 */
public class SolarPanelBlockEntity extends BlockEntity {
    private static final int ENERGY_CAPACITY = 10_000;
    private static final int ENERGY_TRANSFER = 200;
    /** Production en orbite, puis au sol en plein jour, en FE par tick. */
    public static final int ORBIT_OUTPUT = 40;
    public static final int GROUND_OUTPUT = 8;
    /** Intervalle entre deux relevés des prises du grand panneau, et de l'ensoleillement, en ticks. */
    private static final int SCAN_INTERVAL = 40;

    /** Prise du grand panneau : un bloc qui accepte de l'électricité, et la face par laquelle on la lui envoie. */
    private record Outlet(BlockPos pos, Direction side) {
    }

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, 0, ENERGY_TRANSFER, this::setChanged);
    private final List<Outlet> outlets = new ArrayList<>();
    private int output;
    private int ticks;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    private final StationPower.Link link = new StationPower.Link();

    /** Production à cet instant, en FE par tick : rien pendant l'éclipse, en orbite. */
    public int output(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        if (SpaceRules.isVacuum(level)) {
            return StationPower.sunlit(level) ? ORBIT_OUTPUT : 0;
        }
        return level.isBrightOutside() && !level.isRaining() ? GROUND_OUTPUT : 0;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % SCAN_INTERVAL == 0) {
            output = output(level, pos);
            scanOutlets(level, pos);
        }
        energy.generate(output);
        if (energy.getEnergy() <= 0) {
            return;
        }
        for (Outlet outlet : outlets) {
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, outlet.pos(), outlet.side());
            if (target != null) {
                EnergyHandlerUtil.move(energy, target, ENERGY_TRANSFER, null);
            }
        }
        // Ce que les voisins n'ont pas pris part sur le réseau de la station, vers ses batteries et ses équipements.
        StationPower.Bus bus = link.bus(level, pos);
        if (bus != null && energy.getEnergy() > 0) {
            int sent = bus.offer(level, Math.min(energy.getEnergy(), ENERGY_TRANSFER));
            energy.consume(sent);
        }
    }

    /** Relève les prises du grand panneau : tout ce qui touche l'un de ses éléments et accepte de l'électricité. */
    private void scanOutlets(ServerLevel level, BlockPos pos) {
        outlets.clear();
        for (BlockPos element : SolarPanelBlock.array(level, pos)) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = element.relative(direction);
                // Un autre élément de panneau n'est pas une prise : il produit, il ne consomme pas.
                if (level.getBlockState(neighbour).getBlock() instanceof SolarPanelBlock) {
                    continue;
                }
                if (level.getCapability(Capabilities.Energy.BLOCK, neighbour, direction.getOpposite()) != null) {
                    outlets.add(new Outlet(neighbour, direction.getOpposite()));
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }
}
