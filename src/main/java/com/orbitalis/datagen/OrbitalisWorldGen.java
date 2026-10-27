package com.orbitalis.datagen;

import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Génération des minerais dans le monde.
 * <ul>
 * <li>Bauxite : courante, proche de la surface (comme les gisements réels, formés par altération en surface).</li>
 * <li>Lithium : peu courant, à profondeur moyenne.</li>
 * <li>Titane : rare, en profondeur.</li>
 * </ul>
 * À titre de comparaison, le fer vanilla forme des filons de 9 blocs, 10 fois par tronçon.
 */
public final class OrbitalisWorldGen {
    private static final ResourceKey<ConfiguredFeature<?, ?>> ORE_BAUXITE = configuredKey("ore_bauxite");
    private static final ResourceKey<ConfiguredFeature<?, ?>> ORE_LITHIUM = configuredKey("ore_lithium");
    private static final ResourceKey<ConfiguredFeature<?, ?>> ORE_TITANIUM = configuredKey("ore_titanium");

    private static final ResourceKey<PlacedFeature> PLACED_BAUXITE = placedKey("ore_bauxite");
    private static final ResourceKey<PlacedFeature> PLACED_LITHIUM = placedKey("ore_lithium");
    private static final ResourceKey<PlacedFeature> PLACED_TITANIUM = placedKey("ore_titanium");

    private static final ResourceKey<BiomeModifier> ADD_ORES =
            ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Orbitalis.id("add_ores"));

    /** Ensemble des entrées de registres de données à générer. */
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, OrbitalisWorldGen::configuredFeatures)
            .add(Registries.PLACED_FEATURE, OrbitalisWorldGen::placedFeatures)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, OrbitalisWorldGen::biomeModifiers);

    private OrbitalisWorldGen() {
    }

    /** Forme des filons : blocs remplacés et taille. */
    private static void configuredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(ORE_BAUXITE, ore(ModBlocks.BAUXITE_ORE.get(), ModBlocks.DEEPSLATE_BAUXITE_ORE.get(), 10));
        context.register(ORE_LITHIUM, ore(ModBlocks.LITHIUM_ORE.get(), ModBlocks.DEEPSLATE_LITHIUM_ORE.get(), 6));
        context.register(ORE_TITANIUM, ore(ModBlocks.TITANIUM_ORE.get(), ModBlocks.DEEPSLATE_TITANIUM_ORE.get(), 6));
    }

    /** Répartition des filons : nombre par tronçon et altitudes. */
    private static void placedFeatures(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);

        context.register(PLACED_BAUXITE, new PlacedFeature(features.getOrThrow(ORE_BAUXITE), orePlacement(8,
                HeightRangePlacement.uniform(VerticalAnchor.absolute(32), VerticalAnchor.absolute(112)))));
        context.register(PLACED_LITHIUM, new PlacedFeature(features.getOrThrow(ORE_LITHIUM), orePlacement(5,
                HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(48)))));
        context.register(PLACED_TITANIUM, new PlacedFeature(features.getOrThrow(ORE_TITANIUM), orePlacement(4,
                HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(16)))));
    }

    /** Ajoute les filons à tous les biomes de la Surface. */
    private static void biomeModifiers(BootstrapContext<BiomeModifier> context) {
        var biomes = context.lookup(Registries.BIOME);
        var placed = context.lookup(Registries.PLACED_FEATURE);

        context.register(ADD_ORES, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(placed.getOrThrow(PLACED_BAUXITE), placed.getOrThrow(PLACED_LITHIUM),
                        placed.getOrThrow(PLACED_TITANIUM)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    }

    /** Filon qui remplace la pierre par le minerai « pierre » et l'ardoise des abîmes par sa variante. */
    private static ConfiguredFeature<?, ?> ore(Block stoneOre, Block deepslateOre, int size) {
        RuleTest stone = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslate = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        return new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(List.of(
                OreConfiguration.target(stone, stoneOre.defaultBlockState()),
                OreConfiguration.target(deepslate, deepslateOre.defaultBlockState())), size));
    }

    private static List<net.minecraft.world.level.levelgen.placement.PlacementModifier> orePlacement(int count,
            HeightRangePlacement height) {
        return List.of(CountPlacement.of(count), InSquarePlacement.spread(), height, BiomeFilter.biome());
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> configuredKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, Orbitalis.id(name));
    }

    private static ResourceKey<PlacedFeature> placedKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Orbitalis.id(name));
    }
}
