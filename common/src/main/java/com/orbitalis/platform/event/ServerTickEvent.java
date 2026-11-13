package com.orbitalis.platform.event;

import net.minecraft.server.MinecraftServer;

/** La fin d'un tick du serveur. */
public final class ServerTickEvent {
    private ServerTickEvent() {
    }

    public record Post(MinecraftServer getServer) {
    }
}
