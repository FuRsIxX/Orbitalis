package com.orbitalis.platform.transfer.foreign;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;
import com.orbitalis.platform.transfer.transaction.SnapshotJournal;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

/**
 * Les réservoirs d'un bloc d'un autre mod, vus comme une réserve du mod (en millibuckets) : les échanges sont simulés dans la
 * transaction, puis faits à sa validation. Remplir et vider portent sur tout le bloc, quel que soit le réservoir demandé.
 */
public final class ForeignFluids extends SnapshotJournal<ForeignFluids.Pending> implements ResourceHandler<FluidResource> {
    /** Ce que le chargeur sait faire avec les réservoirs de l'autre mod. */
    public interface Backend {
        int size();

        FluidResource resource(int index);

        long amount(int index);

        long capacity(int index);

        boolean valid(int index, FluidResource resource);

        long fill(FluidResource resource, long amount, boolean simulate);

        long drain(FluidResource resource, long amount, boolean simulate);
    }

    record Pending(Map<FluidResource, Long> in, Map<FluidResource, Long> out) {
        Pending copy() {
            return new Pending(new HashMap<>(in), new HashMap<>(out));
        }
    }

    private final Backend backend;
    private Pending pending = new Pending(new HashMap<>(), new HashMap<>());

    public ForeignFluids(Backend backend) {
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
        done.in().forEach((resource, amount) -> backend.fill(resource, amount, false));
        done.out().forEach((resource, amount) -> backend.drain(resource, amount, false));
    }

    @Override
    public int size() {
        return Math.max(1, backend.size());
    }

    @Override
    public FluidResource getResource(int index) {
        return index < backend.size() ? backend.resource(index) : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index < backend.size() ? backend.amount(index) : 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return index < backend.size() ? backend.capacity(index) : 0;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index < backend.size() && backend.valid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return insert(resource, amount, transaction);
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        long already = pending.in().getOrDefault(resource, 0L);
        long accepted = Math.min(amount, backend.fill(resource, already + amount, true) - already);
        if (accepted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending.in().merge(resource, accepted, Long::sum);
        return (int) accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        long already = pending.out().getOrDefault(resource, 0L);
        long given = Math.min(amount, backend.drain(resource, already + amount, true) - already);
        if (given <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending.out().merge(resource, given, Long::sum);
        return (int) given;
    }
}
