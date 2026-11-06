package com.orbitalis.phase3.celestial;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une plante des îles de TRAPPIST-1 e : elle ne pousse que sur le tapis d'algues et le sable des rivages. Sous une
 * lumière rouge sombre, elle est vert-bleu : elle prend ce que la mer lui renvoie.
 */
public class TrappistPlantBlock extends VegetationBlock {
    public static final MapCodec<TrappistPlantBlock> CODEC = simpleCodec(TrappistPlantBlock::new);
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

    public TrappistPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TrappistPlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Vrai si une plante des îles de TRAPPIST-1 e pousse sur ce bloc. */
    public static boolean grows(BlockState ground) {
        return ground.is(ModBlocks.TRAPPIST_MOSS.get()) || ground.is(ModBlocks.TRAPPIST_SAND.get());
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return grows(state);
    }
}
