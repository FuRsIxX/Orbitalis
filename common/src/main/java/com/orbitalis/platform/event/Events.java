package com.orbitalis.platform.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * L'aiguillage des événements du jeu vers les méthodes du mod qui les attendent. Sous NeoForge 26, ces méthodes étaient
 * trouvées par leurs annotations ; ici, la liste est écrite une fois pour toutes, et chaque événement est branché sur celui
 * d'Architectury (ou, à défaut, sur celui du chargeur).
 */
public final class Events {
    private static final List<Consumer<ChunkEvent.Load>> CHUNK_LOAD = new ArrayList<>();
    private static final List<Consumer<ChunkEvent.Unload>> CHUNK_UNLOAD = new ArrayList<>();
    private static final List<Consumer<EntityMountEvent>> MOUNT = new ArrayList<>();
    private static final List<Consumer<BlockDropsEvent>> DROPS = new ArrayList<>();

    private Events() {
    }

    /** Branche tous les abonnements du mod ; une seule fois, au démarrage commun. */
    public static void register() {
        TickEvent.SERVER_POST.register(server -> {
            ServerTickEvent.Post event = new ServerTickEvent.Post(server);
            com.orbitalis.core.atlas.AtlasTiles.onServerTick(event);
            com.orbitalis.core.research.ResearchManager.onServerTick(event);
            com.orbitalis.phase1.satellite.SatelliteManager.onServerTick(event);
            com.orbitalis.phase3.interstellar.ShipJump.onServerTick(event);
        });
        TickEvent.SERVER_LEVEL_POST.register(level ->
                com.orbitalis.phase1.conduit.ConduitNetwork.onLevelTick(new LevelTickEvent.Post(level)));
        TickEvent.PLAYER_POST.register(player -> {
            if (player.level().isClientSide()) {
                return;
            }
            PlayerTickEvent.Post event = new PlayerTickEvent.Post(player);
            com.orbitalis.core.research.ResearchManager.onPlayerTick(event);
            com.orbitalis.phase2.survival.SpaceRules.onPlayerTick(event);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.orbitalis.phase2.survival.RespawnRules.onPlayerTick(serverPlayer);
            }
        });
        dev.architectury.event.events.common.PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) ->
                com.orbitalis.phase2.survival.RespawnRules.afterRespawn(player, conqueredEnd));
        dev.architectury.event.events.common.PlayerEvent.PLAYER_JOIN.register(player -> {
            PlayerEvent.PlayerLoggedInEvent event = new PlayerEvent.PlayerLoggedInEvent(player);
            com.orbitalis.core.research.ResearchManager.onPlayerLoggedIn(event);
            com.orbitalis.core.guide.GuideBookGift.onPlayerLoggedIn(event);
        });
        dev.architectury.event.events.common.PlayerEvent.PLAYER_QUIT.register(player ->
                com.orbitalis.core.research.ResearchManager.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player)));
        LifecycleEvent.SERVER_STARTED.register(server -> {
            ServerStartedEvent event = new ServerStartedEvent(server);
            com.orbitalis.phase2.survival.SpaceClock.onServerStarted(event);
            com.orbitalis.core.world.TestMap.onStarted(event);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            ServerStoppedEvent event = new ServerStoppedEvent(server);
            com.orbitalis.core.atlas.AtlasMarkers.onServerStopped(event);
            com.orbitalis.core.atlas.AtlasSites.onServerStopped(event);
            com.orbitalis.core.atlas.AtlasStore.onServerStopped(event);
            com.orbitalis.phase2.station.StationPower.onServerStopped(event);
        });
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> {
            LevelEvent.Save event = new LevelEvent.Save(level);
            com.orbitalis.core.atlas.AtlasMarkers.onSave(event);
            com.orbitalis.core.atlas.AtlasSites.onSave(event);
            com.orbitalis.core.atlas.AtlasStore.onSave(event);
        });
        CommandRegistrationEvent.EVENT.register((dispatcher, context, selection) -> {
            RegisterCommandsEvent event = new RegisterCommandsEvent(dispatcher, context, selection);
            com.orbitalis.core.research.ResearchCommand.register(event);
            com.orbitalis.phase1.satellite.SatelliteCommand.register(event);
        });
        dev.architectury.event.events.common.BlockEvent.PLACE.register((level, pos, state, placer) -> {
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(level, pos, state, placer);
            com.orbitalis.core.world.FacilitySalvage.onPlace(event);
            return event.isCanceled() ? EventResult.interruptFalse() : EventResult.pass();
        });
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(entity, source, amount);
            com.orbitalis.phase2.flight.CrewRules.onDamage(event);
            return event.isCanceled() ? EventResult.interruptFalse() : EventResult.pass();
        });

        CHUNK_LOAD.add(com.orbitalis.core.atlas.AtlasSites::onChunkLoad);
        CHUNK_LOAD.add(com.orbitalis.core.atlas.AtlasStore::onChunkLoad);
        CHUNK_UNLOAD.add(com.orbitalis.core.atlas.AtlasSites::onChunkUnload);
        CHUNK_UNLOAD.add(com.orbitalis.core.atlas.AtlasStore::onChunkUnload);
        MOUNT.add(com.orbitalis.phase2.flight.CrewRules::onDismount);
        MOUNT.add(com.orbitalis.phase1.spaceport.ElevatorCabinEntity.Rules::onMount);
        DROPS.add(com.orbitalis.core.world.FacilitySalvage::onDrops);
        registerChunkEvents();
    }

    /** Un tronçon de monde vient d'être chargé côté serveur (appelé par le chargeur). */
    public static void chunkLoaded(ServerLevel level, ChunkAccess chunk) {
        ChunkEvent.Load event = new ChunkEvent.Load(level, chunk);
        CHUNK_LOAD.forEach(listener -> listener.accept(event));
    }

    /** Un tronçon de monde va être déchargé côté serveur (appelé par le chargeur). */
    public static void chunkUnloaded(ServerLevel level, ChunkAccess chunk) {
        ChunkEvent.Unload event = new ChunkEvent.Unload(level, chunk);
        CHUNK_UNLOAD.forEach(listener -> listener.accept(event));
    }

    /**
     * Une entité monte sur une autre ({@code mount}) ou en descend : faux si un abonné l'en empêche (appelé par le mixin
     * d'{@code Entity}).
     */
    public static boolean allowMount(Entity mounting, Entity mounted, boolean mount) {
        EntityMountEvent event = new EntityMountEvent(mounting, mounted, mount);
        MOUNT.forEach(listener -> listener.accept(event));
        return !event.isCanceled();
    }

    /** Les objets que va laisser tomber un bloc cassé, à revoir (appelé par le mixin de {@code Block}). */
    public static void drops(BlockDropsEvent event) {
        DROPS.forEach(listener -> listener.accept(event));
    }

    /** Branche le chargement et le déchargement des tronçons, propres à chaque chargeur. */
    @ExpectPlatform
    public static void registerChunkEvents() {
        throw new AssertionError("remplacé par le chargeur");
    }
}
