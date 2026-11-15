package com.orbitalis.fabric;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.Features;
import com.orbitalis.platform.capability.fabric.FabricTransfer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;

/** Orbitalis sous Fabric : le démarrage commun, puis ce qui est propre à Fabric. */
public final class OrbitalisFabric implements ModInitializer {
    /** La condition des recettes du contenu interstellaire : vraie quand ce contenu est montré. */
    private enum InterstellarCondition implements ResourceCondition {
        INSTANCE;

        static final ResourceConditionType<InterstellarCondition> TYPE =
                ResourceConditionType.create(Features.INTERSTELLAR_CONDITION, MapCodec.unit(INSTANCE));

        @Override
        public ResourceConditionType<?> getType() {
            return TYPE;
        }

        @Override
        public boolean test(HolderLookup.@Nullable Provider registryLookup) {
            return Features.interstellar();
        }
    }

    @Override
    public void onInitialize() {
        Orbitalis.init();
        ResourceConditions.register(InterstellarCondition.TYPE);
        // Les minerais du mod dans les biomes de la Terre (sous NeoForge : data/orbitalis/neoforge/biome_modifier/add_ores.json).
        for (String ore : java.util.List.of("ore_bauxite", "ore_lithium", "ore_titanium")) {
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                    net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OVERWORLD),
                    net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, Orbitalis.id(ore)));
        }
        // Sous Fabric, les registres sont remplis dès l'inscription : ce qu'offrent les blocs peut être exposé tout de suite.
        FabricTransfer.expose();
    }
}
