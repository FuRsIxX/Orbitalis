package com.orbitalis.core.network;

import com.orbitalis.phase1.rocket.DesignComputerBlock;
import com.orbitalis.phase1.spaceport.ElevatorCabinEntity;
import com.orbitalis.phase1.spaceport.PadKitItem;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Messages échangés entre le client et le serveur.
 * Les messages reçus par le client sont traités dans {@code OrbitalisClient}, qui n'existe pas sur un serveur dédié.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class ModNetwork {
    /** Version du protocole : à augmenter si le contenu d'un message change. */
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC);
        registrar.playToClient(LaunchStatusPayload.TYPE, LaunchStatusPayload.STREAM_CODEC);
        registrar.playToClient(BreakupPayload.TYPE, BreakupPayload.STREAM_CODEC);
        registrar.playToClient(FlightHudPayload.TYPE, FlightHudPayload.STREAM_CODEC);
        registrar.playToClient(LaunchPadInfoPayload.TYPE, LaunchPadInfoPayload.STREAM_CODEC);
        registrar.playToClient(SatelliteStatusPayload.TYPE, SatelliteStatusPayload.STREAM_CODEC);
        registrar.playToClient(ObservatoryPayload.TYPE, ObservatoryPayload.STREAM_CODEC);
        registrar.playToClient(ProspectPayload.TYPE, ProspectPayload.STREAM_CODEC);
        registrar.playToClient(SurveyPayload.TYPE, SurveyPayload.STREAM_CODEC);
        registrar.playToClient(AtlasTilePayload.TYPE, AtlasTilePayload.STREAM_CODEC);
        registrar.playToClient(AtlasSitesPayload.TYPE, AtlasSitesPayload.STREAM_CODEC);
        registrar.playToClient(AtlasOpenPayload.TYPE, AtlasOpenPayload.STREAM_CODEC);
        registrar.playToClient(StationStatusPayload.TYPE, StationStatusPayload.STREAM_CODEC);
        registrar.playToClient(SuitStatusPayload.TYPE, SuitStatusPayload.STREAM_CODEC);
        // L'écran d'un terminal de station s'actualise.
        registrar.playToServer(StationRequestPayload.TYPE, StationRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.orbitalis.phase2.station.StationStatus.request(player, payload.terminal());
            }
        });
        // L'atlas d'un pupitre pose, renomme ou retire un repère de l'agence.
        registrar.playToServer(AtlasMarkerPayload.TYPE, AtlasMarkerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.orbitalis.core.atlas.AtlasSites.marker(player, payload);
            }
        });
        // L'atlas d'un pupitre demande les installations qu'il connaît, avec leur état.
        registrar.playToServer(AtlasSitesRequestPayload.TYPE, AtlasSitesRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.orbitalis.core.atlas.AtlasSites.request(player, payload);
            }
        });
        // L'atlas d'un joueur demande une tuile de terrain, depuis le pupitre d'un centre de contrôle.
        registrar.playToServer(AtlasRequestPayload.TYPE, AtlasRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.orbitalis.core.atlas.AtlasTiles.request(player, payload);
            }
        });
        // La carte de navigation d'un joueur demande les relevés lointains d'une table de lancement proche de lui ;
        // l'écran d'une station de poursuite, le relevé du pays autour d'elle, pour en bâtir sa planète.
        registrar.playToServer(SurveyRequestPayload.TYPE, SurveyRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level().isLoaded(payload.table())
                    && player.blockPosition().closerThan(payload.table(), 512.0)) {
                if (player.level().getBlockEntity(payload.table()) instanceof com.orbitalis.phase1.spaceport.LaunchTableBlockEntity table) {
                    table.sendSurveys(player);
                } else if (player.level().getBlockState(payload.table()).getBlock() instanceof com.orbitalis.phase1.satellite.TrackingStationBlock
                        && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    com.orbitalis.core.atlas.StationSurvey.request(player, level, payload.table());
                }
            }
        });
        registrar.playToServer(SatelliteCommandPayload.TYPE, SatelliteCommandPayload.STREAM_CODEC, ModNetwork::onSatelliteCommand);
        registrar.playToServer(LaunchCommandPayload.TYPE, LaunchCommandPayload.STREAM_CODEC, ModNetwork::onLaunchCommand);
        registrar.playToServer(UnlockResearchPayload.TYPE, UnlockResearchPayload.STREAM_CODEC, ModNetwork::onUnlockResearch);
        // Commandes du pilote d'une cabine : elles vont à la cabine dans laquelle il est assis. Les équipiers, eux,
        // n'ont pas la main.
        registrar.playToServer(PilotInputPayload.TYPE, PilotInputPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof com.orbitalis.phase2.flight.CapsuleEntity capsule
                    && capsule.isPilot(player)) {
                capsule.setControls(payload.bits());
            }
        });
        registrar.playToServer(PadKitPayload.TYPE, PadKitPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PadKitItem.handle(player, payload);
            }
        });
        registrar.playToServer(StudyPayload.TYPE, StudyPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.orbitalis.phase1.rocket.EngineerWorkbenchBlock.study(player, payload);
            }
        });
        // Copie du plan de la fusée dressée sur le pas de tir le plus proche.
        registrar.playToServer(BlueprintCopyPayload.TYPE, BlueprintCopyPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                var table = com.orbitalis.phase1.spaceport.LaunchTableBlockEntity.nearest(player.level(), player.blockPosition(), 24.0);
                net.minecraft.network.chat.Component problem = table == null
                        ? net.minecraft.network.chat.Component.translatable("spaceport.orbitalis.pad.copy_none") : table.copyPlan(player);
                player.sendSystemMessage(problem != null ? problem.copy().withStyle(net.minecraft.ChatFormatting.GOLD)
                        : net.minecraft.network.chat.Component.translatable("spaceport.orbitalis.pad.copied").withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        });
        // Écran de chantier : le serveur envoie l'état du chantier, le client les ordres de pose.
        registrar.playToClient(ConstructionPayload.TYPE, ConstructionPayload.STREAM_CODEC);
        registrar.playToServer(ConstructionActionPayload.TYPE, ConstructionActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.blockPosition().closerThan(payload.table(), 16.0)
                    && player.level().getBlockEntity(payload.table()) instanceof com.orbitalis.phase1.spaceport.LaunchTableBlockEntity table) {
                int placed = 0;
                int round;
                int rounds = 0;
                // Toutes les couches d'un coup : un seul bruit de pose, à la fin, et non un par couche.
                do {
                    round = table.buildLayer(player, true);
                    placed += round;
                } while (payload.action() == ConstructionActionPayload.ALL && round > 0 && ++rounds < 128);
                if (placed > 0) {
                    table.placedSound();
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        placed > 0 ? "construction.orbitalis.built" : "construction.orbitalis.nothing", placed)
                        .withStyle(placed > 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD), true);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, table.construction(false));
            }
        });
        // Écran du chantier naval : le client demande de poser la section en cours, ou tout ce qu'il peut.
        registrar.playToServer(ShipyardActionPayload.TYPE, ShipyardActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.blockPosition().closerThan(payload.console(), 48.0)
                    && player.level().getBlockEntity(payload.console()) instanceof com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity console) {
                int placed = console.build(player, payload.action() == ShipyardActionPayload.ALL);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        placed > 0 ? "shipyard.orbitalis.built" : "shipyard.orbitalis.nothing", placed)
                        .withStyle(placed > 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD), true);
            }
        });
        registrar.playToServer(DesignPrintPayload.TYPE, DesignPrintPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                DesignComputerBlock.print(player, payload);
            }
        });
        // Panneau de l'ascenseur : le serveur l'ouvre chez le joueur, qui lui renvoie l'arrêt choisi.
        registrar.playBidirectional(ElevatorPayload.TYPE, ElevatorPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ElevatorCabinEntity.handle(player, payload);
            }
        });
    }

    /**
     * Envoie un message à un joueur, s'il peut le recevoir. Un joueur dont le client n'a pas ce canal (un joueur
     * simulé par un test, par exemple) est simplement ignoré, au lieu de faire échouer ce qui était en cours.
     */
    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Envoie un message à tous les joueurs d'un monde qui se trouvent près de l'un de ces points, et qui peuvent le
     * recevoir.
     */
    public static void sendNear(net.minecraft.server.level.ServerLevel level, double radius,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, net.minecraft.world.phys.Vec3... points) {
        for (ServerPlayer player : level.players()) {
            for (net.minecraft.world.phys.Vec3 point : points) {
                if (player.position().distanceToSqr(point) <= radius * radius) {
                    send(player, payload);
                    break;
                }
            }
        }
    }

    private static void onLaunchCommand(LaunchCommandPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LaunchConsoleBlock.handleCommand(player, payload);
        }
    }

    private static void onSatelliteCommand(SatelliteCommandPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            SatelliteManager.handleCommand(player, payload);
        }
    }

    private static void onUnlockResearch(UnlockResearchPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ResearchManager.unlock(player, payload.nodeId());
            // L'état est renvoyé dans tous les cas : si la demande est refusée, l'écran du joueur se remet d'accord.
            ResearchManager.sync(player);
        }
    }
}
