package com.orbitalis.phase3.exploration;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Bloc jamais posé, qui porte les modèles du drone volant : son corps, et son rotor, que le rendu dessine huit fois en
 * le faisant tourner.
 */
public class RotorDroneModelBlock extends Block {
    /** Vrai pour le modèle du rotor, faux pour celui du corps. */
    public static final BooleanProperty ROTOR = BooleanProperty.create("rotor");

    public RotorDroneModelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTOR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTOR);
    }
}
