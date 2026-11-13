package com.orbitalis.platform.event;

import net.minecraft.world.entity.player.Player;

/** La fin du tick d'un joueur. */
public final class PlayerTickEvent {
    private PlayerTickEvent() {
    }

    public record Post(Player getEntity) {
    }
}
