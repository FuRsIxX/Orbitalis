package com.orbitalis.phase2.station;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Bac de culture. Toutes les secondes, si une culture y est plantée et n'est pas encore mûre, il alimente ses
 * diodes (sa propre réserve, ou le réseau de la station) et la culture avance ; tous les {@link #STAGE_TICKS} ticks,
 * elle gagne un stade.
 */
public class HydroponicTrayBlockEntity extends BlockEntity {
    /** Consommation des diodes, en FE par tick. */
    public static final int ENERGY_PER_TICK = 8;
    /** Durée d'un stade de croissance, en ticks : quarante-cinq secondes. */
    public static final int STAGE_TICKS = 900;
    private static final int STEP = 20;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(8_000, 200, 0, this::setChanged);
    private final StationPower.Link link = new StationPower.Link();
    private int progress;
    private boolean powered;
    private int ticks;

    public HydroponicTrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYDROPONIC_TRAY.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    /** Vrai si les diodes étaient sous tension à la dernière seconde. */
    public boolean powered() {
        return powered;
    }

    /** Avancement de la culture vers la maturité, en pour cent. */
    public int growthPercent(BlockState state) {
        int age = state.getValue(HydroponicTrayBlock.AGE);
        return Math.min(100, (age * STAGE_TICKS + progress) * 100 / (HydroponicTrayBlock.MAX_AGE * STAGE_TICKS));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % STEP != 0) {
            return;
        }
        if (state.getValue(HydroponicTrayBlock.CROP) == HydroponicTrayBlock.Crop.NONE || state.getValue(HydroponicTrayBlock.AGE) >= HydroponicTrayBlock.MAX_AGE) {
            powered = true;
            return;
        }
        StationPower.Bus bus = link.bus(level, pos);
        int need = ENERGY_PER_TICK * STEP;
        powered = energy.consume(need) || (bus != null && bus.draw(level, need));
        if (!powered) {
            return;
        }
        progress += STEP;
        if (progress >= STAGE_TICKS) {
            progress = 0;
            level.setBlock(pos, state.setValue(HydroponicTrayBlock.AGE, state.getValue(HydroponicTrayBlock.AGE) + 1), Block.UPDATE_ALL);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
    }
}
