package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.quarry.DerrickBlockEntity;
import com.orbitalis.phase1.quarry.DerrickPlan;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine ce qui n'est pas un bloc autour d'une foreuse : tant que sa tour n'est pas debout, les blocs qui manquent, en
 * fantôme ; et, quand son aperçu est allumé, la zone qu'elle fore : ses arêtes en tirets cyan d'un bloc, le cadre en
 * surface, celui du fond que permet la tour, et les quatre angles qui les relient.
 */
public class DerrickRenderer implements BlockEntityRenderer<DerrickBlockEntity, DerrickRenderer.State> {
    private static final Identifier PREVIEW = Orbitalis.id("textures/entity/drill_preview.png");
    /** Les deux cyans des tirets : celui des voyants de la tour, et un plus sombre. */
    private static final int LIT = 0xFF3FD8F2;
    private static final int DIM = 0xFF1C8FA8;
    /** Épaisseur des arêtes, et hauteur des angles au-dessus du sol, en blocs. */
    private static final float LINE = 2.0F / 16.0F;
    private static final float POST = 1.0F;

    public static class State extends BlockEntityRenderState {
        final List<BlockPos> ghostOffsets = new ArrayList<>();
        final List<BlockState> ghostStates = new ArrayList<>();
        boolean preview;
        /** La zone, dans le repère du bloc de commande : ses bords et le fond. */
        float x0;
        float z0;
        float x1;
        float z1;
        float floor;
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
            for (DerrickPlan.Piece piece : DerrickPlan.missing(derrick.getLevel(), derrick.getBlockPos(), derrick.facing())) {
                state.ghostOffsets.add(piece.offset());
                state.ghostStates.add(piece.block().defaultBlockState());
            }
        }
        state.preview = derrick.isPreviewing();
        if (state.preview) {
            BlockPos pos = derrick.getBlockPos();
            int[] area = derrick.area();
            state.x0 = area[0] - pos.getX();
            state.z0 = area[1] - pos.getZ();
            state.x1 = area[2] + 1 - pos.getX();
            state.z1 = area[3] + 1 - pos.getZ();
            state.floor = derrick.floor() - pos.getY();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.ghostOffsets.isEmpty()) {
            GhostBlocks.submit(state.ghostOffsets, state.ghostStates, poseStack, collector);
        }
        if (!state.preview) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(PREVIEW), (pose, buffer) -> {
            float top = 0.02F;
            // Le cadre en surface et celui du fond, en tirets d'un bloc.
            for (float y : new float[] {top, state.floor}) {
                dashes(buffer, pose, state.x0, y, state.z0, state.x1, state.z0 + LINE);
                dashes(buffer, pose, state.x0, y, state.z1 - LINE, state.x1, state.z1);
                dashes(buffer, pose, state.x0, y, state.z0, state.x0 + LINE, state.z1);
                dashes(buffer, pose, state.x1 - LINE, y, state.z0, state.x1, state.z1);
            }
            // Les quatre angles, du fond jusqu'au-dessus du sol.
            for (float[] corner : new float[][] {{state.x0, state.z0}, {state.x1 - LINE, state.z0}, {state.x0, state.z1 - LINE},
                    {state.x1 - LINE, state.z1 - LINE}}) {
                for (float y = state.floor; y < POST; y += 1.0F) {
                    int color = ((int) Math.floor(y)) % 2 == 0 ? LIT : DIM;
                    bar(buffer, pose, color, corner[0], y, corner[1], corner[0] + LINE, Math.min(POST, y + 1.0F), corner[1] + LINE);
                }
            }
        });
    }

    /** Une arête horizontale, en tirets d'un bloc qui alternent deux cyans, posés sur la grille du monde. */
    private static void dashes(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y, float z0, float x1, float z1) {
        boolean alongX = x1 - x0 >= z1 - z0;
        float start = alongX ? x0 : z0;
        float end = alongX ? x1 : z1;
        for (float from = start; from < end; from += 1.0F) {
            float to = Math.min(end, from + 1.0F);
            int color = Math.floorMod((int) Math.floor(from), 2) == 0 ? LIT : DIM;
            if (alongX) {
                bar(buffer, pose, color, from, y, z0, to, y + LINE, z1);
            } else {
                bar(buffer, pose, color, x0, y, from, x1, y + LINE, to);
            }
        }
    }

    /** Un quadrilatère de lumière, dessiné dans les deux sens. */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        float[][] corners = {{ax, ay, az, 0, 1}, {bx, by, bz, 1, 1}, {cx, cy, cz, 1, 0}, {dx, dy, dz, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] c = corners[pass == 0 ? k : 3 - k];
                buffer.addVertex(pose, c[0], c[1], c[2]).setColor(color).setUv(c[3], c[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** Une arête : un fin pavé de lumière, ses faces. */
    private static void bar(VertexConsumer buffer, PoseStack.Pose pose, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(buffer, pose, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, color, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(buffer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, color, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
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
        int[] area = derrick.area();
        return new AABB(Math.min(area[0], pos.getX() - 2), Math.min(derrick.floor(), pos.getY() - 1), Math.min(area[1], pos.getZ() - 2),
                Math.max(area[2] + 1, pos.getX() + 3), pos.getY() + DerrickPlan.MAX_LEVELS + 2, Math.max(area[3] + 1, pos.getZ() + 3));
    }
}
