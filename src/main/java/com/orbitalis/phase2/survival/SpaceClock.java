package com.orbitalis.phase2.survival;

import com.orbitalis.Orbitalis;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * L'heure de l'espace. L'orbite et la Lune ont leur propre horloge ({@code orbitalis:space}), arrêtée à minuit :
 * ces mondes n'ont ni jour ni nuit, mais les packs de shaders, qui règlent la lumière et le ciel d'après l'heure,
 * y dessinent ainsi un ciel noir et une lumière de nuit, au lieu d'un lever de soleil brumeux plein de nuages.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class SpaceClock {
    public static final ResourceKey<WorldClock> SPACE = ResourceKey.create(Registries.WORLD_CLOCK, Orbitalis.id("space"));
    /** Minuit, en ticks. */
    public static final long MIDNIGHT = 18_000L;

    private SpaceClock() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(SPACE).ifPresent(clock -> {
            server.clockManager().setTotalTicks(clock, MIDNIGHT);
            server.clockManager().setPaused(clock, true);
        });
    }
}
