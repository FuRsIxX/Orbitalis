package com.orbitalis.platform.event;

import net.minecraft.server.MinecraftServer;

/** Le serveur s'est arrêté. */
public record ServerStoppedEvent(MinecraftServer getServer) {
}
