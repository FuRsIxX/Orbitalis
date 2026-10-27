package com.orbitalis.client;

import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;

/**
 * Les astres du ciel de l'espace, dessinés avec lui : comme la lune du jeu, ils sont infiniment loin, derrière tout ce
 * qui est dans le monde (la station, le relief), jamais devant.
 * <ul>
 * <li><b>La planète sous l'orbite.</b> Une calotte de sphère, vue d'en haut : son horizon est courbe, et son bord est
 * le vrai horizon, à une vingtaine de degrés sous l'horizontale. Le sol est fait de pixels nets, à la manière du jeu :
 * des océans plus ou moins profonds, des côtes turquoise, des plaines, des forêts, des déserts, des montagnes ombrées
 * par le relief, des banquises. Il défile lentement sous la station, comme sous un satellite. Par-dessus, une couche
 * de nuages en cubes dérive un peu plus vite ; vers l'horizon, la brume bleue s'épaissit, et au-delà luit le mince
 * liseré de l'atmosphère. Sous l'orbite de Proxima, c'est Proxima b : des déserts rouille, une végétation sombre, des
 * glaces.</li>
 * <li><b>Les astres dans le ciel.</b> Depuis l'orbite, la Lune ; depuis la Lune, la planète bleue ; depuis Europe,
 * Jupiter et ses bandes. Chacun a son côté jour et son côté nuit, et la taille d'un astre du ciel du jeu.</li>
 * </ul>
 * Toutes les images sont peintes ici, une fois, au premier affichage.
 */
final class SkyBodies {
    /** Altitude de la station au-dessus de la planète, et rayon de celle-ci, en unités du ciel. */
    private static final float ALTITUDE = 9.0F;
    private static final float RADIUS = 150.0F;
    /** Largeur d'une répétition de l'image du sol (512 pixels), en unités du ciel : un pixel fait un peu moins d'un quart. */
    private static final float TILE = 120.0F;
    /** Taille de l'image du sol : assez grande pour qu'on ne la voie pas se répéter d'un horizon à l'autre. */
    private static final int GROUND = 512;
    /** Temps que met le sol à défiler d'une image entière, en ticks : six minutes. */
    private static final float SCROLL_TICKS = 7_200.0F;
    private static final int RINGS = 40;
    private static final int SEGMENTS = 72;
    /** Distance des astres du ciel, la même que celle des étoiles. */
    private static final float SKY = 100.0F;

    private static DynamicTexture earth;
    private static DynamicTexture proxima;
    private static DynamicTexture clouds;
    private static DynamicTexture white;
    private static DynamicTexture earthDisc;
    private static DynamicTexture moonDisc;
    private static DynamicTexture jupiterDisc;

    private SkyBodies() {
    }

    /** Dessine les astres du monde où est le joueur, s'il y en a. */
    static void render(Minecraft minecraft, Vector3f sun) {
        if (minecraft.level == null) {
            return;
        }
        var level = minecraft.level;
        float time = level.getGameTime();
        if (SpaceRules.isProximaOrbit(level)) {
            planetBelow(minecraft, proximaTexture(), time, sun, 0xFF8A70, 0xFFB09A);
        } else if (SpaceRules.isOrbit(level)) {
            // La Lune, haute dans le ciel, puis la planète.
            disc(minecraft, moonTexture(), new Vector3f(-0.45F, 0.62F, -0.64F), 4.5F);
            planetBelow(minecraft, earthTexture(), time, sun, 0x8FC4FF, 0xC8E4FF);
        } else if (SpaceRules.isMoon(level)) {
            disc(minecraft, earthDiscTexture(), new Vector3f(0.3F, 0.72F, 0.62F), 8.0F);
        } else if (SpaceRules.isEuropa(level)) {
            disc(minecraft, jupiterTexture(), new Vector3f(-0.66F, 0.3F, 0.69F), 13.0F);
        }
    }

    // ------------------------------------------------------------------
    // La planète sous l'orbite
    // ------------------------------------------------------------------

    /** Un point de la calotte, à l'angle {@code theta} du nadir et à l'azimut {@code phi}, sur une sphère de ce rayon. */
    private static Vector3f capPoint(float radius, float theta, float phi) {
        float altitude = ALTITUDE - (radius - RADIUS);
        return new Vector3f(radius * Mth.sin(theta) * Mth.cos(phi), -(altitude + radius * (1.0F - Mth.cos(theta))),
                radius * Mth.sin(theta) * Mth.sin(phi));
    }

    private static void planetBelow(Minecraft minecraft, DynamicTexture ground, float time, Vector3f sun, int haze, int glow) {
        float horizon = (float) Math.acos(RADIUS / (RADIUS + ALTITUDE));
        float scroll = time / SCROLL_TICKS;
        // Le sol, éclairé par le Soleil, qui défile ; la brume bleue s'y épaissit vers l'horizon.
        draw(minecraft, ground, cap(RADIUS, horizon, scroll, 0.0F, sun, -1, 0, TILE));
        // Les nuages : une image deux fois plus serrée que le sol, qui dérive un peu plus vite que lui.
        draw(minecraft, cloudTexture(), cap(RADIUS + 0.5F, horizon, scroll * 3.2F + 0.37F, scroll * 0.7F, sun, -1, 0, TILE / 2.0F));
        draw(minecraft, whiteTexture(), cap(RADIUS + 0.8F, horizon, 0.0F, 0.0F, null, haze, 1, TILE));
        // Le liseré de l'atmosphère, juste au-dessus de l'horizon.
        draw(minecraft, whiteTexture(), limb(horizon, glow));
    }

    /**
     * Une calotte en anneaux. Avec une image, ses coordonnées suivent la surface (et défilent) ; sans soleil, c'est la
     * brume : une couleur, de plus en plus opaque vers le bord.
     */
    private static BufferBuilder cap(float radius, float horizon, float du, float dv, Vector3f sun, int tint, int mode, float tile) {
        BufferBuilder builder = new BufferBuilder(bytes(RINGS * SEGMENTS), PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < RINGS; i++) {
            float t0 = horizon * i / RINGS;
            float t1 = horizon * (i + 1) / RINGS;
            for (int j = 0; j < SEGMENTS; j++) {
                float p0 = Mth.TWO_PI * j / SEGMENTS;
                float p1 = Mth.TWO_PI * (j + 1) / SEGMENTS;
                // Dans le sens inverse des aiguilles d'une montre vu d'en haut : la face tournée vers la station.
                vertex(builder, radius, t0, p0, horizon, du, dv, sun, tint, mode, tile);
                vertex(builder, radius, t0, p1, horizon, du, dv, sun, tint, mode, tile);
                vertex(builder, radius, t1, p1, horizon, du, dv, sun, tint, mode, tile);
                vertex(builder, radius, t1, p0, horizon, du, dv, sun, tint, mode, tile);
            }
        }
        return builder;
    }

    private static void vertex(BufferBuilder builder, float radius, float theta, float phi, float horizon, float du, float dv, Vector3f sun,
            int tint, int mode, float tile) {
        Vector3f at = capPoint(radius, theta, phi);
        float arc = RADIUS * theta / tile;
        float u = arc * Mth.cos(phi) + du;
        float v = arc * Mth.sin(phi) + dv;
        int color;
        if (mode == 1) {
            // La brume : nulle au nadir, épaisse au ras de l'horizon.
            float edge = theta / horizon;
            color = (int) (Mth.clamp(0.75F * edge * edge * edge, 0.0F, 1.0F) * 255) << 24 | tint;
        } else {
            // Le jour : la face tournée vers le Soleil est la plus claire.
            Vector3f normal = new Vector3f(Mth.sin(theta) * Mth.cos(phi), Mth.cos(theta), Mth.sin(theta) * Mth.sin(phi));
            float light = 0.55F + 0.45F * Math.max(0.0F, normal.dot(sun));
            int c = (int) (light * 255);
            color = 0xFF000000 | c << 16 | c << 8 | c;
        }
        builder.addVertex(at.x, at.y, at.z).setUv(u, v).setColor(color);
    }

    /** Le liseré de l'atmosphère : une bande qui part de l'horizon, vive à sa base, et s'efface en montant. */
    private static BufferBuilder limb(float horizon, int glow) {
        BufferBuilder builder = new BufferBuilder(bytes(SEGMENTS * 2), PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[][] bands = {{0.0F, 1.1F, 0.85F, 0.45F}, {1.1F, 4.0F, 0.45F, 0.0F}};
        for (float[] band : bands) {
            for (int j = 0; j < SEGMENTS; j++) {
                float p0 = Mth.TWO_PI * j / SEGMENTS;
                float p1 = Mth.TWO_PI * (j + 1) / SEGMENTS;
                Vector3f a = capPoint(RADIUS, horizon, p0);
                Vector3f b = capPoint(RADIUS, horizon, p1);
                int low = (int) (band[2] * 255) << 24 | glow;
                int high = (int) (band[3] * 255) << 24 | glow;
                // La face tournée vers l'intérieur de l'anneau, où est la station.
                builder.addVertex(a.x, a.y + band[0], a.z).setUv(0.5F, 0.5F).setColor(low);
                builder.addVertex(b.x, b.y + band[0], b.z).setUv(0.5F, 0.5F).setColor(low);
                builder.addVertex(b.x, b.y + band[1], b.z).setUv(0.5F, 0.5F).setColor(high);
                builder.addVertex(a.x, a.y + band[1], a.z).setUv(0.5F, 0.5F).setColor(high);
            }
        }
        return builder;
    }

    // ------------------------------------------------------------------
    // Les astres du ciel
    // ------------------------------------------------------------------

    /** Un astre : un disque tourné vers celui qui le regarde, à sa place dans le ciel, de cette demi-largeur. */
    private static void disc(Minecraft minecraft, DynamicTexture image, Vector3f direction, float size) {
        BufferBuilder builder = new BufferBuilder(bytes(1), PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f centre = new Vector3f(direction).normalize(SKY);
        Matrix3f turn = new Matrix3f().rotateTowards(new Vector3f(centre).negate(), new Vector3f(0.0F, 1.0F, 0.0F));
        builder.addVertex(new Vector3f(size, -size, 0.0F).mul(turn).add(centre)).setUv(1.0F, 1.0F).setColor(0xFFFFFFFF);
        builder.addVertex(new Vector3f(size, size, 0.0F).mul(turn).add(centre)).setUv(1.0F, 0.0F).setColor(0xFFFFFFFF);
        builder.addVertex(new Vector3f(-size, size, 0.0F).mul(turn).add(centre)).setUv(0.0F, 0.0F).setColor(0xFFFFFFFF);
        builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(turn).add(centre)).setUv(0.0F, 1.0F).setColor(0xFFFFFFFF);
        draw(minecraft, image, builder);
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    private static ByteBufferBuilder bytes(int quads) {
        return new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4 * quads);
    }

    private static void draw(Minecraft minecraft, DynamicTexture image, BufferBuilder builder) {
        try (MeshData data = builder.build()) {
            if (data == null) {
                return;
            }
            int indexCount = data.drawState().indexCount();
            RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewStack()));
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indexBuffer = indices.getBuffer(indexCount);
            try (GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Orbitalis sky body", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
                    RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Orbitalis sky body",
                            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderPipelines.END_SKY);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", image.getTextureView(), image.getSampler());
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Les images
    // ------------------------------------------------------------------

    private static DynamicTexture texture(String name, int size, boolean repeat) {
        return new DynamicTexture(() -> "Orbitalis " + name, size, size, false) {
            {
                sampler = repeat ? RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
                        : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            }
        };
    }

    private static DynamicTexture earthTexture() {
        if (earth == null) {
            earth = texture("planet ground", GROUND, true);
            paintGround(earth.getPixels(), GROUND, 1201L, false);
            earth.upload();
        }
        return earth;
    }

    private static DynamicTexture proximaTexture() {
        if (proxima == null) {
            proxima = texture("proxima ground", GROUND, true);
            paintGround(proxima.getPixels(), GROUND, 4201L, true);
            proxima.upload();
        }
        return proxima;
    }

    private static DynamicTexture cloudTexture() {
        if (clouds == null) {
            clouds = texture("planet clouds", 256, true);
            NativeImage image = clouds.getPixels();
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    float c = cloudDensity(x, y);
                    if (c < 0.6F) {
                        image.setPixel(x, y, 0);
                        continue;
                    }
                    // Deux épaisseurs franches, à la manière des nuages du jeu, et un dessous gris du côté de l'ombre.
                    boolean thick = c > 0.66F;
                    float under = cloudDensity(x + 1, y + 1);
                    int shade = under < 0.6F ? 214 : thick ? 250 : 236;
                    int alpha = thick ? 235 : 150;
                    image.setPixel(x, y, alpha << 24 | shade << 16 | shade << 8 | Math.min(255, shade + 6));
                }
            }
            clouds.upload();
        }
        return clouds;
    }

    private static float cloudDensity(int x, int y) {
        float u = Math.floorMod(x, 256) / 256.0F;
        float v = Math.floorMod(y, 256) / 256.0F;
        float warp = fbm(u, v, 4, 77L, 2) - 0.5F;
        return fbm(u + warp * 0.15F, v * 1.0F, 8, 91L, 4);
    }

    private static DynamicTexture whiteTexture() {
        if (white == null) {
            white = texture("sky white", 4, false);
            NativeImage image = white.getPixels();
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    image.setPixel(x, y, 0xFFFFFFFF);
                }
            }
            white.upload();
        }
        return white;
    }

    /**
     * Le sol d'une planète, en pixels nets : l'altitude, l'humidité et la température tirées de bruits qui se
     * répètent sans couture ; l'océan selon sa profondeur, le rivage, puis les terres selon leur climat, ombrées par
     * leur relief (la lumière vient du nord-ouest).
     */
    private static void paintGround(NativeImage image, int size, long seed, boolean red) {
        float[] height = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size;
                float v = y / (float) size;
                float warp = fbm(u, v, 3, seed + 5, 2) - 0.5F;
                height[y * size + x] = fbm(u + warp * 0.25F, v - warp * 0.2F, 4, seed, 5);
            }
        }
        float sea = red ? 0.47F : 0.53F;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float h = height[y * size + x];
                float u = x / (float) size;
                float v = y / (float) size;
                float wet = fbm(u, v, 5, seed + 11, 3);
                float cold = fbm(u, v, 2, seed + 23, 3);
                int color;
                if (h < sea) {
                    float depth = (sea - h) / sea;
                    if (cold > (red ? 0.7F : 0.84F)) {
                        color = depth < 0.08F ? 0xDCE8F0 : 0xC7D9E6;
                    } else if (red) {
                        color = depth < 0.03F ? 0x3D7F86 : depth < 0.1F ? 0x24606E : depth < 0.25F ? 0x174656 : 0x0E3442;
                    } else {
                        color = depth < 0.025F ? 0x45B8C9 : depth < 0.08F ? 0x2C8CC6 : depth < 0.2F ? 0x1F69AE : 0x164C8A;
                    }
                } else {
                    float land = (h - sea) / (1.0F - sea);
                    if (land < 0.025F) {
                        color = red ? 0xB98A5E : 0xE2D49C;
                    } else if (land > 0.5F || cold > 0.8F) {
                        color = land > 0.62F || cold > 0.8F ? 0xEEF3F7 : red ? 0x6E5A54 : 0x8C857A;
                    } else if (red) {
                        color = wet > 0.58F ? 0x3A1F2E : wet > 0.5F ? 0x5A2A2A : wet > 0.4F ? 0x8C4A2E : 0xA8633A;
                    } else if (cold > 0.62F) {
                        color = wet > 0.5F ? 0x3F6448 : 0x8E9E86;
                    } else if (wet < 0.4F) {
                        color = cold < 0.42F ? 0xD9C38A : 0xBFAE6A;
                    } else if (wet < 0.5F) {
                        color = 0x79A84A;
                    } else if (wet < 0.6F) {
                        color = 0x4E8A36;
                    } else {
                        color = 0x2F6B2A;
                    }
                    // Le relief : plus clair face à la lumière, plus sombre derrière.
                    float slope = height[Math.floorMod(y - 1, size) * size + Math.floorMod(x - 1, size)]
                            - height[Math.floorMod(y + 1, size) * size + Math.floorMod(x + 1, size)];
                    color = scale(color, Mth.clamp(1.0F + slope * 9.0F, 0.78F, 1.18F));
                }
                image.setPixel(x, y, 0xFF000000 | color);
            }
        }
    }

    /** La planète bleue vue de la Lune : un disque, son côté nuit, ses nuages et le liseré de son atmosphère. */
    private static DynamicTexture earthDiscTexture() {
        if (earthDisc == null) {
            earthDisc = texture("earth disc", 64, false);
            NativeImage ground = new NativeImage(256, 256, false);
            paintGround(ground, 256, 1201L, false);
            NativeImage image = earthDisc.getPixels();
            paintSphere(image, 64, (lon, lat) -> {
                int base = ground.getPixel(Math.floorMod((int) (lon / Mth.TWO_PI * 256 * 2), 256), Math.floorMod((int) ((lat / Mth.PI + 0.5F) * 256), 256));
                float cloud = cloudDensity((int) (lon / Mth.TWO_PI * 512), (int) ((lat / Mth.PI + 0.5F) * 256));
                if (Math.abs(lat) > 1.2F) {
                    base = 0xFFEEF3F7;
                }
                return cloud > 0.6F ? 0xFFF4F7FA : base;
            }, 0x8FC4FF, new Vector3f(-0.75F, 0.25F, 0.6F));
            ground.close();
            earthDisc.upload();
        }
        return earthDisc;
    }

    /** La Lune vue de l'orbite : un gris de poussière, ses mers sombres et ses cratères. */
    private static DynamicTexture moonTexture() {
        if (moonDisc == null) {
            moonDisc = texture("moon disc", 48, false);
            paintSphere(moonDisc.getPixels(), 48, (lon, lat) -> {
                float u = lon / Mth.TWO_PI;
                float v = lat / Mth.PI + 0.5F;
                float mare = fbm(u, v, 3, 311L, 3);
                float crater = fbm(u, v, 12, 313L, 2);
                int grey = mare > 0.56F ? 0x8A8C90 : crater > 0.66F ? 0xB9BBBE : 0xA6A8AC;
                return 0xFF000000 | grey;
            }, -1, new Vector3f(0.7F, 0.3F, 0.65F));
            moonDisc.upload();
        }
        return moonDisc;
    }

    /** Jupiter vue d'Europe : ses bandes crème, ocre et brunes, ondulées, et sa grande tache rouge. */
    private static DynamicTexture jupiterTexture() {
        if (jupiterDisc == null) {
            jupiterDisc = texture("jupiter disc", 96, false);
            int[] bands = {0xE9DCC4, 0xC9A27A, 0xF2E8D6, 0xB7835A, 0xEADBC0, 0xA9704C, 0xF0E4CC, 0xC79C72, 0xE4D3B6};
            paintSphere(jupiterDisc.getPixels(), 96, (lon, lat) -> {
                float u = lon / Mth.TWO_PI;
                float v = lat / Mth.PI + 0.5F;
                float wave = (fbm(u * 2.0F, v, 6, 521L, 3) - 0.5F) * 0.05F;
                int band = Mth.clamp((int) ((v + wave) * bands.length), 0, bands.length - 1);
                // La grande tache rouge, au sud, dans sa bande.
                float du = (float) Math.atan2(Math.sin(lon - 0.6F), Math.cos(lon - 0.6F));
                float dv = lat + 0.38F;
                if ((du * du) / 0.06F + (dv * dv) / 0.012F < 1.0F) {
                    return 0xFFC0603E;
                }
                return 0xFF000000 | bands[band];
            }, -1, new Vector3f(-0.6F, 0.2F, 0.78F));
            jupiterDisc.upload();
        }
        return jupiterDisc;
    }

    private interface Surface {
        int at(float longitude, float latitude);
    }

    /**
     * Peint une sphère vue de face : chaque pixel du disque retrouve son point de la sphère, en longitude et latitude ;
     * la lumière vient de {@code light} (vers la vue : z), le côté nuit s'assombrit ; avec une atmosphère, son liseré
     * bleu entoure le disque.
     */
    private static void paintSphere(NativeImage image, int size, Surface surface, int atmosphere, Vector3f light) {
        light.normalize();
        float half = size / 2.0F;
        float body = atmosphere >= 0 ? half - 2.5F : half - 0.5F;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5F - half) / body;
                float dy = (half - y - 0.5F) / body;
                float r2 = dx * dx + dy * dy;
                if (r2 > 1.0F) {
                    // Hors du disque : le liseré de l'atmosphère, puis le vide.
                    float out = (Mth.sqrt(r2) - 1.0F) * body;
                    if (atmosphere >= 0 && out < 2.0F) {
                        int alpha = out < 1.0F ? 150 : 60;
                        image.setPixel(x, y, alpha << 24 | atmosphere);
                    } else {
                        image.setPixel(x, y, 0);
                    }
                    continue;
                }
                float dz = Mth.sqrt(1.0F - r2);
                float lat = (float) Math.asin(dy);
                float lon = (float) Math.atan2(dx, dz);
                int color = surface.at(lon, lat);
                float lit = dx * light.x + dy * light.y + dz * light.z;
                // Le terminateur est franc, comme sur un astre sans brume : trois paliers, puis la nuit.
                float shade = lit > 0.35F ? 1.0F : lit > 0.1F ? 0.82F : lit > -0.05F ? 0.5F : 0.13F;
                color = scale(color & 0xFFFFFF, shade);
                if (atmosphere >= 0 && r2 > 0.82F && lit > -0.05F) {
                    color = mix(color, atmosphere, (r2 - 0.82F) / 0.18F * 0.6F);
                }
                image.setPixel(x, y, 0xFF000000 | color);
            }
        }
    }

    // ------------------------------------------------------------------
    // Bruits et couleurs
    // ------------------------------------------------------------------

    /** Bruit de valeur qui se répète tous les {@code period} pas (u et v de 0 à 1 font une période entière). */
    private static float value(float u, float v, int period, long seed) {
        float x = u * period;
        float y = v * period;
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = lattice(x0, y0, period, seed);
        float b = lattice(x0 + 1, y0, period, seed);
        float c = lattice(x0, y0 + 1, period, seed);
        float d = lattice(x0 + 1, y0 + 1, period, seed);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float lattice(int x, int y, int period, long seed) {
        long h = Math.floorMod(x, period) * 73_856_093L ^ Math.floorMod(y, period) * 19_349_663L ^ seed * 83_492_791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Somme de bruits de plus en plus fins, chacun deux fois plus fin et moitié moins fort que le précédent. */
    private static float fbm(float u, float v, int period, long seed, int octaves) {
        float sum = 0.0F;
        float amplitude = 0.5F;
        float total = 0.0F;
        for (int k = 0; k < octaves; k++) {
            sum += value(u, v, period << k, seed + k) * amplitude;
            total += amplitude;
            amplitude *= 0.5F;
        }
        return sum / total;
    }

    private static int scale(int rgb, float factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * factor));
        int b = Math.min(255, (int) ((rgb & 255) * factor));
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
}
