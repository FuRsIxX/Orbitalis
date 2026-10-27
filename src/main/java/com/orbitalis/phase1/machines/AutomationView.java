package com.orbitalis.phase1.machines;

import java.util.function.IntPredicate;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Vue d'un inventaire ou d'un réservoir exposée aux autres blocs (entonnoirs, tuyaux) :
 * le dépôt suit les règles du contenant, mais le retrait est limité aux emplacements autorisés
 * (en pratique, les sorties de la machine).
 */
public final class AutomationView<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T> delegate;
    private final IntPredicate canExtract;

    public AutomationView(ResourceHandler<T> delegate, IntPredicate canExtract) {
        this.delegate = delegate;
        this.canExtract = canExtract;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public T getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        return delegate.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        return canExtract.test(index) ? delegate.extract(index, resource, amount, transaction) : 0;
    }
}
