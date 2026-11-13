package com.orbitalis.phase3.exploration;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un atterrisseur robotique posé sur la Lune ou sur Mars par une mission sans équipage : un corps doré sur quatre
 * jambes, deux ailes de panneaux solaires, une antenne et son voyant. Il n'est pas fabriqué : il arrive par fusée.
 * Un équipage qui le trouve peut l'interroger d'un clic droit.
 */
public class RoboticLanderBlock extends Block {
    public static final MapCodec<RoboticLanderBlock> CODEC = simpleCodec(RoboticLanderBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 11, 14), Block.box(5.5, 11, 5.5, 10.5, 15.3, 10.5));

    public RoboticLanderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RoboticLanderBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("exploration.orbitalis.lander.info").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}
