package com.orbitalis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.UplinkOpenPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.orbitalis.client.port.event.ClientHooks.ClientTickEvent;
import com.orbitalis.client.port.event.ClientHooks.RegisterKeyMappingsEvent;

/**
 * La touche de l'atlas : N au départ, réglable dans les options du jeu. Elle sert à la liaison satellite du casque de
 * la combinaison avancée ; c'est le serveur qui juge si l'atlas peut s'ouvrir, et qui dit sinon ce qui manque.
 */
public final class UplinkKey {
    public static final KeyMapping OPEN = new KeyMapping("key.orbitalis.atlas", InputConstants.Type.KEYSYM,
            InputConstants.KEY_N, PilotControls.CATEGORY);

    private UplinkKey() {
    }

        public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

        public static void afterTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                com.orbitalis.platform.network.Network.sendToServer(new UplinkOpenPayload());
            }
        }
    }
}
