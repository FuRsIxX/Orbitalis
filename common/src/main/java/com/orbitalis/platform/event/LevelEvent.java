package com.orbitalis.platform.event;

import net.minecraft.world.level.LevelAccessor;

/** Un monde est sauvegardé. */
public final class LevelEvent {
    private LevelEvent() {
    }

    public record Save(LevelAccessor getLevel) {
    }
}
