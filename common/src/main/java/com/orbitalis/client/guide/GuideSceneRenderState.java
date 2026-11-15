package com.orbitalis.client.guide;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ce qu'il faut pour dessiner une démonstration en 3D dans une zone de l'écran :
 * les blocs à afficher, l'angle de vue et le cadrage.
 *
 * @param blocks      blocs visibles, avec leur position dans la scène
 * @param yaw         rotation de la scène autour de l'axe vertical, en degrés
 * @param pitch       inclinaison de la caméra vers le bas, en degrés
 * @param centerX     point de la scène placé au centre de la zone
 * @param scale       taille d'un bloc à l'écran, en pixels d'interface
 * @param scissorArea la découpe en cours dans l'écran, s'il y en a une
 */
public record GuideSceneRenderState(List<PlacedBlock> blocks, float yaw, float pitch,
        float centerX, float centerY, float centerZ,
        int x0, int y0, int x1, int y1, float scale,
        @Nullable ScreenRectangle scissorArea) {

    /** Bloc à dessiner, à une position qui peut être décalée par une animation. */
    public record PlacedBlock(BlockState state, float x, float y, float z) {
    }
}
