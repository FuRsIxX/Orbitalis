package com.orbitalis.phase1.satellite;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.spaceport.Workstation;

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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Station de poursuite : le poste, coiffé d'une parabole, d'où l'on suit les satellites de l'agence du joueur. C'est
 * un poste de travail de trois blocs de large, deux de haut et deux de profondeur ({@link Workstation}).
 * <p>
 * Un clic droit ouvre la carte des orbites et la liste des satellites, et permet de recevoir les données
 * qu'ils ont en mémoire. L'antenne doit voir le ciel. Le bloc ne stocke rien : les satellites appartiennent
 * à l'agence, pas à la station. On peut donc en poser plusieurs, toutes affichent la même chose.
 */
public class TrackingStationBlock extends HorizontalDirectionalBlock implements Workstation.Host {
    public static final MapCodec<TrackingStationBlock> CODEC = simpleCodec(TrackingStationBlock::new);
    /** Liaisons d'écrans en cours : l'agence de chaque joueur qui a désigné une station, en attente du clic sur un mur. */
    private static final Map<UUID, String> PENDING_LINKS = new ConcurrentHashMap<>();

    /** Agence désignée par un joueur pour y relier un mur d'écrans ; la désignation est consommée. */
    public static String takePendingLink(ServerPlayer player) {
        return PENDING_LINKS.remove(player.getUUID());
    }

    public TrackingStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<TrackingStationBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Il faut la place de tout le meuble. Les écrans font face au joueur qui le pose. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = Workstation.placement(context);
        return facing == null ? null : defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            Workstation.placeWings(level, pos, state);
            // Le relevé de la planète se prépare dès la pose : l'écran l'aura tout de suite.
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.orbitalis.core.atlas.StationSurvey.start(serverLevel, pos);
            }
            // L'atlas montre les installations : celle-ci y entre dès qu'elle est posée.
            com.orbitalis.core.atlas.AtlasSites.note(level, pos, com.orbitalis.core.network.AtlasSitesPayload.STATION);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Workstation.removeWings(level, pos, state);
        com.orbitalis.core.atlas.AtlasSites.forget(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Accroupi : on ne consulte pas la station, on commence à relier un mur d'écrans aux satellites de l'agence.
            if (player.isShiftKeyDown()) {
                PENDING_LINKS.put(player.getUUID(), ResearchManager.agencyKey(serverPlayer));
                serverPlayer.sendSystemMessage(Component.translatable("display.orbitalis.link_started").withStyle(ChatFormatting.AQUA));
                return InteractionResult.SUCCESS;
            }
            PacketDistributor.sendToPlayer(serverPlayer, SatelliteManager.status(serverPlayer, pos, true));
            // Le relevé de la planète, pour la carte des orbites : lancé tout de suite, envoyé dès qu'il est prêt.
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.orbitalis.core.atlas.StationSurvey.request(serverPlayer, serverLevel, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
