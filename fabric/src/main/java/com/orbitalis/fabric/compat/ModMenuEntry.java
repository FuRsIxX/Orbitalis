package com.orbitalis.fabric.compat;

import com.orbitalis.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Le bouton « Réglages » d'Orbitalis dans Mod Menu (chargé seulement si Mod Menu est installé). */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
