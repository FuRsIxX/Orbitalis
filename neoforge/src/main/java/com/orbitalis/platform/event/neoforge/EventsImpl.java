package com.orbitalis.platform.event.neoforge;

import com.orbitalis.platform.event.Events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Sous NeoForge : le chargement et le déchargement des tronçons, par le bus de NeoForge. */
public final class EventsImpl {
    private EventsImpl() {
    }

    public static void registerChunkEvents() {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Events.chunkLoaded(level, event.getChunk());
            }
        });
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Events.chunkUnloaded(level, event.getChunk());
            }
        });
    }
}
