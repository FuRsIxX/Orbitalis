package com.orbitalis.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase2.station.GlassDoorBlockEntity;
import com.orbitalis.phase2.station.GlassDoorLeafBlock;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine le battant d'une colonne de la porte vitrée : fermé, il rejoint celui de l'autre colonne au milieu de la
 * porte ; à l'ouverture, il s'écarte vers l'extérieur, de presque toute sa largeur, et glisse dans la paroi voisine.
 * Le modèle du bloc ne porte que le rail et le linteau : les battants sont toujours dessinés ici, pour qu'ils ne se
 * montrent jamais deux fois pendant qu'ils bougent.
 */
public class GlassDoorRenderer implements BlockEntityRenderer<GlassDoorBlockEntity, GlassDoorRenderer.State> {
    /** Course d'un battant, en blocs : un peu moins que sa largeur, il reste un liseré dans l'ouverture. */
    private static final float SLIDE = 15.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        float open;
        Direction facing = Direction.NORTH;
        /** Vrai pour la colonne de droite (vue de face) : son battant glisse vers la droite. */
        boolean right;
        int leafLight;
    }

    public GlassDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GlassDoorBlockEntity door, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(door, state, partialTicks, cameraPosition, breakProgress);
        state.open = door.openness(partialTicks);
        BlockState blockState = door.getBlockState();
        state.facing = blockState.hasProperty(HorizontalDirectionalBlock.FACING) ? blockState.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        state.right = blockState.hasProperty(com.orbitalis.phase2.station.GlassDoorBlock.RIGHT)
                && blockState.getValue(com.orbitalis.phase2.station.GlassDoorBlock.RIGHT);
        // Les battants prennent la lumière la plus claire des cases devant et derrière la porte.
        state.leafLight = state.lightCoords;
        if (door.getLevel() != null) {
            BlockPos pos = door.getBlockPos();
            int front = LightCoordsUtil.getLightCoords(door.getLevel(), pos.relative(state.facing));
            int back = LightCoordsUtil.getLightCoords(door.getLevel(), pos.relative(state.facing.getOpposite()));
            state.leafLight = LightCoordsUtil.max(state.lightCoords, LightCoordsUtil.max(front, back));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Les modèles des battants sont dessinés face au nord : on les tourne comme la porte.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // Vue de face (depuis le nord du modèle), la droite du joueur est à l'ouest : la colonne de droite glisse vers
        // l'ouest, celle de gauche vers l'est.
        poseStack.translate((state.right ? -SLIDE : SLIDE) * state.open, 0.0F, 0.0F);
        BlockState leaf = ModBlocks.GLASS_DOOR_LEAF.get().defaultBlockState().setValue(GlassDoorLeafBlock.RIGHT, state.right);
        ModelCache.get(leaf).submit(poseStack, collector, state.leafLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** Le battant monte sur deux blocs et déborde sur le côté en s'ouvrant. */
    @Override
    public AABB getRenderBoundingBox(GlassDoorBlockEntity door) {
        return new AABB(door.getBlockPos()).expandTowards(0.0, 1.0, 0.0).inflate(1.0);
    }
}
