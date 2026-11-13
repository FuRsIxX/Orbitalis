package com.orbitalis.core.world;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le style d'un centre spatial : tous ses bâtiments partagent les mêmes matériaux, et deux centres se ressemblent
 * rarement. Bardage blanc et liseré bleu d'une agence, béton brut et orange d'un centre d'essais, enduit clair et
 * céramique d'un centre du désert, blanc et orange d'une base polaire…
 *
 * @param wall   les murs
 * @param accent les bandeaux, les portes de hangar, les bandes des réservoirs
 * @param floor  les sols intérieurs
 * @param roof   les toits plats
 * @param frame  les poteaux, les angles, les encadrements
 */
public record FacilityStyle(BlockState wall, BlockState accent, BlockState floor, BlockState roof, BlockState frame) {
    public static final int COUNT = 5;
    private static FacilityStyle[] styles;

    public static FacilityStyle of(int index) {
        if (styles == null) {
            BlockState concrete = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
            BlockState siding = ModBlocks.FACILITY_SIDING.get().defaultBlockState();
            BlockState blue = ModBlocks.BLUE_FACILITY_SIDING.get().defaultBlockState();
            BlockState orange = ModBlocks.ORANGE_FACILITY_SIDING.get().defaultBlockState();
            BlockState tiles = ModBlocks.FACILITY_TILES.get().defaultBlockState();
            BlockState gray = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
            styles = new FacilityStyle[] {
                    // L'agence : bardage blanc, liseré bleu, carrelage clair.
                    new FacilityStyle(siding, blue, tiles, concrete, concrete),
                    // Le centre d'essais : béton brut, bandeaux orange, sol d'andésite polie.
                    new FacilityStyle(concrete, orange, Blocks.POLISHED_ANDESITE.defaultBlockState(), gray, Blocks.SMOOTH_STONE.defaultBlockState()),
                    // L'institut : enduit gris clair, céramique turquoise, sol de pierre lisse.
                    new FacilityStyle(Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState(), Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN).defaultBlockState(),
                            Blocks.SMOOTH_STONE.defaultBlockState(), concrete, gray),
                    // Le centre du désert : enduit blanc cassé, liseré bleu.
                    new FacilityStyle(Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(), blue, tiles, Blocks.SMOOTH_STONE.defaultBlockState(),
                            concrete),
                    // La base polaire : bardage blanc, gros bandeaux orange, qu'on voit de loin dans la neige.
                    new FacilityStyle(siding, orange, Blocks.POLISHED_ANDESITE.defaultBlockState(), gray, concrete)};
        }
        return styles[Math.floorMod(index, COUNT)];
    }
}
