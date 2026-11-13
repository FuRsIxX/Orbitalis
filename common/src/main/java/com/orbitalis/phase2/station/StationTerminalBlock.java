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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Terminal de la station : une console murale. Un clic droit ouvre le tableau de bord de la station où il se
 * trouve : son plan, son énergie et son orbite, son air, ses laboratoires, sa serre, son équipage et ses vaisseaux.
 */
public class StationTerminalBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StationTerminalBlock> CODEC = simpleCodec(StationTerminalBlock::new);

    public StationTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<StationTerminalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos station = StationManager.stationAt(serverLevel, pos);
            if (station == null) {
                serverPlayer.sendSystemMessage(Component.translatable("station.orbitalis.terminal.none").withStyle(ChatFormatting.GOLD));
            } else {
                StationStatus.send(serverPlayer, pos, station, true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
