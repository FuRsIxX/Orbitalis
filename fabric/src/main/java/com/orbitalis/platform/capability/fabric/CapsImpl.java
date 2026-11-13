package com.orbitalis.platform.capability.fabric;

import org.jspecify.annotations.Nullable;

import com.orbitalis.platform.capability.BlockCapability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sous Fabric : ce qu'offrent les blocs des autres mods, par l'API de transfert de Fabric et Team Reborn Energy. */
public final class CapsImpl {
    private CapsImpl() {
    }

    public static void invalidate(Level level, BlockPos pos) {
        // Fabric ne garde rien en cache : rien à oublier.
    }

    public static <T, C> @Nullable T foreign(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        return FabricTransfer.foreign(level, capability, pos, state, blockEntity, context);
    }
}
