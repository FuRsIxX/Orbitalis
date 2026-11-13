package com.orbitalis.platform.transfer.foreign;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.item.ItemResource;
import com.orbitalis.platform.transfer.transaction.SnapshotJournal;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

/**
 * L'inventaire d'un bloc d'un autre mod, vu comme une réserve du mod : les échanges sont simulés emplacement par emplacement
 * dans la transaction, puis faits à sa validation.
 */
public final class ForeignItems extends SnapshotJournal<ForeignItems.Pending> implements ResourceHandler<ItemResource> {
    /** Ce que le chargeur sait faire avec l'inventaire de l'autre mod. */
    public interface Backend {
        int size();

        ItemResource resource(int slot);

        int amount(int slot);

        int capacity(int slot, ItemResource resource);

        boolean valid(int slot, ItemResource resource);

        int insert(int slot, ItemResource resource, int count, boolean simulate);

        int extract(int slot, ItemResource resource, int count, boolean simulate);
    }

    /** Ce qui attend, dans un emplacement : l'objet, et combien. */
    record Move(ItemResource resource, int count) {
    }

    record Pending(Map<Integer, Move> in, Map<Integer, Move> out) {
        Pending copy() {
            return new Pending(new HashMap<>(in), new HashMap<>(out));
        }
    }

    private final Backend backend;
    private Pending pending = new Pending(new HashMap<>(), new HashMap<>());

    public ForeignItems(Backend backend) {
        this.backend = backend;
    }

    @Override
    protected Pending createSnapshot() {
        return pending.copy();
    }

    @Override
    protected void revertToSnapshot(Pending snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onRootCommit(Pending originalState) {
        Pending done = pending;
        pending = new Pending(new HashMap<>(), new HashMap<>());
        done.out().forEach((slot, move) -> backend.extract(slot, move.resource(), move.count(), false));
        done.in().forEach((slot, move) -> backend.insert(slot, move.resource(), move.count(), false));
    }

    @Override
    public int size() {
        return backend.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return backend.resource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return backend.amount(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return backend.capacity(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return backend.valid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        Move move = pending.in().get(index);
        if (move != null && !move.resource().equals(resource)) {
            return 0;
        }
        int already = move == null ? 0 : move.count();
        int accepted = Math.min(amount, backend.insert(index, resource, already + amount, true) - already);
        if (accepted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending.in().put(index, new Move(resource, already + accepted));
        return accepted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        Move move = pending.out().get(index);
        if (move != null && !move.resource().equals(resource)) {
            return 0;
        }
        int already = move == null ? 0 : move.count();
        int given = Math.min(amount, backend.extract(index, resource, already + amount, true) - already);
        if (given <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending.out().put(index, new Move(resource, already + given));
        return given;
    }
}
