package com.orbitalis.phase2.station;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Porte de sas : deux blocs de haut, un cadre mince au milieu de la paroi, et une écoutille d'un seul tenant, à hublot,
 * qui remonte dans le caisson du linteau.
 * <p>
 * Elle s'ouvre d'elle-même quand on s'avance vers elle en la regardant, ou qu'on se tient dans le passage : le sas se
 * met d'abord à niveau (on entend l'air s'échapper ou revenir, le voyant passe à l'ambre), puis l'écoutille remonte en
 * trois temps et le voyant passe au vert. Une fois le passage libre, elle redescend. Un clic droit la ferme tout de
 * suite (ou l'ouvre, si elle est fermée). Fermée, elle est étanche, voyant rouge. Chaque position de l'écoutille est un
 * vrai modèle de bloc : rien n'est dessiné en mouvement.
 * <p>
 * Les portes d'un même sas sont verrouillées entre elles : tant que l'une est ouverte ou en cycle, les autres restent
 * fermées. On ne peut donc jamais ouvrir la station sur le vide par mégarde.
 */
public class AirlockDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AirlockDoorBlock> CODEC = simpleCodec(AirlockDoorBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Phase> PHASE = EnumProperty.create("phase", Phase.class);
    /** Position de l'écoutille : 0 fermée, 3 rentrée dans le linteau. */
    public static final int MAX_STEP = 3;
    public static final IntegerProperty STEP = IntegerProperty.create("step", 0, MAX_STEP);
    /** Hauteur du bas de l'écoutille au-dessus du sol, à chaque position, en pixels (le seuil fait un pixel). */
    public static final int[] STEP_HEIGHT = {1, 11, 21, 31};
    /** Haut du passage : au-dessus, le caisson du linteau, où l'écoutille se range (en pixels, depuis le seuil). */
    public static final float HOUSING = 29.5F;
    /** Distance dans laquelle les portes d'un même sas se verrouillent entre elles, en blocs. */
    public static final int INTERLOCK_RANGE = 6;

    /** Où en est la porte. */
    public enum Phase implements StringRepresentable {
        /** Fermée et étanche : voyant rouge. */
        CLOSED("closed"),
        /** Le sas se met à niveau avant d'ouvrir : voyant ambre. */
        CYCLING("cycling"),
        /** Ouverte, ou l'écoutille qui remonte : voyant vert, l'air passe. */
        OPEN("open"),
        /** L'écoutille redescend : voyant ambre, déjà étanche. */
        CLOSING("closing");

        private final String name;

        Phase(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private static final Map<String, VoxelShape> SHAPES = new HashMap<>();

    public AirlockDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(PHASE, Phase.CLOSED).setValue(STEP, 0));
    }

    @Override
    protected MapCodec<AirlockDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, PHASE, STEP);
    }

    /** Vrai si cette case est une porte de sas ouverte : l'air passe. */
    public static boolean isOpen(BlockState state) {
        return state.getBlock() instanceof AirlockDoorBlock && state.getValue(PHASE) == Phase.OPEN;
    }

    // ------------------------------------------------------------------
    // Formes : le cadre, le seuil ou le caisson, et ce qui reste de l'écoutille dans la case
    // ------------------------------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean alongX = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        int step = state.getValue(STEP);
        return SHAPES.computeIfAbsent(alongX + "/" + lower + "/" + step, key -> shape(alongX, lower, step));
    }

    /**
     * Grande ouverte, la porte ne retient personne : on passe sans se cogner au cadre ni au caisson. Sinon, elle arrête
     * là où se trouve l'écoutille.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PHASE) == Phase.OPEN && state.getValue(STEP) == MAX_STEP ? Shapes.empty() : getShape(state, level, pos, context);
    }

    private static VoxelShape shape(boolean alongX, boolean lower, int step) {
        // Les montants et leur rebord, puis le seuil ou le caisson du linteau.
        VoxelShape shape = Shapes.or(box(alongX, 0, 0, 3.5, 3, 16, 12.5), box(alongX, 13, 0, 3.5, 16, 16, 12.5));
        shape = Shapes.or(shape, lower ? box(alongX, 3, 0, 4, 13, 1, 12) : box(alongX, 0, 13.5, 3.5, 16, 16, 12.5));
        int base = lower ? 0 : 16;
        float from = Math.max(STEP_HEIGHT[step], base);
        float to = Math.min(Math.min(STEP_HEIGHT[step] + 29.0F, HOUSING), base + 16);
        if (to > from) {
            shape = Shapes.or(shape, box(alongX, 2, from - base, 6.5, 14, to - base, 9.5));
        }
        return shape;
    }

    /** Un pavé dessiné pour une porte qui court le long de x, tourné d'un quart de tour si elle court le long de z. */
    private static VoxelShape box(boolean alongX, double x0, double y0, double z0, double x1, double y1, double z1) {
        return alongX ? Block.box(x0, y0, z0, x1, y1, z1) : Block.box(z0, y0, x0, z1, y1, x1);
    }

    // ------------------------------------------------------------------
    // Deux blocs de haut
    // ------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            // L'autre moitié a disparu : celle-ci s'en va aussi. Sinon, les deux suivent la même phase et la même position.
            return neighbourState.is(this) && neighbourState.getValue(HALF) != half
                    ? state.setValue(PHASE, neighbourState.getValue(PHASE)).setValue(FACING, neighbourState.getValue(FACING))
                            .setValue(STEP, neighbourState.getValue(STEP))
                    : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** En créatif, casser le haut ne doit pas faire tomber la porte du bas en objet. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------------
    // Ouverture et fermeture
    // ------------------------------------------------------------------

    /** La moitié basse de la porte qui occupe cette case. */
    public static BlockPos lowerOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Un clic droit : la porte ouverte se ferme tout de suite, la porte fermée s'ouvre. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos lower = lowerOf(state, pos);
            boolean wasOpen = state.getValue(PHASE) == Phase.OPEN;
            Component problem = operate(serverLevel, lower);
            if (problem != null) {
                player.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.GOLD));
            } else if (serverLevel.getBlockEntity(lower) instanceof AirlockDoorBlockEntity door) {
                door.operatedByHand(wasOpen);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Ouvre ou ferme la porte, pour le code (essais, aperçus) : ouverte ainsi, elle reste ouverte jusqu'à ce qu'on la
     * ferme. Fermée, elle lance son cycle d'ouverture, sauf si une autre porte du même sas est ouverte ou en cycle ;
     * ouverte, elle se referme.
     *
     * @return ce qui l'en empêche, ou {@code null}
     */
    public static Component toggle(ServerLevel level, BlockPos pos) {
        Component problem = operate(level, pos);
        BlockState state = level.getBlockState(pos);
        if (problem == null && state.getBlock() instanceof AirlockDoorBlock) {
            hold(level, lowerOf(state, pos), state.getValue(PHASE) != Phase.CLOSING);
        }
        return problem;
    }

    /** Tient la porte ouverte (elle ne se refermera pas d'elle-même), ou la rend à son automatisme. */
    public static void hold(Level level, BlockPos lower, boolean held) {
        if (level.getBlockEntity(lower) instanceof AirlockDoorBlockEntity door) {
            door.setHeld(held);
        }
    }

    /** Ouvre ou ferme la porte, comme le fait la porte d'elle-même. */
    static Component operate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirlockDoorBlock)) {
            return null;
        }
        BlockPos lower = lowerOf(state, pos);
        switch (state.getValue(PHASE)) {
            case CLOSED -> {
                if (interlocked(level, lower)) {
                    return Component.translatable("station.orbitalis.airlock.interlock");
                }
                setPhase(level, lower, Phase.CYCLING);
                if (level.getBlockEntity(lower) instanceof AirlockDoorBlockEntity door) {
                    door.startCycle();
                }
                level.playSound(null, lower.above(), ModSounds.AIRLOCK_CYCLE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            case CYCLING -> {
                return Component.translatable("station.orbitalis.airlock.cycling");
            }
            case CLOSING -> {
                // Elle redescend : on la fait remonter.
                setPhase(level, lower, Phase.OPEN);
                level.playSound(null, lower.above(), ModSounds.AIRLOCK_HATCH_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            case OPEN -> {
                setPhase(level, lower, Phase.CLOSING);
                level.playSound(null, lower.above(), ModSounds.AIRLOCK_HATCH_CLOSE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        return null;
    }

    /**
     * Vrai si l'autre porte du même sas est ouverte ou en cycle : celle-ci doit rester fermée. L'autre porte est dans
     * l'axe du passage, tournée dans le même sens, à moins de {@link #INTERLOCK_RANGE} blocs. Les portes d'un autre
     * sas, même proches, ne comptent pas.
     */
    public static boolean interlocked(ServerLevel level, BlockPos lower) {
        BlockState self = level.getBlockState(lower);
        if (!(self.getBlock() instanceof AirlockDoorBlock)) {
            return false;
        }
        Direction.Axis axis = self.getValue(FACING).getAxis();
        for (int along = -INTERLOCK_RANGE; along <= INTERLOCK_RANGE; along++) {
            if (along == 0) {
                continue;
            }
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos other = axis == Direction.Axis.X ? lower.offset(along, dy, 0) : lower.offset(0, dy, along);
                BlockState state = level.getBlockState(other);
                if (state.getBlock() instanceof AirlockDoorBlock && state.getValue(FACING).getAxis() == axis
                        && state.getValue(PHASE) != Phase.CLOSED) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Met les deux moitiés de la porte dans cette phase. */
    public static void setPhase(Level level, BlockPos lower, Phase phase) {
        set(level, lower, state -> state.setValue(PHASE, phase));
    }

    /** Met l'écoutille des deux moitiés à cette position. */
    public static void setStep(Level level, BlockPos lower, int step) {
        set(level, lower, state -> state.setValue(STEP, step));
    }

    private static void set(Level level, BlockPos lower, java.util.function.UnaryOperator<BlockState> change) {
        BlockState state = level.getBlockState(lower);
        if (!(state.getBlock() instanceof AirlockDoorBlock)) {
            return;
        }
        level.setBlock(lower, change.apply(state), Block.UPDATE_ALL);
        BlockState upper = level.getBlockState(lower.above());
        if (upper.getBlock() instanceof AirlockDoorBlock) {
            level.setBlock(lower.above(), change.apply(upper), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------
    // Entité : la présence, le cycle et la montée de l'écoutille (serveur)
    // ------------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new AirlockDoorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AirlockDoorBlockEntity door && tickLevel instanceof ServerLevel serverLevel) {
                door.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
