package com.orbitalis.client.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le rendu d'une entité de bloc à la façon de Minecraft 26 (un état extrait, puis soumis), posé sur celui de 1.21.1. La pile
 * de transformations part du coin du bloc.
 */
public interface BlockEntityRenderer26<T extends BlockEntity, S extends BlockEntityRenderState> extends BlockEntityRenderer<T> {
    /** Une boîte sans bornes : le rendu se voit de partout. */
    AABB INFINITE = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    S createRenderState();

    default void extractRenderState(T blockEntity, S state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        state.blockPos = blockEntity.getBlockPos();
        state.blockState = blockEntity.getBlockState();
        state.blockEntityType = blockEntity.getType();
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    @Override
    default void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        S state = createRenderState();
        state.lightCoords = packedLight;
        CameraRenderState camera = CameraRenderState.current();
        extractRenderState(blockEntity, state, partialTick, camera.pos, null);
        submit(state, poseStack, new SubmitNodeCollector(buffers), camera);
    }

    /** Vrai si le rendu se dessine même quand son bloc n'est pas à l'écran (il déborde de beaucoup). */
    default boolean shouldRenderOffScreen() {
        return false;
    }

    @Override
    default boolean shouldRenderOffScreen(T blockEntity) {
        return shouldRenderOffScreen();
    }

    /** La boîte où le rendu dessine, pour savoir s'il est à l'écran (NeoForge s'en sert ; Fabric dessine sans la regarder). */
    default AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos());
    }
}
