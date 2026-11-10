package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;

/**
 * L'écran du changement de monde, à l'arrivée dans une orbite. Pendant un saut en distorsion, le joueur ne doit pas voir
 * « Chargement du terrain » : l'écran reste blanc de l'éclair (voir {@link WarpFx}) le temps que le monde d'arrivée se
 * charge, puis s'efface sur le tunnel. Hors d'un saut, c'est l'écran ordinaire du jeu.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public class WarpLoadingScreen extends LevelLoadingScreen {
    public WarpLoadingScreen(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) {
        super(tracker, reason);
    }

    @SubscribeEvent
    static void register(RegisterDimensionTransitionScreenEvent event) {
        for (var orbit : java.util.List.of(SpaceRules.ORBIT, SpaceRules.PROXIMA_ORBIT, SpaceRules.TRAPPIST_ORBIT, SpaceRules.KEPLER_ORBIT,
                SpaceRules.TARANTULA_ORBIT)) {
            event.registerIncomingEffect(orbit, WarpLoadingScreen::new);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!WarpFx.active()) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        graphics.fill(0, 0, width, height, 0xFF000000 | WarpFx.glow());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!WarpFx.active()) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        extractBackground(graphics, mouseX, mouseY, partialTick);
    }
}
