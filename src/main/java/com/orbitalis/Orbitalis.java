package com.orbitalis;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.core.registry.ModFeatures;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModCreativeTabs;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.core.registry.ModRecipes;
import com.orbitalis.gametest.OrbitalisGameTests;
import com.orbitalis.phase1.conduit.ConduitNetwork;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Point d'entrée du mod Orbitalis, chargé côté client et côté serveur.
 */
@Mod(Orbitalis.MODID)
public class Orbitalis {
    /** Identifiant du mod. Doit correspondre à mod_id dans gradle.properties. */
    public static final String MODID = "orbitalis";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Orbitalis(IEventBus modEventBus, ModContainer modContainer) {
        // Le réglage de démarrage d'abord : il dit quel contenu en préparation montrer, et il est lu dès son enregistrement.
        modContainer.registerConfig(ModConfig.Type.STARTUP, com.orbitalis.core.config.StartupConfig.SPEC);
        // Registres (blocs, items, entités de bloc, interfaces, recettes, onglet créatif).
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        com.orbitalis.core.registry.ModDataComponents.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.register(modEventBus);
        ModFeatures.register(modEventBus);
        com.orbitalis.core.registry.ModBiomeSources.register(modEventBus);
        com.orbitalis.core.registry.ModStructures.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        OrbitalisGameTests.register(modEventBus);

        // Configuration : le fichier serveur est synchronisé vers les clients en multijoueur,
        // le fichier client reste local à chaque joueur.
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        // Les attributs des entités vivantes du mod : le rover.
        modEventBus.addListener(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class,
                event -> event.put(ModEntities.ROVER.get(), com.orbitalis.phase3.exploration.RoverEntity.createAttributes().build()));
        // Lueur des câbles et tuyaux en service.
        NeoForge.EVENT_BUS.addListener(ConduitNetwork::onLevelTick);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Orbitalis : initialisation commune terminée.");
    }

    /** Crée un identifiant dans l'espace de noms du mod, par exemple orbitalis:steel_ingot. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
