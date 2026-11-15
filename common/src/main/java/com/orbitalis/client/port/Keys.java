package com.orbitalis.client.port;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

/** Les touches de modification tenues en ce moment, et le double clic (que 1.21.1 ne signale pas aux écrans). */
public final class Keys {
    private static final long DOUBLE_CLICK_MS = 250;
    private static long lastClick;
    private static int lastButton = -1;
    private static double lastX;
    private static double lastY;

    private Keys() {
    }

    private static boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    public static int modifiers() {
        int modifiers = 0;
        if (down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT)) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }

    /** Vrai si ce clic suit de près un clic du même bouton, au même endroit. */
    static boolean doubleClick(double x, double y, int button) {
        long now = net.minecraft.Util.getMillis();
        boolean result = button == lastButton && now - lastClick < DOUBLE_CLICK_MS && Math.abs(x - lastX) < 2 && Math.abs(y - lastY) < 2;
        lastClick = result ? 0 : now;
        lastButton = button;
        lastX = x;
        lastY = y;
        return result;
    }
}
