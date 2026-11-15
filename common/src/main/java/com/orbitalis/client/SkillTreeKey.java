package com.orbitalis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.screen.SkillTreeScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.orbitalis.client.port.event.ClientHooks.ClientTickEvent;
import com.orbitalis.client.port.event.ClientHooks.RegisterKeyMappingsEvent;

/**
 * La touche de l'arbre de compétences : K au départ, réglable dans les options du jeu, rubrique « Orbitalis ».
 * Elle ouvre l'arbre où que l'on soit, sans avoir à retourner au terminal de recherche.
 */
public final class SkillTreeKey {
    public static final KeyMapping OPEN = new KeyMapping("key.orbitalis.skill_tree", InputConstants.Type.KEYSYM,
            InputConstants.KEY_K, PilotControls.CATEGORY);

    private SkillTreeKey() {
    }

        public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

        public static void afterTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new SkillTreeScreen());
            }
        }
    }
}
