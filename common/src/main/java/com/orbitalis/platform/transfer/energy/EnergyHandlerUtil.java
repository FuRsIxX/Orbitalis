package com.orbitalis.platform.transfer.energy;

import com.orbitalis.platform.transfer.transaction.Transaction;
import com.orbitalis.platform.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Les mouvements d'énergie d'une réserve à l'autre. */
public final class EnergyHandlerUtil {
    private EnergyHandlerUtil() {
    }

    /** Fait passer au plus {@code amount} FE de {@code from} à {@code to} ; rend ce qui est passé. */
    public static int move(@Nullable EnergyHandler from, @Nullable EnergyHandler to, int amount, @Nullable TransactionContext transaction) {
        if (from == null || to == null || amount <= 0) {
            return 0;
        }
        try (Transaction outer = Transaction.open(transaction)) {
            int available;
            try (Transaction probe = Transaction.open(outer)) {
                available = from.extract(amount, probe);
            }
            int accepted;
            try (Transaction probe = Transaction.open(outer)) {
                accepted = to.insert(available, probe);
            }
            if (accepted <= 0) {
                return 0;
            }
            int extracted = from.extract(accepted, outer);
            int inserted = to.insert(extracted, outer);
            if (inserted != extracted) {
                return 0;
            }
            outer.commit();
            return inserted;
        }
    }
}
