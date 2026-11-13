package com.orbitalis.phase1.rocket;

import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;

/** Le plan étalé sur une table à dessin. Il tombe au sol si l'on casse la table. */
public class DraftingTableBlockEntity extends BlockEntity implements com.orbitalis.platform.Compat.RemovalAware {
    private ItemStack plan = ItemStack.EMPTY;

    public DraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRAFTING_TABLE.get(), pos, state);
    }

    public void setPlan(ItemStack plan) {
        this.plan = plan;
        setChanged();
    }

    public ItemStack takePlan() {
        ItemStack taken = plan;
        plan = ItemStack.EMPTY;
        setChanged();
        return taken;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !plan.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, plan);
            plan = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        output.store("Plan", ItemStack.OPTIONAL_CODEC, plan);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        plan = input.read("Plan", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }
}
