package com.orbitalis.core.config;

import com.orbitalis.platform.config.ModConfigSpec;

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

    private static final String FILE = "orbitalis-server.toml";

    private ServerConfig() {
    }

    /**
     * Au démarrage d'un monde : lit son fichier. S'il n'existe pas encore, il part de {@code defaultconfigs/orbitalis-server.toml}
     * quand le dossier du jeu en a un (comme NeoForge), sinon des valeurs par défaut.
     */
    public static void loadFor(net.minecraft.server.MinecraftServer server) {
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serverconfig").resolve(FILE);
        java.nio.file.Path model = dev.architectury.platform.Platform.getGameFolder().resolve("defaultconfigs").resolve(FILE);
        if (!java.nio.file.Files.exists(file) && java.nio.file.Files.exists(model)) {
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.copy(model, file);
            } catch (java.io.IOException failed) {
                com.orbitalis.Orbitalis.LOGGER.warn("Réglages par défaut non copiés ({})", model, failed);
            }
        }
        SPEC.load(file);
    }

    /** Envoie les réglages à un joueur (à sa connexion). */
    public static void sendTo(net.minecraft.server.level.ServerPlayer player) {
        com.orbitalis.platform.network.Network.sendToPlayer(player, new com.orbitalis.core.network.ServerConfigPayload(SPEC.snapshot()));
    }

    /** Envoie les réglages à tous les joueurs (après un changement à l'écran, monde ouvert en réseau local). */
    public static void broadcast(net.minecraft.server.MinecraftServer server) {
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
