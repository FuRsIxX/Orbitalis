package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase3.interstellar.RingDriveBlockEntity;
import com.orbitalis.phase3.interstellar.RingSegmentBlock;
import com.orbitalis.phase3.interstellar.ShipPlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * L'anneau du vaisseau en rotation : chaque bloc de l'anneau et de ses rayons, à la place de son segment invisible,
 * tourné autour de l'axe du vaisseau de l'angle que donne le moteur. Un segment qui manque ne se dessine pas.
 * <p>
 * Chaque bloc garde la variante de modèle qu'il a, posé à cette place (la coque matelassée ou à main courante) : le jeu
 * la tire de la position du bloc, et on la tire de même. Rien ne change d'aspect quand l'anneau démarre ou s'arrête.
 */
public class RingDriveRenderer implements BlockEntityRenderer<RingDriveBlockEntity, RingDriveRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final List<BlockPos> offsets = new ArrayList<>();
        final List<List<BakedQuad>> models = new ArrayList<>();
        final List<Boolean> glass = new ArrayList<>();
        float angle;
        Direction.Axis axis = Direction.Axis.X;
        int ringLight;
    }

    private record Cell(BlockState state, List<BakedQuad> quads) {
    }

    /** Les faces de chaque bloc de l'anneau, à sa place : relevées une fois, tant que le bloc ne change pas. */
    private final Map<BlockPos, Cell> cells = new HashMap<>();

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
        state.models.clear();
        state.glass.clear();
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
                BlockState original = RingSegmentBlock.original(there);
                state.offsets.add(cell.subtract(pos));
                state.models.add(quads(cell, original));
                state.glass.add(original.is(ModBlocks.STATION_WINDOW.get()));
            }
        }
    }

    /** Les faces du modèle de ce bloc, dans la variante qu'il a à cette place. */
    private List<BakedQuad> quads(BlockPos cell, BlockState state) {
        Cell known = cells.get(cell);
        if (known != null && known.state() == state) {
            return known.quads();
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(state.getSeed(cell)), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction direction : Direction.values()) {
                quads.addAll(part.getQuads(direction));
            }
            quads.addAll(part.getQuads(null));
        }
        cells.put(cell.immutable(), new Cell(state, List.copyOf(quads)));
        return quads;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.offsets.isEmpty()) {
            return;
        }
        // Le pivot : le centre du bloc de l'axe, sous le moteur.
        float down = ShipPlan.RING_DRIVE_Y;
        int light = state.ringLight;
        for (int i = 0; i < state.offsets.size(); i++) {
            BlockPos offset = state.offsets.get(i);
            List<BakedQuad> quads = state.models.get(i);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F - down, 0.5F);
            poseStack.mulPose(state.axis == Direction.Axis.X ? Axis.XP.rotationDegrees(state.angle) : Axis.ZP.rotationDegrees(state.angle));
            poseStack.translate(offset.getX() - 0.5F, offset.getY() + down - 0.5F, offset.getZ() - 0.5F);
            collector.submitCustomGeometry(poseStack, state.glass.get(i) ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
                    (pose, buffer) -> {
                        for (BakedQuad quad : quads) {
                            put(pose, buffer, quad, light);
                        }
                    });
            poseStack.popPose();
        }
    }

    private static void put(PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, int light) {
        Vector3fc normal = quad.direction().getUnitVec3f();
        for (int vertex = 0; vertex < 4; vertex++) {
            Vector3fc position = quad.position(vertex);
            long uv = quad.packedUV(vertex);
            buffer.addVertex(pose, position.x(), position.y(), position.z()).setColor(0xFFFFFFFF).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normal.x(), normal.y(), normal.z());
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
