package com.orbitalis.phase1.satellite;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minerais qu'un satellite d'observation sait reconnaître depuis l'orbite : ceux dont une agence spatiale a besoin,
 * et les grands classiques. Chacun a sa couleur sur la carte de prospection.
 */
public enum ProspectOre {
    BAUXITE(0xFFE08A4A, List.of(ModBlocks.BAUXITE_ORE, ModBlocks.DEEPSLATE_BAUXITE_ORE)),
    TITANIUM(0xFFB8C7DA, List.of(ModBlocks.TITANIUM_ORE, ModBlocks.DEEPSLATE_TITANIUM_ORE)),
    LITHIUM(0xFFE58FD0, List.of(ModBlocks.LITHIUM_ORE, ModBlocks.DEEPSLATE_LITHIUM_ORE)),
    IRON(0xFFD8AF93, List.of(() -> Blocks.IRON_ORE, () -> Blocks.DEEPSLATE_IRON_ORE)),
    COPPER(0xFF4FC3A1, List.of(() -> Blocks.COPPER_ORE, () -> Blocks.DEEPSLATE_COPPER_ORE)),
    COAL(0xFF7A7F87, List.of(() -> Blocks.COAL_ORE, () -> Blocks.DEEPSLATE_COAL_ORE)),
    GOLD(0xFFFFD84A, List.of(() -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE)),
    REDSTONE(0xFFFF5D5D, List.of(() -> Blocks.REDSTONE_ORE, () -> Blocks.DEEPSLATE_REDSTONE_ORE)),
    DIAMOND(0xFF6FE3F0, List.of(() -> Blocks.DIAMOND_ORE, () -> Blocks.DEEPSLATE_DIAMOND_ORE));

    private final int color;
    private final List<Supplier<? extends Block>> blocks;

    ProspectOre(int color, List<Supplier<? extends Block>> blocks) {
        this.color = color;
        this.blocks = blocks;
    }

    /** Couleur du minerai sur la carte. */
    public int color() {
        return color;
    }

    public String nameKey() {
        return "satellite.orbitalis.ore." + name().toLowerCase(Locale.ROOT);
    }

    /** Minerai que représente ce bloc, ou {@code null}. */
    public static ProspectOre of(BlockState state) {
        for (ProspectOre ore : values()) {
            for (Supplier<? extends Block> block : ore.blocks) {
                if (state.is(block.get())) {
                    return ore;
                }
            }
        }
        return null;
    }
}
