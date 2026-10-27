package com.orbitalis.core.registry;

import com.mojang.serialization.MapCodec;
import com.orbitalis.Orbitalis;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registre des sources de biomes du mod : la façon dont un monde répartit ses biomes.
 */
public final class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Orbitalis.MODID);

    /** Proxima b : face de jour, bande du crépuscule, face de nuit, d'est en ouest. */
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<com.orbitalis.phase3.celestial.ProximaBiomeSource>> PROXIMA =
            BIOME_SOURCES.register("proxima", () -> com.orbitalis.phase3.celestial.ProximaBiomeSource.CODEC);

    private ModBiomeSources() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
    }
}
