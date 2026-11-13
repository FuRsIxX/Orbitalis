package com.orbitalis.phase3.exploration;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Bloc jamais posé, qui porte les modèles du rover : son châssis, et sa roue, que le rendu dessine quatre fois en la
 * faisant tourner et braquer.
 */
public class RoverModelBlock extends Block {
    /** Vrai pour le modèle de la roue, faux pour celui du châssis. */
    public static final BooleanProperty WHEEL = BooleanProperty.create("wheel");

    public RoverModelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WHEEL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WHEEL);
    }
}
