package com.orbitalis.phase1.spaceport;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Bloc d'une structure animée du pas de tir : segment de tour de service ou bras ombilical.
 * <p>
 * Posé seul, il s'affiche avec son modèle, comme n'importe quel bloc. Quand le complexe est en service,
 * la table de lancement en prend la charge ({@link #MANAGED}) : le bloc devient invisible et c'est la table
 * qui dessine la tour entière et ses bras, pour pouvoir les animer en douceur pendant le compte à rebours
 * (bras qui pivotent, tour qui recule). Le bloc reste en place et garde sa collision.
 * <p>
 * Le bloc s'oriente à la pose : posé contre le flanc d'un autre bloc, il lui tourne le dos (un bras part ainsi de
 * la tour, un caillebotis de la table) ; posé au sol, il prend la direction du regard du joueur. Cette orientation
 * ne sert qu'à l'affichage du bloc seul : dans un pas de tir assemblé, la table sait où est chaque chose.
 */
public class ManagedStructureBlock extends Block {
    /** Vrai quand la table de lancement dessine ce bloc à sa place. */
    public static final BooleanProperty MANAGED = BooleanProperty.create("managed");
    /** Sens dans lequel le bloc est tourné : celui de l'axe x de son modèle. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ManagedStructureBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MANAGED, false).setValue(FACING, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MANAGED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(MANAGED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /** Un clic droit sur le caillebotis du carneau, à main vide de bloc, vaut un clic droit sur la table de lancement. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.is(ModBlocks.FLAME_TRENCH.get()) || player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        LaunchTableBlockEntity table = LaunchTableBlockEntity.ownerOfDeck(level, pos);
        if (table == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(table.getBlockPos()).useWithoutItem(level, player, hitResult.withPosition(table.getBlockPos()));
    }

    /** Posé : la table de lancement voisine relève aussitôt sa structure. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    /** Cassé : la structure se défait à l'instant, sans attendre le relevé périodique de la table. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }
}
