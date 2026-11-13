package com.orbitalis.platform.event;

import net.minecraft.world.level.Level;

/** La fin du tick d'un monde. */
public final class LevelTickEvent {
    private LevelTickEvent() {
    }

    public record Post(Level getLevel) {
    }
}
