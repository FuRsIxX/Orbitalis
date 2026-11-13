package com.orbitalis.phase2.station;

import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sas d'amarrage d'une station : un collier d'amarrage et son écoutille, tournés vers l'extérieur. Les capsules
 * de l'agence viennent s'y amarrer en arrivant en orbite ; depuis l'intérieur de la station, un clic droit sur
 * le sas fait remonter à bord de la capsule amarrée. Il est étanche, comme la coque.
 */
public class DockingPortBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DockingPortBlock> CODEC = simpleCodec(DockingPortBlock::new);
    /** Distance maximale entre le sas et une capsule qui y est amarrée, en blocs. */
    private static final double DOCKED_RANGE = 5.0;

    public DockingPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<DockingPortBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Le collier d'amarrage est tourné vers l'extérieur : à l'opposé du joueur qui pose le sas depuis l'intérieur. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        List<CapsuleEntity> capsules = level.getEntitiesOfClass(CapsuleEntity.class, new AABB(pos).inflate(DOCKED_RANGE),
                capsule -> capsule.getPhase() == CapsuleEntity.Phase.ORBIT && !capsule.isVehicle());
        if (capsules.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("station.orbitalis.dock.empty").withStyle(ChatFormatting.GOLD));
        } else {
            serverPlayer.startRiding(capsules.stream().min(Comparator.comparingDouble(capsule -> capsule.distanceToSqr(Vec3.atCenterOf(pos)))).get());
        }
        return InteractionResult.SUCCESS;
    }
}
