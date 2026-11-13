package com.orbitalis.phase1.quarry;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Derrick : le bloc de commande d'une tour de forage (voir {@link DerrickBlockEntity}). Il luit en forant. Un clic
 * droit ouvre son écran : prospection, carottage, filtres et réglages.
 */
public class DerrickBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public DerrickBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(DerrickBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** En mode créatif, un derrick qui contient quelque chose tombe quand même, avec son contenu. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.orbitalis.phase1.machines.MachineDrops.keepInCreative(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Cassée, la foreuse rend à sa tour son aspect à bâtir. */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level$, BlockPos pos, BlockState newState$, boolean movedByPiston) {
        super.onRemove(state, level$, pos, newState$, movedByPiston);
        if (state.is(newState$.getBlock()) || !(level$ instanceof ServerLevel level)) {
            return;
        }
        if (!level.getBlockState(pos).is(this)) {
            DerrickBlockEntity.releaseTower(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof DerrickBlockEntity derrick) {
            derrick.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DerrickBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof DerrickBlockEntity derrick) {
                derrick.serverTick((ServerLevel) tickLevel, pos, tickState);
            }
        };
    }
}
