package com.orbitalis.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.phase1.spaceport.ElevatorCabinEntity;
import com.orbitalis.phase1.spaceport.StructurePartBlock;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Dessine la cabine de l'ascenseur de la tour : un seul modèle du kit du pas de tir, tourné comme la tour et posé
 * sur le plancher de la cabine. Ses occupants sont dessinés par le jeu, debout dedans.
 */
public class ElevatorCabinRenderer extends EntityRenderer<ElevatorCabinEntity, ElevatorCabinRenderer.State> {
    public static class State extends EntityRenderState {
        Direction facing = Direction.EAST;
    }

    public ElevatorCabinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ElevatorCabinEntity cabin, State state, float partialTicks) {
        super.extractRenderState(cabin, state, partialTicks);
        state.facing = cabin.getFacing();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Le modèle est centré sur son bloc : il va de l'arrière de la tour vers la fusée, le long de son axe x.
        poseStack.mulPose(Axis.YP.rotationDegrees(LaunchTableRenderer.angleTo(state.facing)));
        poseStack.translate(-0.5F, (float) ElevatorCabinEntity.FLOOR, -0.5F);
        LaunchTableRenderer.submitPart(StructurePartBlock.Part.ELEVATOR_CABIN, poseStack, collector, state.lightCoords);
        // La cabine fait trois blocs de haut : un modèle ne montant pas si haut, son tiers supérieur est une seconde pièce.
        poseStack.translate(0.0F, 2.0F, 0.0F);
        LaunchTableRenderer.submitPart(StructurePartBlock.Part.ELEVATOR_CABIN_TOP, poseStack, collector, state.lightCoords);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** La cabine est bien plus haute que son plancher : elle reste dessinée tant qu'une partie en est visible. */
    @Override
    protected AABB getBoundingBoxForCulling(ElevatorCabinEntity cabin) {
        return cabin.getBoundingBox().expandTowards(0.0, 3.0, 0.0);
    }
}
