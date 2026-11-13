package com.orbitalis.platform.capability;

import dev.architectury.injectables.annotations.ExpectPlatform;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trouver ce qu'offre le bloc d'à côté : d'abord ce que le mod a inscrit pour ses propres blocs, puis, pour l'énergie, les
 * fluides et les objets, ce qu'offrent les blocs des autres mods, par le système de leur chargeur.
 */
public final class Caps {
    private Caps() {
    }

    public static <T, C> @Nullable T get(Level level, BlockCapability<T, C> capability, BlockPos pos, C context) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        return get(level, capability, pos, state, blockEntity, context);
    }

    public static <T, C> @Nullable T get(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        if (capability.handles(state, blockEntity)) {
            return capability.own(level, pos, state, blockEntity, context);
        }
        return foreign(level, capability, pos, state, blockEntity, context);
    }

    /** Ce bloc a changé ce qu'il offre : le chargeur oublie ce qu'il en savait (NeoForge garde les capacités en cache). */
    @ExpectPlatform
    public static void invalidate(Level level, BlockPos pos) {
        throw new AssertionError("remplacé par le chargeur");
    }

    /** Ce qu'offre un bloc d'un autre mod, par le système du chargeur ; rien pour les capacités propres au mod. */
    @ExpectPlatform
    public static <T, C> @Nullable T foreign(Level level, BlockCapability<T, C> capability, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        throw new AssertionError("remplacé par le chargeur");
    }
}
