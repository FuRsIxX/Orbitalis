package com.orbitalis.platform.event;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Un bloc cassé va laisser tomber ces objets : la liste peut être changée. */
public final class BlockDropsEvent extends Cancelable {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final @Nullable BlockEntity blockEntity;
    private final @Nullable Entity breaker;
    private final List<ItemStack> drops;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Entity breaker,
            List<ItemStack> drops) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.blockEntity = blockEntity;
        this.breaker = breaker;
        this.drops = drops;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public @Nullable BlockEntity getBlockEntity() {
        return blockEntity;
    }

    public @Nullable Entity getBreaker() {
        return breaker;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }
}
