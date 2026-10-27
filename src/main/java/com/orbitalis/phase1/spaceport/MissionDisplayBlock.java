package com.orbitalis.phase1.spaceport;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.satellite.TrackingStationBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Écran de contrôle : une dalle d'affichage mince, qu'on accroche au mur.
 * <p>
 * Des dalles posées côte à côte et tournées du même côté s'assemblent en un seul écran, d'une seule dalle jusqu'à
 * seize de large sur neuf de haut. L'image passe d'une dalle à l'autre sans couture : seul le tour de l'écran porte
 * un liseré.
 * <p>
 * L'écran n'affiche rien de lui-même : il montre ce que lui fournissent les ordinateurs et les réservoirs auxquels
 * il est relié. Il suffit qu'ils le touchent, ou qu'un câble de données les relie à lui : un pupitre de lancement
 * donne le lanceur et son pas de tir, une station de poursuite les satellites, des réservoirs leurs niveaux. Un
 * pupitre ou une station trop éloignés pour un câble peuvent aussi être désignés à la main (accroupi + clic droit
 * dessus, puis clic droit sur l'écran).
 * <p>
 * Côté serveur, l'écran se réveille une fois par seconde pour relever ce à quoi il est relié.
 */
public class MissionDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<MissionDisplayBlock> CODEC = simpleCodec(MissionDisplayBlock::new);

    /** Ce qu'affiche l'écran. */
    public enum Mode implements StringRepresentable {
        IDLE,
        COUNTDOWN,
        FLIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    /** De chaque côté, vu de face : vrai si l'écran continue sur la dalle voisine. Le liseré ne se dessine qu'ailleurs. */
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** Épaisseur de la dalle, en pixels : elle est plaquée contre le mur derrière elle. */
    private static final int DEPTH = 3;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(0, 0, 16 - DEPTH, 16, 16, 16),
            Direction.SOUTH, Block.box(0, 0, 0, 16, 16, DEPTH),
            Direction.WEST, Block.box(16 - DEPTH, 0, 0, 16, 16, 16),
            Direction.EAST, Block.box(0, 0, 0, DEPTH, 16, 16)));
    /** Intervalle entre deux relevés, en ticks. */
    private static final int REFRESH_TICKS = 20;

    public MissionDisplayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.IDLE)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<MissionDisplayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, UP, DOWN, LEFT, RIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Pour quelqu'un qui regarde l'écran de face, direction qui va vers sa droite. */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    private boolean continues(LevelReader level, BlockPos neighbour, Direction facing) {
        BlockState state = level.getBlockState(neighbour);
        return state.is(this) && state.getValue(FACING) == facing;
    }

    /** L'écran fait face au joueur qui le pose, et se joint aux dalles voisines tournées comme lui. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Les murs d'écrans sont ceux du centre de contrôle : il faut en maîtriser la technologie pour les poser.
        if (!com.orbitalis.core.research.ResearchManager.mayPlace(context, "mission_control")) {
            return null;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction right = rightOf(facing);
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(UP, continues(level, pos.above(), facing)).setValue(DOWN, continues(level, pos.below(), facing))
                .setValue(RIGHT, continues(level, pos.relative(right), facing))
                .setValue(LEFT, continues(level, pos.relative(right.getOpposite()), facing));
    }

    /** Une dalle voisine a été posée ou retirée : le liseré de ce côté apparaît ou disparaît. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Direction facing = state.getValue(FACING);
        Direction right = rightOf(facing);
        BooleanProperty side = directionToNeighbour == Direction.UP ? UP : directionToNeighbour == Direction.DOWN ? DOWN
                : directionToNeighbour == right ? RIGHT : directionToNeighbour == right.getOpposite() ? LEFT : null;
        return side == null ? state : state.setValue(side, continues(level, neighbourPos, facing));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissionDisplayBlockEntity(pos, state);
    }

    /** Côté client seulement : la dalle mesure l'écran dont elle fait partie, pour y dessiner l'image. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof MissionDisplayBlockEntity display) {
                display.clientTick();
            }
        };
    }

    /** L'écran retient l'agence de celui qui le pose : c'est elle dont il suit les satellites, relié à une station de poursuite. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof MissionDisplayBlockEntity display) {
            display.setOwner(ResearchManager.agencyKey(player));
        }
    }

    /**
     * Clic droit. Accroupi : affichage suivant, sur tout l'écran. Après avoir désigné un pupitre ou une station de
     * poursuite (accroupi + clic droit dessus) : l'écran y est relié à distance. Sinon : rappel de ce à quoi
     * l'écran est relié.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        MissionDisplayBlockEntity.Wall wall = MissionDisplayBlockEntity.wallOf(level, pos);
        if (!(level.getBlockEntity(wall.origin()) instanceof MissionDisplayBlockEntity origin)) {
            return InteractionResult.SUCCESS;
        }
        // Accroupi : on passe à l'affichage suivant, sur tout l'écran.
        if (player.isShiftKeyDown()) {
            MissionDisplayBlockEntity.Page next = origin.nextPage();
            for (BlockPos block : wall.blocks()) {
                if (level.getBlockEntity(block) instanceof MissionDisplayBlockEntity display) {
                    display.setPage(next);
                }
            }
            serverPlayer.sendOverlayMessage(Component.translatable("display.orbitalis.page_changed",
                    Component.translatable(next.nameKey())).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        // Liaison à distance aux satellites : accroupi + clic droit sur une station de poursuite, puis clic droit sur l'écran.
        String agency = TrackingStationBlock.takePendingLink(serverPlayer);
        if (agency != null) {
            for (BlockPos block : wall.blocks()) {
                if (level.getBlockEntity(block) instanceof MissionDisplayBlockEntity display) {
                    display.setSatelliteLink(agency);
                    display.refreshSatellites(level.getServer());
                }
            }
            serverPlayer.sendSystemMessage(Component.translatable("display.orbitalis.satellites_linked", wall.width(), wall.height())
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        BlockPos console = LaunchConsoleBlock.takePendingLink(serverPlayer);
        if (console != null) {
            LaunchTableBlockEntity table = LaunchConsoleBlock.linkedTable(level, console);
            if (table == null) {
                serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.console.not_linked", LaunchConsoleBlock.RANGE)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            for (BlockPos block : wall.blocks()) {
                if (level.getBlockEntity(block) instanceof MissionDisplayBlockEntity display) {
                    display.setLink(table.getBlockPos());
                }
            }
            serverPlayer.sendSystemMessage(Component.translatable("display.orbitalis.linked", wall.width(), wall.height())
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        // L'écran prend l'agence de celui qui s'en sert s'il n'en a pas encore (écran posé avant cette règle).
        if (level instanceof ServerLevel serverLevel) {
            origin.adopt(ResearchManager.agencyKey(serverPlayer));
            origin.scan(serverLevel, wall);
        }
        serverPlayer.sendSystemMessage(Component.translatable(origin.hasSource() ? "display.orbitalis.sources" : "display.orbitalis.hint",
                wall.width(), wall.height(), origin.sourceCount()).withStyle(origin.hasSource() ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Premier réveil à la pose ; un simple changement d'affichage ne relance pas un second cycle.
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, REFRESH_TICKS);
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MissionDisplayBlockEntity.Wall wall = MissionDisplayBlockEntity.wallOf(level, pos);
        // La dalle du coin relève ce à quoi l'écran est relié ; les autres suivent son pas de tir pour leur éclairage.
        if (wall.origin().equals(pos) && level.getBlockEntity(pos) instanceof MissionDisplayBlockEntity display) {
            display.scan(level, wall);
            display.refreshSatellites(level.getServer());
        }
        LaunchTableBlockEntity table = level.getBlockEntity(wall.origin()) instanceof MissionDisplayBlockEntity origin
                ? origin.linkedTable() : null;
        Mode mode = table == null ? Mode.IDLE : switch (table.getPhase()) {
            case COUNTDOWN -> Mode.COUNTDOWN;
            case FLIGHT -> Mode.FLIGHT;
            default -> Mode.IDLE;
        };
        if (state.getValue(MODE) != mode) {
            level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, REFRESH_TICKS);
    }
}
