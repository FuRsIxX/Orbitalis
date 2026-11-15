package com.orbitalis.client.port;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Les pixels d'une image en ARGB, comme en Minecraft 26 ; 1.21.1 les range en ABGR ({@code setPixelRGBA} malgré son nom).
 */
public final class Images {
    private Images() {
    }

    private static int swap(int color) {
        return color & 0xFF00FF00 | (color & 0xFF) << 16 | color >> 16 & 0xFF;
    }

    public static void setPixel(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, swap(argb));
    }

    public static int getPixel(NativeImage image, int x, int y) {
        return swap(image.getPixelRGBA(x, y));
    }
}
