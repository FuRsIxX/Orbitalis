package com.orbitalis.phase3.interstellar;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Caisson d'hibernation : une capsule dressée, à hublot givré, où un équipier dort debout pendant le voyage, le corps
 * refroidi. Il tient sur deux blocs de haut, contre le flanc de la section d'hibernation.
 */
public class HibernationPodBlock extends TwoHighBlock {
    public static final MapCodec<HibernationPodBlock> CODEC = simpleCodec(HibernationPodBlock::new);

    public HibernationPodBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<HibernationPodBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Le caisson devient celui du joueur : il s'y réveillera s'il meurt, tant que le vaisseau a de l'air.
            BlockPos lower = TwoHighBlock.lower(state, pos);
            Located ship = locate(serverPlayer.level(), lower);
            if (ship == null) {
                serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.pod.status").withStyle(ChatFormatting.AQUA));
            } else {
                com.orbitalis.phase2.survival.RespawnRules.setPodBerth(serverPlayer, lower,
                        com.orbitalis.core.research.ResearchManager.agencyKey(serverPlayer), ship.origin(), ship.out());
                serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.pod.berth").withStyle(ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Le vaisseau dont ce caisson fait partie : sa poupe et sa proue, retrouvées d'après le plan ; ou {@code null}. */
    private record Located(BlockPos origin, Direction out) {
    }

    private static Located locate(Level level, BlockPos lower) {
        for (Direction out : Direction.Plane.HORIZONTAL) {
            for (ShipPlan.Piece piece : ShipPlan.pieces(out)) {
                if (!piece.state().is(com.orbitalis.core.registry.ModBlocks.HIBERNATION_POD.get()) || ShipPlan.free(piece)) {
                    continue;
                }
                BlockPos origin = lower.subtract(piece.offset());
                if (ShipSystems.check(level, origin, out).aboard()) {
                    return new Located(origin, out);
                }
            }
        }
        return null;
    }
}
