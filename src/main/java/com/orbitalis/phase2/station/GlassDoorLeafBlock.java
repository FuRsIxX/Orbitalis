package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Battant d'une porte vitrée. Ce bloc n'est jamais posé : il ne sert qu'à porter le modèle du battant, que le rendu
 * de la porte fait coulisser (les deux colonnes ont le même battant).
 */
public class GlassDoorLeafBlock extends Block {
    public static final MapCodec<GlassDoorLeafBlock> CODEC = simpleCodec(GlassDoorLeafBlock::new);
    /** Faux pour le battant de la colonne de gauche, vrai pour celui de droite (vues de face). */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public GlassDoorLeafBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<GlassDoorLeafBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIGHT);
    }
}
