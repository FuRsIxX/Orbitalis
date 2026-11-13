package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Bloc de paroi tourné vers la pièce : le revêtement capitonné, le plafonnier. C'est un bloc de coque plein et
 * étanche ; seule sa face avant change, celle qui regarde le joueur qui le pose.
 */
public class StationFacingBlock extends DirectionalBlock {
    public static final MapCodec<StationFacingBlock> CODEC = simpleCodec(StationFacingBlock::new);

    public StationFacingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<StationFacingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
}
