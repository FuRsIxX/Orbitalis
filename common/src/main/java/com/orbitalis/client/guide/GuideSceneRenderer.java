package com.orbitalis.client.guide;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.client.port.GuiGraphicsExtractor;
import com.orbitalis.client.port.LightCoordsUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Dessine une démonstration du manuel en 3D : chaque bloc est rendu avec son vrai modèle
 * (orientation, face allumée, raccordements des conduits), directement dans l'interface, découpé à sa zone.
 */
public final class GuideSceneRenderer {
    /** La profondeur de la scène au-dessus du fond de l'écran. */
    private static final float DEPTH = 200.0F;

    private GuideSceneRenderer() {
    }

    /** Dessine la scène dans sa zone ; l'écran passe ensuite à la strate suivante pour dessiner par-dessus. */
    public static void draw(GuiGraphicsExtractor extractor, GuideSceneRenderState state) {
        GuiGraphics graphics = extractor.vanilla();
        Minecraft minecraft = Minecraft.getInstance();
        graphics.flush();
        graphics.enableScissor(state.x0(), state.y0(), state.x1(), state.y1());
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        // Le centre de la zone, la scène centrée verticalement ; un bloc fait « scale » pixels.
        poseStack.translate((state.x0() + state.x1()) / 2.0F, (state.y0() + state.y1()) / 2.0F, DEPTH);
        poseStack.scale(state.scale(), state.scale(), -state.scale());
        applyCamera(poseStack, state.yaw(), state.pitch());
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        for (GuideSceneRenderState.PlacedBlock block : state.blocks()) {
            poseStack.pushPose();
            poseStack.translate(block.x() - state.centerX(), block.y() - state.centerY(), block.z() - state.centerZ());
            minecraft.getBlockRenderer().renderSingleBlock(block.state(), poseStack, buffers, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        graphics.flush();
        Lighting.setupForFlatItems();
        poseStack.popPose();
        graphics.disableScissor();
    }

    /** Oriente la scène : l'axe y pointe vers le haut de l'écran, puis la caméra s'incline et tourne. */
    private static void applyCamera(PoseStack poseStack, float yaw, float pitch) {
        // Demi-tour autour de l'axe z, comme pour les entités dessinées dans les interfaces :
        // il redresse la scène sans inverser la profondeur.
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    /**
     * Position à l'écran d'un point de la scène, relative au centre de la zone et pour un bloc d'un pixel de côté.
     * Reproduit exactement {@link #applyCamera} : l'écran s'en sert pour cadrer la scène et y superposer
     * les flux et les objets flottants.
     */
    public static Vector3f project(float x, float y, float z, float yaw, float pitch) {
        Matrix4f camera = new Matrix4f()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(-pitch))
                .rotateY((float) Math.toRadians(yaw));
        return camera.transformPosition(new Vector3f(x, y, z));
    }

    /**
     * Inverse de {@link #project} : ramène un vecteur de l'écran dans le repère de la scène.
     * L'écran s'en sert pour retrouver le bloc situé sous la souris.
     */
    public static Vector3f unproject(float x, float y, float z, float yaw, float pitch) {
        Matrix4f camera = new Matrix4f()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(-pitch))
                .rotateY((float) Math.toRadians(yaw));
        // Une rotation s'inverse en la transposant.
        return camera.transpose().transformPosition(new Vector3f(x, y, z));
    }
}
