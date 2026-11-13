package com.orbitalis.phase3.celestial;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une plante de Kepler-452b : elle ne pousse que sur le gazon bronze et le sol des plaines. Écrasée par une pesanteur
 * une fois et demie plus forte que celle de la planète, elle reste basse et s'étale.
 */
public class KeplerPlantBlock extends BushBlock {
    public static final MapCodec<KeplerPlantBlock> CODEC = simpleCodec(KeplerPlantBlock::new);
    private static final VoxelShape SHAPE = com.orbitalis.platform.Compat.column(14.0, 0.0, 9.0);

    public KeplerPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<KeplerPlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Vrai si une plante de Kepler-452b pousse sur ce bloc. */
    public static boolean grows(BlockState ground) {
        return ground.is(ModBlocks.KEPLER_TURF.get()) || ground.is(ModBlocks.KEPLER_SOIL.get());
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return grows(state);
    }
}
