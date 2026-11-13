package com.orbitalis.platform.capability;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ce qu'un bloc peut offrir à ses voisins, face par face (une réserve d'énergie, de fluide, une prise de données). Les blocs du
 * mod l'inscrivent au démarrage ({@link RegisterCapabilitiesEvent}) ; ceux des autres mods sont trouvés par le chargeur (voir
 * {@link Caps}).
 *
 * @param <T> ce qui est offert
 * @param <C> le contexte de la demande (la face)
 */
public final class BlockCapability<T, C> {
    /** Ce qu'offre une entité de bloc, selon la face. */
    @FunctionalInterface
    public interface EntityProvider<T, C, E extends BlockEntity> {
        @Nullable T get(E blockEntity, C context);
    }

    /** Ce qu'offre un bloc, selon sa place et la face. */
    @FunctionalInterface
    public interface BlockProvider<T, C> {
        @Nullable T get(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context);
    }

    private final ResourceLocation id;
    private final Class<T> type;
    final Map<BlockEntityType<?>, EntityProvider<T, C, ?>> byEntity = new HashMap<>();
    final Map<Block, BlockProvider<T, C>> byBlock = new HashMap<>();

    private BlockCapability(ResourceLocation id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public static <T> BlockCapability<T, net.minecraft.core.Direction> createSided(ResourceLocation id, Class<T> type) {
        return new BlockCapability<>(id, type);
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    /** Ce que ce bloc du mod offre, ou {@code null} : seulement ce qui a été inscrit par le mod. */
    @SuppressWarnings("unchecked")
    public @Nullable T own(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context) {
        BlockProvider<T, C> block = byBlock.get(state.getBlock());
        if (block != null) {
            return block.get(level, pos, state, blockEntity, context);
        }
        if (blockEntity != null) {
            EntityProvider<T, C, BlockEntity> entity = (EntityProvider<T, C, BlockEntity>) byEntity.get(blockEntity.getType());
            if (entity != null) {
                return entity.get(blockEntity, context);
            }
        }
        return null;
    }

    /** Vrai si le mod a inscrit quelque chose pour ce bloc ou ce type d'entité de bloc. */
    public boolean handles(BlockState state, @Nullable BlockEntity blockEntity) {
        return byBlock.containsKey(state.getBlock()) || blockEntity != null && byEntity.containsKey(blockEntity.getType());
    }

    public Map<BlockEntityType<?>, EntityProvider<T, C, ?>> entityProviders() {
        return byEntity;
    }

    public Map<Block, BlockProvider<T, C>> blockProviders() {
        return byBlock;
    }

    @Override
    public String toString() {
        return "BlockCapability[" + id + "]";
    }
}
