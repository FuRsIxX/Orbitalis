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
                // Ce que la source peut donner (un essai), puis ce que la cible prend vraiment, puis on le retire de la source.
                // La cible n'est sollicitée qu'une fois : certaines comptent ce qu'elles reçoivent par tick (le bras d'une
                // table de lancement), et un essai leur aurait pris leur part.
                int available;
                try (Transaction probe = Transaction.open(outer)) {
                    available = from.extract(index, resource, amount - moved, probe);
                }
                if (available <= 0) {
                    continue;
                }
                try (Transaction step = Transaction.open(outer)) {
                    int inserted = to.insert(resource, available, step);
                    if (inserted <= 0) {
                        continue;
                    }
                    int extracted = from.extract(index, resource, inserted, step);
                    if (extracted != inserted) {
                        // La source a changé d'avis entre l'essai et le mouvement : on annule ce pas.
                        continue;
                    }
                    step.commit();
                    moved += inserted;
                }
            }
            outer.commit();
        }
        return moved;
    }
}
