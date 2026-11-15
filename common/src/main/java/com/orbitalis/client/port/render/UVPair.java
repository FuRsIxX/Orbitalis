package com.orbitalis.client.port.render;

/** Deux coordonnées de texture réunies en un seul nombre, comme en Minecraft 26. */
public final class UVPair {
    private UVPair() {
    }

    public static long pack(float u, float v) {
        return (long) Float.floatToRawIntBits(u) << 32 | Float.floatToRawIntBits(v) & 0xFFFFFFFFL;
    }

    public static float unpackU(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    public static float unpackV(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
}
