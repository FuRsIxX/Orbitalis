package com.orbitalis.client;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.particle.OrbitalisParticle;
import com.orbitalis.client.particle.PlanetParticle;
import com.orbitalis.client.render.CapsuleRenderer;
import com.orbitalis.client.render.ElevatorCabinRenderer;
import com.orbitalis.client.render.FluidTankRenderer;
import com.orbitalis.client.render.LaunchTableRenderer;
import com.orbitalis.client.render.MissionDisplayRenderer;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.client.screen.CombustionGeneratorScreen;
import com.orbitalis.client.screen.CryogenicTankScreen;
import com.orbitalis.client.screen.DesignScreen;
import com.orbitalis.client.screen.ElevatorScreen;
import com.orbitalis.client.screen.FluidMachineScreen;
import com.orbitalis.client.screen.GuideScreen;
import com.orbitalis.client.screen.LaunchConsoleScreen;
import com.orbitalis.client.screen.LaunchPadScreen;
import com.orbitalis.client.screen.ProcessingMachineScreen;
import com.orbitalis.client.screen.TrackingStationScreen;
import com.orbitalis.core.guide.GuideBookItem;
import com.orbitalis.core.network.ElevatorPayload;
import com.orbitalis.core.network.LaunchPadInfoPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.core.network.ResearchSyncPayload;
import com.orbitalis.core.network.SatelliteStatusPayload;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.core.registry.ModMenus;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.spaceport.PadKitItem;
import com.orbitalis.platform.network.Network;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.registry.menu.MenuRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Le démarrage du client, commun à Fabric et NeoForge : écrans, rendus, particules, teintes, messages venus du serveur, et
 * relais par lesquels le code commun ouvre les écrans. Jamais chargé sur un serveur dédié.
 */
public final class OrbitalisClient {
    private OrbitalisClient() {
    }

    public static void init() {
        hooks();
        com.orbitalis.client.port.event.ClientEvents.register();
        entityRenderers();
        particles();
        payloads();
        ColorHandlerRegistry.registerBlockColors((state, level, pos, tint) -> {
            boolean gas = state.is(ModBlocks.GAS_PIPE.get());
            if (level == null || pos == null) {
                return com.orbitalis.phase1.conduit.PipeColor.NONE.tint(tint, gas);
            }
            return com.orbitalis.phase1.conduit.PipeBlockEntity.colorAt(level, pos).tint(tint, gas);
        }, ModBlocks.FLUID_PIPE.registrySupplier(), ModBlocks.GAS_PIPE.registrySupplier());
        // Ce qui demande les registres remplis : les écrans des interfaces, les rendus des entités de bloc, les couches.
        ClientLifecycleEvent.CLIENT_SETUP.register(minecraft -> {
            screens();
            blockEntityRenderers();
            renderLayers();
            Orbitalis.LOGGER.info("Orbitalis : initialisation client terminée.");
        });
    }

    /** Les relais du code commun vers le client : ouvrir un écran, jouer un son qui suit un bloc. */
    private static void hooks() {
        GuideBookItem.screenOpener = () -> Minecraft.getInstance().setScreen(new GuideScreen());
        ResearchManager.clientView = ClientResearch::get;
        com.orbitalis.phase1.spaceport.LaunchTableBlockEntity.delugeSound = DelugeSound::start;
        com.orbitalis.phase3.interstellar.ShipyardConsoleBlock.screenOpener =
                pos -> Minecraft.getInstance().setScreen(new com.orbitalis.client.screen.ShipyardScreen(pos));
        com.orbitalis.phase1.rocket.EngineerWorkbenchBlock.screenOpener =
                () -> Minecraft.getInstance().setScreen(new com.orbitalis.client.screen.EngineerScreen());
        com.orbitalis.phase1.spaceport.LaunchTableBlockEntity.engineSound = EngineSound::start;
        com.orbitalis.phase2.station.LifeSupportBlock.hum = StationSounds::hum;
        PadKitItem.airClick = PadKitPlacement::use;
        PadKitItem.blockClick = PadKitPlacement::useOn;
    }

    private static void screens() {
        MenuRegistry.registerScreenFactory(ModMenus.PROCESSING_MACHINE.get(), ProcessingMachineScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.FLUID_MACHINE.get(), FluidMachineScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.CRYOGENIC_TANK.get(), CryogenicTankScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SUIT_WORKBENCH.get(), com.orbitalis.client.screen.SuitWorkbenchScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.DRILL_DERRICK.get(), com.orbitalis.client.screen.DerrickScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.ACCUMULATOR.get(), com.orbitalis.client.screen.AccumulatorScreen::new);
    }

    private static void blockEntityRenderers() {
        BlockEntityRendererRegistry.register(ModBlockEntities.LAUNCH_TABLE.get(), LaunchTableRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.SHIPYARD_CONSOLE.get(), com.orbitalis.client.render.ShipyardConsoleRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.MISSION_DISPLAY.get(), MissionDisplayRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.DRILL_DERRICK.get(), com.orbitalis.client.render.DerrickRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.GLASS_DOOR.get(), com.orbitalis.client.render.GlassDoorRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.RING_DRIVE.get(), com.orbitalis.client.render.RingDriveRenderer::new);
    }

    private static void entityRenderers() {
        EntityRendererRegistry.register(ModEntities.CAPSULE.registrySupplier(), CapsuleRenderer::new);
        EntityRendererRegistry.register(ModEntities.ELEVATOR_CABIN.registrySupplier(), ElevatorCabinRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROVER.registrySupplier(), com.orbitalis.client.render.RoverRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROTOR_DRONE.registrySupplier(), com.orbitalis.client.render.RotorDroneRenderer::new);
    }

    /**
     * Les blocs à découper (pixels transparents) ou translucides : 26.2 le voyait à leurs textures, 1.21.1 veut qu'on le
     * dise (liste écrite par tools/render_layers_121.py).
     */
    private static void renderLayers() {
        // Lue dans le jar du mod : au démarrage du client, les ressources du jeu ne sont pas encore chargées.
        java.io.InputStream in = OrbitalisClient.class.getResourceAsStream("/assets/orbitalis/render_layers.json");
        if (in == null) {
            Orbitalis.LOGGER.error("Couches de rendu des blocs introuvables");
            return;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject layers = JsonParser.parseReader(reader).getAsJsonObject();
            register(layers, "cutout", RenderType.cutout());
            register(layers, "translucent", RenderType.translucent());
        } catch (java.io.IOException | RuntimeException failed) {
            Orbitalis.LOGGER.error("Couches de rendu des blocs illisibles", failed);
        }
    }

    private static void register(JsonObject layers, String name, RenderType type) {
        for (var element : layers.getAsJsonArray(name)) {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(element.getAsString()));
            RenderTypeRegistry.register(type, block);
        }
    }

    /** Particules du mod : chacune a ses images, parcourues au fil de sa vie, et son comportement. */
    private static void particles() {
        particle(ModParticles.EXHAUST_SMOKE, OrbitalisParticle.Style.EXHAUST_SMOKE);
        particle(ModParticles.ENGINE_FLAME, OrbitalisParticle.Style.ENGINE_FLAME);
        particle(ModParticles.SPARK, OrbitalisParticle.Style.SPARK);
        particle(ModParticles.CRYO_MIST, OrbitalisParticle.Style.CRYO_MIST);
        particle(ModParticles.VAPOR, OrbitalisParticle.Style.VAPOR);
        ParticleProviderRegistry.register(ModParticles.PLANET.registrySupplier(), PlanetParticle::provider);
        particle(ModParticles.SOOT, OrbitalisParticle.Style.SOOT);
        particle(ModParticles.LAUNCH_SMOKE, OrbitalisParticle.Style.LAUNCH_SMOKE);
        particle(ModParticles.LAUNCH_STEAM, OrbitalisParticle.Style.LAUNCH_STEAM);
        particle(ModParticles.DELUGE_WATER, OrbitalisParticle.Style.DELUGE_WATER);
        particle(ModParticles.BLAST_FIRE, OrbitalisParticle.Style.BLAST_FIRE);
        particle(ModParticles.BLAST_SMOKE, OrbitalisParticle.Style.BLAST_SMOKE);
        particle(ModParticles.BLAST_CRYO, OrbitalisParticle.Style.BLAST_CRYO);
        particle(ModParticles.TRENCH_FIRE, OrbitalisParticle.Style.TRENCH_FIRE);
        particle(ModParticles.LAUNCH_EMBER, OrbitalisParticle.Style.LAUNCH_EMBER);
        particle(ModParticles.LAUNCH_DUST, OrbitalisParticle.Style.LAUNCH_DUST);
        particle(ModParticles.HEAT_HAZE, OrbitalisParticle.Style.HEAT_HAZE);
        particle(ModParticles.LAUNCH_FLASH, OrbitalisParticle.Style.LAUNCH_FLASH);
    }

    private static void particle(com.orbitalis.platform.registry.DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> type,
            OrbitalisParticle.Style style) {
        ParticleProviderRegistry.register(type.registrySupplier(), sprites -> OrbitalisParticle.provider(style, sprites));
    }

    /** Messages reçus du serveur. */
    private static void payloads() {
        Network.onClient(ResearchSyncPayload.TYPE, (payload, context) -> ClientResearch.receive(payload));
        // Foreuse : l'état complet (réglages, carte, carottage) arrive à l'ouverture puis à chaque commande.
        Network.onClient(com.orbitalis.core.network.DerrickStatusPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().screen instanceof com.orbitalis.client.screen.DerrickScreen screen) {
                screen.receive(payload);
            }
        });
        // Ordinateur de conception : il ouvre l'écran sur son brouillon, puis montre les réglages des autres joueurs.
        Network.onClient(com.orbitalis.core.network.DesignStatePayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.open()) {
                minecraft.setScreen(new DesignScreen(payload));
            } else if (minecraft.screen instanceof DesignScreen screen) {
                screen.receive(payload);
            }
        });
        // Informations d'un pas de tir : clic droit sur la structure formée.
        Network.onClient(LaunchPadInfoPayload.TYPE, (payload, context) -> Minecraft.getInstance().setScreen(new LaunchPadScreen(payload)));
        // Chantier d'une fusée : clic droit sur la table, puis actualisations après chaque pose.
        Network.onClient(com.orbitalis.core.network.ConstructionPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.open() && !payload.plan().isEmpty()) {
                minecraft.setScreen(new com.orbitalis.client.screen.ConstructionScreen(payload));
            } else if (minecraft.screen instanceof com.orbitalis.client.screen.ConstructionScreen screen) {
                screen.update(payload);
            }
        });
        // Satellites de l'agence : clic droit sur une station de poursuite, puis actualisations de l'écran ouvert.
        Network.onClient(SatelliteStatusPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.open()) {
                minecraft.setScreen(new TrackingStationScreen(payload));
            } else if (minecraft.screen instanceof TrackingStationScreen station) {
                station.update(payload);
            }
        });
        // Tuile de terrain de l'atlas : elle va se faire peindre.
        Network.onClient(com.orbitalis.core.network.AtlasTilePayload.TYPE, (payload, context) -> com.orbitalis.client.render.AtlasMap.accept(payload));
        // La tablette de mission, ou la liaison satellite du casque, ouvre l'atlas, centré sur l'endroit où l'on est.
        Network.onClient(com.orbitalis.core.network.AtlasOpenPayload.TYPE, (payload, context) -> Minecraft.getInstance().setScreen(
                new com.orbitalis.client.screen.AtlasScreen(null, payload.source(), payload.source(), true)));
        Network.onClient(com.orbitalis.core.network.AtlasSitesPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.AtlasScreen.acceptSites(payload));
        // Relevé lointain du terrain : il va à la carte de navigation de sa table de lancement.
        Network.onClient(com.orbitalis.core.network.SurveyPayload.TYPE, (payload, context) -> {
            com.orbitalis.client.render.IsoMap.acceptSurvey(payload.table(), payload.lod(), payload.cells());
            com.orbitalis.client.render.StationGlobe.accept(payload.table(), payload.lod(), payload.cells());
        });
        // L'observatoire : il va à l'écran de l'observatoire ouvert depuis cette station.
        Network.onClient(com.orbitalis.core.network.ObservatoryPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.ObservatoryScreen.accept(payload));
        // Relevé de prospection : il va à l'écran de la station qui l'a demandé.
        Network.onClient(com.orbitalis.core.network.ProspectPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().screen instanceof TrackingStationScreen station) {
                station.prospect(payload);
            }
        });
        // Tableau de bord d'une station : clic droit sur un terminal, puis actualisations de l'écran ouvert.
        Network.onClient(com.orbitalis.core.network.StationStatusPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.StationScreen.accept(payload));
        // Le saut en distorsion : les étoiles qui s'étirent, l'éclair, le tunnel, la sortie.
        Network.onClient(com.orbitalis.core.network.WarpPayload.TYPE, (payload, context) -> WarpFx.receive(payload));
        // Poste de pilotage du vaisseau interstellaire.
        Network.onClient(com.orbitalis.core.network.ShipHelmPayload.TYPE,
                (payload, context) -> Minecraft.getInstance().setScreen(new com.orbitalis.client.screen.ShipHelmScreen(payload)));
        // Panneau de commande de l'ascenseur de la tour.
        Network.onClient(ElevatorPayload.PANEL, (payload, context) -> Minecraft.getInstance().setScreen(new ElevatorScreen(payload)));
        // Une fusée se disloque : chaque client met l'accident en scène.
        Network.onClient(com.orbitalis.core.network.BreakupPayload.TYPE, (payload, context) -> BreakupFx.receive(payload));
        Network.onClient(com.orbitalis.core.network.FlightHudPayload.TYPE, (payload, context) -> CockpitHud.onFlight(payload));
        // Les capteurs de la combinaison que porte le joueur.
        Network.onClient(com.orbitalis.core.network.SuitStatusPayload.TYPE, (payload, context) -> SuitHud.onStatus(payload));
        Network.onClient(LaunchStatusPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            CockpitHud.onStatus(payload);
            if (payload.open()) {
                // Depuis un pupitre : le centre de contrôle. Depuis un pupitre d'essai, ou la cabine d'une fusée : le
                // séquenceur de lancement, seul.
                Block block = minecraft.level == null ? null : minecraft.level.getBlockState(payload.console()).getBlock();
                minecraft.setScreen(block instanceof com.orbitalis.phase1.spaceport.LaunchConsoleBlock ? new LaunchConsoleScreen(payload)
                        : new com.orbitalis.client.screen.SequenceScreen(payload, null,
                                block instanceof com.orbitalis.phase1.spaceport.TestConsoleBlock));
            } else if (minecraft.screen instanceof LaunchConsoleScreen console) {
                // Actualisation demandée par l'écran déjà ouvert.
                console.update(payload);
            } else if (minecraft.screen instanceof com.orbitalis.client.screen.SequenceScreen sequence) {
                sequence.update(payload);
            }
        });
    }
}
