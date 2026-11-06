package com.orbitalis.phase3.celestial;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'herbe des marées de TRAPPIST-1 e : des rubans pourpres qui poussent sous l'eau, au fond des mers, comme l'herbe
 * marine de la planète. Elle ne vit que dans l'eau, et la poudre d'os ne la fait pas grandir.
 */
public class TideGrassBlock extends SeagrassBlock {
    public static final MapCodec<SeagrassBlock> CODEC = simpleCodec(TideGrassBlock::new);

    public TideGrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SeagrassBlock> codec() {
        return CODEC;
    }

    /** Elle tient au fond, et seulement dans l'eau : à l'air libre, elle ne pousse pas. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos) && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    /** L'herbe marine de la planète deviendrait sa grande herbe marine : celle-ci reste ce qu'elle est. */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }
}
