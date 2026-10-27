package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Portique d'embarquement : la grande porte par laquelle l'équipage passe de la tour à la capsule. Il fait trois
 * blocs de large et trois de haut, comme la cabine de l'ascenseur qui s'y arrête, et s'ouvre dans la face de la
 * tour tournée vers la fusée, à la hauteur de la capsule.
 * <p>
 * Le portique est fait de neuf cases du même bloc, chacune sachant sa place ({@link #COLUMN}, {@link #ROW}). Celle
 * du milieu, en bas, est son cœur : c'est elle que l'on pose, dans la colonne technique, et c'est d'elle que part
 * la passerelle. On passe à travers les neuf cases : seuls sont solides le seuil, les deux montants et le linteau.
 * Casser une case casse tout le portique.
 * <p>
 * Dans un pas de tir assemblé, une passerelle solide s'étend de son seuil jusqu'à la capsule (voir
 * {@code LaunchTableBlockEntity}) ; elle se retire au compte à rebours. L'ascenseur de la tour s'arrête à son niveau.
 */
public class BoardingGateBlock extends Block {
    /** Place de la case dans la largeur du portique : 0 et 2 pour les montants, 1 pour le passage du milieu. */
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    /** Place de la case dans la hauteur du portique : 0 pour le seuil, 2 pour le linteau. */
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    /** Sens du passage : celui de l'axe x du modèle, tourné vers la fusée. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Vrai quand la porte palière est fermée : le passage est barré. L'ascenseur la ferme quand sa cabine n'est pas
     * à cet étage, pour qu'on ne tombe pas dans la gaine ; sans ascenseur, elle reste ouverte.
     */
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    // Formes d'une case, pour un passage tourné vers l'est : le seuil, le linteau, un montant de chaque côté, la porte.
    private static final double[] SILL = {0, 0, 0, 16, 1, 16};
    private static final double[] LINTEL = {0, 11, 0, 16, 16, 16};
    private static final double[] POST_LEFT = {0, 0, 0, 16, 16, 3};
    private static final double[] POST_RIGHT = {0, 0, 13, 16, 16, 16};
    private static final double[] DOOR = {7, 0, 0, 9, 16, 16};

    public BoardingGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLUMN, 1).setValue(ROW, 0).setValue(FACING, Direction.EAST).setValue(CLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLUMN, ROW, FACING, CLOSED);
    }

    /** Vrai pour le cœur du portique : la case du milieu, en bas. */
    public static boolean isCore(BlockState state) {
        return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0;
    }

    /** Position du cœur du portique auquel appartient une case. */
    public static BlockPos core(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(ROW)).relative(state.getValue(FACING).getClockWise(), 1 - state.getValue(COLUMN));
    }

    /** Les neuf cases d'un portique, et l'état de chacune. */
    private List<BlockPos> cells(BlockPos core, Direction facing) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int row = 0; row <= 2; row++) {
            for (int column = 0; column <= 2; column++) {
                cells.add(core.above(row).relative(facing.getClockWise(), column - 1));
            }
        }
        return cells;
    }

    private BlockState cell(int index, Direction facing, boolean closed) {
        return defaultBlockState().setValue(ROW, index / 3).setValue(COLUMN, index % 3).setValue(FACING, facing).setValue(CLOSED, closed);
    }

    /**
     * Dresse un portique entier, cœur en {@code core}, à la place de ce qui s'y trouve : pour le poser dans une
     * tour déjà bâtie.
     */
    public void place(Level level, BlockPos core, Direction facing) {
        List<BlockPos> cells = cells(core, facing);
        for (int i = 0; i < cells.size(); i++) {
            level.setBlock(cells.get(i), cell(i, facing, false), Block.UPDATE_ALL);
        }
    }

    /** Ouvre ou ferme la porte palière d'un portique dont le cœur est en {@code core}. */
    public void setClosed(Level level, BlockPos core, boolean closed) {
        BlockState state = level.getBlockState(core);
        if (!state.is(this)) {
            return;
        }
        for (BlockPos pos : cells(core, state.getValue(FACING))) {
            BlockState cell = level.getBlockState(pos);
            if (cell.is(this) && cell.getValue(CLOSED) != closed) {
                level.setBlock(pos, cell.setValue(CLOSED, closed), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape shape = Shapes.empty();
        if (state.getValue(ROW) == 0) {
            shape = Shapes.or(shape, PadPartBlock.rotated(SILL, facing));
        }
        if (state.getValue(ROW) == 2) {
            shape = Shapes.or(shape, PadPartBlock.rotated(LINTEL, facing));
        }
        if (state.getValue(COLUMN) == 0) {
            shape = Shapes.or(shape, PadPartBlock.rotated(POST_LEFT, facing));
        }
        if (state.getValue(COLUMN) == 2) {
            shape = Shapes.or(shape, PadPartBlock.rotated(POST_RIGHT, facing));
        }
        if (state.getValue(CLOSED)) {
            shape = Shapes.or(shape, PadPartBlock.rotated(DOOR, facing));
        }
        return shape;
    }

    /** Il faut la place des neuf cases. Posé contre un flanc, le portique lui tourne le dos. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection();
        Level level = context.getLevel();
        for (BlockPos pos : cells(context.getClickedPos(), facing)) {
            if (level.isOutsideBuildHeight(pos) || (!pos.equals(context.getClickedPos()) && !level.getBlockState(pos).canBeReplaced())) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        place(level, pos, state.getValue(FACING));
    }

    /** Clic droit : appelle la cabine de l'ascenseur à cet étage. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos core = core(pos, state);
            ElevatorCabinEntity.call(serverLevel, core, core.getY());
        }
        return InteractionResult.SUCCESS;
    }

    /** Posé : la table de lancement voisine relève aussitôt sa structure. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    /** Cassé par un joueur : le portique rend son objet, quelle que soit la case cassée. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            Block.popResource(level, core(pos, state), new ItemStack(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Une case retirée emporte les huit autres. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos cell : cells(core(pos, state), state.getValue(FACING))) {
            if (level.getBlockState(cell).is(this)) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }
}
