package com.orbitalis.platform.transfer;

import com.mojang.serialization.Codec;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.transfer.resource.Resource;
import com.orbitalis.platform.transfer.transaction.SnapshotJournal;
import com.orbitalis.platform.transfer.transaction.TransactionContext;

import net.minecraft.core.NonNullList;

/**
 * Une réserve en emplacements, chacun tenant une pile ({@code S}) d'une seule ressource ({@code T}). Chaque emplacement note
 * son contenu avant de changer, pour revenir en arrière si la transaction est annulée.
 *
 * @param <S> la pile (fluide ou objet)
 * @param <T> la ressource
 */
public abstract class StacksResourceHandler<S, T extends Resource> implements ResourceHandler<T> {
    protected NonNullList<S> stacks;
    protected final S emptyStack;
    private final Codec<S> stackCodec;
    private final NonNullList<Journal> journals;

    protected StacksResourceHandler(int size, S emptyStack, Codec<S> stackCodec) {
        this(NonNullList.withSize(size, emptyStack), emptyStack, stackCodec);
    }

    protected StacksResourceHandler(NonNullList<S> stacks, S emptyStack, Codec<S> stackCodec) {
        this.stacks = stacks;
        this.emptyStack = emptyStack;
        this.stackCodec = stackCodec;
        this.journals = NonNullList.create();
        for (int index = 0; index < stacks.size(); index++) {
            journals.add(new Journal(index));
        }
    }

    protected void setStacks(NonNullList<S> stacks) {
        this.stacks = stacks;
    }

    /** Écrit le contenu, emplacement par emplacement. */
    public void serialize(ValueOutput output) {
        for (int index = 0; index < stacks.size(); index++) {
            S stack = stacks.get(index);
            if (getAmountFrom(stack) > 0) {
                output.store(Integer.toString(index), stackCodec, stack);
            }
        }
    }

    /** Relit le contenu ; un emplacement absent de la sauvegarde est vide. */
    public void deserialize(ValueInput input) {
        for (int index = 0; index < stacks.size(); index++) {
            stacks.set(index, input.read(Integer.toString(index), stackCodec).orElse(emptyStack));
        }
    }

    /** Met cet emplacement à cette ressource et cette quantité, hors transaction. */
    public void set(int index, T resource, int amount) {
        S previous = stacks.get(index);
        stacks.set(index, resource.isEmpty() || amount <= 0 ? emptyStack : getStackFrom(resource, amount));
        onContentsChanged(index, previous);
    }

    protected abstract T getResourceFrom(S stack);

    protected abstract int getAmountFrom(S stack);

    protected abstract S getStackFrom(T resource, int amount);

    protected abstract S copyOf(S stack);

    protected boolean matches(S stack, T resource) {
        return getResourceFrom(stack).equals(resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return true;
    }

    protected abstract int getCapacity(int index, T resource);

    protected void onContentsChanged(int index, S previousContents) {
    }

    public NonNullList<S> copyToList() {
        NonNullList<S> copy = NonNullList.withSize(stacks.size(), emptyStack);
        for (int index = 0; index < stacks.size(); index++) {
            copy.set(index, copyOf(stacks.get(index)));
        }
        return copy;
    }

    @Override
    public int size() {
        return stacks.size();
    }

    @Override
    public T getResource(int index) {
        return getResourceFrom(stacks.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return getAmountFrom(stacks.get(index));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return getCapacity(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0 || !isValid(index, resource)) {
            return 0;
        }
        S stack = stacks.get(index);
        int current = getAmountFrom(stack);
        if (current > 0 && !matches(stack, resource)) {
            return 0;
        }
        int inserted = Math.min(amount, getCapacity(index, resource) - current);
        if (inserted <= 0) {
            return 0;
        }
        journals.get(index).updateSnapshots(transaction);
        stacks.set(index, getStackFrom(resource, current + inserted));
        return inserted;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        S stack = stacks.get(index);
        int current = getAmountFrom(stack);
        if (current <= 0 || !matches(stack, resource)) {
            return 0;
        }
        int extracted = Math.min(amount, current);
        journals.get(index).updateSnapshots(transaction);
        stacks.set(index, current - extracted <= 0 ? emptyStack : getStackFrom(resource, current - extracted));
        return extracted;
    }

    private final class Journal extends SnapshotJournal<S> {
        private final int index;

        Journal(int index) {
            this.index = index;
        }

        @Override
        protected S createSnapshot() {
            return copyOf(stacks.get(index));
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            stacks.set(index, snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            onContentsChanged(index, originalState);
        }
    }
}
