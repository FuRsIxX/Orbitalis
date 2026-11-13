package com.orbitalis.platform.capability.fabric;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orbitalis.Orbitalis;
import com.orbitalis.platform.capability.BlockCapability;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;
import com.orbitalis.platform.transfer.foreign.ForeignEnergy;
import com.orbitalis.platform.transfer.foreign.ForeignFluids;
import com.orbitalis.platform.transfer.foreign.ForeignItems;
import com.orbitalis.platform.transfer.item.ItemResource;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import team.reborn.energy.api.EnergyStorage;

/**
 * Sous Fabric : l'énergie (Team Reborn Energy), les fluides et les objets (API de transfert de Fabric) entre les blocs du mod
 * et ceux des autres mods. Les fluides du mod se comptent en millibuckets, ceux de Fabric en gouttes : 81 par millibucket.
 */
public final class FabricTransfer {
    private static final long DROPLETS = 81;

    private FabricTransfer() {
    }

    // ------------------------------------------------------------------
    // Les transactions de Fabric, doublées par celles du mod
    // ------------------------------------------------------------------

    /** Les transactions du mod ouvertes en face de celles de Fabric, sur ce fil. */
    private static final ThreadLocal<Map<TransactionContext, com.orbitalis.platform.transfer.transaction.Transaction>> MIRRORS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * La transaction du mod qui suit cette transaction de Fabric : ouverte au besoin, niveau par niveau, et validée ou annulée
     * en même temps qu'elle.
     */
    static com.orbitalis.platform.transfer.transaction.TransactionContext ours(TransactionContext fabric) {
        Map<TransactionContext, com.orbitalis.platform.transfer.transaction.Transaction> mirrors = MIRRORS.get();
        com.orbitalis.platform.transfer.transaction.Transaction current = null;
        for (int depth = 0; depth <= fabric.nestingDepth(); depth++) {
            Transaction level = fabric.getOpenTransaction(depth);
            com.orbitalis.platform.transfer.transaction.Transaction mirror = mirrors.get(level);
            if (mirror == null) {
                com.orbitalis.platform.transfer.transaction.TransactionContext parent = current != null ? current
                        : com.orbitalis.platform.transfer.transaction.Transaction.getCurrentOpenedTransaction();
                com.orbitalis.platform.transfer.transaction.Transaction opened =
                        com.orbitalis.platform.transfer.transaction.Transaction.open(parent);
                mirrors.put(level, opened);
                if (depth == 0) {
                    // La transaction racine du mod se ferme après celle de Fabric : ce qu'elle déclenche alors (un échange
                    // avec un autre mod) peut ouvrir de nouvelles transactions de Fabric.
                    level.addCloseCallback((transaction, result) -> MIRRORS.get().remove(level));
                    level.addOuterCloseCallback(result -> {
                        if (result.wasCommitted()) {
                            opened.commit();
                        } else {
                            opened.close();
                        }
                    });
                } else {
                    level.addCloseCallback((transaction, result) -> {
                        MIRRORS.get().remove(level);
                        if (result.wasCommitted()) {
                            opened.commit();
                        } else {
                            opened.close();
                        }
                    });
                }
                mirror = opened;
            }
            current = mirror;
        }
        return current;
    }

    /** Une transaction de Fabric à nous, pour simuler ou faire un échange avec un autre mod. */
    private static Transaction open() {
        return Transaction.isOpen() ? Transaction.openNested(Transaction.getCurrentUnsafe()) : Transaction.openOuter();
    }

    // ------------------------------------------------------------------
    // Les blocs des autres mods, vus par le mod
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static <T, C> @Nullable T foreign(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        Direction side = (Direction) context;
        if (capability == Capabilities.Energy.BLOCK) {
            EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, state, blockEntity, side);
            return storage == null || storage instanceof ExposedEnergy ? null : (T) foreignEnergy(storage);
        }
        if (capability == Capabilities.Fluid.BLOCK) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, state, blockEntity, side);
            return storage == null || storage instanceof ExposedFluids ? null : (T) foreignFluids(storage);
        }
        if (capability == Capabilities.Item.BLOCK) {
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, state, blockEntity, side);
            return storage == null || storage instanceof ExposedItems ? null : (T) foreignItems(storage);
        }
        return null;
    }

    private static EnergyHandler foreignEnergy(EnergyStorage storage) {
        return new ForeignEnergy(new ForeignEnergy.Backend() {
            @Override
            public long amount() {
                return storage.getAmount();
            }

            @Override
            public long capacity() {
                return storage.getCapacity();
            }

            @Override
            public long insert(long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long done = storage.insert(amount, transaction);
                    if (!simulate) {
                        transaction.commit();
                    }
                    return done;
                }
            }

            @Override
            public long extract(long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long done = storage.extract(amount, transaction);
                    if (!simulate) {
                        transaction.commit();
                    }
                    return done;
                }
            }
        });
    }

    private static FluidVariant variant(FluidResource resource) {
        return resource.isEmpty() ? FluidVariant.blank() : FluidVariant.of(resource.getFluid(), resource.getComponentsPatch());
    }

    private static FluidResource resource(FluidVariant variant) {
        return variant.isBlank() ? FluidResource.EMPTY : FluidResource.of(variant.getFluid(), variant.getComponents());
    }

    private static ItemVariant variant(ItemResource resource) {
        return resource.isEmpty() ? ItemVariant.blank() : ItemVariant.of(resource.toStack());
    }

    private static ItemResource resource(ItemVariant variant) {
        return variant.isBlank() ? ItemResource.EMPTY : ItemResource.of(variant.toStack());
    }

    private static <V> List<StorageView<V>> views(Storage<V> storage) {
        List<StorageView<V>> views = new ArrayList<>();
        for (StorageView<V> view : storage) {
            views.add(view);
        }
        return views;
    }

    private static ResourceHandler<FluidResource> foreignFluids(Storage<FluidVariant> storage) {
        List<StorageView<FluidVariant>> views = views(storage);
        return new ForeignFluids(new ForeignFluids.Backend() {
            @Override
            public int size() {
                return views.size();
            }

            @Override
            public FluidResource resource(int index) {
                StorageView<FluidVariant> view = views.get(index);
                return view.isResourceBlank() ? FluidResource.EMPTY : FabricTransfer.resource(view.getResource());
            }

            @Override
            public long amount(int index) {
                return views.get(index).getAmount() / DROPLETS;
            }

            @Override
            public long capacity(int index) {
                return views.get(index).getCapacity() / DROPLETS;
            }

            @Override
            public boolean valid(int index, FluidResource resource) {
                return storage.supportsInsertion();
            }

            @Override
            public long fill(FluidResource resource, long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long done = storage.insert(variant(resource), amount * DROPLETS, transaction) / DROPLETS;
                    if (!simulate) {
                        transaction.commit();
                    }
                    return done;
                }
            }

            @Override
            public long drain(FluidResource resource, long amount, boolean simulate) {
                try (Transaction transaction = open()) {
                    long done = storage.extract(variant(resource), amount * DROPLETS, transaction) / DROPLETS;
                    if (!simulate) {
                        transaction.commit();
                    }
                    return done;
                }
            }
        });
    }

    private static ResourceHandler<ItemResource> foreignItems(Storage<ItemVariant> storage) {
        SlottedStorage<ItemVariant> slotted = storage instanceof SlottedStorage<ItemVariant> s ? s : null;
        List<StorageView<ItemVariant>> views = slotted == null ? views(storage) : List.of();
        return new ForeignItems(new ForeignItems.Backend() {
            private StorageView<ItemVariant> view(int slot) {
                return slotted != null ? slotted.getSlot(slot) : views.get(slot);
            }

            @Override
            public int size() {
                return slotted != null ? slotted.getSlotCount() : Math.max(1, views.size());
            }

            @Override
            public ItemResource resource(int slot) {
                if (slotted == null && slot >= views.size()) {
                    return ItemResource.EMPTY;
                }
                StorageView<ItemVariant> view = view(slot);
                return view.isResourceBlank() ? ItemResource.EMPTY : FabricTransfer.resource(view.getResource());
            }

            @Override
            public int amount(int slot) {
                return slotted == null && slot >= views.size() ? 0 : (int) Math.min(Integer.MAX_VALUE, view(slot).getAmount());
            }

            @Override
            public int capacity(int slot, ItemResource resource) {
                return slotted == null && slot >= views.size() ? 64 : (int) Math.min(Integer.MAX_VALUE, view(slot).getCapacity());
            }

            @Override
            public boolean valid(int slot, ItemResource resource) {
                return storage.supportsInsertion();
            }

            @Override
            public int insert(int slot, ItemResource resource, int count, boolean simulate) {
                try (Transaction transaction = open()) {
                    long done;
                    if (slotted != null) {
                        done = slotted.getSlot(slot).insert(variant(resource), count, transaction);
                    } else {
                        // Sans emplacements numérotés, on remplit le bloc entier, par le premier.
                        done = slot == 0 ? storage.insert(variant(resource), count, transaction) : 0;
                    }
                    if (!simulate) {
                        transaction.commit();
                    }
                    return (int) done;
                }
            }

            @Override
            public int extract(int slot, ItemResource resource, int count, boolean simulate) {
                if (slotted == null && slot >= views.size()) {
                    return 0;
                }
                try (Transaction transaction = open()) {
                    long done = view(slot).extract(variant(resource), count, transaction);
                    if (!simulate) {
                        transaction.commit();
                    }
                    return (int) done;
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Les blocs du mod, offerts aux autres mods
    // ------------------------------------------------------------------

    /** Inscrit, pour Fabric, tout ce que les blocs du mod offrent. */
    public static void expose() {
        Orbitalis.registerCapabilities();
        expose(Capabilities.Energy.BLOCK, EnergyStorage.SIDED, FabricTransfer::energy);
        expose(Capabilities.Fluid.BLOCK, FluidStorage.SIDED, FabricTransfer::fluids);
        expose(Capabilities.Item.BLOCK, ItemStorage.SIDED, FabricTransfer::items);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <O, F> void expose(BlockCapability<O, Direction> capability, BlockApiLookup<F, @Nullable Direction> lookup,
            java.util.function.Function<O, F> wrap) {
        for (Map.Entry<BlockEntityType<?>, BlockCapability.EntityProvider<O, Direction, ?>> entry : capability.entityProviders().entrySet()) {
            BlockCapability.EntityProvider provider = entry.getValue();
            lookup.registerForBlockEntity((blockEntity, side) -> {
                O own = (O) provider.get(blockEntity, side);
                return own == null ? null : wrap.apply(own);
            }, (BlockEntityType) entry.getKey());
        }
        for (Map.Entry<Block, BlockCapability.BlockProvider<O, Direction>> entry : capability.blockProviders().entrySet()) {
            BlockCapability.BlockProvider<O, Direction> provider = entry.getValue();
            lookup.registerForBlocks((level, pos, state, blockEntity, side) -> {
                O own = provider.get(level, pos, state, blockEntity, side);
                return own == null ? null : wrap.apply(own);
            }, entry.getKey());
        }
    }

    private static EnergyStorage energy(EnergyHandler handler) {
        return new ExposedEnergy(handler);
    }

    private static Storage<FluidVariant> fluids(ResourceHandler<FluidResource> handler) {
        return new ExposedFluids(handler);
    }

    private static Storage<ItemVariant> items(ResourceHandler<ItemResource> handler) {
        return new ExposedItems(handler);
    }

    record ExposedEnergy(EnergyHandler handler) implements EnergyStorage {
        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            return handler.insert((int) Math.min(Integer.MAX_VALUE, maxAmount), ours(transaction));
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            return handler.extract((int) Math.min(Integer.MAX_VALUE, maxAmount), ours(transaction));
        }

        @Override
        public long getAmount() {
            return handler.getAmountAsLong();
        }

        @Override
        public long getCapacity() {
            return handler.getCapacityAsLong();
        }
    }

    /** Les réservoirs du mod, emplacement par emplacement, pour Fabric. */
    record ExposedFluids(ResourceHandler<FluidResource> handler) implements SlottedStorage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS);
            return amount <= 0 || resource.isBlank() ? 0 : handler.insert(resource(resource), amount, ours(transaction)) * DROPLETS;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS);
            return amount <= 0 || resource.isBlank() ? 0 : handler.extract(resource(resource), amount, ours(transaction)) * DROPLETS;
        }

        @Override
        public int getSlotCount() {
            return handler.size();
        }

        @Override
        public SingleSlotStorage<FluidVariant> getSlot(int slot) {
            return new FluidSlot(handler, slot);
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            List<StorageView<FluidVariant>> slots = new ArrayList<>();
            for (int slot = 0; slot < handler.size(); slot++) {
                slots.add(new FluidSlot(handler, slot));
            }
            return slots.iterator();
        }
    }

    record FluidSlot(ResourceHandler<FluidResource> handler, int slot) implements SingleSlotStorage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS);
            return amount <= 0 || resource.isBlank() ? 0 : handler.insert(slot, resource(resource), amount, ours(transaction)) * DROPLETS;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS);
            return amount <= 0 || resource.isBlank() ? 0 : handler.extract(slot, resource(resource), amount, ours(transaction)) * DROPLETS;
        }

        @Override
        public boolean isResourceBlank() {
            return handler.getResource(slot).isEmpty() || handler.getAmountAsLong(slot) <= 0;
        }

        @Override
        public FluidVariant getResource() {
            return variant(handler.getResource(slot));
        }

        @Override
        public long getAmount() {
            return handler.getAmountAsLong(slot) * DROPLETS;
        }

        @Override
        public long getCapacity() {
            return handler.getCapacityAsLong(slot, handler.getResource(slot)) * DROPLETS;
        }
    }

    /** L'inventaire du mod, emplacement par emplacement, pour Fabric. */
    record ExposedItems(ResourceHandler<ItemResource> handler) implements SlottedStorage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount);
            return amount <= 0 || resource.isBlank() ? 0 : handler.insert(resource(resource), amount, ours(transaction));
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount);
            return amount <= 0 || resource.isBlank() ? 0 : handler.extract(resource(resource), amount, ours(transaction));
        }

        @Override
        public int getSlotCount() {
            return handler.size();
        }

        @Override
        public SingleSlotStorage<ItemVariant> getSlot(int slot) {
            return new ItemSlot(handler, slot);
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            List<StorageView<ItemVariant>> slots = new ArrayList<>();
            for (int slot = 0; slot < handler.size(); slot++) {
                slots.add(new ItemSlot(handler, slot));
            }
            return slots.iterator();
        }
    }

    record ItemSlot(ResourceHandler<ItemResource> handler, int slot) implements SingleSlotStorage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount);
            return amount <= 0 || resource.isBlank() ? 0 : handler.insert(slot, resource(resource), amount, ours(transaction));
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            int amount = (int) Math.min(Integer.MAX_VALUE, maxAmount);
            return amount <= 0 || resource.isBlank() ? 0 : handler.extract(slot, resource(resource), amount, ours(transaction));
        }

        @Override
        public boolean isResourceBlank() {
            return handler.getResource(slot).isEmpty() || handler.getAmountAsLong(slot) <= 0;
        }

        @Override
        public ItemVariant getResource() {
            return variant(handler.getResource(slot));
        }

        @Override
        public long getAmount() {
            return handler.getAmountAsLong(slot);
        }

        @Override
        public long getCapacity() {
            return handler.getCapacityAsLong(slot, handler.getResource(slot));
        }
    }
}
