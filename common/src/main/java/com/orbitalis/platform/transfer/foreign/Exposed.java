package com.orbitalis.platform.transfer.foreign;

import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.resource.Resource;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.transaction.Transaction;

/**
 * Les réserves du mod, offertes à un chargeur qui ne connaît que « essayer » et « faire » : chaque appel ouvre sa propre
 * transaction (imbriquée dans celle en cours s'il y en a une), validée seulement si l'on ne fait pas qu'essayer.
 */
public final class Exposed {
    private Exposed() {
    }

    private static Transaction open() {
        return Transaction.open(Transaction.getCurrentOpenedTransaction());
    }

    public static int insert(EnergyHandler handler, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.insert(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }

    public static int extract(EnergyHandler handler, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.extract(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int insert(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.insert(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int extract(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.extract(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int insert(ResourceHandler<T> handler, int index, T resource, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.insert(index, resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int extract(ResourceHandler<T> handler, int index, T resource, int amount, boolean simulate) {
        try (Transaction transaction = open()) {
            int done = handler.extract(index, resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return done;
        }
    }
}
