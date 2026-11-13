package com.orbitalis.platform.event;

import net.minecraft.server.MinecraftServer;

/** Le serveur a fini de démarrer. */
public record ServerStartedEvent(MinecraftServer getServer) {
}
