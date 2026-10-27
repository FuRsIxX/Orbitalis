package com.orbitalis.datagen;

import java.util.List;
import java.util.Set;

import com.orbitalis.Orbitalis;
import com.orbitalis.datagen.lang.EnUsLanguageProvider;
import com.orbitalis.datagen.lang.FrFrLanguageProvider;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Point d'entrée du datagen (gradlew runData).
 * Chaque fournisseur écrit ses fichiers dans src/generated/resources.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class OrbitalisDataGenerators {
    private OrbitalisDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        // Ressources (côté client) : traductions et modèles.
        event.createProvider(EnUsLanguageProvider::new);
        event.createProvider(FrFrLanguageProvider::new);
        event.createProvider(OrbitalisModelProvider::new);

        // Données (côté serveur) : recettes, loot tables, tags et génération du monde.
        event.createProvider(OrbitalisRecipeProvider.Runner::new);
        event.createProvider((output, registries) -> new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(OrbitalisBlockLoot::new, LootContextParamSets.BLOCK)),
                registries));
        var blockTags = event.createProvider(OrbitalisBlockTagsProvider::new);
        event.createProvider((output, registries) ->
                new OrbitalisItemTagsProvider(output, registries, blockTags.contentsGetter()));
        event.createDatapackRegistryObjects(OrbitalisWorldGen.BUILDER);
    }
}
