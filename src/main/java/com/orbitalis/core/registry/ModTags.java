package com.orbitalis.core.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags communs (espace de noms « c »), partagés entre les mods.
 * Ils permettent aux autres mods (Create, etc.) de reconnaître nos matériaux, et inversement.
 */
public final class ModTags {
    private ModTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> ORES_BAUXITE = tag("ores/bauxite");
        public static final TagKey<Block> ORES_TITANIUM = tag("ores/titanium");
        public static final TagKey<Block> ORES_LITHIUM = tag("ores/lithium");

        public static final TagKey<Block> STORAGE_BLOCKS_STEEL = tag("storage_blocks/steel");
        public static final TagKey<Block> STORAGE_BLOCKS_ALUMINUM = tag("storage_blocks/aluminum");
        public static final TagKey<Block> STORAGE_BLOCKS_TITANIUM = tag("storage_blocks/titanium");

        private Blocks() {
        }

        private static TagKey<Block> tag(String path) {
            return BlockTags.create(Identifier.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> ORES_BAUXITE = tag("ores/bauxite");
        public static final TagKey<Item> ORES_TITANIUM = tag("ores/titanium");
        public static final TagKey<Item> ORES_LITHIUM = tag("ores/lithium");

        public static final TagKey<Item> STORAGE_BLOCKS_STEEL = tag("storage_blocks/steel");
        public static final TagKey<Item> STORAGE_BLOCKS_ALUMINUM = tag("storage_blocks/aluminum");
        public static final TagKey<Item> STORAGE_BLOCKS_TITANIUM = tag("storage_blocks/titanium");

        public static final TagKey<Item> RAW_MATERIALS_BAUXITE = tag("raw_materials/bauxite");
        public static final TagKey<Item> RAW_MATERIALS_TITANIUM = tag("raw_materials/titanium");
        public static final TagKey<Item> RAW_MATERIALS_LITHIUM = tag("raw_materials/lithium");

        public static final TagKey<Item> INGOTS_STEEL = tag("ingots/steel");
        public static final TagKey<Item> INGOTS_ALUMINUM = tag("ingots/aluminum");
        public static final TagKey<Item> INGOTS_TITANIUM = tag("ingots/titanium");
        public static final TagKey<Item> INGOTS_LITHIUM = tag("ingots/lithium");
        public static final TagKey<Item> INGOTS_ALUMINUM_LITHIUM = tag("ingots/aluminum_lithium");

        public static final TagKey<Item> PLATES = tag("plates");
        public static final TagKey<Item> PLATES_STEEL = tag("plates/steel");
        public static final TagKey<Item> PLATES_ALUMINUM = tag("plates/aluminum");
        public static final TagKey<Item> PLATES_TITANIUM = tag("plates/titanium");
        public static final TagKey<Item> PLATES_ALUMINUM_LITHIUM = tag("plates/aluminum_lithium");

        private Items() {
        }

        private static TagKey<Item> tag(String path) {
            return ItemTags.create(Identifier.fromNamespaceAndPath("c", path));
        }
    }
}
