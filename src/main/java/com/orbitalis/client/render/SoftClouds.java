package com.orbitalis.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Les nuages de l'atlas, adoucis. On part de l'image des nuages du jeu, pour garder leurs formes et leur dérive, et
 * l'on en tire une image deux fois plus fine : les bords sont estompés par un flou, la densité varie (un nuage est
 * plus épais en son cœur qu'à ses franges), les plus petits lambeaux disparaissent, et chaque nuage reçoit un léger
 * modelé, plus clair du côté du soleil, plus gris dessous. L'image se répète sans couture, comme celle du jeu.
 */
public final class SoftClouds {
    private static final Identifier VANILLA = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    public static final Identifier ID = Orbitalis.id("atlas_soft_clouds");
    /** Taille de l'image adoucie, en points : deux points par case de l'image du jeu. */
    public static final int SIZE = 512;

    private static boolean built;
    private static boolean ready;

    private SoftClouds() {
    }

    /** La texture des nuages adoucis, construite à la première demande ; {@code null} si l'image du jeu manque. */
    public static Identifier get() {
        if (!built) {
            built = true;
            build();
        }
        return ready ? ID : null;
    }

    private static void build() {
        int n;
        float[] mask;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(VANILLA).open();
                NativeImage source = NativeImage.read(in)) {
            n = source.getWidth();
            mask = new float[n * n];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    mask[y * n + x] = ((source.getPixel(x, y) >>> 24) & 255) > 0 ? 1.0F : 0.0F;
                }
            }
        } catch (Exception e) {
            Orbitalis.LOGGER.warn("Nuages de l'atlas : image du jeu illisible", e);
            return;
        }
        // Flou en boîte, trois passes, sur une image qui se referme sur elle-même : une gaussienne, sans couture.
        float[] soft = blur(blur(blur(mask, n, 2), n, 2), n, 1);
        DynamicTexture texture = new DynamicTexture(() -> "Orbitalis soft clouds", SIZE, SIZE, false) {
            {
                sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeat(com.mojang.blaze3d.textures.FilterMode.LINEAR);
            }
        };
        NativeImage image = texture.getPixels();
        float scale = (float) n / SIZE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float u = x * scale;
                float v = y * scale;
                float density = sample(soft, n, u, v);
                // Seul le cœur des nuages reste : les franges s'effacent en douceur, les lambeaux disparaissent.
                float body = smooth(0.32F, 0.82F, density);
                if (body <= 0.0F) {
                    image.setPixel(x, y, 0);
                    continue;
                }
                // Modelé : plus clair face au soleil (au nord-ouest), plus gris à l'opposé.
                float lit = sample(soft, n, u - 1.2F, v - 1.2F) - sample(soft, n, u + 1.2F, v + 1.2F);
                float shade = clamp(0.86F + 0.1F * density + 0.35F * lit, 0.72F, 1.0F);
                int alpha = Math.round(205.0F * body);
                int r = Math.round(255 * shade);
                int g = Math.round(255 * shade);
                int b = Math.round(255 * Math.min(1.0F, shade * 1.03F));
                image.setPixel(x, y, alpha << 24 | r << 16 | g << 8 | b);
            }
        }
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(ID, texture);
        ready = true;
    }

    /** Flou en boîte de rayon {@code r}, horizontal puis vertical, sur une image qui se répète. */
    private static float[] blur(float[] in, int n, int r) {
        float[] tmp = new float[n * n];
        float[] out = new float[n * n];
        float width = 2 * r + 1;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    sum += in[y * n + Math.floorMod(x + k, n)];
                }
                tmp[y * n + x] = sum / width;
            }
        }
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    sum += tmp[Math.floorMod(y + k, n) * n + x];
                }
                out[y * n + x] = sum / width;
            }
        }
        return out;
    }

    /** Lecture bilinéaire d'une image qui se répète. */
    private static float sample(float[] img, int n, float u, float v) {
        int x0 = (int) Math.floor(u - 0.5F);
        int y0 = (int) Math.floor(v - 0.5F);
        float fx = u - 0.5F - x0;
        float fy = v - 0.5F - y0;
        float a = img[Math.floorMod(y0, n) * n + Math.floorMod(x0, n)];
        float b = img[Math.floorMod(y0, n) * n + Math.floorMod(x0 + 1, n)];
        float c = img[Math.floorMod(y0 + 1, n) * n + Math.floorMod(x0, n)];
        float d = img[Math.floorMod(y0 + 1, n) * n + Math.floorMod(x0 + 1, n)];
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
