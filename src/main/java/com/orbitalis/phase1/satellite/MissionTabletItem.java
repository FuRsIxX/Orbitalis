package com.orbitalis.phase1.satellite;

import com.orbitalis.core.atlas.AtlasAccess;
import com.orbitalis.core.network.AtlasOpenPayload;
import com.orbitalis.core.network.ModNetwork;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Tablette de mission : l'atlas du centre de contrôle, dans la main, partout à la surface de la planète. Elle ne
 * reçoit rien d'elle-même : il faut qu'un satellite de télécommunications de l'agence relaie les données. Ouverte,
 * elle montre ce que l'agence connaît autour de l'endroit où l'on se trouve.
 */
public class MissionTabletItem extends Item {
    /** Technologie qui permet de s'en servir. */
    public static final String RESEARCH = "mission_tablet";

    public MissionTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Component refusal = AtlasAccess.tabletRefusal(serverPlayer);
            if (refusal != null) {
                serverPlayer.sendOverlayMessage(refusal);
            } else {
                ModNetwork.send(serverPlayer, new AtlasOpenPayload(serverPlayer.blockPosition()));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
