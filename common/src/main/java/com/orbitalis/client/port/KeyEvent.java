package com.orbitalis.client.port;

import org.lwjgl.glfw.GLFW;

/** Une touche du clavier, à la façon de Minecraft 26. */
public record KeyEvent(int key, int scancode, int modifiers) {
    public int input() {
        return key;
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

    public boolean isEscape() {
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean isConfirmation() {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
    }
}
