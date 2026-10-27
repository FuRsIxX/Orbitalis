package com.orbitalis.phase2.station;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Porte vitrée coulissante, comme à l'entrée d'un supermarché : deux blocs de large et deux de haut, un battant de
 * verre par colonne, dans un mince cadre d'aluminium. À l'ouverture, les deux battants s'écartent chacun de son côté
 * et glissent dans les parois voisines. Elle s'ouvre et se ferme comme une porte du jeu : d'un clic droit, ou par un
 * signal de redstone.
 * <p>
 * On la pose depuis la colonne de gauche (vue de face) : la colonne de droite se met à droite du joueur. Le modèle
 * du bloc ne porte que le rail et le linteau ; les battants sont dessinés par le rendu de la porte.
 */
public class GlassDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GlassDoorBlock> CODEC = simpleCodec(GlassDoorBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Faux pour la colonne de gauche, vrai pour celle de droite (vues de face). */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // Fermée : le battant, au milieu de l'épaisseur du bloc. Ouverte : le rail du bas, le linteau du haut.
    private static final VoxelShape CLOSED_X = Block.box(0, 0, 6.5, 16, 16, 9.5);
    private static final VoxelShape CLOSED_Z = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final VoxelShape SILL_X = Block.box(0, 0, 6.5, 16, 0.5, 9.5);
    private static final VoxelShape SILL_Z = Block.box(6.5, 0, 0, 9.5, 0.5, 16);
    private static final VoxelShape HEADER_X = Block.box(0, 14, 5.5, 16, 16, 10.5);
    private static final VoxelShape HEADER_Z = Block.box(5.5, 14, 0, 10.5, 16, 16);

    public GlassDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(RIGHT, false).setValue(OPEN, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<GlassDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, RIGHT, OPEN, POWERED);
    }

    /** Du côté de la colonne de gauche vers celle de droite : la droite du joueur qui regarde la porte de face. */
    public static Direction toRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** La direction de l'autre colonne, vue depuis celle-ci. */
    private static Direction toPartner(BlockState state) {
        Direction right = toRight(state.getValue(FACING));
        return state.getValue(RIGHT) ? right.getOpposite() : right;
    }

    /** La case basse de la colonne de gauche : celle qui fait foi pour toute la porte. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(RIGHT) ? lower.relative(toRight(state.getValue(FACING)).getOpposite()) : lower;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Les battants courent en travers de la direction de la porte.
        boolean alongX = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (!state.getValue(OPEN)) {
            return alongX ? CLOSED_X : CLOSED_Z;
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return alongX ? HEADER_X : HEADER_Z;
        }
        return alongX ? SILL_X : SILL_Z;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return getShape(state, level, pos, context);
        }
        // Ouverte, on passe sans buter sur le rail ; le linteau reste au-dessus des têtes.
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? getShape(state, level, pos, context) : Shapes.empty();
    }

    // ------------------------------------------------------------------
    // Quatre cases : deux colonnes de deux blocs
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos right = pos.relative(toRight(facing));
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)
                || !level.getBlockState(right).canBeReplaced(context) || !level.getBlockState(right.above()).canBeReplaced(context)) {
            return null;
        }
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above())
                || level.hasNeighborSignal(right) || level.hasNeighborSignal(right.above());
        return defaultBlockState().setValue(FACING, facing).setValue(OPEN, powered).setValue(POWERED, powered);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        place(level, pos, state);
    }

    /** Pose les trois autres cases de la porte, d'après la case basse de gauche. */
    public static void place(Level level, BlockPos origin, BlockState state) {
        BlockPos right = origin.relative(toRight(state.getValue(FACING)));
        level.setBlock(origin.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlock(right, state.setValue(RIGHT, true), Block.UPDATE_ALL);
        level.setBlock(right.above(), state.setValue(RIGHT, true).setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean vertical = direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (vertical || direction == toPartner(state)) {
            // L'autre moitié, ou l'autre colonne, a disparu : celle-ci s'en va aussi. Sinon, toutes suivent le même état.
            boolean partner = neighbourState.is(this) && neighbourState.getValue(FACING) == state.getValue(FACING)
                    && (vertical ? neighbourState.getValue(HALF) != half && neighbourState.getValue(RIGHT) == state.getValue(RIGHT)
                            : neighbourState.getValue(HALF) == half && neighbourState.getValue(RIGHT) != state.getValue(RIGHT));
            return partner
                    ? state.setValue(OPEN, neighbourState.getValue(OPEN)).setValue(POWERED, neighbourState.getValue(POWERED))
                    : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** En créatif, casser une case ne doit pas faire tomber la porte en objet. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos origin = origin(state, pos);
            BlockState first = level.getBlockState(origin);
            if (!origin.equals(pos) && first.is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, origin, Block.getId(first));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------------
    // Ouverture : au clic, ou par la redstone
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        setOpen(level, origin(state, pos), !state.getValue(OPEN));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (defaultBlockState().is(block)) {
            return;
        }
        BlockPos origin = origin(state, pos);
        BlockPos right = origin.relative(toRight(state.getValue(FACING)));
        boolean powered = level.hasNeighborSignal(origin) || level.hasNeighborSignal(origin.above())
                || level.hasNeighborSignal(right) || level.hasNeighborSignal(right.above());
        if (powered != state.getValue(POWERED)) {
            if (powered != state.getValue(OPEN)) {
                setOpen(level, origin, powered);
            }
            for (BlockPos part : new BlockPos[] {origin, origin.above(), right, right.above()}) {
                BlockState now = level.getBlockState(part);
                if (now.is(this)) {
                    level.setBlock(part, now.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Ouvre ou ferme les quatre cases de la porte, avec le souffle feutré de ses battants. */
    private void setOpen(Level level, BlockPos origin, boolean open) {
        BlockState first = level.getBlockState(origin);
        if (!first.is(this) || first.getValue(OPEN) == open) {
            return;
        }
        BlockPos right = origin.relative(toRight(first.getValue(FACING)));
        for (BlockPos part : new BlockPos[] {origin, origin.above(), right, right.above()}) {
            BlockState now = level.getBlockState(part);
            if (now.is(this)) {
                level.setBlock(part, now.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
        if (!level.isClientSide()) {
            level.playSound(null, origin.above(), open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.45F, 1.6F);
        }
    }

    // ------------------------------------------------------------------
    // Entité : le coulissement du battant de chaque colonne (client)
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new GlassDoorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof GlassDoorBlockEntity door) {
                door.clientTick(tickState);
            }
        };
    }
}
