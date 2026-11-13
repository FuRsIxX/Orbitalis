package com.orbitalis.platform.capability.neoforge;

import org.jspecify.annotations.Nullable;

import com.orbitalis.platform.capability.BlockCapability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sous NeoForge : ce qu'offrent les blocs des autres mods, par les capacités de NeoForge. */
public final class CapsImpl {
    private CapsImpl() {
    }

    public static void invalidate(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
    }

    public static <T, C> @Nullable T foreign(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        return NeoForgeTransfer.foreign(level, capability, pos, state, blockEntity, context);
    }
}
