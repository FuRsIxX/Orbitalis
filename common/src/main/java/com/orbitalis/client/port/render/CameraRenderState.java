package com.orbitalis.client.port.render;

import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** La caméra de l'image en cours : où elle est, comment elle est tournée (comme en Minecraft 26). */
public final class CameraRenderState {
    public Vec3 pos = Vec3.ZERO;
    public Quaternionf orientation = new Quaternionf();
    public float yRot;
    public float xRot;

    public static CameraRenderState of(Camera camera) {
        CameraRenderState state = new CameraRenderState();
        state.pos = camera.getPosition();
        state.orientation = new Quaternionf(camera.rotation());
        state.yRot = camera.getYRot();
        state.xRot = camera.getXRot();
        return state;
    }

    /** La caméra principale du jeu, en ce moment. */
    public static CameraRenderState current() {
        return of(Minecraft.getInstance().gameRenderer.getMainCamera());
    }
}
