package com.orbitalis.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration client : préférences d'affichage propres à chaque joueur.
 * Fichier : config/orbitalis-client.toml
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = BUILDER
            .comment("Densité des particules de flammes et de fumée (0.0 = aucune, 1.0 = normale).")
            .translation("orbitalis.configuration.particleDensity")
            .defineInRange("particleDensity", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue METRIC_UNITS = BUILDER
            .comment("Affiche les statistiques en unités métriques (kg, m/s, kN). Sinon : unités impériales.")
            .translation("orbitalis.configuration.metricUnits")
            .define("metricUnits", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
