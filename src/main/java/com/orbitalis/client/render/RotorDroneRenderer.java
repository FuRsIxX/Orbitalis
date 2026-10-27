package com.orbitalis.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase3.exploration.RotorDroneEntity;
import com.orbitalis.phase3.exploration.RotorDroneModelBlock;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dessine le drone volant à partir de deux modèles de bloc (portés par un bloc jamais posé) : son corps, puis ses huit
 * rotors, deux par bras, qui tournent en sens contraires. En croisière, il penche le nez vers l'avant, comme un vrai
 * giravion qui avance.
 */
public class RotorDroneRenderer extends EntityRenderer<RotorDroneEntity, RotorDroneRenderer.State> {
    /** Le modèle fait un bloc et demi d'envergure ; le drone est dessiné un peu plus grand. */
    private static final float SCALE = 1.2F;
    /** Moyeux des quatre bras, en pixels du modèle, et hauteur des deux rotors de chaque bras. */
    private static final float[][] HUBS = {{-3.5F, 8.0F}, {19.5F, 8.0F}, {8.0F, -3.5F}, {8.0F, 19.5F}};
    private static final float ROTOR_LOW = 8.4F;
    private static final float ROTOR_HIGH = 11.6F;
    /** Inclinaison du nez en croisière, en degrés. */
    private static final float CRUISE_TILT = 9.0F;

    public static class State extends EntityRenderState {
        float yaw;
        float rotor;
        float tilt;
    }

    public RotorDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.7F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RotorDroneEntity drone, State state, float partialTicks) {
        super.extractRenderState(drone, state, partialTicks);
        state.yaw = drone.getYRot(partialTicks);
        state.rotor = drone.rotorAngle(partialTicks);
        RotorDroneEntity.State flight = drone.state();
        state.tilt = flight == RotorDroneEntity.State.OUTBOUND || flight == RotorDroneEntity.State.RETURN ? CRUISE_TILT : 0.0F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState body = ModBlocks.ROTOR_DRONE_MODEL.get().defaultBlockState();
        BlockState rotor = body.setValue(RotorDroneModelBlock.ROTOR, true);
        poseStack.pushPose();
        // Le modèle regarde le nord ; le drone regarde là où pointe son lacet, le nez un peu baissé en croisière.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        poseStack.translate(0.0F, 0.6F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.tilt));
        poseStack.translate(0.0F, -0.6F, 0.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        ModelCache.get(body).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        for (int i = 0; i < HUBS.length; i++) {
            for (int level = 0; level < 2; level++) {
                poseStack.pushPose();
                poseStack.translate(HUBS[i][0] / 16.0F, (level == 0 ? ROTOR_LOW : ROTOR_HIGH) / 16.0F, HUBS[i][1] / 16.0F);
                // Les deux rotors d'un bras tournent en sens contraires, et chaque bras est décalé des autres.
                float angle = (level == 0 ? state.rotor : -state.rotor) + i * 0.7F;
                poseStack.mulPose(Axis.YP.rotation(angle));
                poseStack.translate(-0.5F, 0.0F, -0.5F);
                ModelCache.get(rotor).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
