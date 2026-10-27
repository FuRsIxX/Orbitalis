package com.orbitalis.phase3.interstellar;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le pupitre du chantier naval orbital, dans la salle de contrôle du chantier, face à sa verrière. Un clic droit ouvre
 * l'écran du chantier : l'avancement du vaisseau, section par section, et de quoi poser ce qui manque.
 */
public class ShipyardConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ShipyardConsoleBlock> CODEC = simpleCodec(ShipyardConsoleBlock::new);
    /** Ouvre l'écran du chantier chez le joueur ; remplacé côté client. */
    public static Consumer<BlockPos> screenOpener = pos -> {
    };

    public ShipyardConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ShipyardConsoleBlock> codec() {
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
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof ShipyardConsoleBlockEntity console) {
            if (console.linked()) {
                screenOpener.accept(pos);
            } else {
                player.sendSystemMessage(Component.translatable("shipyard.orbitalis.unlinked").withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipyardConsoleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ShipyardConsoleBlockEntity console) {
                if (tickLevel.isClientSide()) {
                    console.clientTick();
                } else {
                    console.serverTick();
                }
            }
        };
    }
}
