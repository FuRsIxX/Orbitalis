package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity;

import com.orbitalis.client.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.orbitalis.client.port.render.BlockEntityRenderState;
import com.orbitalis.client.port.render.ModelFeatureRenderer;
import com.orbitalis.client.port.render.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le plan du vaisseau, dans le berceau du chantier : à la place de chaque bloc qui manque, ce bloc en fantôme. Ceux de
 * la section en cours sont détaillés et cernés ; le reste du vaisseau n'est qu'une silhouette pâle.
 */
public class ShipyardConsoleRenderer implements com.orbitalis.client.port.render.BlockEntityRenderer26<ShipyardConsoleBlockEntity, ShipyardConsoleRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final List<BlockPos> ghostOffsets = new ArrayList<>();
        final List<BlockState> ghostStates = new ArrayList<>();
        int focus;
    }

    public ShipyardConsoleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShipyardConsoleBlockEntity console, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        com.orbitalis.client.port.render.BlockEntityRenderer26.super.extractRenderState(console, state, partialTicks, cameraPosition, breakProgress);
        state.ghostOffsets.clear();
        state.ghostStates.clear();
        for (ShipyardConsoleBlockEntity.Ghost ghost : console.ghosts()) {
            state.ghostOffsets.add(ghost.offset());
            state.ghostStates.add(ghost.state());
        }
        state.focus = console.focus();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        GhostBlocks.submitFocused(state.ghostOffsets, state.ghostStates, state.focus, poseStack, collector);
    }

    /** Le vaisseau s'étend bien au-delà du pupitre : il est dessiné même si le pupitre est hors champ. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(ShipyardConsoleBlockEntity console) {
        return com.orbitalis.client.port.render.BlockEntityRenderer26.INFINITE;
    }
}
