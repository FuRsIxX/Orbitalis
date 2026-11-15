package com.orbitalis.client.port;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Les images peintes par le mod : en 26, chacune avait son échantillonneur (filtrée ou non, répétée ou non) ; en 1.21.1, on
 * règle la texture elle-même, une fois créée.
 */
public final class Textures {
    private Textures() {
    }

    /** Une image vide de {@code width × height}, filtrée si {@code linear}, répétée sur ses bords si {@code repeat}. */
    public static DynamicTexture create(String name, int width, int height, boolean clear, boolean linear, boolean repeat) {
        DynamicTexture texture = new DynamicTexture(width, height, clear);
        configure(texture, linear, repeat);
        return texture;
    }

    public static DynamicTexture create(String name, int width, int height, boolean clear) {
        return create(name, width, height, clear, false, false);
    }

    public static DynamicTexture create(String name, NativeImage image, boolean linear, boolean repeat) {
        DynamicTexture texture = new DynamicTexture(image);
        configure(texture, linear, repeat);
        return texture;
    }

    private static void configure(DynamicTexture texture, boolean linear, boolean repeat) {
        Runnable apply = () -> {
            texture.setFilter(linear, false);
            texture.bind();
            int wrap = repeat ? GL11.GL_REPEAT : 0x812F;
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        };
        if (RenderSystem.isOnRenderThread()) {
            apply.run();
        } else {
            RenderSystem.recordRenderCall(apply::run);
        }
    }
}
