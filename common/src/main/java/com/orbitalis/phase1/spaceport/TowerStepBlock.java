package com.orbitalis.phase1.spaceport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Marche de l'escalier de la tour de service. Invisible : c'est la table de lancement qui dessine l'escalier
 * (voir {@code LaunchTableRenderer}) ; ce bloc lui donne seulement sa solidité, pour qu'on puisse y monter.
 * <p>
 * Comme les flancs de fusée, il est posé et retiré par la table de lancement et n'existe pas en objet. On ne
 * peut ni le viser ni le casser, et il cède sa place à tout bloc qu'un joueur pose à son emplacement.
 */
public class TowerStepBlock extends Block {
    /** Vrai si la marche est à mi-hauteur du bloc, faux si elle est à son pied. */
    public static final BooleanProperty HIGH = BooleanProperty.create("high");

    private static final VoxelShape LOW_STEP = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape HIGH_STEP = Block.box(0.0, 8.0, 0.0, 16.0, 9.0, 16.0);

    public TowerStepBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HIGH, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HIGH);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Rien à viser : le regard et les clics traversent la marche. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HIGH) ? HIGH_STEP : LOW_STEP;
    }

    /** La marche laisse passer toute la lumière et n'assombrit pas ses voisins. */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
