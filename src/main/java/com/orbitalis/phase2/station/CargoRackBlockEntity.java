package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Contenu d'un casier de fret : vingt-sept emplacements, comme un coffre. */
public class CargoRackBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CargoRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARGO_RACK.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    /** Emplacements occupés. */
    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : items) {
            used += stack.isEmpty() ? 0 : 1;
        }
        return used;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.orbitalis.cargo_rack");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!remove && level != null && !user.getLivingEntity().isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 0.5F, 0.9F);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, items);
        }
    }
}
