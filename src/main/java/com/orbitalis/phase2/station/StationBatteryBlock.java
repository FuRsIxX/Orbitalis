package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * Batterie de station : un accumulateur lithium-ion. Dans une station, elle se raccorde d'elle-même au réseau et le
 * soutient pendant les éclipses ; ailleurs, elle se charge et se décharge par câble. Sa jauge montre sa charge en
 * quatre barres.
 */
public class StationBatteryBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<StationBatteryBlock> CODEC = simpleCodec(StationBatteryBlock::new);
    /** Charge affichée par la jauge, de 0 à 4 barres. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    public StationBatteryBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<StationBatteryBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBatteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof StationBatteryBlockEntity battery && tickLevel instanceof ServerLevel serverLevel) {
                battery.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StationBatteryBlockEntity battery) {
            int stored = battery.getEnergy().getEnergy();
            int capacity = battery.getEnergy().getCapacity();
            serverPlayer.sendSystemMessage(Component.translatable("station.orbitalis.battery.charge", stored / 1000, capacity / 1000,
                    stored * 100L / Math.max(1, capacity)).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}
