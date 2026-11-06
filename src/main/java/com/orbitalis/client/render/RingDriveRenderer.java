package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.phase3.interstellar.RingDriveBlockEntity;
import com.orbitalis.phase3.interstellar.RingSegmentBlock;
import com.orbitalis.phase3.interstellar.ShipPlan;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * L'anneau du vaisseau en rotation : chaque bloc de l'anneau et de ses rayons, à la place de son segment invisible,
 * tourné autour de l'axe du vaisseau de l'angle que donne le moteur. Un segment qui manque ne se dessine pas.
 */
public class RingDriveRenderer implements BlockEntityRenderer<RingDriveBlockEntity, RingDriveRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final List<BlockPos> offsets = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        float angle;
        Direction.Axis axis = Direction.Axis.X;
        int ringLight;
    }

    public RingDriveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RingDriveBlockEntity drive, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(drive, state, partialTicks, cameraPosition, breakProgress);
        state.offsets.clear();
        state.states.clear();
        Level level = drive.getLevel();
        if (level == null || !drive.spinning()) {
            return;
        }
        state.angle = (float) (drive.angle(level.getGameTime() + (double) partialTicks) % 360.0);
        state.axis = drive.out().getAxis();
        // L'anneau est dehors, sous le ciel : la lumière du ciel en plein, et celle des lampes du couloir.
        state.ringLight = LightCoordsUtil.pack(LightCoordsUtil.block(state.lightCoords), 15);
        BlockPos origin = drive.shipOrigin();
        BlockPos pos = drive.getBlockPos();
        for (ShipPlan.Piece piece : ShipPlan.ring(drive.out())) {
            BlockPos cell = origin.offset(piece.offset());
            BlockState there = level.getBlockState(cell);
            if (there.getBlock() instanceof RingSegmentBlock) {
                state.offsets.add(cell.subtract(pos));
                state.states.add(RingSegmentBlock.original(there));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.offsets.isEmpty()) {
            return;
        }
        // Le pivot : le centre du bloc de l'axe, sous le moteur.
        float down = ShipPlan.RING_DRIVE_Y;
        for (int i = 0; i < state.offsets.size(); i++) {
            BlockPos offset = state.offsets.get(i);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F - down, 0.5F);
            poseStack.mulPose(state.axis == Direction.Axis.X ? Axis.XP.rotationDegrees(state.angle) : Axis.ZP.rotationDegrees(state.angle));
            poseStack.translate(offset.getX() - 0.5F, offset.getY() + down - 0.5F, offset.getZ() - 0.5F);
            ModelCache.get(state.states.get(i)).submit(poseStack, collector, state.ringLight, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** L'anneau déborde largement du moteur : il est dessiné même si le moteur est hors champ. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(RingDriveBlockEntity drive) {
        BlockPos center = drive.ringCenter();
        int r = ShipPlan.RING_RADIUS + 1;
        return new AABB(center).inflate(r);
    }
}
