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

/** Réserve d'une batterie de station, raccordée au réseau de sa station tant qu'elle est en place. */
public class StationBatteryBlockEntity extends BlockEntity {
    public static final int CAPACITY = 500_000;
    private static final int TRANSFER = 2_000;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(CAPACITY, TRANSFER, TRANSFER, this::setChanged);
    private final StationPower.Link link = new StationPower.Link();
    private StationPower.Bus bus;
    private int ticks;

    public StationBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION_BATTERY.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        StationPower.Bus current = link.bus(level, pos);
        if (current != bus) {
            if (bus != null) {
                bus.detach(this);
            }
            bus = current;
        }
        if (bus != null) {
            bus.attach(this);
        }
        if (ticks++ % 20 == 0) {
            int charge = energy.getEnergy() <= 0 ? 0 : 1 + Math.min(3, energy.getEnergy() * 4 / (CAPACITY + 1));
            if (state.getValue(StationBatteryBlock.CHARGE) != charge) {
                level.setBlock(pos, state.setValue(StationBatteryBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (bus != null) {
            bus.detach(this);
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
