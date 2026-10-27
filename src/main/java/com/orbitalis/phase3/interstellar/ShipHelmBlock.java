package com.orbitalis.phase3.interstellar;

import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le poste de pilotage, sur la passerelle, face à la verrière de la proue. Un clic droit y fait le bilan du vaisseau :
 * sa coque et chacun de ses systèmes, puis s'il est prêt à partir.
 */
public class ShipHelmBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ShipHelmBlock> CODEC = simpleCodec(ShipHelmBlock::new);

    public ShipHelmBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ShipHelmBlock> codec() {
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

    /** La poupe du vaisseau dont ce poste serait le poste de pilotage : il regarde la poupe, la proue est derrière lui. */
    public static BlockPos shipOrigin(BlockPos helm, Direction facing) {
        Direction out = facing.getOpposite();
        return helm.subtract(ShipPlan.local(out, ShipPlan.HELM_A, 0, ShipPlan.HELM_Y));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Direction out = state.getValue(FACING).getOpposite();
            ShipSystems systems = ShipSystems.check(level, shipOrigin(pos, state.getValue(FACING)), out);
            if (!systems.aboard()) {
                serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.helm.not_aboard").withStyle(ChatFormatting.GOLD));
            } else {
                systems.report().forEach(serverPlayer::sendSystemMessage);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
