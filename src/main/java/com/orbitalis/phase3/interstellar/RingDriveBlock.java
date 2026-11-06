package com.orbitalis.phase3.interstellar;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le moteur de l'anneau, au plafond du couloir, au droit des rayons : sa couronne dentée fait tourner l'anneau autour
 * du vaisseau, et la force qui plaque l'équipage contre le sol lui tient lieu de pesanteur. Un clic droit le lance ou
 * l'arrête (voir {@link RingDriveBlockEntity}).
 */
public class RingDriveBlock extends Block implements EntityBlock {
    public static final MapCodec<RingDriveBlock> CODEC = simpleCodec(RingDriveBlock::new);

    public RingDriveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RingDriveBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RingDriveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RingDriveBlockEntity drive) {
                drive.tick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RingDriveBlockEntity drive) {
            Component answer = drive.toggle();
            if (answer != null) {
                serverPlayer.sendSystemMessage(answer.copy().withStyle(ChatFormatting.AQUA), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
