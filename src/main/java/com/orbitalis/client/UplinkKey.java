package com.orbitalis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.UplinkOpenPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * La touche de l'atlas : N au départ, réglable dans les options du jeu. Elle sert à la liaison satellite du casque de
 * la combinaison avancée ; c'est le serveur qui juge si l'atlas peut s'ouvrir, et qui dit sinon ce qui manque.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class UplinkKey {
    public static final KeyMapping OPEN = new KeyMapping("key.orbitalis.atlas", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_N, PilotControls.CATEGORY);

    private UplinkKey() {
    }

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @SubscribeEvent
    static void afterTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(new UplinkOpenPayload());
            }
        }
    }
}
