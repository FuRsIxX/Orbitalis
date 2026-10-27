package com.orbitalis.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration serveur : règles de jeu partagées par tous les joueurs d'un monde.
 * Fichier : saves/<monde>/serverconfig/orbitalis-server.toml (synchronisé vers les clients).
 */
public final class ServerConfig {
    /** Niveau de réalisme global. */
    public enum Realism {
        /** Arcade : pas de pannes, marges confortables. */
        ARCADE,
        /** Équilibré : réglage par défaut. */
        BALANCED,
        /** Réaliste : contraintes proches du réel. */
        REALISTIC
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Realism> REALISM = BUILDER
            .comment("Niveau de réalisme global : ARCADE, BALANCED ou REALISTIC.")
            .translation("orbitalis.configuration.realism")
            .defineEnum("realism", Realism.BALANCED);

    public static final ModConfigSpec.BooleanValue FAILURES_ENABLED = BUILDER
            .comment("Autorise les pannes et les échecs de lancement liés à la fiabilité des composants.")
            .translation("orbitalis.configuration.failuresEnabled")
            .define("failuresEnabled", true);

    public static final ModConfigSpec.DoubleValue FUEL_CONSUMPTION_MULTIPLIER = BUILDER
            .comment("Multiplicateur de consommation de carburant (1.0 = valeurs normales).")
            .translation("orbitalis.configuration.fuelConsumptionMultiplier")
            .defineInRange("fuelConsumptionMultiplier", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue COST_MULTIPLIER = BUILDER
            .comment("Multiplicateur des coûts (recherche, construction, lancements). 1.0 = valeurs normales.")
            .translation("orbitalis.configuration.costMultiplier")
            .defineInRange("costMultiplier", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.BooleanValue KESSLER_SYNDROME = BUILDER
            .comment("Active l'effet Kessler : trop de débris en orbite provoque des collisions en chaîne.")
            .translation("orbitalis.configuration.kesslerSyndrome")
            .define("kesslerSyndrome", false);

    public static final ModConfigSpec.BooleanValue SATELLITE_LIFETIME = BUILDER
            .comment("Les satellites ont une durée de vie : passé ce délai, ils cessent de fonctionner et doivent être remplacés.")
            .translation("orbitalis.configuration.satelliteLifetime")
            .define("satelliteLifetime", true);

    public static final ModConfigSpec.BooleanValue BOIL_OFF = BUILDER
            .comment("Les ergols cryogéniques chargés dans une fusée s'évaporent lentement tant qu'elle attend sur son pas de tir.")
            .translation("orbitalis.configuration.boilOff")
            .define("boilOff", true);

    public static final ModConfigSpec.EnumValue<com.orbitalis.phase1.spaceport.RocketBreakup.Damage> ACCIDENT_DAMAGE = BUILDER
            .comment("Dégâts d'un accident de fusée. FULL : le pas de tir et le terrain alentour (blocs détruits, cratère, sol calciné, feux). "
                    + "PAD_ONLY : les blocs du pas de tir seulement. NONE : aucun bloc n'est touché.")
            .translation("orbitalis.configuration.accidentDamage")
            .defineEnum("accidentDamage", com.orbitalis.phase1.spaceport.RocketBreakup.Damage.FULL);

    public static final ModConfigSpec.BooleanValue GIVE_GUIDE_BOOK = BUILDER
            .comment("Offre le manuel d'Orbitalis à chaque joueur lors de sa première connexion.")
            .translation("orbitalis.configuration.giveGuideBook")
            .define("giveGuideBook", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }
}
