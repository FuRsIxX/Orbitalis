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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Baie d'expériences : le meuble du laboratoire. En orbite, dans une pièce pressurisée et sous tension, elle mène des
 * expériences en apesanteur les unes après les autres ; chacune rapporte des points de recherche à l'agence de la
 * station. Un clic droit affiche ce qu'elle fait, ou ce qui lui manque.
 */
public class LabRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<LabRackBlock> CODEC = simpleCodec(LabRackBlock::new);
    /** Vrai pendant une expérience : l'écran et les voyants s'animent. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public LabRackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<LabRackBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabRackBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LabRackBlockEntity rack && tickLevel instanceof ServerLevel serverLevel) {
                rack.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LabRackBlockEntity rack) {
            Component message = switch (rack.getStatus()) {
                case NOT_IN_ORBIT -> Component.translatable("station.orbitalis.lab.ground").withStyle(ChatFormatting.GOLD);
                case NO_AIR -> Component.translatable("station.orbitalis.lab.no_air").withStyle(ChatFormatting.GOLD);
                case NO_POWER -> Component.translatable("station.orbitalis.lab.no_power", LabRackBlockEntity.ENERGY_PER_TICK)
                        .withStyle(ChatFormatting.RED);
                case RUNNING -> Component.translatable("station.orbitalis.lab.running", rack.progressPercent(), rack.getExperiments())
                        .withStyle(ChatFormatting.GREEN);
            };
            serverPlayer.sendSystemMessage(message);
        }
        return InteractionResult.SUCCESS;
    }
}
