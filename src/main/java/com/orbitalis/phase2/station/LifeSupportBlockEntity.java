package com.orbitalis.phase2.station;

import java.util.Set;

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
 * Générateur d'air d'une station : alimenté en électricité, il maintient un air respirable dans le volume clos
 * qui l'entoure. Il relève ce volume régulièrement : une paroi cassée ou une porte ouverte sur le vide
 * se voit en deux secondes, et la pièce se dépressurise.
 */
public class LifeSupportBlockEntity extends BlockEntity {
    /** Résultat du dernier relevé. */
    public enum Status {
        /** Pas assez d'électricité. */
        NO_POWER,
        /** Le volume n'est pas clos : l'air s'échappe. */
        LEAK,
        /** Le volume est clos et pressurisé. */
        PRESSURIZED
    }

    /** Réserve : de quoi tenir une éclipse entière sans batterie. */
    private static final int ENERGY_CAPACITY = 60_000;
    private static final int ENERGY_TRANSFER = 400;
    /** Consommation, en FE par tick. */
    public static final int ENERGY_PER_TICK = 20;
    /** Économie due aux plantes de la serre, en FE par tick et par bac planté, et économie maximale. */
    public static final int PLANT_SAVING = 2;
    private static final int MAX_PLANT_SAVING = 14;
    /** Intervalle entre deux relevés du volume, en ticks. */
    private static final int SCAN_INTERVAL = 40;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    private final StationPower.Link link = new StationPower.Link();
    private Status status = Status.NO_POWER;
    private int volume;
    private int plants;
    private int ticks;

    public LifeSupportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFE_SUPPORT.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public Status getStatus() {
        return status;
    }

    /** Bacs de culture plantés dans le volume pressurisé. */
    public int getPlants() {
        return plants;
    }

    /** Consommation actuelle, en FE par tick : les plantes de la serre allègent le travail du générateur. */
    public int consumption() {
        return ENERGY_PER_TICK - Math.min(MAX_PLANT_SAVING, plants * PLANT_SAVING);
    }

    /** Volume pressurisé, en cases (une case vaut un mètre cube). */
    public int getVolume() {
        return volume;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % SCAN_INTERVAL != 0) {
            return;
        }
        // L'électricité de la période est prise d'un coup, dans sa réserve ou sur le réseau de la station : sans elle,
        // plus d'air.
        Set<Long> cells = Set.of();
        int need = consumption() * SCAN_INTERVAL;
        StationPower.Bus bus = link.bus(level, pos);
        if (!energy.consume(need) && (bus == null || !bus.draw(level, need))) {
            status = Status.NO_POWER;
        } else {
            cells = StationAir.scan(level, pos);
            status = cells.isEmpty() ? Status.LEAK : Status.PRESSURIZED;
        }
        volume = cells.size();
        plants = StationAir.plants(level, cells);
        StationAir.setRoom(level, pos, cells);
        boolean active = status == Status.PRESSURIZED;
        if (state.getValue(LifeSupportBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(LifeSupportBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    /** Bloc retiré ou déchargé : le volume qu'il pressurisait ne l'est plus. */
    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            StationAir.setRoom(level, worldPosition, Set.of());
        }
        super.setRemoved();
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
