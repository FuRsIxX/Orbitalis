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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine ce qui n'est pas un bloc autour d'une foreuse : tant que sa tour n'est pas debout, les blocs qui manquent, en
 * fantôme ; ensuite, la flèche qui part du haut de la tour jusqu'au-dessus du milieu de la zone forée, et le train de
 * tiges qui en descend jusqu'à la couche en cours, et qui tourne en forant.
 */
public class DerrickRenderer implements BlockEntityRenderer<DerrickBlockEntity, DerrickRenderer.State> {
    /** Demi-largeur du train de tiges, puis de la flèche, en blocs. */
    private static final float ROD = 2.5F / 16.0F;
    private static final float BOOM = 3.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        final List<BlockPos> ghostOffsets = new ArrayList<>();
        final List<BlockState> ghostStates = new ArrayList<>();
        boolean formed;
        /** Haut de la tour, milieu de la tour et milieu de la zone, dans le repère du bloc de commande. */
        float boomY;
        float towerX;
        float towerZ;
        float centreX;
        float centreZ;
        float bottom;
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
        Direction facing = derrick.facing();
        state.formed = derrick.isFormed();
        if (derrick.getLevel() != null && !state.formed) {
            for (DerrickPlan.Piece piece : DerrickPlan.missing(derrick.getLevel(), derrick.getBlockPos(), facing)) {
                state.ghostOffsets.add(piece.offset());
                state.ghostStates.add(piece.block().defaultBlockState());
            }
        }
        BlockPos pos = derrick.getBlockPos();
        BlockPos tower = BlockPos.ZERO.relative(facing.getOpposite());
        state.towerX = tower.getX() + 0.5F;
        state.towerZ = tower.getZ() + 0.5F;
        int[] area = derrick.area();
        state.centreX = (area[0] + area[2] + 1) / 2.0F - pos.getX();
        state.centreZ = (area[1] + area[3] + 1) / 2.0F - pos.getZ();
        state.boomY = derrick.levels() + 1.0F;
        state.bottom = derrick.boreBottom() - pos.getY();
        boolean drilling = derrick.getBlockState().getValue(BlockStateProperties.LIT);
        long time = derrick.getLevel() == null ? 0 : derrick.getLevel().getGameTime();
        state.spin = drilling ? ((time % 16) + partialTicks) / 16.0F : 0.0F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.ghostOffsets.isEmpty()) {
            GhostBlocks.submit(state.ghostOffsets, state.ghostStates, poseStack, collector);
        }
        if (!state.formed) {
            return;
        }
        TextureAtlasSprite steel = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(ModBlocks.STEEL_BLOCK.get().defaultBlockState()).sprite();
        TextureAtlasSprite mast = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(ModBlocks.DRILL_MAST.get().defaultBlockState()).sprite();
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.text(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            float y0 = state.boomY;
            float y1 = state.boomY + 2 * BOOM;
            // La flèche : d'abord dans l'axe de la tour, puis, pour un tronçon décalé, de côté jusqu'au milieu de la zone.
            float midX = Math.abs(state.centreX - state.towerX) > Math.abs(state.centreZ - state.towerZ) ? state.centreX : state.towerX;
            float midZ = midX == state.centreX ? state.towerZ : state.centreZ;
            beam(buffer, pose, mast, light, state.towerX, state.towerZ, midX, midZ, y0, y1);
            beam(buffer, pose, mast, light, midX, midZ, state.centreX, state.centreZ, y0, y1);
            // Le train de tiges, de la flèche à la couche en cours, tronçon par tronçon : la texture défile, la tige tourne.
            for (float top = y0; top > state.bottom; top -= 1.0F) {
                float low = Math.max(state.bottom, top - 1.0F);
                rod(buffer, pose, steel, light, state.spin, state.centreX, state.centreZ, low, top);
            }
        });
    }

    /** Un tronçon droit de la flèche, d'un point à un autre dans l'axe, pavé par pavé d'un bloc : la texture ne s'étire pas. */
    private static void beam(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float ax, float az,
            float bx, float bz, float y0, float y1) {
        float length = Math.abs(bx - ax) + Math.abs(bz - az);
        if (length <= 0.0F) {
            return;
        }
        float dx = (bx - ax) / length;
        float dz = (bz - az) / length;
        for (float from = 0.0F; from < length; from += 1.0F) {
            float to = Math.min(length, from + 1.0F);
            float x0 = ax + dx * from;
            float z0 = az + dz * from;
            float x1 = ax + dx * to;
            float z1 = az + dz * to;
            box(buffer, pose, sprite, light, Math.min(x0, x1) - BOOM, y0, Math.min(z0, z1) - BOOM, Math.max(x0, x1) + BOOM, y1,
                    Math.max(z0, z1) + BOOM);
        }
    }

    /** Un pavé, ses six faces tournées vers l'extérieur, la texture plaquée entière sur chacune. */
    private static void box(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float[][][] faces = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}},
                {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}};
        float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        for (int face = 0; face < faces.length; face++) {
            int shade = face == 0 ? 0xFFFFFFFF : face == 1 ? 0xFF7F7F7F : face < 4 ? 0xFFCCCCCC : 0xFF999999;
            for (int k = 0; k < 4; k++) {
                float[] c = faces[face][k];
                buffer.addVertex(pose, c[0], c[1], c[2]).setColor(shade).setUv(uv[k][0], uv[k][1]).setLight(light);
            }
        }
    }

    /** Un tronçon du train de tiges, entre deux hauteurs, ses quatre faces dessinées dans les deux sens. */
    private static void rod(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float spin, float cx, float cz,
            float low, float high) {
        float lo = -ROD;
        float hi = ROD;
        float[][] sides = {{lo, lo, hi, lo}, {hi, hi, lo, hi}, {lo, hi, lo, lo}, {hi, lo, hi, hi}};
        float u0 = sprite.getU(spin * 0.5F);
        float u1 = sprite.getU(spin * 0.5F + 0.3F);
        float v0 = sprite.getV(0.0F);
        float v1 = sprite.getV(high - low);
        for (float[] s : sides) {
            float[][] corners = {{cx + s[0], low, cz + s[1], u0, v1}, {cx + s[2], low, cz + s[3], u1, v1},
                    {cx + s[2], high, cz + s[3], u1, v0}, {cx + s[0], high, cz + s[1], u0, v0}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] c = corners[pass == 0 ? k : 3 - k];
                    buffer.addVertex(pose, c[0], c[1], c[2]).setColor(0xFFB8C0C8).setUv(c[3], c[4]).setLight(light);
                }
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
        int[] area = derrick.area();
        return new AABB(Math.min(area[0], pos.getX() - 2), derrick.boreBottom(), Math.min(area[1], pos.getZ() - 2),
                Math.max(area[2] + 1, pos.getX() + 3), pos.getY() + DerrickPlan.MAX_LEVELS + 2, Math.max(area[3] + 1, pos.getZ() + 3));
    }
}
