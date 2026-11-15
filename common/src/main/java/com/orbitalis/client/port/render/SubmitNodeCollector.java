package com.orbitalis.client.port.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ce à quoi les rendus du mod « soumettent » leurs formes en Minecraft 26, posé sur les tampons de 1.21.1 : chaque soumission
 * est dessinée tout de suite dans le tampon du type de rendu demandé.
 */
public final class SubmitNodeCollector {
    /** Une forme libre : ses sommets, écrits dans le tampon de son type de rendu. */
    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public MultiBufferSource buffers() {
        return buffers;
    }

    /** L'ordre de dessin, en 26 ; ici, les formes se dessinent dans l'ordre où elles arrivent. */
    public SubmitNodeCollector order(int order) {
        return this;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType type, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(type));
    }

    /** Un bloc entier, avec son vrai modèle. */
    public void submitBlock(PoseStack poseStack, BlockState state, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers, light, overlay);
    }

    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode,
            int light, int color, int background, int outline) {
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, shadow, poseStack.last().pose(), buffers, mode, background, light);
    }

    public void submitText(PoseStack poseStack, float x, float y, Component text, boolean shadow, Font.DisplayMode mode,
            int light, int color, int background, int outline) {
        submitText(poseStack, x, y, text.getVisualOrderText(), shadow, mode, light, color, background, outline);
    }

    /** Le contour d'une forme, en traits (la largeur des traits n'est pas réglable par forme en 1.21.1). */
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType type, int color, float width, boolean unused) {
        VertexConsumer buffer = buffers.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        float a = (color >>> 24) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float) (x2 - x1);
            float ny = (float) (y2 - y1);
            float nz = (float) (z2 - z1);
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0.0F) {
                nx /= length;
                ny /= length;
                nz /= length;
            }
            buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
            buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        });
    }
}
