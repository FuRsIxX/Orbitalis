package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.gametest.framework.StructureGridSpawner;

/**
 * Les tests en jeu, plus écartés : certains bâtissent au-delà de leur structure (un module de station, une épave qui retombe).
 * En 26.2, chaque test gardait 15 blocs libres autour de lui ; 1.21.1 n'en laisse que 5 ou 6 entre deux tests.
 */
@Mixin(StructureGridSpawner.class)
public abstract class StructureGridSpawnerMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final int ORBITALIS_PADDING = 20;

    @ModifyConstant(method = "spawnStructure", constant = @Constant(intValue = 5))
    private int orbitalis$columns(int spacing) {
        return ORBITALIS_PADDING;
    }

    @ModifyConstant(method = "spawnStructure", constant = @Constant(intValue = 6))
    private int orbitalis$rows(int spacing) {
        return ORBITALIS_PADDING;
    }
}
