package com.orbitalis.client.port.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Les types de rendu, sous les noms de Minecraft 26. */
public final class RenderTypes {
    private RenderTypes() {
    }

    public static RenderType entityCutout(ResourceLocation texture) {
        return RenderType.entityCutout(texture);
    }

    public static RenderType entityTranslucentEmissive(ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    public static RenderType lines() {
        return RenderType.lines();
    }

    public static RenderType text(ResourceLocation texture) {
        return RenderType.text(texture);
    }

    public static RenderType textBackground() {
        return RenderType.textBackground();
    }
}
