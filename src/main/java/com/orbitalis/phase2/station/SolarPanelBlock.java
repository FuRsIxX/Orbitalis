package com.orbitalis.phase2.station;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Panneau solaire : une nappe de cellules sur son mât. Des panneaux posés côte à côte se raccordent en un seul
 * grand panneau : les cadres disparaissent entre eux et les cellules se rejoignent, jusqu'à six panneaux sur trois.
 * Au-delà, un nouveau panneau reste indépendant : il garde son cadre et forme le début d'un autre grand panneau.
 * <p>
 * Un grand panneau met son électricité en commun : il suffit de brancher une machine ou un câble sur un seul
 * de ses éléments. Un clic droit affiche la taille du panneau et sa production.
 */
public class SolarPanelBlock extends Block implements EntityBlock {
    /** Plus grand panneau possible : six éléments sur trois. */
    public static final int MAX_LENGTH = 6;
    public static final int MAX_WIDTH = 3;

    /** Raccordement à l'élément voisin, de chaque côté. */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public SolarPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty connection : CONNECTIONS.values()) {
            state = state.setValue(connection, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH, BlockStateProperties.WEST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ------------------------------------------------------------------
    // Raccordement
    // ------------------------------------------------------------------

    /**
     * À la pose : l'élément se raccorde à tous les panneaux qu'il touche, si l'ensemble tient dans six sur trois.
     * Sinon il reste indépendant.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Panneaux solaires : il faut en maîtriser la technologie pour les poser.
        if (!com.orbitalis.core.research.ResearchManager.mayPlace(context, "solar_power")) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Set<BlockPos> array = new HashSet<>();
        array.add(pos);
        boolean touches = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).is(this)) {
                touches = true;
                array.addAll(array(level, neighbour));
            }
        }
        BlockState state = defaultBlockState();
        if (touches && fits(array)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                state = state.setValue(CONNECTIONS.get(direction), level.getBlockState(pos.relative(direction)).is(this));
            }
        }
        return state;
    }

    /** Un voisin a changé : on est raccordé à lui si c'est un panneau qui se raccorde à nous. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BooleanProperty connection = CONNECTIONS.get(directionToNeighbour);
        if (connection == null) {
            return state;
        }
        boolean connected = neighbourState.is(this) && neighbourState.getValue(CONNECTIONS.get(directionToNeighbour.getOpposite()));
        return state.setValue(connection, connected);
    }

    /** Tous les éléments du grand panneau auquel appartient celui-ci, en suivant les raccordements. */
    public static Set<BlockPos> array(BlockGetter level, BlockPos start) {
        Set<BlockPos> array = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        array.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && array.size() <= MAX_LENGTH * MAX_WIDTH) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SolarPanelBlock)) {
                continue;
            }
            for (Map.Entry<Direction, BooleanProperty> side : CONNECTIONS.entrySet()) {
                BlockPos next = pos.relative(side.getKey());
                if (state.getValue(side.getValue()) && array.add(next)) {
                    queue.add(next);
                }
            }
        }
        return array;
    }

    /** Vrai si ces éléments tiennent dans un rectangle de six sur trois, dans un sens ou dans l'autre. */
    private static boolean fits(Set<BlockPos> array) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : array) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        return Math.max(sizeX, sizeZ) <= MAX_LENGTH && Math.min(sizeX, sizeZ) <= MAX_WIDTH;
    }

    // ------------------------------------------------------------------
    // Entité de bloc et interaction
    // ------------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SolarPanelBlockEntity panel && tickLevel instanceof ServerLevel serverLevel) {
                panel.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            // Production de tout le grand panneau : chaque élément compte selon qu'il voit le ciel ou non.
            Set<BlockPos> array = array(level, pos);
            int output = 0;
            for (BlockPos element : array) {
                if (level.getBlockEntity(element) instanceof SolarPanelBlockEntity panel) {
                    output += panel.output(serverLevel, element);
                }
            }
            serverPlayer.sendSystemMessage(Component.translatable(output > 0 ? "station.orbitalis.solar.output" : "station.orbitalis.solar.dark",
                    array.size(), output).withStyle(output > 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
        return InteractionResult.SUCCESS;
    }
}
