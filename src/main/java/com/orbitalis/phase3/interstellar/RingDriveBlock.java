package com.orbitalis.phase3.interstellar;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le moteur de l'anneau, au plafond du couloir, au droit des rayons : sa couronne dentée fera tourner l'anneau autour
 * du vaisseau, et la force qui plaque l'équipage contre le sol de l'anneau lui tiendra lieu de pesanteur.
 */
public class RingDriveBlock extends Block {
    public static final MapCodec<RingDriveBlock> CODEC = simpleCodec(RingDriveBlock::new);

    public RingDriveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RingDriveBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.ring_drive.status").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}
