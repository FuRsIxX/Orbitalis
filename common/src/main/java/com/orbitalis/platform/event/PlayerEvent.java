package com.orbitalis.platform.event;

import net.minecraft.world.entity.player.Player;

/** Un joueur arrive sur le serveur, ou le quitte. */
public final class PlayerEvent {
    private PlayerEvent() {
    }

    public record PlayerLoggedInEvent(Player getEntity) {
    }

    public record PlayerLoggedOutEvent(Player getEntity) {
    }
}
