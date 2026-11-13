package com.orbitalis.phase2.survival;

import com.orbitalis.Orbitalis;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import com.orbitalis.platform.event.ServerStartedEvent;

/**
 * L'heure de l'espace. L'orbite et la Lune ont une heure arrêtée à minuit (fixed_time de leur type de monde) :
 * ces mondes n'ont ni jour ni nuit, mais les packs de shaders, qui règlent la lumière et le ciel d'après l'heure,
 * y dessinent ainsi un ciel noir et une lumière de nuit, au lieu d'un lever de soleil brumeux plein de nuages.
 */
public final class SpaceClock {
    /** Minuit, en ticks. */
    public static final long MIDNIGHT = 18_000L;

    private SpaceClock() {
    }

        public static void onServerStarted(ServerStartedEvent event) {
        // En 1.21.1, l'heure arrêtée à minuit est celle des types de monde de l'orbite et de la Lune (fixed_time) : rien à faire.

    }
}
