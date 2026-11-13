package com.orbitalis.platform.transfer.transaction;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Une transaction d'échange (énergie, fluides, objets) : on y essaie des mouvements, puis on les garde ({@link #commit()}) ou on
 * les annule (fermeture sans validation). Les transactions s'imbriquent ; une seule pile par fil d'exécution.
 */
public final class Transaction implements TransactionContext, AutoCloseable {
    private static final ThreadLocal<List<Transaction>> STACK = ThreadLocal.withInitial(ArrayList::new);

    private final int depth;
    private final List<SnapshotJournal<?>> journals = new ArrayList<>();
    private boolean open = true;

    private Transaction(int depth) {
        this.depth = depth;
    }

    /** Ouvre une transaction racine : aucune ne doit déjà être ouverte sur ce fil. */
    public static Transaction openRoot() {
        List<Transaction> stack = STACK.get();
        if (!stack.isEmpty()) {
            throw new IllegalStateException("Une transaction est déjà ouverte sur ce fil.");
        }
        return push(stack);
    }

    /** Ouvre une transaction dans {@code parent}, ou une racine si {@code parent} est nul. */
    public static Transaction open(@Nullable TransactionContext parent) {
        if (parent == null) {
            return openRoot();
        }
        List<Transaction> stack = STACK.get();
        if (stack.isEmpty() || stack.get(stack.size() - 1) != parent) {
            throw new IllegalStateException("La transaction parente n'est pas la dernière ouverte.");
        }
        return push(stack);
    }

    private static Transaction push(List<Transaction> stack) {
        Transaction transaction = new Transaction(stack.size());
        stack.add(transaction);
        return transaction;
    }

    /** La transaction ouverte en dernier sur ce fil, ou rien. */
    public static @Nullable TransactionContext getCurrentOpenedTransaction() {
        List<Transaction> stack = STACK.get();
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    @Override
    public int depth() {
        return depth;
    }

    @Override
    public void register(SnapshotJournal<?> journal) {
        journals.add(journal);
    }

    /** Garde ce qui a été fait dans la transaction, et la ferme. */
    public void commit() {
        close(true);
    }

    /** Ferme la transaction ; sans {@link #commit()} avant, tout ce qui s'y est fait est annulé. */
    @Override
    public void close() {
        if (open) {
            close(false);
        }
    }

    private void close(boolean keep) {
        if (!open) {
            throw new IllegalStateException("Transaction déjà fermée.");
        }
        List<Transaction> stack = STACK.get();
        if (stack.isEmpty() || stack.get(stack.size() - 1) != this) {
            throw new IllegalStateException("Seule la dernière transaction ouverte peut se fermer.");
        }
        open = false;
        stack.remove(stack.size() - 1);
        Transaction parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        // À rebours, comme une pile : le dernier participant inscrit revient le premier.
        for (int k = journals.size() - 1; k >= 0; k--) {
            journals.get(k).close(this, keep, parent);
        }
        journals.clear();
    }
}
