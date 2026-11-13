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
 * Une plante de Tarentule b : elle ne pousse que dans la poussière rose. Sous la lumière bleue d'une jeune étoile et dans
 * un air mince, elle est faite de fils et de voiles, et elle luit.
 */
public class TarantulaPlantBlock extends BushBlock {
    public static final MapCodec<TarantulaPlantBlock> CODEC = simpleCodec(TarantulaPlantBlock::new);
    private static final VoxelShape SHAPE = com.orbitalis.platform.Compat.column(12.0, 0.0, 14.0);

    public TarantulaPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TarantulaPlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Vrai si une plante de Tarentule b pousse sur ce bloc. */
    public static boolean grows(BlockState ground) {
        return ground.is(ModBlocks.TARANTULA_DUST.get());
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return grows(state);
    }
}
