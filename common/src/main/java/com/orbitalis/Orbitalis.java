package com.orbitalis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

/**
 * Point d'entrée commun d'Orbitalis pour Minecraft 1.21.1 : appelé par chaque chargeur (Fabric, NeoForge) au démarrage.
 * Tout ce qui est propre à un chargeur reste dans son module ; ici, seulement la logique du mod.
 */
public final class Orbitalis {
    public static final String MODID = "orbitalis";
    public static final Logger LOGGER = LoggerFactory.getLogger("Orbitalis");

    private Orbitalis() {
    }

    /** Un identifiant du mod. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Démarrage commun, des deux côtés. */
    public static void init() {
        LOGGER.info("Orbitalis : démarrage commun");
    }

    /** Démarrage propre au client. */
    public static void initClient() {
        LOGGER.info("Orbitalis : démarrage du client");
    }
}
