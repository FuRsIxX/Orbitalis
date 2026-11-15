package com.orbitalis.client.port;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/** La lumière d'un point (bloc et ciel, rangés en un seul entier), à la façon de Minecraft 26. */
public final class LightCoordsUtil {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private LightCoordsUtil() {
    }

    public static int pack(int block, int sky) {
        return LightTexture.pack(block, sky);
    }

    public static int block(int packed) {
        return LightTexture.block(packed);
    }

    public static int sky(int packed) {
        return LightTexture.sky(packed);
    }

    /** La plus forte des deux lumières, bloc par bloc et ciel par ciel. */
    public static int max(int a, int b) {
        return pack(Math.max(block(a), block(b)), Math.max(sky(a), sky(b)));
    }

    public static int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        return LevelRenderer.getLightColor(level, pos);
    }
}
