package com.orbitalis.phase1.spaceport;

import java.util.Comparator;
import java.util.List;

import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Télécommande de récupération : un clic droit, où que l'on vise, démonte la fusée posée sur le pas de tir
 * le plus proche et la rend au joueur. Plus besoin de viser la table de lancement sous la fusée.
 * <p>
 * S'il n'y a pas de fusée à reprendre, elle annule le plan de fusée déposé sur ce pas de tir et le rend ; à défaut,
 * elle récupère de la même façon la capsule habitée posée la plus proche.
 */
public class RecoveryRemoteItem extends Item {
    /** Portée de la télécommande, en blocs. */
    public static final double RANGE = 48.0;

    public RecoveryRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(recover(level, serverPlayer));
        }
        return InteractionResult.SUCCESS;
    }

    /** Récupère ce qui peut l'être et renvoie le message à afficher au joueur. */
    private static Component recover(Level level, ServerPlayer player) {
        LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, player.blockPosition(), RANGE);
        if (table != null && table.hasRocket()) {
            ItemStack rocket = table.removeRocket();
            if (rocket.isEmpty()) {
                // Compte à rebours ou vol en cours : on ne démonte pas une fusée qui part.
                return Component.translatable("recovery.orbitalis.busy").withStyle(ChatFormatting.GOLD);
            }
            give(player, rocket);
            return Component.translatable("recovery.orbitalis.rocket").withStyle(ChatFormatting.GREEN);
        }

        // Un plan de fusée déposé, dont la fusée n'est pas encore bâtie : le plan est annulé et rendu. Les blocs déjà
        // posés restent en place, à reprendre à la main.
        if (table != null && table.hasRocketPlan()) {
            ItemStack blueprint = table.takeBlueprint();
            if (blueprint.isEmpty()) {
                return Component.translatable("recovery.orbitalis.busy").withStyle(ChatFormatting.GOLD);
            }
            give(player, blueprint);
            return Component.translatable("recovery.orbitalis.plan").withStyle(ChatFormatting.GREEN);
        }

        List<CapsuleEntity> capsules = level.getEntitiesOfClass(CapsuleEntity.class, player.getBoundingBox().inflate(RANGE),
                capsule -> capsule.getPhase() == CapsuleEntity.Phase.LANDED && !capsule.isVehicle());
        if (!capsules.isEmpty()) {
            CapsuleEntity nearest = capsules.stream().min(Comparator.comparingDouble(capsule -> capsule.distanceToSqr(player))).get();
            nearest.discard();
            give(player, nearest.asItem());
            return Component.translatable("recovery.orbitalis.capsule").withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("recovery.orbitalis.nothing", (int) RANGE).withStyle(ChatFormatting.GRAY);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
