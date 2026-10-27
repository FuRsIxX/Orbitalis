package com.orbitalis.phase1.spaceport;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Projecteur de mât : un tronçon de mât paratonnerre qui porte une plateforme et deux projecteurs. Chaque mât d'un
 * pas de tir en a un, sous sa tête.
 * <p>
 * Allumé, il éclaire ce qu'il vise et non ses abords : son faisceau dépose de la lumière le long de son trajet
 * ({@link PadLights}). Posé à la main, ses projecteurs regardent le joueur qui le pose ; seule la table de lancement
 * peut le tourner en diagonale, vers sa fusée.
 * <p>
 * Sur un pas de tir en service, c'est la table de lancement qui le commande : elle le tourne vers la fusée, l'allume
 * à la tombée de la nuit et pendant les lancements, l'éteint au matin. Posé ailleurs, il s'allume au courant de
 * redstone.
 */
public class FloodlightBlock extends ManagedStructureBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Vrai si le projecteur vise en diagonale : un huitième de tour à droite de son orientation. */
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");

    public FloodlightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(DIAGONAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, DIAGONAL);
    }

    /** Posé à la main, le projecteur se tourne vers le joueur, droit, sans diagonale. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(DIAGONAL, false)
                .setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** État d'un projecteur qui vise dans la direction (dx, dz), chacun valant -1, 0 ou 1. */
    public static BlockState aimed(BlockState state, int dx, int dz) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int diagonal = 0; diagonal <= 1; diagonal++) {
                Direction side = facing.getClockWise();
                if (facing.getStepX() + diagonal * side.getStepX() == dx && facing.getStepZ() + diagonal * side.getStepZ() == dz) {
                    return state.setValue(FACING, facing).setValue(DIAGONAL, diagonal == 1);
                }
            }
        }
        return state;
    }

    /** Allume ou éteint le faisceau d'un projecteur, d'après son état. */
    public static void shine(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(LIT)) {
            PadLights.cast(level, pos, state.getValue(FACING), state.getValue(DIAGONAL));
        } else {
            PadLights.douse(level, pos, state.getValue(FACING), state.getValue(DIAGONAL));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        // Un projecteur qu'on tourne ou qu'on éteint reprend d'abord la lumière de son ancien faisceau.
        if (oldState.is(this) && oldState.getValue(LIT)) {
            PadLights.douse(level, pos, oldState.getValue(FACING), oldState.getValue(DIAGONAL));
        }
        shine(level, pos, state);
    }

    /** Hors d'un pas de tir en service, le courant de redstone allume le projecteur. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && !state.getValue(MANAGED)) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, powered), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PadLights.douse(level, pos, state.getValue(FACING), state.getValue(DIAGONAL));
    }
}
