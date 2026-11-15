package com.orbitalis.client.port.render;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Une face d'un modèle de bloc, lue à la façon de Minecraft 26 (ses coins, ses coordonnées de texture, sa texture et sa
 * teinte), sur la face de 1.21.1 dont les sommets sont rangés dans un tableau d'entiers.
 */
public final class BakedQuad {
    /** Le format des sommets des blocs : position (3), couleur, texture (2), lumière, normale. */
    private static final int STRIDE = 8;

    /** La texture et la teinte de la face. */
    public record MaterialInfo(TextureAtlasSprite sprite, int tintIndex) {
    }

    private final net.minecraft.client.renderer.block.model.BakedQuad quad;
    private final int[] vertices;

    public BakedQuad(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        this.quad = quad;
        this.vertices = quad.getVertices();
    }

    public net.minecraft.client.renderer.block.model.BakedQuad vanilla() {
        return quad;
    }

    public Direction direction() {
        return quad.getDirection();
    }

    public Vector3fc position(int vertex) {
        int at = vertex * STRIDE;
        return new Vector3f(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]), Float.intBitsToFloat(vertices[at + 2]));
    }

    /** Les coordonnées de texture du sommet, réunies comme en 26 (voir {@link UVPair}). */
    public long packedUV(int vertex) {
        int at = vertex * STRIDE;
        return UVPair.pack(Float.intBitsToFloat(vertices[at + 4]), Float.intBitsToFloat(vertices[at + 5]));
    }

    public MaterialInfo materialInfo() {
        return new MaterialInfo(quad.getSprite(), quad.getTintIndex());
    }

    public boolean isTinted() {
        return quad.isTinted();
    }
}
