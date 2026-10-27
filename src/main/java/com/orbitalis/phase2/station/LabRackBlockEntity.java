package com.orbitalis.phase2.station;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.survival.SpaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Baie d'expériences. Toutes les secondes, elle vérifie qu'elle est en orbite, que l'air de la pièce est respirable
 * devant elle et qu'elle a de quoi s'alimenter (sa propre réserve, remplie par câble, ou le réseau de la station) ;
 * alors son expérience avance. Une expérience menée à bien rapporte {@link #POINTS_PER_EXPERIMENT} point de
 * recherche à l'agence qui possède la station.
 */
public class LabRackBlockEntity extends BlockEntity {
    /** Ce que fait la baie. */
    public enum Status {
        /** Pas en orbite : l'apesanteur manque. */
        NOT_IN_ORBIT,
        /** La pièce n'est pas pressurisée. */
        NO_AIR,
        /** Pas assez d'électricité. */
        NO_POWER,
        /** Une expérience est en cours. */
        RUNNING
    }

    /** Consommation, en FE par tick. */
    public static final int ENERGY_PER_TICK = 40;
    /** Durée d'une expérience, en ticks : une orbite, cinq minutes. */
    public static final int EXPERIMENT_TICKS = StationPower.ORBIT_PERIOD;
    public static final int POINTS_PER_EXPERIMENT = 1;
    /** Prime de la première expérience menée à bien par une agence. */
    private static final int REWARD_FIRST_EXPERIMENT = 50;
    private static final int STEP = 20;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(20_000, 400, 0, this::setChanged);
    private final StationPower.Link link = new StationPower.Link();
    private Status status = Status.NOT_IN_ORBIT;
    private int progress;
    private int experiments;
    private int ticks;

    public LabRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB_RACK.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public Status getStatus() {
        return status;
    }

    /** Expériences menées à bien depuis la pose de la baie. */
    public int getExperiments() {
        return experiments;
    }

    public int progressPercent() {
        return progress * 100 / EXPERIMENT_TICKS;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % STEP != 0) {
            return;
        }
        BlockPos front = pos.relative(state.getValue(LabRackBlock.FACING));
        StationPower.Bus bus = link.bus(level, pos);
        int need = ENERGY_PER_TICK * STEP;
        if (!SpaceRules.isOrbit(level)) {
            status = Status.NOT_IN_ORBIT;
        } else if (!StationAir.isBreathable(level, front)) {
            status = Status.NO_AIR;
        } else if (!energy.consume(need) && (bus == null || !bus.draw(level, need))) {
            status = Status.NO_POWER;
        } else {
            status = Status.RUNNING;
            progress += STEP;
            if (progress >= EXPERIMENT_TICKS) {
                progress = 0;
                experiments++;
                credit(level, link.station(level, pos));
            }
            setChanged();
        }
        boolean lit = status == Status.RUNNING;
        if (state.getValue(LabRackBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LabRackBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** L'expérience est finie : ses résultats vont à l'agence de la station. */
    private static void credit(ServerLevel level, BlockPos station) {
        String agency = station == null ? null : StationManager.agencyOf(level.getServer(), station);
        if (agency == null) {
            return;
        }
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        research.addPoints(POINTS_PER_EXPERIMENT);
        if (research.completeMilestone("first_experiment")) {
            research.addPoints(REWARD_FIRST_EXPERIMENT);
        }
        ResearchManager.changed(level.getServer(), agency);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
        output.putInt("experiments", experiments);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
        experiments = input.getIntOr("experiments", 0);
    }
}
