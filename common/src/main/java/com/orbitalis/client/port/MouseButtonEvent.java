package com.orbitalis.client.port;

import org.lwjgl.glfw.GLFW;

/** Un clic de souris, à la façon de Minecraft 26 : où, quel bouton, avec quelles touches tenues. */
public record MouseButtonEvent(double x, double y, int button, int modifiers) {
    public MouseButtonEvent(double x, double y, int button) {
        this(x, y, button, Keys.modifiers());
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }
}
