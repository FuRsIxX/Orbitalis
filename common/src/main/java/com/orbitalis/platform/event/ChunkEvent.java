package com.orbitalis.platform.event;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Un tronçon de monde est chargé, ou déchargé. */
public final class ChunkEvent {
    private ChunkEvent() {
    }

    public record Load(LevelAccessor getLevel, ChunkAccess getChunk) {
    }

    public record Unload(LevelAccessor getLevel, ChunkAccess getChunk) {
    }
}
