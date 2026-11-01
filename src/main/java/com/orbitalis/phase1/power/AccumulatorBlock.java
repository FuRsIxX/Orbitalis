package com.orbitalis.phase1.power;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Accumulateur : un bloc qui stocke l'électricité, sans réglage. On le pose, on branche : sa jauge, cinq crans de
 * charge, regarde le joueur qui le pose ; l'électricité entre par cette face et les quatre autres, et ressort par le dos
 * (voir {@link AccumulatorBlockEntity}). Il en existe quatre générations, chacune fabriquée avec la précédente.
 */
public class AccumulatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Les générations : capacité en FE, débit d'entrée comme de sortie en FE par tick, couleur de la jauge. */
    public enum Tier {
        BASIC(1_000_000, 1_000, 0xFF5BE37D),
        ADVANCED(4_000_000, 4_000, 0xFF3FD8F2),
        ELITE(16_000_000, 16_000, 0xFFE8C03A),
        QUANTUM(64_000_000, 64_000, 0xFFC04BFF);

        public final int capacity;
        public final int transfer;
        public final int color;

        Tier(int capacity, int transfer, int color) {
            this.capacity = capacity;
            this.transfer = transfer;
            this.color = color;
        }
    }

    /** Charge visible sur la jauge de l'avant : 0 vide, 5 pleine. */
    public static final int MAX_CHARGE = 5;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    private final Tier tier;

    public AccumulatorBlock(BlockBehaviour.Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new AccumulatorBlock(properties, tier));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE);
    }

    /** La face avant, celle de la jauge, regarde le joueur qui le pose ; la sortie est au dos. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** En mode créatif, un accumulateur chargé tombe quand même, avec son énergie. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.orbitalis.phase1.machines.MachineDrops.keepInCreative(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AccumulatorBlockEntity accumulator) {
            serverPlayer.openMenu(accumulator, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccumulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AccumulatorBlockEntity accumulator) {
                accumulator.serverTick((ServerLevel) tickLevel, pos, tickState);
            }
        };
    }
}
