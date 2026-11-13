package com.orbitalis.platform.transfer;

import com.orbitalis.platform.transfer.resource.Resource;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

/**
 * Une réserve de ressources (fluides, objets) en emplacements numérotés, qui accepte des entrées et des sorties dans une
 * transaction.
 *
 * @param <T> la ressource
 */
public interface ResourceHandler<T extends Resource> {
    int size();

    T getResource(int index);

    long getAmountAsLong(int index);

    default int getAmountAsInt(int index) {
        return (int) Math.min(Integer.MAX_VALUE, getAmountAsLong(index));
    }

    long getCapacityAsLong(int index, T resource);

    default int getCapacityAsInt(int index, T resource) {
        return (int) Math.min(Integer.MAX_VALUE, getCapacityAsLong(index, resource));
    }

    boolean isValid(int index, T resource);

    /** Fait entrer au plus {@code amount} de cette ressource dans cet emplacement ; rend ce qui est entré. */
    int insert(int index, T resource, int amount, TransactionContext transaction);

    /** Fait entrer au plus {@code amount} de cette ressource, emplacement après emplacement. */
    default int insert(T resource, int amount, TransactionContext transaction) {
        int inserted = 0;
        for (int index = 0; index < size() && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    /** Fait sortir au plus {@code amount} de cette ressource de cet emplacement ; rend ce qui est sorti. */
    int extract(int index, T resource, int amount, TransactionContext transaction);

    default int extract(T resource, int amount, TransactionContext transaction) {
        int extracted = 0;
        for (int index = 0; index < size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }
}
