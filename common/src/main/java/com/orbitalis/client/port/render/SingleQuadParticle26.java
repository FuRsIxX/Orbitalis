package com.orbitalis.client.port.render;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Une particule faite d'une image, à la façon de Minecraft 26 (sa « couche », sa lumière, et ses faces « extraites » à chaque
 * image), sur la particule à texture de 1.21.1.
 */
public abstract class SingleQuadParticle26 extends TextureSheetParticle {
    /** Opaque ou translucide. */
    public enum Layer {
        OPAQUE,
        TRANSLUCENT;

        public static Layer bySprite(TextureAtlasSprite sprite) {
            return TRANSLUCENT;
        }
    }

    /** Ce que reçoit {@link #extract} en 26 ; ici, les faces vont droit au tampon de l'image. */
    public static final class QuadParticleRenderState {
        private QuadParticleRenderState() {
        }
    }

    private static final QuadParticleRenderState STATE = new QuadParticleRenderState();
    private VertexConsumer buffer;

    protected SingleQuadParticle26(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        setSprite(sprite);
    }

    protected SingleQuadParticle26(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd);
        setSprite(sprite);
    }

    public Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return getLayer() == Layer.OPAQUE ? ParticleRenderType.PARTICLE_SHEET_OPAQUE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public int getLightCoords(float partialTick) {
        return super.getLightColor(partialTick);
    }

    @Override
    protected final int getLightColor(float partialTick) {
        return getLightCoords(partialTick);
    }

    @Override
    public final void render(VertexConsumer buffer, Camera camera, float partialTick) {
        this.buffer = buffer;
        extract(STATE, camera, partialTick);
    }

    /** Les faces de la particule, pour cette image : par défaut, une image tournée vers la caméra. */
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        super.render(buffer, camera, partialTick);
    }

    /** Une face tournée de {@code rotation}, à {@code (x, y, z)} de la caméra. */
    protected void extractRotatedQuad(QuadParticleRenderState state, Quaternionf rotation, float x, float y, float z, float partialTick) {
        renderRotatedQuad(buffer, rotation, x, y, z, partialTick);
    }
}
