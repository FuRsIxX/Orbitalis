package com.orbitalis.platform.transfer;

import java.util.function.Predicate;

import com.orbitalis.platform.transfer.resource.Resource;
import com.orbitalis.platform.transfer.transaction.Transaction;
import com.orbitalis.platform.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Les mouvements d'une réserve à l'autre. */
public final class ResourceHandlerUtil {
    private ResourceHandlerUtil() {
    }

    /**
     * Fait passer au plus {@code amount} de ressources acceptées par {@code filter} de {@code from} à {@code to}, emplacement
     * par emplacement. Sans transaction donnée, le mouvement se fait dans une transaction à lui, validée à la fin.
     *
     * @return la quantité passée
     */
    public static <T extends Resource> int move(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to, Predicate<T> filter,
            int amount, @Nullable TransactionContext transaction) {
        if (from == null || to == null || amount <= 0) {
            return 0;
        }
        int moved = 0;
        try (Transaction outer = Transaction.open(transaction)) {
            for (int index = 0; index < from.size() && moved < amount; index++) {
                T resource = from.getResource(index);
                if (resource.isEmpty() || !filter.test(resource)) {
                    continue;
                }
                // Ce que la source peut donner, puis ce que la cible peut prendre, puis le mouvement réel.
                int available;
                try (Transaction probe = Transaction.open(outer)) {
                    available = from.extract(index, resource, amount - moved, probe);
                }
                if (available <= 0) {
                    continue;
                }
                int accepted;
                try (Transaction probe = Transaction.open(outer)) {
                    accepted = to.insert(resource, available, probe);
                }
                if (accepted <= 0) {
                    continue;
                }
                int extracted = from.extract(index, resource, accepted, outer);
                int inserted = to.insert(resource, extracted, outer);
                if (inserted != extracted) {
                    // Une réserve a changé d'avis entre l'essai et le mouvement : on n'en fait rien.
                    return 0;
                }
                moved += inserted;
            }
            outer.commit();
        }
        return moved;
    }
}
