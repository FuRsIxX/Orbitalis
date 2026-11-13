package com.orbitalis.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Les étiquettes communes aux chargeurs ({@code c:}), que Fabric et NeoForge partagent en 1.21.1. */
public final class Tags {
    private Tags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> ORES = tag("ores");
        public static final TagKey<Block> ORES_IN_GROUND_STONE = tag("ores_in_ground/stone");
        public static final TagKey<Block> ORES_IN_GROUND_DEEPSLATE = tag("ores_in_ground/deepslate");
        public static final TagKey<Block> ORES_IN_GROUND_NETHERRACK = tag("ores_in_ground/netherrack");

        private Blocks() {
        }

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> ORES = tag("ores");
        public static final TagKey<Item> RAW_MATERIALS = tag("raw_materials");
        public static final TagKey<Item> DUSTS_REDSTONE = tag("dusts/redstone");
        public static final TagKey<Item> INGOTS_IRON = tag("ingots/iron");
        public static final TagKey<Item> INGOTS_COPPER = tag("ingots/copper");
        public static final TagKey<Item> INGOTS_GOLD = tag("ingots/gold");
        public static final TagKey<Item> GLASS_BLOCKS = tag("glass_blocks");

        private Items() {
        }

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
