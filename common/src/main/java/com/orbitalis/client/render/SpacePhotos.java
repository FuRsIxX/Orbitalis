package com.orbitalis.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

/**
 * Les images de l'observatoire, en textures : chacune est peinte à la première demande (voir {@link SpacePhotoPainter}),
 * hors du fil de rendu, puis gardée tant que le jeu tourne.
 */
public final class SpacePhotos {
    /** Une image : sa peinture en cours, puis sa texture, et l'instant où elle est prête (pour son fondu). */
    private static final class Photo {
        CompletableFuture<int[]> painting;
        ResourceLocation id;
        long readyAt;
    }

    private static final Map<String, Photo> PHOTOS = new HashMap<>();

    private SpacePhotos() {
    }

    /** La texture de l'image de cet astre, ou {@code null} tant qu'elle se peint. */
    public static ResourceLocation get(String body) {
        Photo photo = PHOTOS.computeIfAbsent(body, key -> {
            Photo fresh = new Photo();
            fresh.painting = CompletableFuture.supplyAsync(() -> SpacePhotoPainter.paint(key));
            return fresh;
        });
        if (photo.id == null && photo.painting.isDone()) {
            int[] pixels = photo.painting.isCompletedExceptionally() ? null : photo.painting.getNow(null);
            if (pixels == null) {
                return null;
            }
            DynamicTexture texture = com.orbitalis.client.port.Textures.create("Orbitalis space photo", SpacePhotoPainter.WIDTH, SpacePhotoPainter.HEIGHT, false, true, false);
            NativeImage image = texture.getPixels();
            for (int y = 0; y < SpacePhotoPainter.HEIGHT; y++) {
                for (int x = 0; x < SpacePhotoPainter.WIDTH; x++) {
                    com.orbitalis.client.port.Images.setPixel(image, x, y, pixels[y * SpacePhotoPainter.WIDTH + x]);
                }
            }
            texture.upload();
            photo.id = Orbitalis.id("space_photo/" + body);
            Minecraft.getInstance().getTextureManager().register(photo.id, texture);
            photo.readyAt = Util.getMillis();
        }
        return photo.id;
    }

    /** Avancement du fondu d'apparition de l'image, de 0 à 1. */
    public static float appear(String body) {
        Photo photo = PHOTOS.get(body);
        return photo == null || photo.id == null ? 0.0F : Math.min(1.0F, (Util.getMillis() - photo.readyAt) / 350.0F);
    }
}
