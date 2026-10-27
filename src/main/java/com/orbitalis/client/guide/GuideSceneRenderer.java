package com.orbitalis.client.guide;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Dessine une démonstration du manuel en 3D : chaque bloc est rendu avec son vrai modèle
 * (orientation, face allumée, raccordements des conduits), dans une image incrustée dans l'interface.
 */
public class GuideSceneRenderer extends PictureInPictureRenderer<GuideSceneRenderState> {
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    @Override
    public Class<GuideSceneRenderState> getRenderStateClass() {
        return GuideSceneRenderState.class;
    }

    @Override
    protected void renderToTexture(GuideSceneRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        BlockModelResolver resolver = minecraft.getBlockModelResolver();

        applyCamera(poseStack, state.yaw(), state.pitch());
        for (GuideSceneRenderState.PlacedBlock block : state.blocks()) {
            poseStack.pushPose();
            poseStack.translate(block.x() - state.centerX(), block.y() - state.centerY(), block.z() - state.centerZ());
            BlockModelRenderState model = new BlockModelRenderState();
            resolver.update(model, block.state(), DISPLAY_CONTEXT);
            model.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
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

    /** La scène est centrée verticalement dans sa zone. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "orbitalis guide scene";
    }
}
