package com.orbitalis.client;

import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * L'apparence des mondes de l'espace (orbites, Lune, Europe) : pas de nuages, pas de ciel du jeu ; le ciel est celui du mod
 * (voir {@link SpaceSky}). Sous NeoForge, le jeu appelle {@link #renderSky} ; sous Fabric, le ciel est inscrit à part, pour
 * chaque monde.
 */
public final class SpaceEffects extends DimensionSpecialEffects {
    public static final SpaceSky SKY = new SpaceSky();

    public SpaceEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    /** Le ciel du mod, à la place de celui du jeu (méthode de NeoForge ; elle ne sert à rien d'autre). */
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
            Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return SKY.render(modelViewMatrix);
    }

    /** Pas de nuages dans l'espace (méthode de NeoForge). */
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
            double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true;
    }
}
