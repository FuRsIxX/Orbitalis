package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase3.celestial.CraterFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

/**
 * Registre des éléments de terrain du mod (ce que la génération du monde pose sur le relief).
 */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Orbitalis.MODID);

    /** Cratère d'impact lunaire. */
    public static final DeferredHolder<Feature<?>, CraterFeature> CRATER = FEATURES.register("crater", CraterFeature::new);
    /** Les lineae d'Europe, puis ses terrains chaotiques. */
    public static final DeferredHolder<Feature<?>, com.orbitalis.phase3.celestial.EuropaTerrainFeature> EUROPA_LINEAE =
            FEATURES.register("europa_lineae", () -> new com.orbitalis.phase3.celestial.EuropaTerrainFeature(false));
    public static final DeferredHolder<Feature<?>, com.orbitalis.phase3.celestial.EuropaTerrainFeature> EUROPA_CHAOS =
            FEATURES.register("europa_chaos", () -> new com.orbitalis.phase3.celestial.EuropaTerrainFeature(true));

    private ModFeatures() {
    }

    public static void register() {
        FEATURES.register();
    }
}
