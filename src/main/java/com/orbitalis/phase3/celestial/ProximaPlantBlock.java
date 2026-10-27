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
 * Une plante de Proxima b : elle ne pousse que sur la mousse et le régolithe de la bande du crépuscule. Sous une lumière
 * aussi rouge, elles sont sombres, presque noires : elles boivent toute la lumière qu'elles reçoivent.
 */
public class ProximaPlantBlock extends VegetationBlock {
    public static final MapCodec<ProximaPlantBlock> CODEC = simpleCodec(ProximaPlantBlock::new);
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

    public ProximaPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ProximaPlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Vrai si une plante de Proxima b pousse sur ce bloc. */
    public static boolean grows(BlockState ground) {
        return ground.is(ModBlocks.PROXIMA_MOSS.get()) || ground.is(ModBlocks.PROXIMA_REGOLITH.get());
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return grows(state);
    }
}
