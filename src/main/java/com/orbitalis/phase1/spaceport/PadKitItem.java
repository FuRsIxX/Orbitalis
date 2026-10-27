package com.orbitalis.phase1.spaceport;

import com.orbitalis.core.network.PadKitPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Kit de pas de tir : le plan d'un pas de tir, et sa table de lancement.
 * <p>
 * Un clic droit ouvre l'écran du kit : on y choisit la taille du tablier, la tour (à escalier ou à ascenseur) et
 * l'orientation, en voyant ce que le pas de tir pourra lancer et tous les blocs à réunir. Le plan choisi, le pas de
 * tir s'esquisse là où l'on regarde ; la molette le fait tourner, et un second clic droit, sur le sol, pose la table
 * de lancement un bloc au-dessus (le déflecteur ira dessous). Le plan se dessine alors en jeu : il reste à poser les
 * blocs, un à un. Un clic droit sur une table déjà posée change son plan.
 * <p>
 * Le choix et l'esquisse se passent chez le client (voir {@code PadKitPlacement}) ; le serveur ne fait que poser.
 */
public class PadKitItem extends Item {
    /** Distance maximale entre le joueur et la table qu'il pose ou règle, en blocs. */
    private static final double REACH = 10.0;

    /** Clic droit sur un bloc, côté client. */
    @FunctionalInterface
    public interface BlockClick {
        void accept(BlockPos clicked, Direction face, Direction look);
    }

    /** Relais vers le client : clic droit dans le vide, et clic droit sur un bloc. */
    public static Runnable airClick = () -> {
    };
    public static BlockClick blockClick = (clicked, face, look) -> {
    };

    public PadKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            blockClick.accept(context.getClickedPos(), context.getClickedFace(), context.getHorizontalDirection());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            airClick.run();
        }
        return InteractionResult.SUCCESS;
    }

    /** Le joueur a validé un plan : on pose la table s'il le faut, et on lui confie le plan. */
    public static void handle(ServerPlayer player, PadKitPayload payload) {
        ServerLevel level = player.level();
        BlockPos pos = payload.table();
        boolean holding = player.getMainHandItem().is(ModItems.PAD_KIT.get()) || player.getOffhandItem().is(ModItems.PAD_KIT.get());
        if (!holding || !level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
            return;
        }
        // Seules les tailles proposées se posent : une demande d'une autre taille est ramenée à la plus proche.
        PadPlan plan = PadPlan.forKit(payload.radius(), payload.elevator(), payload.facing());
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.LAUNCH_TABLE.get())) {
            if (!state.canBeReplaced() || level.isOutsideBuildHeight(pos)) {
                player.sendSystemMessage(Component.translatable("spaceport.orbitalis.kit.no_room").withStyle(ChatFormatting.GOLD));
                return;
            }
            level.setBlockAndUpdate(pos, ModBlocks.LAUNCH_TABLE.get().defaultBlockState());
            level.playSound(null, pos, com.orbitalis.core.registry.ModSounds.PART_PLACED.get(), SoundSource.BLOCKS, 1.0F, 0.9F);
            // Le kit est la table et son plan : il est consommé quand la table est posée.
            if (!player.getAbilities().instabuild) {
                (player.getMainHandItem().is(ModItems.PAD_KIT.get()) ? player.getMainHandItem() : player.getOffhandItem()).shrink(1);
            }
        }
        if (level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table) {
            if (table.hasRocket() || table.getPhase() != LaunchTableBlockEntity.Phase.IDLE) {
                player.sendSystemMessage(Component.translatable("spaceport.orbitalis.table.busy").withStyle(ChatFormatting.GOLD));
                return;
            }
            table.setPlan(plan);
            player.sendSystemMessage(Component.translatable("spaceport.orbitalis.kit.placed", plan.deckSize(), plan.deckSize(),
                    plan.pieces().size()).withStyle(ChatFormatting.GREEN));
        }
    }
}
