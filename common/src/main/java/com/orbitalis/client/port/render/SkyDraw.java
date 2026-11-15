package com.orbitalis.client.port.render;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Le dessin du ciel du mod (étoiles, astres, distorsion) en 1.21.1 : des faces texturées et colorées, translucides, sans
 * écrire la profondeur, avec la pile de transformations du jeu (que le ciel du mod règle sur la caméra avant de dessiner).
 * C'était, en 26, le pipeline {@code END_SKY} d'une passe de rendu.
 */
public final class SkyDraw {
    private SkyDraw() {
    }

    private static void begin(AbstractTexture texture) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void end() {
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Dessine ces faces une fois. */
    public static void draw(@Nullable MeshData data, AbstractTexture texture) {
        if (data == null) {
            return;
        }
        begin(texture);
        BufferUploader.drawWithShader(data);
        end();
    }

    /** Garde ces faces sur la carte graphique, pour les redessiner à chaque image. */
    public static VertexBuffer upload(MeshData data) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
        return buffer;
    }

    /** Dessine des faces gardées, avec la pile de transformations en cours. */
    public static void draw(VertexBuffer buffer, AbstractTexture texture) {
        begin(texture);
        buffer.bind();
        buffer.drawWithShader(new Matrix4f(RenderSystem.getModelViewStack()), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        end();
    }

    /** Règle la pile de transformations du jeu sur la caméra, le temps de dessiner le ciel du mod. */
    public static void push(Matrix4f modelView) {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(modelView);
        RenderSystem.applyModelViewMatrix();
    }

    public static void pop() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
