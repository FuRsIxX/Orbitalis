package com.orbitalis.platform.transfer.transaction;

/**
 * Une transaction ouverte, vue par ceux qui y prennent part : on y note son état avant de le changer, pour pouvoir revenir
 * en arrière si la transaction est annulée.
 */
public interface TransactionContext {
    /** Profondeur de la transaction : 0 pour la transaction racine. */
    int depth();

    /** Inscrit un participant, qui sera prévenu à la fermeture de la transaction. */
    void register(SnapshotJournal<?> journal);
}
