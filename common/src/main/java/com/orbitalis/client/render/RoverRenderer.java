package com.orbitalis.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase3.exploration.RoverEntity;
import com.orbitalis.phase3.exploration.RoverModelBlock;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dessine le rover à partir de deux modèles de bloc (portés par un bloc jamais posé) : son châssis, puis ses quatre
 * roues. Les roues tournent de la distance parcourue et braquent dans les virages (l'avant d'un côté, l'arrière un peu
 * de l'autre) ; le châssis se cabre, plonge et penche selon la conduite. Son conducteur est dessiné par le jeu.
 */
public class RoverRenderer extends EntityRenderer<RoverEntity, RoverRenderer.State> {
    /** Les modèles sont dessinés à 32 pixels pour deux blocs ; le rover en fait 1,75 de long. */
    private static final float SCALE = 0.875F;
    /** Centre de chaque roue dans le modèle du châssis, en pixels : gauche avant, droite avant, gauche arrière, droite arrière. */
    private static final float[][] WHEELS = {{-6, -2}, {22, -2}, {-6, 18}, {22, 18}};
    private static final float WHEEL_Y = 5.0F;
    /** Part du braquage que prennent les roues arrière, dans l'autre sens. */
    private static final float REAR_STEER = 0.5F;

    public static class State extends EntityRenderState {
        float yaw;
        float spin;
        float steer;
        float pitch;
        float roll;
    }

    public RoverRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.8F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RoverEntity rover, State state, float partialTicks) {
        super.extractRenderState(rover, state, partialTicks);
        state.yaw = rover.renderYaw(partialTicks);
        state.spin = rover.wheelSpin(partialTicks);
        state.steer = rover.steer(partialTicks);
        state.pitch = rover.bodyPitch(partialTicks);
        state.roll = rover.bodyRoll(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState body = ModBlocks.ROVER_MODEL.get().defaultBlockState();
        BlockState wheel = body.setValue(RoverModelBlock.WHEEL, true);
        poseStack.pushPose();
        // Les modèles regardent le nord ; le rover regarde là où pointe son lacet.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        // Les roues restent au sol : elles braquent autour de leur pivot, et tournent sur leur axe.
        for (int i = 0; i < WHEELS.length; i++) {
            boolean right = i % 2 == 1;
            boolean front = i < 2;
            poseStack.pushPose();
            poseStack.translate(WHEELS[i][0] / 16.0F, WHEEL_Y / 16.0F, WHEELS[i][1] / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(front ? -state.steer : state.steer * REAR_STEER));
            if (right) {
                // La même roue, retournée : son moyeu regarde vers l'extérieur, et elle tourne donc à l'envers.
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            poseStack.mulPose(Axis.XP.rotation(right ? state.spin : -state.spin));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            ModelCache.get(wheel).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // Le châssis, sur ses suspensions : il pivote autour de son centre, au ras des roues.
        poseStack.translate(0.5F, WHEEL_Y / 16.0F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.translate(-0.5F, -WHEEL_Y / 16.0F, -0.5F);
        ModelCache.get(body).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
