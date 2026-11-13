package com.orbitalis.platform;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Le glissement d'une entité vers la position que le serveur lui donne, en quelques ticks, chez le client : la même forme que
 * l'{@code InterpolationHandler} de Minecraft 26. L'entité lui passe chaque nouvelle position ({@link #lerpTo}) et l'appelle à
 * chaque tick ({@link #interpolate}).
 */
public final class InterpolationHandler {
    private final Entity entity;
    private final int steps;
    private int remaining;
    private double x;
    private double y;
    private double z;
    private float yRot;
    private float xRot;

    public InterpolationHandler(Entity entity, int steps) {
        this.entity = entity;
        this.steps = steps;
    }

    /** La position visée, à atteindre en {@code steps} ticks. */
    public void lerpTo(double x, double y, double z, float yRot, float xRot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.remaining = steps;
    }

    public boolean hasActiveInterpolation() {
        return remaining > 0;
    }

    /** Un pas vers la position visée. */
    public void interpolate() {
        if (remaining <= 0) {
            return;
        }
        double step = 1.0 / remaining;
        entity.setPos(Mth.lerp(step, entity.getX(), x), Mth.lerp(step, entity.getY(), y), Mth.lerp(step, entity.getZ(), z));
        entity.setYRot(entity.getYRot() + (float) (Mth.wrapDegrees(yRot - entity.getYRot()) * step));
        entity.setXRot(entity.getXRot() + (float) ((xRot - entity.getXRot()) * step));
        remaining--;
    }

    public void cancel() {
        remaining = 0;
    }
}
