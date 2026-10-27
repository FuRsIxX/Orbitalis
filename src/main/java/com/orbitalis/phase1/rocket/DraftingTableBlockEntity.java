package com.orbitalis.phase1.rocket;

import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Le plan étalé sur une table à dessin. Il tombe au sol si l'on casse la table. */
public class DraftingTableBlockEntity extends BlockEntity {
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !plan.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, plan);
            plan = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Plan", ItemStack.OPTIONAL_CODEC, plan);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        plan = input.read("Plan", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }
}
