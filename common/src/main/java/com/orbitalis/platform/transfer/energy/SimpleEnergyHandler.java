package com.orbitalis.platform.transfer.energy;

import com.orbitalis.platform.transfer.transaction.SnapshotJournal;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

/** Une réserve d'énergie simple : une capacité, et des débits d'entrée et de sortie par appel. */
public class SimpleEnergyHandler implements EnergyHandler {
    protected int energy;
    protected int capacity;
    protected int maxInsert;
    protected int maxExtract;
    private final Journal journal = new Journal();

    public SimpleEnergyHandler(int capacity) {
        this(capacity, capacity, capacity);
    }

    public SimpleEnergyHandler(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract) {
        this(capacity, maxInsert, maxExtract, 0);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract, int energy) {
        this.capacity = Math.max(0, capacity);
        this.maxInsert = Math.max(0, maxInsert);
        this.maxExtract = Math.max(0, maxExtract);
        this.energy = Math.max(0, energy);
    }

    public void serialize(com.orbitalis.platform.nbt.ValueOutput output) {
        output.putInt("energy", energy);
    }

    public void deserialize(com.orbitalis.platform.nbt.ValueInput input) {
        energy = Math.max(0, input.getIntOr("energy", 0));
    }

    /** Met la réserve à cette quantité, hors transaction. */
    public void set(int amount) {
        int clamped = Math.max(0, amount);
        if (clamped != energy) {
            int previous = energy;
            energy = clamped;
            onEnergyChanged(previous);
        }
    }

    protected void onEnergyChanged(int previousAmount) {
    }

    @Override
    public long getAmountAsLong() {
        return energy;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int inserted = Math.min(capacity - energy, Math.min(Math.max(0, amount), maxInsert));
        if (inserted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        energy += inserted;
        return inserted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int extracted = Math.min(energy, Math.min(Math.max(0, amount), maxExtract));
        if (extracted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        energy -= extracted;
        return extracted;
    }

    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (energy != originalState) {
                onEnergyChanged(originalState);
            }
        }
    }
}
