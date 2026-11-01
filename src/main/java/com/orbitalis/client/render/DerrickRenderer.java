package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.quarry.DerrickBlockEntity;
import com.orbitalis.phase1.quarry.DerrickPlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine ce qui n'est pas un bloc autour d'un derrick : tant que sa tour n'est pas complète, les blocs qui manquent,
 * en fantôme ; ensuite, le train de tiges qui descend du derrick jusqu'au fond de son puits, et qui tourne en forant.
 */
public class DerrickRenderer implements BlockEntityRenderer<DerrickBlockEntity, DerrickRenderer.State> {
    /** Demi-largeur du train de tiges, en blocs. */
    private static final float ROD = 2.5F / 16.0F;

    public static class State extends BlockEntityRenderState {
        final List<BlockPos> ghostOffsets = new ArrayList<>();
        final List<BlockState> ghostStates = new ArrayList<>();
        int depth;
        boolean drilling;
        float spin;
    }

    public DerrickRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DerrickBlockEntity derrick, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(derrick, state, partialTicks, cameraPosition, breakProgress);
        state.ghostOffsets.clear();
        state.ghostStates.clear();
        if (derrick.getLevel() != null && !derrick.isFormed()) {
            for (DerrickPlan.Piece piece : DerrickPlan.missing(derrick.getLevel(), derrick.getBlockPos())) {
                state.ghostOffsets.add(piece.offset());
                state.ghostStates.add(piece.block().defaultBlockState());
            }
        }
        state.depth = Math.max(0, derrick.getBlockPos().getY() - derrick.boreBottom());
        state.drilling = derrick.getBlockState().getValue(BlockStateProperties.LIT);
        long time = derrick.getLevel() == null ? 0 : derrick.getLevel().getGameTime();
        state.spin = state.drilling ? ((time % 16) + partialTicks) / 16.0F : 0.0F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.ghostOffsets.isEmpty()) {
            GhostBlocks.submit(state.ghostOffsets, state.ghostStates, poseStack, collector);
        }
        if (state.depth <= 0) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(ModBlocks.STEEL_BLOCK.get().defaultBlockState()).sprite();
        int light = state.lightCoords;
        float lo = 0.5F - ROD;
        float hi = 0.5F + ROD;
        float spin = state.spin;
        int depth = state.depth;
        collector.submitCustomGeometry(poseStack, RenderTypes.text(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            for (int k = 1; k <= depth; k++) {
                float y0 = -k;
                float y1 = -k + 1;
                // Les quatre faces d'un tronçon ; la texture défile pendant le forage : la tige tourne.
                side(buffer, pose, sprite, light, spin, lo, y0, lo, hi, y1, lo);
                side(buffer, pose, sprite, light, spin, hi, y0, hi, lo, y1, hi);
                side(buffer, pose, sprite, light, spin, lo, y0, hi, lo, y1, lo);
                side(buffer, pose, sprite, light, spin, hi, y0, lo, hi, y1, hi);
            }
        });
    }

    /** Une face verticale, de (x0, z0) à (x1, z1), dessinée dans les deux sens. */
    private static void side(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float spin,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        float u0 = sprite.getU(spin * 0.5F);
        float u1 = sprite.getU(spin * 0.5F + 0.3F);
        float v0 = sprite.getV(0.0F);
        float v1 = sprite.getV(1.0F);
        float[][] corners = {{x0, y0, z0, u0, v1}, {x1, y0, z1, u1, v1}, {x1, y1, z1, u1, v0}, {x0, y1, z0, u0, v0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] c = corners[pass == 0 ? k : 3 - k];
                buffer.addVertex(pose, c[0], c[1], c[2]).setColor(0xFFB8C0C8).setUv(c[3], c[4]).setLight(light);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(DerrickBlockEntity derrick) {
        BlockPos pos = derrick.getBlockPos();
        return new AABB(pos.getX() - 1, derrick.boreBottom(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + DerrickPlan.LEG_HEIGHT + 1, pos.getZ() + 2);
    }
}
