package com.orbitalis.platform.transfer.foreign;

import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.transaction.SnapshotJournal;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

/**
 * La réserve d'énergie d'un bloc d'un autre mod, vue comme une réserve du mod : dans une transaction, les échanges ne sont
 * que simulés et additionnés ; ils se font pour de bon quand la transaction racine est validée.
 */
public final class ForeignEnergy extends SnapshotJournal<long[]> implements EnergyHandler {
    /** Ce que le chargeur sait faire avec la réserve de l'autre mod. */
    public interface Backend {
        long amount();

        long capacity();

        long insert(long amount, boolean simulate);

        long extract(long amount, boolean simulate);
    }

    private final Backend backend;
    private long in;
    private long out;

    public ForeignEnergy(Backend backend) {
        this.backend = backend;
    }

    @Override
    protected long[] createSnapshot() {
        return new long[] {in, out};
    }

    @Override
    protected void revertToSnapshot(long[] snapshot) {
        in = snapshot[0];
        out = snapshot[1];
    }

    @Override
    protected void onRootCommit(long[] originalState) {
        long toInsert = in;
        long toExtract = out;
        in = 0;
        out = 0;
        if (toInsert > 0) {
            backend.insert(toInsert, false);
        }
        if (toExtract > 0) {
            backend.extract(toExtract, false);
        }
    }

    @Override
    public long getAmountAsLong() {
        return Math.max(0, backend.amount() + in - out);
    }

    @Override
    public long getCapacityAsLong() {
        return backend.capacity();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        long accepted = Math.min(amount, backend.insert(in + amount, true) - in);
        if (accepted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        in += accepted;
        return (int) accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        long given = Math.min(amount, backend.extract(out + amount, true) - out);
        if (given <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        out += given;
        return (int) given;
    }
}
