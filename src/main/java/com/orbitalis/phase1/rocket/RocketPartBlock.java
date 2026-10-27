package com.orbitalis.phase1.rocket;

import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Bloc de construction d'une fusée : moteur, réservoir, interétage, baie de charge utile, coiffe, cabine, panneau
 * de coque, chemin de câbles, ordinateur de bord...
 * <p>
 * Une fusée se bâtit bloc par bloc au-dessus de la table de lancement, d'après un plan ({@link RocketPlan}) sorti de
 * l'ordinateur de conception. Chaque bloc posé ou cassé prévient la table voisine : quand le dernier est en place,
 * elle forme la fusée. Sa face avant regarde le joueur qui le pose.
 */
public class RocketPartBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RocketPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Le bloc d'une pièce (moteur, réservoir, interétage, pointe) rend la pièce : autant qu'elle a coûté si elle a été
     * posée sur le fantôme d'un plan imprimé (la baie d'un étage à trois moteurs en rend trois), une sinon.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.Item piece = RocketPlan.pieceOf(this);
        if (piece != null && !level.isClientSide() && !player.getAbilities().instabuild) {
            LaunchTableBlockEntity.PlannedPiece planned = LaunchTableBlockEntity.plannedPieceAt(level, pos);
            int count = planned != null && planned.piece().block() == this ? planned.table().refundOf(planned.piece()) : 1;
            if (count > 0) {
                Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(piece, count));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }
}
