package com.orbitalis.client.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.level.block.state.BlockState;

/** Le modèle d'un état de bloc, prêt à dessiner (comme en Minecraft 26). */
public final class BlockModelRenderState {
    private final BlockState state;

    public BlockModelRenderState(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    /** Dessine le bloc, du coin de la pile de transformations ; {@code tint} est ignoré (le modèle a ses teintes). */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int tint) {
        collector.submitBlock(poseStack, state, light, overlay);
    }
}
