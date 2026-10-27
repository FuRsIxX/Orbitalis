package com.orbitalis.phase2.survival;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Établi de combinaison : un plan de travail de deux blocs de long, où l'on pose les pièces d'une combinaison pour
 * fixer ou retirer ses modules. À gauche, les tiroirs et le plateau des modules ; à droite, l'armoire et la console
 * de diagnostic.
 * <p>
 * On le pose depuis sa moitié gauche (vue de face) : la moitié droite se met à droite du joueur.
 */
public class SuitWorkbenchBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SuitWorkbenchBlock> CODEC = simpleCodec(SuitWorkbenchBlock::new);
    /** Faux pour la moitié gauche (vue de face), vrai pour la moitié droite. */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** Le meuble et son plan de travail, sans ce qui est posé dessus. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 13.5, 16), Block.box(0, 13.5, 14.5, 16, 16, 16));

    public SuitWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<SuitWorkbenchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** De la moitié gauche vers la moitié droite : la droite du joueur qui regarde l'établi de face. */
    public static Direction toRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static Direction toPartner(BlockState state) {
        Direction right = toRight(state.getValue(FACING));
        return state.getValue(RIGHT) ? right.getOpposite() : right;
    }

    /** La moitié gauche de l'établi qui occupe cette case. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        return state.getValue(RIGHT) ? pos.relative(toRight(state.getValue(FACING)).getOpposite()) : pos;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos right = context.getClickedPos().relative(toRight(facing));
        if (!context.getLevel().getBlockState(right).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        place(level, pos, state);
    }

    /** Pose la moitié droite de l'établi, d'après sa moitié gauche. */
    public static void place(Level level, BlockPos origin, BlockState state) {
        level.setBlock(origin.relative(toRight(state.getValue(FACING))), state.setValue(RIGHT, true), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == toPartner(state)) {
            // L'autre moitié a disparu : celle-ci s'en va aussi.
            boolean partner = neighbourState.is(this) && neighbourState.getValue(FACING) == state.getValue(FACING)
                    && neighbourState.getValue(RIGHT) != state.getValue(RIGHT);
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** En créatif, casser la moitié droite ne doit pas faire tomber l'établi en objet. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(RIGHT)) {
            BlockPos origin = origin(state, pos);
            BlockState left = level.getBlockState(origin);
            if (left.is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, origin, Block.getId(left));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockPos origin = origin(state, pos);
            player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new SuitWorkbenchMenu(id, inventory,
                    ContainerLevelAccess.create(level, origin)), Component.translatable("container.orbitalis.suit_workbench")));
        }
        return InteractionResult.SUCCESS;
    }
}
