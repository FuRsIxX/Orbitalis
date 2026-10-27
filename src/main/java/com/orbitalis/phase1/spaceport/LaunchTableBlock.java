package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.network.LaunchPadInfoPayload;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketItem;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase1.rocket.RocketStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Table de lancement : se pose au centre d'un carré de dalles de pas de tir. Un clic droit met le complexe
 * en service (si l'agence du joueur maîtrise la technologie), puis ouvre l'écran d'informations du pas de tir.
 */
public class LaunchTableBlock extends BaseEntityBlock {
    public static final MapCodec<LaunchTableBlock> CODEC = simpleCodec(LaunchTableBlock::new);
    /** Vrai quand le complexe est opérationnel : les voyants de la table s'allument. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** Technologie nécessaire pour mettre un pas de tir en service. */
    public static final String RESEARCH_PAD = "launch_pad";
    /** Distance maximale entre un bloc de la structure et sa table : la fusée la plus haute fait une soixantaine de blocs. */
    private static final double STRUCTURE_RANGE = 72.0;

    public LaunchTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<LaunchTableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            // Côté client : seulement l'animation de la tour et des bras.
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof LaunchTableBlockEntity table) {
                    table.clientTick();
                }
            };
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LaunchTableBlockEntity table && tickLevel instanceof ServerLevel serverLevel) {
                table.serverTick(serverLevel);
            }
        };
    }

    /** Avec une fusée assemblée en main : on la pose sur la table, si le complexe est en service et assez grand. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        // Kit de pas de tir : c'est lui qui ouvre son écran, pour changer le plan de cette table.
        if (stack.is(ModItems.PAD_KIT.get())) {
            return InteractionResult.PASS;
        }
        // Plan de fusée : on le dépose sur le pas de tir, et les blocs à poser apparaissent au-dessus de la table.
        if (stack.is(ModItems.ROCKET_BLUEPRINT.get())) {
            if (level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table && player instanceof ServerPlayer serverPlayer) {
                // Un plan d'archive se valide d'abord à l'établi ; et toute fusée demande les technologies de ses pièces.
                if (com.orbitalis.phase1.rocket.RocketBlueprintItem.isArchive(stack)) {
                    serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.archive").withStyle(ChatFormatting.GOLD));
                    return InteractionResult.SUCCESS;
                }
                String missing = serverPlayer.getAbilities().instabuild ? null
                        : com.orbitalis.phase1.rocket.DesignComputerBlock.lockedResearch(serverPlayer,
                                com.orbitalis.phase1.rocket.RocketBlueprintItem.parts(stack));
                if (missing != null) {
                    serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                            Component.translatable("research.orbitalis.node." + missing)).withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                table.refresh(level);
                Component problem = table.applyBlueprint(stack);
                if (problem != null) {
                    serverPlayer.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD));
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.rocket.plan_placed", stack.getHoverName(),
                            table.rocketPieces().size()).withStyle(ChatFormatting.GREEN));
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Charge utile (satellite, capsule, vaisseau) : on la confie à la table, pour la fusée qu'on y bâtit sans plan.
        if (com.orbitalis.phase1.rocket.RocketParts.is(stack, com.orbitalis.phase1.rocket.RocketParts.Kind.PAYLOAD)) {
            if (level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table && player instanceof ServerPlayer serverPlayer) {
                table.refresh(level);
                Component problem = table.offerCargo(stack);
                if (problem != null) {
                    serverPlayer.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD));
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable("free.orbitalis.cargo_loaded", stack.getHoverName()).withStyle(ChatFormatting.GREEN));
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.is(ModItems.ROCKET.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        table.refresh(level);
        RocketStats stats = RocketStats.of(RocketItem.design(stack));
        Component problem = null;
        if (!table.isOperational()) {
            problem = Component.translatable("spaceport.orbitalis.table.not_operational");
        } else if (table.hasRocket()) {
            problem = Component.translatable("spaceport.orbitalis.table.occupied");
        } else if (!stats.isFlightReady()) {
            problem = Component.translatable("spaceport.orbitalis.table.rocket_invalid");
        } else if (stats.liftoffMass() > table.getComplex().maxRocketMass()) {
            problem = Component.translatable("spaceport.orbitalis.table.rocket_too_heavy",
                    (int) (table.getComplex().maxRocketMass() / 1000));
        } else if (!table.isHeightAllowedFor(stack)) {
            problem = Component.translatable("spaceport.orbitalis.table.rocket_too_tall",
                    RocketShape.of(RocketItem.design(stack)).height(), table.getComplex().maxRocketHeight());
        } else if (!table.hasRoomFor(stack)) {
            problem = Component.translatable("spaceport.orbitalis.table.no_room", RocketShape.of(RocketItem.design(stack)).height());
        } else if (!table.setRocket(stack)) {
            problem = Component.translatable("spaceport.orbitalis.table.busy");
        }
        if (problem != null) {
            serverPlayer.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        stack.shrink(1);
        serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.rocket_placed").withStyle(ChatFormatting.GREEN));
        // Satellite à bord : on prévient tout de suite si le lanceur est trop juste pour son orbite de travail.
        SatelliteType satellite = SatelliteType.of(RocketItem.design(table.getRocket()).payload());
        if (satellite != null && !satellite.orbit().isReachedBy(stats.reach())) {
            serverPlayer.sendSystemMessage(Component.translatable("satellite.orbitalis.table.out_of_reach",
                    Component.translatable(satellite.orbit().nameKey()), (int) satellite.orbit().requiredDeltaV(),
                    (int) stats.totalDeltaV()).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        // Accroupi, à main vide, sur un pas de tir qui porte un plan de fusée : une fusée bâtie redevient ses blocs ;
        // sinon, on reprend le plan.
        if (player.isShiftKeyDown() && table.hasRocketPlan()) {
            if (table.hasRocket()) {
                serverPlayer.sendSystemMessage(table.unformRocket(null)
                        ? Component.translatable("spaceport.orbitalis.rocket.unformed").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("spaceport.orbitalis.table.busy").withStyle(ChatFormatting.GOLD));
            } else {
                ItemStack blueprint = table.takeBlueprint();
                if (blueprint.isEmpty()) {
                    serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.busy").withStyle(ChatFormatting.GOLD));
                } else {
                    player.getInventory().placeItemBackInInventory(blueprint);
                    serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.rocket.plan_taken"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Accroupi, à main vide, sans fusée ni plan : on reprend la charge utile confiée à la table.
        if (player.isShiftKeyDown() && !table.hasRocket() && !table.getFreeCargo().isEmpty()) {
            ItemStack cargo = table.takeFreeCargo();
            player.getInventory().placeItemBackInInventory(cargo);
            serverPlayer.sendSystemMessage(Component.translatable("free.orbitalis.cargo_taken", cargo.getHoverName()));
            return InteractionResult.SUCCESS;
        }
        // Accroupi, à main vide : on reprend la fusée posée sur la table.
        if (player.isShiftKeyDown() && table.hasRocket()) {
            ItemStack rocket = table.removeRocket();
            if (rocket.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.busy").withStyle(ChatFormatting.GOLD));
            } else {
                player.getInventory().placeItemBackInInventory(rocket);
                serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.rocket_removed"));
            }
            return InteractionResult.SUCCESS;
        }

        // Une taille qui n'existe plus : le pas de tir ne sert plus tant qu'il n'est pas refait.
        if (table.isRetiredSize()) {
            int side = 2 * table.getPlan().radius() + 1;
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.retired_size", side, side).withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !ResearchManager.isUnlocked(player, RESEARCH_PAD)) {
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + RESEARCH_PAD)).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        table.commission(true);
        // Une fusée en chantier sur un pas de tir formé : l'écran de chantier dit où elle en est, ce qui manque, et
        // pose les couches d'un bouton.
        if (table.getComplex().isComplete() && table.hasRocketPlan() && !table.hasRocket()) {
            PacketDistributor.sendToPlayer(serverPlayer, table.construction(true));
            return InteractionResult.SUCCESS;
        }
        // Pose par couche : la couche cernée de jaune se bâtit d'un clic, avec les blocs que le joueur a sur lui.
        int built = table.buildLayer(serverPlayer);
        if (built > 0) {
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.layer_built", built).withStyle(ChatFormatting.GREEN));
            if (!table.getComplex().isComplete() || (table.hasRocketPlan() && !table.hasRocket())) {
                return InteractionResult.SUCCESS;
            }
        }
        LaunchComplex complex = table.getComplex();
        if (complex.isComplete()) {
            // Structure assemblée : on ouvre l'écran d'informations du pas de tir (une fusée en chantier a le sien).
            openInfo(serverPlayer, table);
        } else {
            // Structure incomplète : on dit précisément ce qu'il reste à poser.
            for (Component line : missingParts(level, table)) {
                serverPlayer.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Nombre de sortes de blocs détaillées dans la liste de ce qui manque. */
    private static final int MISSING_LINES = 10;

    /** Ce qu'il reste à poser pour que le pas de tir soit assemblé : une ligne par sorte de bloc. */
    public static List<Component> missingParts(Level level, LaunchTableBlockEntity table) {
        List<Component> lines = new ArrayList<>();
        PadPlan plan = table.getPlan();
        if (plan == null) {
            lines.add(Component.translatable("spaceport.orbitalis.table.no_plan"));
            return lines;
        }
        Map<Block, Integer> missing = LaunchComplex.missingBlocks(level, table.getBlockPos(), plan);
        if (!missing.isEmpty()) {
            int total = missing.values().stream().mapToInt(Integer::intValue).sum();
            lines.add(Component.translatable("spaceport.orbitalis.table.missing_header", plan.deckSize(), plan.deckSize(), total));
            int shown = 0;
            for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
                if (shown++ == MISSING_LINES) {
                    lines.add(Component.translatable("spaceport.orbitalis.table.missing_more", missing.size() - MISSING_LINES));
                    break;
                }
                lines.add(Component.translatable("spaceport.orbitalis.table.missing_line", entry.getValue(), entry.getKey().getName()));
            }
        }
        if (!table.getComplex().skyClear()) {
            lines.add(Component.translatable("spaceport.orbitalis.table.sky_blocked"));
        }
        return lines;
    }

    /** Ouvre, chez le joueur, l'écran d'informations de ce pas de tir et de sa fusée. */
    public static void openInfo(ServerPlayer player, LaunchTableBlockEntity table) {
        LaunchComplex complex = table.getComplex();
        int[] fuel = table.fuelSnapshot();
        PacketDistributor.sendToPlayer(player, new LaunchPadInfoPayload(table.isCommissioned(), complex.radius(), complex.skyClear(),
                complex.hasDeflector(), complex.hasTrench(), complex.mastHeight(),
                complex.towerHeight(), table.requiredTowerHeight(), complex.arms(), table.isTowerAuthorized(),
                table.getRocket(), table.rocketHeight(), fuel[0], fuel[1], fuel[2], fuel[4], fuel[5], table.getPhase().ordinal()));
    }

    /**
     * Clic droit sur un tronçon de fusée : on monte à bord si elle emporte une capsule habitée ; accroupi, ou si
     * elle n'en emporte pas, on consulte les informations du pas de tir.
     */
    public static void useRocket(ServerPlayer player, Level level, BlockPos rocketBlock) {
        LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, rocketBlock, STRUCTURE_RANGE);
        if (table == null) {
            return;
        }
        // On n'embarque qu'en cliquant sur la cabine elle-même : partout ailleurs sur la fusée, le clic ouvre les
        // informations du pas de tir.
        // C'est la hauteur du clic qui compte : sur une fusée large, la cabine est entourée de flancs invisibles, et
        // c'est l'un d'eux que l'on vise.
        int index = rocketBlock.getY() - table.getBlockPos().getY() - 1;
        boolean cabin = table.hasRocket() && index >= 0 && index < table.rocketShape().height()
                && table.rocketShape().parts().get(index).isCrewModule();
        if (!table.isCrewed() || !cabin || player.isSecondaryUseActive()) {
            table.refresh(level);
            openInfo(player, table);
            return;
        }
        Component problem = table.board(player);
        player.sendSystemMessage(problem == null
                ? Component.translatable("capsule.orbitalis.boarded").withStyle(ChatFormatting.GREEN)
                : problem.copy().withStyle(ChatFormatting.GOLD));
    }

    /**
     * Ouvre les informations du pas de tir auquel appartient un bloc de la structure (dalle ou tronçon de fusée) :
     * celui de la table de lancement la plus proche.
     */
    public static void openInfo(ServerPlayer player, Level level, BlockPos structureBlock) {
        LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, structureBlock, STRUCTURE_RANGE);
        if (table != null) {
            table.refresh(level);
            openInfo(player, table);
        }
    }
}
