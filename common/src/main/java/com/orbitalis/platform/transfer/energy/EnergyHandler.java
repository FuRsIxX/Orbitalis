package com.orbitalis.platform.transfer.energy;

import com.orbitalis.platform.transfer.transaction.TransactionContext;

/** Une réserve d'énergie, en FE, qui accepte des entrées et des sorties dans une transaction. */
public interface EnergyHandler {
    long getAmountAsLong();

    default int getAmountAsInt() {
        return (int) Math.min(Integer.MAX_VALUE, getAmountAsLong());
    }

    long getCapacityAsLong();

    default int getCapacityAsInt() {
        return (int) Math.min(Integer.MAX_VALUE, getCapacityAsLong());
    }

    int insert(int amount, TransactionContext transaction);

    int extract(int amount, TransactionContext transaction);
}
