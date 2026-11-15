package com.orbitalis.client.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Le shader du ciel du mod (assets/orbitalis/shaders/core/sky) : celui des faces texturées et colorées du jeu, sans le seuil
 * qui jette les pixels de moins de 10 % d'opacité. Les nuées très légères de la Voie lactée et les halos s'éteignent ainsi en
 * douceur, comme en 26. Chaque chargeur le compile et le donne ici.
 */
public final class SkyShader {
    private static @Nullable ShaderInstance shader;

    private SkyShader() {
    }

    public static void set(ShaderInstance compiled) {
        shader = compiled;
    }

    /** Le shader du ciel, ou celui du jeu s'il n'a pas pu être compilé. */
    public static ShaderInstance get() {
        return shader != null ? shader : GameRenderer.getPositionTexColorShader();
    }
}
