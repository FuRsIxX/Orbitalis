package com.orbitalis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.screen.SkillTreeScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * La touche de l'arbre de compétences : K au départ, réglable dans les options du jeu, rubrique « Orbitalis ».
 * Elle ouvre l'arbre où que l'on soit, sans avoir à retourner au terminal de recherche.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class SkillTreeKey {
    public static final KeyMapping OPEN = new KeyMapping("key.orbitalis.skill_tree", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_K, PilotControls.CATEGORY);

    private SkillTreeKey() {
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
                minecraft.gui.setScreen(new SkillTreeScreen());
            }
        }
    }
}
