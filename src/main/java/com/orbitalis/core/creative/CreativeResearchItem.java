package com.orbitalis.core.creative;

import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.core.research.ResearchNodes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Dossier de recherche créatif : un objet de test. Un clic droit débloque toutes les technologies
 * de l'agence du joueur et lui donne une réserve de points de recherche. Il n'est pas consommé.
 */
public class CreativeResearchItem extends Item {
    /** Points de recherche donnés à chaque utilisation. */
    private static final int POINTS = 10_000;

    public CreativeResearchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            AgencyResearch research = ResearchManager.of(serverPlayer);
            ResearchNodes.all().forEach(node -> research.forceUnlock(node.id()));
            research.addPoints(POINTS);
            ResearchManager.changed(serverPlayer);
            serverPlayer.sendSystemMessage(Component.translatable("creative.orbitalis.research_dossier.used", POINTS)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return InteractionResult.SUCCESS;
    }
}
