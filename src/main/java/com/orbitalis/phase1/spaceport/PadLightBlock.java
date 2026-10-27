package com.orbitalis.phase1.spaceport;

import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Lumière de pas de tir : un bloc d'air qui éclaire. On ne le voit pas, on ne le touche pas, on ne peut ni le poser
 * ni le ramasser : ce sont les projecteurs et les balises qui le déposent là où porte leur lumière, et le reprennent
 * en s'éteignant ({@link PadLights}).
 */
public class PadLightBlock extends AirBlock {
    /** Force de la lumière, de 0 à 15. */
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    public PadLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }
}
