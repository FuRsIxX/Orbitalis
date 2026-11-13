package com.orbitalis.platform.capability.neoforge;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orbitalis.Orbitalis;
import com.orbitalis.platform.capability.BlockCapability;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;
import com.orbitalis.platform.transfer.foreign.Exposed;
import com.orbitalis.platform.transfer.foreign.ForeignEnergy;
import com.orbitalis.platform.transfer.foreign.ForeignFluids;
import com.orbitalis.platform.transfer.foreign.ForeignItems;
import com.orbitalis.platform.transfer.item.ItemResource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Sous NeoForge : l'énergie, les fluides et les objets entre les blocs du mod et ceux des autres mods, par les capacités de
 * NeoForge ({@code IEnergyStorage}, {@code IFluidHandler}, {@code IItemHandler}).
 */
public final class NeoForgeTransfer {
    private NeoForgeTransfer() {
    }

    // ------------------------------------------------------------------
    // Les blocs des autres mods, vus par le mod
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static <T, C> @Nullable T foreign(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        Direction side = (Direction) context;
        if (capability == Capabilities.Energy.BLOCK) {
            IEnergyStorage storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, state,
                    blockEntity, side);
            return storage == null || storage instanceof ExposedEnergy ? null : (T) foreignEnergy(storage);
        }
        if (capability == Capabilities.Fluid.BLOCK) {
            IFluidHandler handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, state,
                    blockEntity, side);
            return handler == null || handler instanceof ExposedFluids ? null : (T) foreignFluids(handler);
        }
        if (capability == Capabilities.Item.BLOCK) {
            IItemHandler handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state,
                    blockEntity, side);
            return handler == null || handler instanceof ExposedItems ? null : (T) foreignItems(handler);
        }
        return null;
    }

    private static EnergyHandler foreignEnergy(IEnergyStorage storage) {
        return new ForeignEnergy(new ForeignEnergy.Backend() {
            @Override
            public long amount() {
                return storage.getEnergyStored();
            }

            @Override
            public long capacity() {
                return storage.getMaxEnergyStored();
            }

            @Override
            public long insert(long amount, boolean simulate) {
                return storage.canReceive() ? storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate) : 0;
            }

            @Override
            public long extract(long amount, boolean simulate) {
                return storage.canExtract() ? storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate) : 0;
            }
        });
    }

    private static FluidStack stack(FluidResource resource, long amount) {
        return resource.isEmpty() || amount <= 0 ? FluidStack.EMPTY
                : new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.wrapAsHolder(resource.getFluid()),
                        (int) Math.min(Integer.MAX_VALUE, amount), resource.getComponentsPatch());
    }

    private static FluidResource resource(FluidStack stack) {
        return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack.getFluid(), stack.getComponentsPatch());
    }

    private static IFluidHandler.FluidAction action(boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }

    private static ResourceHandler<FluidResource> foreignFluids(IFluidHandler handler) {
        return new ForeignFluids(new ForeignFluids.Backend() {
            @Override
            public int size() {
                return handler.getTanks();
            }

            @Override
            public FluidResource resource(int index) {
                return NeoForgeTransfer.resource(handler.getFluidInTank(index));
            }

            @Override
            public long amount(int index) {
                return handler.getFluidInTank(index).getAmount();
            }

            @Override
            public long capacity(int index) {
                return handler.getTankCapacity(index);
            }

            @Override
            public boolean valid(int index, FluidResource resource) {
                return handler.isFluidValid(index, stack(resource, 1));
            }

            @Override
            public long fill(FluidResource resource, long amount, boolean simulate) {
                return handler.fill(stack(resource, amount), action(simulate));
            }

            @Override
            public long drain(FluidResource resource, long amount, boolean simulate) {
                return handler.drain(stack(resource, amount), action(simulate)).getAmount();
            }
        });
    }

    private static ResourceHandler<ItemResource> foreignItems(IItemHandler handler) {
        return new ForeignItems(new ForeignItems.Backend() {
            @Override
            public int size() {
                return handler.getSlots();
            }

            @Override
            public ItemResource resource(int slot) {
                return ItemResource.of(handler.getStackInSlot(slot));
            }

            @Override
            public int amount(int slot) {
                return handler.getStackInSlot(slot).getCount();
            }

            @Override
            public int capacity(int slot, ItemResource resource) {
                return resource.isEmpty() ? handler.getSlotLimit(slot) : Math.min(handler.getSlotLimit(slot), resource.getMaxStackSize());
            }

            @Override
            public boolean valid(int slot, ItemResource resource) {
                return handler.isItemValid(slot, resource.toStack());
            }

            @Override
            public int insert(int slot, ItemResource resource, int count, boolean simulate) {
                ItemStack rest = handler.insertItem(slot, resource.toStack(count), simulate);
                return count - rest.getCount();
            }

            @Override
            public int extract(int slot, ItemResource resource, int count, boolean simulate) {
                if (!resource.matches(handler.getStackInSlot(slot))) {
                    return 0;
                }
                return handler.extractItem(slot, count, simulate).getCount();
            }
        });
    }

    // ------------------------------------------------------------------
    // Les blocs du mod, offerts aux autres mods
    // ------------------------------------------------------------------

    /** Inscrit, pour NeoForge, tout ce que les blocs du mod offrent. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void expose(RegisterCapabilitiesEvent event) {
        Orbitalis.registerCapabilities();
        for (Map.Entry<BlockEntityType<?>, BlockCapability.EntityProvider<EnergyHandler, Direction, ?>> entry
                : Capabilities.Energy.BLOCK.entityProviders().entrySet()) {
            BlockCapability.EntityProvider provider = entry.getValue();
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, (BlockEntityType) entry.getKey(),
                    (blockEntity, side) -> energy((EnergyHandler) provider.get((BlockEntity) blockEntity, side)));
        }
        for (Map.Entry<Block, BlockCapability.BlockProvider<EnergyHandler, Direction>> entry : Capabilities.Energy.BLOCK.blockProviders().entrySet()) {
            BlockCapability.BlockProvider<EnergyHandler, Direction> provider = entry.getValue();
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, blockEntity, side) -> energy(provider.get(level, pos, state, blockEntity, side)), entry.getKey());
        }
        for (Map.Entry<BlockEntityType<?>, BlockCapability.EntityProvider<ResourceHandler<FluidResource>, Direction, ?>> entry
                : Capabilities.Fluid.BLOCK.entityProviders().entrySet()) {
            BlockCapability.EntityProvider provider = entry.getValue();
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, (BlockEntityType) entry.getKey(),
                    (blockEntity, side) -> fluids((ResourceHandler<FluidResource>) provider.get((BlockEntity) blockEntity, side)));
        }
        for (Map.Entry<Block, BlockCapability.BlockProvider<ResourceHandler<FluidResource>, Direction>> entry
                : Capabilities.Fluid.BLOCK.blockProviders().entrySet()) {
            BlockCapability.BlockProvider<ResourceHandler<FluidResource>, Direction> provider = entry.getValue();
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, blockEntity, side) -> fluids(provider.get(level, pos, state, blockEntity, side)), entry.getKey());
        }
        for (Map.Entry<BlockEntityType<?>, BlockCapability.EntityProvider<ResourceHandler<ItemResource>, Direction, ?>> entry
                : Capabilities.Item.BLOCK.entityProviders().entrySet()) {
            BlockCapability.EntityProvider provider = entry.getValue();
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, (BlockEntityType) entry.getKey(),
                    (blockEntity, side) -> items((ResourceHandler<ItemResource>) provider.get((BlockEntity) blockEntity, side)));
        }
        for (Map.Entry<Block, BlockCapability.BlockProvider<ResourceHandler<ItemResource>, Direction>> entry
                : Capabilities.Item.BLOCK.blockProviders().entrySet()) {
            BlockCapability.BlockProvider<ResourceHandler<ItemResource>, Direction> provider = entry.getValue();
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    (level, pos, state, blockEntity, side) -> items(provider.get(level, pos, state, blockEntity, side)), entry.getKey());
        }
    }

    private static @Nullable IEnergyStorage energy(@Nullable EnergyHandler handler) {
        return handler == null ? null : new ExposedEnergy(handler);
    }

    private static @Nullable IFluidHandler fluids(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : new ExposedFluids(handler);
    }

    private static @Nullable IItemHandler items(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : new ExposedItems(handler);
    }

    record ExposedEnergy(EnergyHandler handler) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return Exposed.insert(handler, toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return Exposed.extract(handler, toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return handler.getAmountAsInt();
        }

        @Override
        public int getMaxEnergyStored() {
            return handler.getCapacityAsInt();
        }

        @Override
        public boolean canExtract() {
            return Exposed.extract(handler, 1, true) > 0;
        }

        @Override
        public boolean canReceive() {
            return Exposed.insert(handler, 1, true) > 0;
        }
    }

    record ExposedFluids(ResourceHandler<FluidResource> handler) implements IFluidHandler {
        @Override
        public int getTanks() {
            return handler.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return stack(handler.getResource(tank), handler.getAmountAsLong(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return handler.getCapacityAsInt(tank, handler.getResource(tank));
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return handler.isValid(tank, resource(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource.isEmpty() ? 0 : Exposed.insert(handler, resource(resource), resource.getAmount(), action.simulate());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidResource wanted = resource(resource);
            return stack(wanted, Exposed.extract(handler, wanted, resource.getAmount(), action.simulate()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int tank = 0; tank < handler.size(); tank++) {
                FluidResource present = handler.getResource(tank);
                if (!present.isEmpty() && handler.getAmountAsLong(tank) > 0) {
                    int drained = Exposed.extract(handler, present, maxDrain, action.simulate());
                    if (drained > 0) {
                        return stack(present, drained);
                    }
                }
            }
            return FluidStack.EMPTY;
        }
    }

    record ExposedItems(ResourceHandler<ItemResource> handler) implements IItemHandler {
        @Override
        public int getSlots() {
            return handler.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int inserted = Exposed.insert(handler, slot, ItemResource.of(stack), stack.getCount(), simulate);
            return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemResource present = handler.getResource(slot);
            if (present.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            return present.toStack(Exposed.extract(handler, slot, present, amount, simulate));
        }

        @Override
        public int getSlotLimit(int slot) {
            return handler.getCapacityAsInt(slot, handler.getResource(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return handler.isValid(slot, ItemResource.of(stack));
        }
    }
}
