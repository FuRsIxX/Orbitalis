package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import com.orbitalis.phase3.interstellar.TwoHighBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Port de jonction : une écoutille ronde fermée, de deux blocs de haut, au milieu d'une face de nœud ou de module. C'est
 * là, et seulement là, qu'un module s'accroche : un kit en main, on vise le port depuis l'intérieur, le module se dessine
 * en fantôme de l'autre côté, dans l'axe, et un clic droit le déploie ; le port s'ouvre alors en passage.
 * <p>
 * Il fait partie de la paroi : plein et étanche. {@link #FACING} regarde vers l'extérieur, là où le module se bâtira.
 * On le pose depuis l'intérieur, en regardant dehors, dans une ouverture de deux blocs de haut : c'est ainsi qu'une
 * station bâtie avant les ports en gagne.
 */
public class StationPortBlock extends TwoHighBlock {
    public static final MapCodec<StationPortBlock> CODEC = simpleCodec(StationPortBlock::new);

    public StationPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<StationPortBlock> codec() {
        return CODEC;
    }

    /** Posé depuis l'intérieur : le port regarde là où regarde le joueur, vers le dehors. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("station.orbitalis.port.hint").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** La moitié haute du port dont ce bloc fait partie : le repère des modules s'y attache, à hauteur d'yeux. */
    public static BlockPos upper(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
    }

    /** Un port de jonction, haut et bas, tourné vers {@code out}. */
    public static BlockState state(Direction out, DoubleBlockHalf half) {
        return com.orbitalis.core.registry.ModBlocks.STATION_PORT.get().defaultBlockState().setValue(FACING, out).setValue(HALF, half);
    }
}
