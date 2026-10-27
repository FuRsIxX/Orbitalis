package com.orbitalis.core.guide;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Offre le manuel à chaque joueur lors de sa première connexion à un monde.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class GuideBookGift {
    private static final String RECEIVED_KEY = "orbitalis_guide_received";

    private GuideBookGift() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ServerConfig.GIVE_GUIDE_BOOK.get()) {
            return;
        }
        Player player = event.getEntity();
        // Les données rangées sous PERSISTED_NBT_TAG sont conservées après la mort du joueur.
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        if (persisted.getBooleanOr(RECEIVED_KEY, false)) {
            return;
        }
        persisted.putBoolean(RECEIVED_KEY, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.GUIDE_BOOK.get()));
    }
}
