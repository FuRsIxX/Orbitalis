package com.orbitalis.datagen;

import java.util.concurrent.CompletableFuture;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagCopyingItemTagProvider;

/**
 * Génère les tags d'items. Les tags des blocs sont recopiés vers leurs items.
 */
public class OrbitalisItemTagsProvider extends BlockTagCopyingItemTagProvider {
    public OrbitalisItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags, Orbitalis.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Tags recopiés depuis les blocs.
        copy(ModTags.Blocks.ORES_BAUXITE, ModTags.Items.ORES_BAUXITE);
        copy(ModTags.Blocks.ORES_TITANIUM, ModTags.Items.ORES_TITANIUM);
        copy(ModTags.Blocks.ORES_LITHIUM, ModTags.Items.ORES_LITHIUM);
        copy(Tags.Blocks.ORES, Tags.Items.ORES);
        copy(Tags.Blocks.ORES_IN_GROUND_STONE, Tags.Items.ORES_IN_GROUND_STONE);
        copy(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE, Tags.Items.ORES_IN_GROUND_DEEPSLATE);
        copy(ModTags.Blocks.STORAGE_BLOCKS_STEEL, ModTags.Items.STORAGE_BLOCKS_STEEL);
        copy(ModTags.Blocks.STORAGE_BLOCKS_ALUMINUM, ModTags.Items.STORAGE_BLOCKS_ALUMINUM);
        copy(ModTags.Blocks.STORAGE_BLOCKS_TITANIUM, ModTags.Items.STORAGE_BLOCKS_TITANIUM);
        copy(Tags.Blocks.STORAGE_BLOCKS, Tags.Items.STORAGE_BLOCKS);

        // Minerais bruts.
        tag(ModTags.Items.RAW_MATERIALS_BAUXITE).add(ModItems.RAW_BAUXITE.getKey());
        tag(ModTags.Items.RAW_MATERIALS_TITANIUM).add(ModItems.RAW_TITANIUM.getKey());
        tag(ModTags.Items.RAW_MATERIALS_LITHIUM).add(ModItems.RAW_LITHIUM.getKey());
        tag(Tags.Items.RAW_MATERIALS).addTags(ModTags.Items.RAW_MATERIALS_BAUXITE,
                ModTags.Items.RAW_MATERIALS_TITANIUM, ModTags.Items.RAW_MATERIALS_LITHIUM);

        // Lingots.
        tag(ModTags.Items.INGOTS_STEEL).add(ModItems.STEEL_INGOT.getKey());
        tag(ModTags.Items.INGOTS_ALUMINUM).add(ModItems.ALUMINUM_INGOT.getKey());
        tag(ModTags.Items.INGOTS_TITANIUM).add(ModItems.TITANIUM_INGOT.getKey());
        tag(ModTags.Items.INGOTS_LITHIUM).add(ModItems.LITHIUM_INGOT.getKey());
        tag(ModTags.Items.INGOTS_ALUMINUM_LITHIUM).add(ModItems.ALUMINUM_LITHIUM_INGOT.getKey());
        tag(Tags.Items.INGOTS).addTags(ModTags.Items.INGOTS_STEEL, ModTags.Items.INGOTS_ALUMINUM,
                ModTags.Items.INGOTS_TITANIUM, ModTags.Items.INGOTS_LITHIUM, ModTags.Items.INGOTS_ALUMINUM_LITHIUM);

        // Tôles.
        tag(ModTags.Items.PLATES_STEEL).add(ModItems.STEEL_PLATE.getKey());
        tag(ModTags.Items.PLATES_ALUMINUM).add(ModItems.ALUMINUM_PLATE.getKey());
        tag(ModTags.Items.PLATES_TITANIUM).add(ModItems.TITANIUM_PLATE.getKey());
        tag(ModTags.Items.PLATES_ALUMINUM_LITHIUM).add(ModItems.ALUMINUM_LITHIUM_PLATE.getKey());
        tag(ModTags.Items.PLATES).addTags(ModTags.Items.PLATES_STEEL, ModTags.Items.PLATES_ALUMINUM,
                ModTags.Items.PLATES_TITANIUM, ModTags.Items.PLATES_ALUMINUM_LITHIUM);
    }
}
