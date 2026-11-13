package com.orbitalis.platform.transfer.transaction;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Un participant aux transactions : avant chaque changement, il note son état (une fois par niveau de transaction), pour
 * pouvoir y revenir si la transaction est annulée. Quand la transaction racine est validée, il est prévenu
 * ({@link #onRootCommit}) avec son état d'avant.
 *
 * @param <T> la forme de l'état noté
 */
public abstract class SnapshotJournal<T> {
    /** L'état noté à chaque niveau de transaction ; {@code null} : rien de noté à ce niveau. */
    private final List<@Nullable T> snapshots = new ArrayList<>();

    protected abstract T createSnapshot();

    protected abstract void revertToSnapshot(T snapshot);

    protected void releaseSnapshot(T snapshot) {
    }

    protected void onRootCommit(T originalState) {
    }

    /** Vrai si un état a été noté dans une transaction encore ouverte. */
    public final boolean isInTransaction() {
        for (T snapshot : snapshots) {
            if (snapshot != null) {
                return true;
            }
        }
        return false;
    }

    /** À appeler avant chaque changement : note l'état, s'il ne l'est pas encore à ce niveau de transaction. */
    public void updateSnapshots(TransactionContext transaction) {
        int depth = transaction.depth();
        while (snapshots.size() <= depth) {
            snapshots.add(null);
        }
        if (snapshots.get(depth) == null) {
            snapshots.set(depth, createSnapshot());
            transaction.register(this);
        }
    }

    final void close(TransactionContext transaction, boolean keep, @Nullable TransactionContext parent) {
        int depth = transaction.depth();
        T snapshot = snapshots.get(depth);
        snapshots.set(depth, null);
        if (snapshot == null) {
            return;
        }
        if (!keep) {
            revertToSnapshot(snapshot);
            releaseSnapshot(snapshot);
        } else if (parent != null) {
            // Validée dans une transaction parente : l'état d'avant remonte d'un niveau, s'il n'y en a pas déjà un.
            int up = parent.depth();
            if (snapshots.get(up) == null) {
                snapshots.set(up, snapshot);
                parent.register(this);
            } else {
                releaseSnapshot(snapshot);
            }
        } else {
            onRootCommit(snapshot);
            releaseSnapshot(snapshot);
        }
    }
}
