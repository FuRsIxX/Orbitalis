package com.orbitalis.platform.event.fabric;

import com.orbitalis.platform.event.Events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

/** Sous Fabric : le chargement et le déchargement des tronçons, par l'API de Fabric. */
public final class EventsImpl {
    private EventsImpl() {
    }

    public static void registerChunkEvents() {
        ServerChunkEvents.CHUNK_LOAD.register(Events::chunkLoaded);
        ServerChunkEvents.CHUNK_UNLOAD.register(Events::chunkUnloaded);
    }
}
