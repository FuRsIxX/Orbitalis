package com.orbitalis.phase1.spaceport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.network.LaunchCommandPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;

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
 * Pupitre de lancement : poste du centre de contrôle. Il se relie à la table de lancement la plus proche,
 * affiche l'état de son complexe et de sa fusée, et commande le compte à rebours.
 * Il doit se trouver à distance de sécurité du pas de tir.
 * <p>
 * C'est un poste de travail de trois blocs de large, deux de haut et deux de profondeur ({@link Workstation}).
 */
public class LaunchConsoleBlock extends HorizontalDirectionalBlock implements Workstation.Host {
    public static final MapCodec<LaunchConsoleBlock> CODEC = simpleCodec(LaunchConsoleBlock::new);

    /** Portée du pupitre : au-delà, la table de lancement n'est pas reliée. */
    public static final int RANGE = 64;
    /** Distance de sécurité : en deçà, le pupitre est trop près du pas de tir. */
    public static final int SAFE_DISTANCE = 8;
    /** Portée du pupitre quand un satellite de télécommunications de l'agence relaie ses ordres, en blocs. */
    public static final int RELAY_RANGE = 256;
    /** Distance maximale entre un joueur et le pupitre qu'il commande. */
    private static final double REACH = 8.0;
    /** Liaisons d'écrans en cours : le pupitre désigné par chaque joueur, en attente du clic sur un mur d'écrans. */
    private static final Map<UUID, BlockPos> PENDING_LINKS = new ConcurrentHashMap<>();

    public LaunchConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<LaunchConsoleBlock> codec() {
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
            // L'atlas montre les installations : celle-ci y entre dès qu'elle est posée.
            com.orbitalis.core.atlas.AtlasSites.note(level, pos, com.orbitalis.core.network.AtlasSitesPayload.CONSOLE);
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
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        // Accroupi : on ne consulte pas le pupitre, on commence à lui relier un mur d'écrans.
        if (player.isShiftKeyDown()) {
            PENDING_LINKS.put(player.getUUID(), pos.immutable());
            serverPlayer.sendSystemMessage(Component.translatable("display.orbitalis.link_started").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        if (!isAuthorized(player)) {
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + LaunchTableBlock.RESEARCH_PAD)).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        // Le serveur relève l'état du complexe et l'envoie au joueur, dont le client ouvre l'écran du pupitre.
        PacketDistributor.sendToPlayer(serverPlayer, status(level, pos, true, ResearchManager.agencyKey(serverPlayer)));
        return InteractionResult.SUCCESS;
    }

    /** Pupitre désigné par un joueur pour y relier un mur d'écrans ; la désignation est consommée. */
    public static BlockPos takePendingLink(ServerPlayer player) {
        return PENDING_LINKS.remove(player.getUUID());
    }

    private static boolean isAuthorized(Player player) {
        return player.getAbilities().instabuild || ResearchManager.isUnlocked(player, LaunchTableBlock.RESEARCH_PAD);
    }

    /** Table de lancement reliée à un pupitre : la plus proche dans sa portée, ou {@code null}. */
    public static LaunchTableBlockEntity linkedTable(Level level, BlockPos console) {
        return LaunchTableBlockEntity.nearest(level, console, RANGE);
    }

    /**
     * Table reliée au pupitre d'un joueur de cette agence. Au-delà de la portée ordinaire, il faut un satellite de
     * télécommunications en service : il relaie les ordres jusqu'à un pas de tir bien plus lointain.
     */
    public static LaunchTableBlockEntity linkedTable(Level level, BlockPos console, String agency) {
        LaunchTableBlockEntity table = linkedTable(level, console);
        if (table == null && agency != null && level.getServer() != null
                && com.orbitalis.phase1.satellite.SatelliteManager.hasRelay(level.getServer(), agency)) {
            table = LaunchTableBlockEntity.nearest(level, console, RELAY_RANGE);
        }
        return table;
    }

    /** Vrai si le pupitre est assez loin de la table pour commander un lancement. */
    public static boolean isSafe(BlockPos console, LaunchTableBlockEntity table) {
        return Math.sqrt(table.getBlockPos().distSqr(console)) >= SAFE_DISTANCE;
    }

    /**
     * État du complexe relié à un pupitre.
     *
     * @param open vrai si le client doit ouvrir l'écran, faux pour une simple actualisation
     */
    public static LaunchStatusPayload status(Level level, BlockPos console, boolean open) {
        return status(level, console, open, null);
    }

    /**
     * État du complexe relié à un pupitre, pour un joueur de cette agence : ses satellites lui donnent la météo,
     * et relaient ses ordres plus loin.
     */
    public static LaunchStatusPayload status(Level level, BlockPos console, boolean open, String agency) {
        LaunchTableBlockEntity table = linkedTable(level, console, agency);
        if (table == null) {
            return LaunchStatusPayload.notLinked(console, open);
        }
        if (table.getPhase() == LaunchTableBlockEntity.Phase.IDLE) {
            table.refresh(level);
        }
        LaunchComplex complex = table.getComplex();
        int[] forecast = agency == null || level.getServer() == null ? new int[] {-1, 0}
                : com.orbitalis.phase1.satellite.SatelliteManager.forecast(level.getServer(), agency);
        boolean hasRocket = table.hasRocket();
        RocketStats stats = hasRocket ? table.rocketStats() : null;
        return new LaunchStatusPayload(console, open, true,
                (int) Math.round(Math.sqrt(table.getBlockPos().distSqr(console))), table.isCommissioned(), complex.radius(),
                complex.missingPads(), complex.skyClear(), complex.towerHeight(), complex.arms(), table.isTowerAuthorized(),
                table.requiredTowerHeight(),
                hasRocket, hasRocket ? (float) stats.liftoffMass() : 0.0F, hasRocket ? stats.reach().ordinal() : 0,
                table.needsArm(), table.fuelPercent(), table.isWeatherGo(),
                table.getPhase().ordinal(), table.getSeconds(), (float) table.flightAltitudeKm(), (float) table.flightSpeed(),
                table.getLastResult().ordinal(), table.getLastPoints(), table.isLiquid(), table.areValvesOpen(), table.pressureSeconds(),
                table.isRetracted(), table.hasDeluge(), table.delugeSeconds(),
                table.delugeNeed() <= 0 ? 0 : Math.min(100, 100 * table.getWater() / table.delugeNeed()), table.thrustPercent(),
                table.holdSeconds(), table.flightBits(), table.flightWindowSeconds(), table.getAssist(),
                complex.capacityRadius(), table.busyStep(), table.busyPercent(), table.pollState(), forecast[0], forecast[1],
                table.chillSeconds(), table.internalState(), table.overpressurePercent());
    }

    /**
     * Traite une demande venue de l'écran d'un pupitre, ou du séquenceur ouvert depuis une cabine : actualisation,
     * étape de la séquence de lancement, arrêt.
     */
    public static void handleCommand(ServerPlayer player, LaunchCommandPayload command) {
        Level level = player.level();
        BlockPos console = command.console();
        if (!level.isLoaded(console)) {
            return;
        }
        // Le joueur doit se trouver devant un vrai pupitre et avoir le droit de s'en servir, ou être assis dans la
        // cabine de la fusée que porte cette table de lancement.
        boolean atConsole = level.getBlockState(console).getBlock() instanceof LaunchConsoleBlock
                && player.blockPosition().closerThan(console, REACH) && isAuthorized(player);
        boolean aboard = player.getVehicle() instanceof com.orbitalis.phase2.flight.CapsuleEntity capsule && console.equals(capsule.getTable());
        if (!atConsole && !aboard) {
            return;
        }
        String agency = ResearchManager.agencyKey(player);
        LaunchTableBlockEntity table = linkedTable(level, console, agency);
        if (table != null) {
            int action = command.action();
            LaunchStep[] steps = LaunchStep.values();
            if (action == LaunchCommandPayload.ABORT) {
                table.abort();
            } else if (action >= LaunchCommandPayload.STEP && action < LaunchCommandPayload.STEP + steps.length) {
                LaunchStep step = steps[action - LaunchCommandPayload.STEP];
                // On n'allume pas les moteurs depuis un pupitre posé trop près du pas de tir.
                Component problem = atConsole && step == LaunchStep.IGNITE && !isSafe(console, table)
                        ? Component.translatable("sequence.orbitalis.too_close", SAFE_DISTANCE)
                        : table.request(step, agency);
                if (problem != null) {
                    player.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        PacketDistributor.sendToPlayer(player, status(level, console, false, agency));
    }

}
