package com.orbitalis.core.guide;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.orbitalis.platform.event.PlayerEvent;

/**
 * Offre le manuel à chaque joueur lors de sa première connexion à un monde.
 */
public final class GuideBookGift {
    private static final String RECEIVED_KEY = "orbitalis_guide_received";

    private GuideBookGift() {
    }

        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ServerConfig.GIVE_GUIDE_BOOK.get()) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        // Les données du joueur rangées par le mod sont conservées après sa mort.
        CompoundTag data = com.orbitalis.platform.PlayerData.of(player);
        if (data.getBoolean(RECEIVED_KEY)) {
            return;
        }
        data.putBoolean(RECEIVED_KEY, true);
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.GUIDE_BOOK.get()));
    }
}
