package com.orbitalis.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration de démarrage : lue avant tout le reste, au lancement du jeu ou du serveur, et propre à chaque
 * installation. Elle décide de ce que le mod montre, avant même que les objets et les recettes soient chargés.
 * Fichier : config/orbitalis-startup.toml. Un changement ne compte qu'au lancement suivant.
 */
public final class StartupConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HIDE_INTERSTELLAR = BUILDER
            .comment("Masque le voyage interstellaire (chantier naval orbital, vaisseau, distorsion, exoplanètes, autres galaxies).",
                    "Masqué, il n'apparaît ni dans les recettes, ni dans l'arbre de recherche, ni dans le guide.",
                    "Sur un serveur, le serveur et chaque joueur doivent avoir le même réglage.")
            .translation("orbitalis.configuration.hide_interstellar")
            .define("hide_interstellar", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StartupConfig() {
    }
}
