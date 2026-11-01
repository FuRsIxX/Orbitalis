package com.orbitalis.client.render;

import com.orbitalis.core.registry.ModFluids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

/**
 * Apparence d'un fluide stocké : la texture animée de l'eau de Minecraft, teintée à la couleur du fluide. C'est celle
 * des fluides posés dans le monde : l'eau d'une jauge ou d'un réservoir est la même que celle d'un lac.
 */
public final class FluidSprites {
    private FluidSprites() {
    }

    /** La texture de l'eau immobile, dans l'atlas des blocs. */
    public static TextureAtlasSprite still() {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.WATER.defaultBlockState()).sprite();
    }

    /** Teinte du fluide, opaque. */
    public static int tint(Fluid fluid) {
        return 0xFF000000 | ModFluids.color(fluid);
    }
}
