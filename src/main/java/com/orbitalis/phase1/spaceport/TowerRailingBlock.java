package com.orbitalis.phase1.spaceport;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rambarde de tour : une demi-dalle qui porte un garde-corps. Elle se comporte comme une barrière : elle se raccorde
 * d'elle-même aux rambardes voisines, et on ne la franchit pas en sautant, sa collision montant bien plus haut que
 * ce que l'on voit.
 */
public class TowerRailingBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** Vrai quand une autre rambarde est posée dessus : le potelet et les lisses s'arrêtent au bord du bloc. */
    public static final BooleanProperty UP = BlockStateProperties.UP;
    private static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST,
            Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    /** Hauteur visible du garde-corps, et hauteur de sa collision : on se tient sur la demi-dalle, d'où l'on saute plus haut. */
    private static final double SEEN = 22.0;
    private static final double SOLID = 32.0;

    private final VoxelShape[] outlines = new VoxelShape[16];
    private final VoxelShape[] collisions = new VoxelShape[16];

    public TowerRailingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false));
        for (int mask = 0; mask < 16; mask++) {
            outlines[mask] = shape(mask, SEEN);
            collisions[mask] = shape(mask, SOLID);
        }
    }

    /** La demi-dalle, le potelet central, et une lisse vers chaque côté raccordé (nord, est, sud, ouest). */
    private static VoxelShape shape(int mask, double height) {
        VoxelShape shape = Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(6, 8, 6, 10, height, 10));
        if ((mask & 1) != 0) {
            shape = Shapes.or(shape, Block.box(7, 8, 0, 9, height, 6));
        }
        if ((mask & 2) != 0) {
            shape = Shapes.or(shape, Block.box(10, 8, 7, 16, height, 9));
        }
        if ((mask & 4) != 0) {
            shape = Shapes.or(shape, Block.box(7, 8, 10, 9, height, 16));
        }
        if ((mask & 8) != 0) {
            shape = Shapes.or(shape, Block.box(0, 8, 7, 6, height, 9));
        }
        return shape.optimize();
    }

    private static int mask(BlockState state) {
        return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outlines[mask(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisions[mask(state)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Une rambarde se raccorde aux autres rambardes. */
    private BlockState connected(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), level.getBlockState(pos.relative(side.getKey())).is(this));
        }
        return state.setValue(UP, level.getBlockState(pos.above()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == Direction.UP) {
            return state.setValue(UP, neighbourState.is(this));
        }
        BooleanProperty side = SIDES.get(directionToNeighbour);
        return side == null ? state : state.setValue(side, neighbourState.is(this));
    }

    /** Posée par une commande ou par un plan, la rambarde n'a pas été raccordée : elle le fait ici. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState connected = connected(level, pos);
        if (connected != state) {
            level.setBlock(pos, connected, Block.UPDATE_ALL);
        }
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
