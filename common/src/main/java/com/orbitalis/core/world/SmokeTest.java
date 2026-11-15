package com.orbitalis.core.world;

import com.orbitalis.Orbitalis;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Essai de démarrage (lancé avec {@code -Dorbitalis.smokeTest=true}) : une fois le serveur prêt, génère le tronçon central
 * de chaque dimension du mod, note ce qui s'y trouve, puis arrête le serveur. Sert à vérifier un portage sans ouvrir le jeu.
 */
public final class SmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("orbitalis.smokeTest");
    private static int ticks = -1;

    private SmokeTest() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        LifecycleEvent.SERVER_STARTED.register(SmokeTest::run);
        // Un serveur dédié arrêté garde sa console ouverte : l'essai termine le processus lui-même.
        LifecycleEvent.SERVER_STOPPED.register(server -> new Thread(() -> System.exit(0), "orbitalis-essai-fin").start());
        TickEvent.SERVER_POST.register(server -> {
            if (ticks >= 0 && ++ticks > 40) {
                ticks = -1;
                Orbitalis.LOGGER.info("[essai] arrêt du serveur");
                server.halt(false);
            }
        });
    }

    private static void run(MinecraftServer server) {
        int failures = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.dimension().location().getNamespace().equals(Orbitalis.MODID)) {
                continue;
            }
            try {
                level.getChunk(0, 0);
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8);
                Orbitalis.LOGGER.info("[essai] {} : tronçon généré, surface à y={}, heure fixe {}", level.dimension().location(), top,
                        level.dimensionType().fixedTime().orElse(-1L));
            } catch (RuntimeException e) {
                failures++;
                Orbitalis.LOGGER.error("[essai] {} : échec de la génération", level.dimension().location(), e);
            }
        }
        Orbitalis.LOGGER.info("[essai] blocs {}, objets {}, échecs {}",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().stream().filter(id -> id.getNamespace().equals(Orbitalis.MODID)).count(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(Orbitalis.MODID)).count(),
                failures);
        ticks = 0;
    }
}
