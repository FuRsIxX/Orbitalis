package com.orbitalis.client.port.event;

import net.minecraft.client.Camera;

/** Ce que voit la caméra, à chaque image : ses angles, la couleur du brouillard, le champ de vision (comme sous NeoForge). */
public final class ViewportEvent {
    private ViewportEvent() {
    }

    /** Les angles de la caméra, en degrés, qu'on peut changer (secousses, cabine qui tourne). */
    public static final class ComputeCameraAngles {
        private final Camera camera;
        private final double partialTick;
        private float yaw;
        private float pitch;
        private float roll;

        public ComputeCameraAngles(Camera camera, double partialTick, float yaw, float pitch, float roll) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public Camera getCamera() {
            return camera;
        }

        public double getPartialTick() {
            return partialTick;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public float getRoll() {
            return roll;
        }

        public void setRoll(float roll) {
            this.roll = roll;
        }
    }

    /** La couleur du brouillard (et du ciel bas), de 0 à 1. */
    public static final class ComputeFogColor {
        private final Camera camera;
        private final double partialTick;
        private float red;
        private float green;
        private float blue;

        public ComputeFogColor(Camera camera, double partialTick, float red, float green, float blue) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public Camera getCamera() {
            return camera;
        }

        public double getPartialTick() {
            return partialTick;
        }

        public float getRed() {
            return red;
        }

        public void setRed(float red) {
            this.red = red;
        }

        public float getGreen() {
            return green;
        }

        public void setGreen(float green) {
            this.green = green;
        }

        public float getBlue() {
            return blue;
        }

        public void setBlue(float blue) {
            this.blue = blue;
        }
    }

    /** Le champ de vision, en degrés. */
    public static final class ComputeFov {
        private final Camera camera;
        private final double partialTick;
        private double fov;

        public ComputeFov(Camera camera, double partialTick, double fov) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.fov = fov;
        }

        public Camera getCamera() {
            return camera;
        }

        public double getPartialTick() {
            return partialTick;
        }

        public double getFOV() {
            return fov;
        }

        public void setFOV(double fov) {
            this.fov = fov;
        }
    }
}
