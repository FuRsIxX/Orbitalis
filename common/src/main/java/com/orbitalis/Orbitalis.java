package com.orbitalis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.config.StartupConfig;
import com.orbitalis.core.registry.ModBiomeSources;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModCreativeTabs;
import com.orbitalis.core.registry.ModDataComponents;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.core.registry.ModFeatures;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.registry.ModRecipes;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.core.registry.ModStructures;
import com.orbitalis.platform.capability.RegisterCapabilitiesEvent;

import dev.architectury.platform.Platform;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Point d'entrée commun d'Orbitalis pour Minecraft 1.21.1 : appelé par chaque chargeur (Fabric, NeoForge) au démarrage.
 * Tout ce qui est propre à un chargeur reste dans son module ; ici, seulement la logique du mod.
 */
public final class Orbitalis {
    public static final String MODID = "orbitalis";
    public static final Logger LOGGER = LoggerFactory.getLogger("Orbitalis");

    private static boolean capabilities;

    private Orbitalis() {
    }

    /** Un identifiant du mod. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Démarrage commun, des deux côtés. */
    public static void init() {
        // Le réglage de démarrage d'abord : il dit quel contenu en préparation montrer, et il est lu dès l'inscription des registres.
        StartupConfig.SPEC.load(Platform.getConfigFolder().resolve(MODID + "-startup.toml"));
        ServerConfig.SPEC.load(Platform.getConfigFolder().resolve(MODID + "-server.toml"));

        ModBlocks.register();
        ModItems.register();
        ModDataComponents.register();
        ModFluids.register();
        ModBlockEntities.register();
        ModEntities.register();
        ModParticles.register();
        ModSounds.register();
        ModFeatures.register();
        ModBiomeSources.register();
        ModStructures.register();
        ModMenus.register();
        ModRecipes.register();
        ModCreativeTabs.register();

        // Les attributs des entités vivantes du mod : le rover.
        EntityAttributeRegistry.register(ModEntities.ROVER::get, com.orbitalis.phase3.exploration.RoverEntity::createAttributes);

        com.orbitalis.core.network.ModNetwork.register();
        com.orbitalis.platform.event.Events.register();
        LOGGER.info("Orbitalis : démarrage commun");
    }

    /**
     * Ce qu'offrent les blocs du mod (énergie, fluides, objets) : inscrit une fois, quand les entités de bloc existent. Le
     * chargeur l'appelle avant de l'exposer aux autres mods.
     */
    public static synchronized void registerCapabilities() {
        if (!capabilities) {
            capabilities = true;
            ModBlockEntities.registerCapabilities(RegisterCapabilitiesEvent.INSTANCE);
        }
    }

    /** Démarrage propre au client. */
    public static void initClient() {
        ClientConfig.SPEC.load(Platform.getConfigFolder().resolve(MODID + "-client.toml"));
        LOGGER.info("Orbitalis : démarrage du client");
    }
}
