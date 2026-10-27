package com.orbitalis;

import com.orbitalis.client.screen.DesignScreen;
import com.orbitalis.phase1.rocket.DesignComputerBlock;
import com.orbitalis.client.screen.ElevatorScreen;
import com.orbitalis.core.network.ElevatorPayload;
import com.orbitalis.client.PadKitPlacement;
import com.orbitalis.phase1.spaceport.PadKitItem;
import com.orbitalis.client.guide.GuideSceneRenderState;
import com.orbitalis.client.guide.GuideSceneRenderer;
import com.orbitalis.client.screen.CombustionGeneratorScreen;
import com.orbitalis.client.screen.CryogenicTankScreen;
import com.orbitalis.client.screen.FluidMachineScreen;
import com.orbitalis.client.screen.GuideScreen;
import com.orbitalis.client.screen.ProcessingMachineScreen;
import com.orbitalis.core.guide.GuideBookItem;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import com.orbitalis.client.render.LaunchTableRenderer;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.client.particle.OrbitalisParticle;
import com.orbitalis.client.particle.PlanetParticle;
import com.orbitalis.client.render.CapsuleRenderer;
import com.orbitalis.client.render.ElevatorCabinRenderer;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.client.render.FluidTankRenderer;
import com.orbitalis.client.render.MissionDisplayRenderer;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.client.screen.LaunchConsoleScreen;
import com.orbitalis.client.screen.LaunchPadScreen;
import com.orbitalis.client.screen.TrackingStationScreen;
import com.orbitalis.core.network.SatelliteStatusPayload;
import com.orbitalis.core.network.LaunchPadInfoPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.core.network.ResearchSyncPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Point d'entrée client. Cette classe n'est jamais chargée sur un serveur dédié :
 * on peut donc y utiliser le code réservé au client sans risque.
 */
@Mod(value = Orbitalis.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public class OrbitalisClient {
    public OrbitalisClient(ModContainer container) {
        // Écran de configuration accessible depuis Mods > Orbitalis > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Le manuel ouvre son écran via ce relais (voir GuideBookItem).
        GuideBookItem.screenOpener = () -> Minecraft.getInstance().gui.setScreen(new GuideScreen());
        ResearchManager.clientView = ClientResearch::get;
        // L'ordinateur de conception aussi.
        com.orbitalis.phase1.spaceport.LaunchTableBlockEntity.delugeSound = com.orbitalis.client.DelugeSound::start;
        DesignComputerBlock.screenOpener = () -> Minecraft.getInstance().gui.setScreen(new DesignScreen());
        com.orbitalis.phase3.interstellar.ShipyardConsoleBlock.screenOpener =
                pos -> Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.ShipyardScreen(pos));
        com.orbitalis.phase1.rocket.EngineerWorkbenchBlock.screenOpener =
                () -> Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.EngineerScreen());
        // Le kit de pas de tir passe par le même relais : écran, esquisse et pose du plan.
        // Le grondement des moteurs : une boucle que le client tient de l'allumage à l'espace.
        com.orbitalis.phase1.spaceport.LaunchTableBlockEntity.engineSound = com.orbitalis.client.EngineSound::start;
        // Le ronronnement des générateurs d'air de la station.
        com.orbitalis.phase2.station.LifeSupportBlock.hum = com.orbitalis.client.StationSounds::hum;
        PadKitItem.airClick = PadKitPlacement::use;
        PadKitItem.blockClick = PadKitPlacement::useOn;
        // Même principe pour le terminal de recherche.
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Orbitalis.LOGGER.info("Orbitalis : initialisation client terminée.");
    }

    /** Associe chaque interface (menu) à son écran. */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PROCESSING_MACHINE.get(), ProcessingMachineScreen::new);
        event.register(ModMenus.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        event.register(ModMenus.FLUID_MACHINE.get(), FluidMachineScreen::new);
        event.register(ModMenus.CRYOGENIC_TANK.get(), CryogenicTankScreen::new);
        event.register(ModMenus.SUIT_WORKBENCH.get(), com.orbitalis.client.screen.SuitWorkbenchScreen::new);
    }

    /** Le ciel de l'espace, celui de l'orbite et de la Lune : entièrement dessiné par le mod (voir SpaceSky). */
    @SubscribeEvent
    static void registerSkies(net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(com.orbitalis.client.SpaceSky.ID, new com.orbitalis.client.SpaceSky());
    }

    /** Rendus animés : la table de lancement dessine la tour de service et les bras du pas de tir. */
    /** La maquette 3D du pas de tir, dans l'écran du centre de contrôle. */
    @SubscribeEvent
    static void registerPictures(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(com.orbitalis.client.render.PadViewRenderer.State.class, com.orbitalis.client.render.PadViewRenderer::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LAUNCH_TABLE.get(), LaunchTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHIPYARD_CONSOLE.get(), com.orbitalis.client.render.ShipyardConsoleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MISSION_DISPLAY.get(), MissionDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GLASS_DOOR.get(), com.orbitalis.client.render.GlassDoorRenderer::new);
        event.registerEntityRenderer(ModEntities.CAPSULE.get(), CapsuleRenderer::new);
        event.registerEntityRenderer(ModEntities.ELEVATOR_CABIN.get(), ElevatorCabinRenderer::new);
        event.registerEntityRenderer(ModEntities.ROVER.get(), com.orbitalis.client.render.RoverRenderer::new);
        event.registerEntityRenderer(ModEntities.ROTOR_DRONE.get(), com.orbitalis.client.render.RotorDroneRenderer::new);
    }

    /** Messages reçus du serveur. */
    @SubscribeEvent
    static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ResearchSyncPayload.TYPE, (payload, context) -> ClientResearch.receive(payload));
        // L'état d'un complexe de lancement arrive quand le joueur utilise un pupitre : on ouvre son écran.
        // Informations d'un pas de tir : clic droit sur la structure formée.
        event.register(LaunchPadInfoPayload.TYPE,
                (payload, context) -> Minecraft.getInstance().gui.setScreen(new LaunchPadScreen(payload)));
        // Chantier d'une fusée : clic droit sur la table, puis actualisations après chaque pose.
        event.register(com.orbitalis.core.network.ConstructionPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.open() && !payload.plan().isEmpty()) {
                minecraft.gui.setScreen(new com.orbitalis.client.screen.ConstructionScreen(payload));
            } else if (minecraft.gui.screen() instanceof com.orbitalis.client.screen.ConstructionScreen screen) {
                screen.update(payload);
            }
        });
        // Satellites de l'agence : clic droit sur une station de poursuite, puis actualisations de l'écran ouvert.
        event.register(SatelliteStatusPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.open()) {
                minecraft.gui.setScreen(new TrackingStationScreen(payload));
            } else if (minecraft.gui.screen() instanceof TrackingStationScreen station) {
                station.update(payload);
            }
        });
        // Relevé lointain du terrain : il va à la carte de navigation de sa table de lancement.
        // Tuile de terrain de l'atlas : elle va se faire peindre.
        event.register(com.orbitalis.core.network.AtlasTilePayload.TYPE,
                (payload, context) -> com.orbitalis.client.render.AtlasMap.accept(payload));
        // La tablette de mission ouvre l'atlas, centré sur l'endroit où l'on est.
        event.register(com.orbitalis.core.network.AtlasOpenPayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(
                new com.orbitalis.client.screen.AtlasScreen(null, payload.source(), payload.source(), true)));
        event.register(com.orbitalis.core.network.AtlasSitesPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.AtlasScreen.acceptSites(payload));
        event.register(com.orbitalis.core.network.SurveyPayload.TYPE,
                (payload, context) -> {
                    com.orbitalis.client.render.IsoMap.acceptSurvey(payload.table(), payload.lod(), payload.cells());
                    com.orbitalis.client.render.StationGlobe.accept(payload.table(), payload.lod(), payload.cells());
                });
        // Relevé de prospection : il va à l'écran de la station qui l'a demandé.
        // L'observatoire : il va à l'écran de l'observatoire ouvert depuis cette station.
        event.register(com.orbitalis.core.network.ObservatoryPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.ObservatoryScreen.accept(payload));
        event.register(com.orbitalis.core.network.ProspectPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().gui.screen() instanceof TrackingStationScreen station) {
                station.prospect(payload);
            }
        });
        // Tableau de bord d'une station : clic droit sur un terminal, puis actualisations de l'écran ouvert.
        event.register(com.orbitalis.core.network.StationStatusPayload.TYPE,
                (payload, context) -> com.orbitalis.client.screen.StationScreen.accept(payload));
        // Panneau de commande de l'ascenseur de la tour.
        event.register(ElevatorPayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new ElevatorScreen(payload)));
        // Une fusée se disloque : chaque client met l'accident en scène.
        event.register(com.orbitalis.core.network.BreakupPayload.TYPE, (payload, context) -> com.orbitalis.client.BreakupFx.receive(payload));
        event.register(com.orbitalis.core.network.FlightHudPayload.TYPE, (payload, context) -> com.orbitalis.client.CockpitHud.onFlight(payload));
        // Les capteurs de la combinaison que porte le joueur.
        event.register(com.orbitalis.core.network.SuitStatusPayload.TYPE, (payload, context) -> com.orbitalis.client.SuitHud.onStatus(payload));
        event.register(LaunchStatusPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            com.orbitalis.client.CockpitHud.onStatus(payload);
            if (payload.open()) {
                // Depuis un pupitre : le centre de contrôle. Depuis la cabine d'une fusée : le séquenceur de lancement.
                boolean console = minecraft.level != null
                        && minecraft.level.getBlockState(payload.console()).getBlock() instanceof com.orbitalis.phase1.spaceport.LaunchConsoleBlock;
                minecraft.gui.setScreen(console ? new LaunchConsoleScreen(payload) : new com.orbitalis.client.screen.SequenceScreen(payload, null));
            } else if (minecraft.gui.screen() instanceof LaunchConsoleScreen console) {
                // Actualisation demandée par l'écran déjà ouvert.
                console.update(payload);
            } else if (minecraft.gui.screen() instanceof com.orbitalis.client.screen.SequenceScreen sequence) {
                sequence.update(payload);
            }
        });
    }

    /** Particules du mod : chacune a ses images, parcourues au fil de sa vie, et son comportement. */
    @SubscribeEvent
    static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.EXHAUST_SMOKE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.EXHAUST_SMOKE, sprites));
        event.registerSpriteSet(ModParticles.ENGINE_FLAME.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.ENGINE_FLAME, sprites));
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.SPARK, sprites));
        event.registerSpriteSet(ModParticles.CRYO_MIST.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.CRYO_MIST, sprites));
        event.registerSpriteSet(ModParticles.VAPOR.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.VAPOR, sprites));
        event.registerSpriteSet(ModParticles.PLANET.get(), PlanetParticle::provider);
        event.registerSpriteSet(ModParticles.SOOT.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.SOOT, sprites));
        event.registerSpriteSet(ModParticles.LAUNCH_SMOKE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.LAUNCH_SMOKE, sprites));
        event.registerSpriteSet(ModParticles.LAUNCH_STEAM.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.LAUNCH_STEAM, sprites));
        event.registerSpriteSet(ModParticles.DELUGE_WATER.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.DELUGE_WATER, sprites));
        event.registerSpriteSet(ModParticles.BLAST_FIRE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.BLAST_FIRE, sprites));
        event.registerSpriteSet(ModParticles.BLAST_SMOKE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.BLAST_SMOKE, sprites));
        event.registerSpriteSet(ModParticles.BLAST_CRYO.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.BLAST_CRYO, sprites));
        event.registerSpriteSet(ModParticles.TRENCH_FIRE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.TRENCH_FIRE, sprites));
        event.registerSpriteSet(ModParticles.LAUNCH_EMBER.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.LAUNCH_EMBER, sprites));
        event.registerSpriteSet(ModParticles.LAUNCH_DUST.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.LAUNCH_DUST, sprites));
        event.registerSpriteSet(ModParticles.HEAT_HAZE.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.HEAT_HAZE, sprites));
        event.registerSpriteSet(ModParticles.LAUNCH_FLASH.get(), sprites -> OrbitalisParticle.provider(OrbitalisParticle.Style.LAUNCH_FLASH, sprites));
    }

    /** Rendu 3D des démonstrations du manuel. */
    @SubscribeEvent
    static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(GuideSceneRenderState.class, GuideSceneRenderer::new);
    }

    /**
     * Apparence des fluides du mod (utilisée notamment par JEI) : la texture de l'eau, teintée à la couleur du fluide.
     */
    /** Sous le méthane de Titan : une brume brune, épaisse, où l'on ne voit qu'à quelques blocs. */
    @SubscribeEvent
    static void registerClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public void modifyFogColor(net.minecraft.client.Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level,
                    int renderDistance, float darkenWorldAmount, org.joml.Vector4f fluidFogColor) {
                fluidFogColor.set(0.26F, 0.16F, 0.07F, 1.0F);
            }

            @Override
            public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.fog.environment.FogEnvironment environment,
                    float renderDistance, float partialTick, net.minecraft.client.renderer.fog.FogData fogData) {
                fogData.environmentalStart = 0.0F;
                fogData.environmentalEnd = 7.0F;
            }
        }, ModFluids.LIQUID_METHANE.type().get());
    }

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        Material still = new Material(Identifier.withDefaultNamespace("block/water_still"));
        Material flowing = new Material(Identifier.withDefaultNamespace("block/water_flow"));
        for (ModFluids.Entry fluid : ModFluids.entries()) {
            FluidTintSource tint = state -> fluid.color();
            event.register(new FluidModel.Unbaked(still, flowing, null, tint), fluid.source(), fluid.flowing());
        }
    }
}
