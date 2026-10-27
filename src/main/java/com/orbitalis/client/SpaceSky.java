package com.orbitalis.client;

import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.orbitalis.Orbitalis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;

/**
 * Le ciel de l'espace : celui qu'on voit depuis l'orbite et depuis la Lune. Il ne reprend rien du ciel du jeu : tout
 * y est construit ici.
 * <ul>
 * <li><b>Les étoiles.</b> Plus de seize mille, réparties sur la voûte : une poussière de points faibles, plus d'un
 * millier d'étoiles franches de toutes les couleurs (bleutées, blanches, jaunes, orangées, rouges), et une
 * quarantaine de très brillantes, avec leurs aigrettes. Elles se serrent le long du plan de la galaxie.</li>
 * <li><b>La Voie lactée.</b> Une grande écharpe qui fait le tour du ciel, inclinée : des nuées claires, dorées vers
 * le bulbe, bleutées et violettes dans les bras, entaillées par des bandes de poussière sombre.</li>
 * <li><b>Quelques nébuleuses</b> à l'écart, roses ou turquoise, et <b>le Soleil</b>, un disque blanc dans son halo.</li>
 * <li><b>Les astres</b> (voir {@link SkyBodies}) : la planète sous l'orbite, la Lune, la planète vue de la Lune,
 * Jupiter vue d'Europe.</li>
 * </ul>
 * Le tout est un seul maillage, bâti une fois, dessiné autour de la caméra ; il tourne très lentement.
 * <p>
 * Avec un pack de shaders actif (Iris), ce ciel n'est pas dessiné : le pack garde la main sur le ciel du jeu.
 */
public final class SpaceSky implements CustomSkyboxRenderer {
    /** Nom du ciel, à donner au type de dimension ({@code neoforge:custom_skybox}). */
    public static final Identifier ID = Orbitalis.id("deep_space");
    private static final Identifier ATLAS = Orbitalis.id("space_sky");
    private static final float DISTANCE = 100.0F;
    /** Inclinaison du plan de la galaxie sur l'horizon, en degrés, et direction du bulbe le long de ce plan. */
    private static final float GALAXY_TILT = 62.0F;
    private static final float GALAXY_TURN = 35.0F;
    /** Durée d'un tour complet du ciel, en ticks : presque immobile, mais vivant. */
    private static final float TURN_TICKS = 48_000.0F;
    private static final int[] STAR_TINTS = {0xBFD4FF, 0xFFFFFF, 0xFFFFFF, 0xFFF4DC, 0xFFE2B0, 0xFFC89A, 0xFFB4A4};

    private GpuBuffer mesh;
    private int indexCount;
    private float builtForSun = Float.NaN;
    /** Vrai si le maillage a été bâti pour l'orbite de Proxima : son soleil est une naine rouge. */
    private boolean builtRed;
    private DynamicTexture atlas;

    // ------------------------------------------------------------------
    // Images : un halo doux, et une étoile à aigrettes
    // ------------------------------------------------------------------

    private DynamicTexture atlas() {
        if (atlas != null) {
            return atlas;
        }
        atlas = new DynamicTexture(() -> "Orbitalis space sky", 64, 32, true);
        NativeImage image = atlas.getPixels();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                float dx = (x - 15.5F) / 15.5F;
                float dy = (y - 15.5F) / 15.5F;
                float radius = Mth.sqrt(dx * dx + dy * dy);
                // À gauche : un halo qui s'éteint en douceur vers le bord.
                float glow = (float) Math.pow(Math.max(0.0F, 1.0F - radius), 2.2);
                image.setPixel(x, y, (int) (glow * 255) << 24 | 0xFFFFFF);
                // À droite : un cœur vif, un petit halo, et quatre aigrettes fines.
                float core = (float) Math.pow(Math.max(0.0F, 1.0F - radius * 3.2F), 1.5);
                float halo = (float) Math.pow(Math.max(0.0F, 1.0F - radius * 1.6F), 3.0) * 0.45F;
                float spikes = Math.max(0.0F, 1.0F - radius) * (Math.abs(dx) < 0.07F || Math.abs(dy) < 0.07F ? 0.75F : 0.0F);
                image.setPixel(32 + x, y, (int) (Mth.clamp(core + halo + spikes * spikes, 0.0F, 1.0F) * 255) << 24 | 0xFFFFFF);
            }
        }
        atlas.upload();
        Minecraft.getInstance().getTextureManager().register(ATLAS, atlas);
        return atlas;
    }

    // ------------------------------------------------------------------
    // Maillage
    // ------------------------------------------------------------------

    /** Un point de la voûte, donné par sa longitude et sa latitude galactiques (en degrés), ramené dans le repère du ciel. */
    private static Vector3f galactic(float longitude, float latitude) {
        float lon = longitude * Mth.DEG_TO_RAD;
        float lat = latitude * Mth.DEG_TO_RAD;
        Vector3f point = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), Mth.cos(lat) * Mth.sin(lon));
        return point.rotateX(GALAXY_TILT * Mth.DEG_TO_RAD).rotateY(GALAXY_TURN * Mth.DEG_TO_RAD);
    }

    /** Un carré tourné vers la caméra, à cet endroit de la voûte. {@code sprite} : 0 pour le halo, 1 pour l'étoile à aigrettes. */
    private static void quad(BufferBuilder builder, Vector3f direction, float size, float spin, int sprite, int argb) {
        Vector3f centre = new Vector3f(direction).normalize(DISTANCE);
        Matrix3f turn = new Matrix3f().rotateTowards(new Vector3f(centre).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(spin);
        float u0 = sprite * 0.5F + 0.008F;
        float u1 = sprite * 0.5F + 0.492F;
        builder.addVertex(new Vector3f(size, -size, 0.0F).mul(turn).add(centre)).setUv(u1, 0.984F).setColor(argb);
        builder.addVertex(new Vector3f(size, size, 0.0F).mul(turn).add(centre)).setUv(u1, 0.016F).setColor(argb);
        builder.addVertex(new Vector3f(-size, size, 0.0F).mul(turn).add(centre)).setUv(u0, 0.016F).setColor(argb);
        builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(turn).add(centre)).setUv(u0, 0.984F).setColor(argb);
    }

    private static int tint(int rgb, float alpha) {
        return (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24 | rgb;
    }

    /** Une direction tirée au hasard sur toute la voûte. */
    private static Vector3f anywhere(RandomSource random) {
        float y = random.nextFloat() * 2.0F - 1.0F;
        float around = random.nextFloat() * Mth.TWO_PI;
        float flat = Mth.sqrt(1.0F - y * y);
        return new Vector3f(flat * Mth.cos(around), y, flat * Mth.sin(around));
    }

    /** Une direction tirée près du plan de la galaxie, plus souvent du côté du bulbe. */
    private static Vector3f inGalaxy(RandomSource random, float spread) {
        float longitude = random.nextFloat() < 0.45F ? (float) random.nextGaussian() * 38.0F : random.nextFloat() * 360.0F;
        return galactic(longitude, (float) random.nextGaussian() * spread);
    }

    private void build(float sunAngle, boolean red) {
        if (mesh != null) {
            mesh.close();
        }
        builtForSun = sunAngle;
        builtRed = red;
        RandomSource random = RandomSource.createThreadLocalInstance(20_261_013L);
        int quads = 0;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4 * 20_000)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            // 1. La Voie lactée, d'abord : tout le reste se dessine par-dessus. De grandes nuées très légères...
            for (int i = 0; i < 420; i++) {
                float longitude = i / 420.0F * 360.0F + (float) random.nextGaussian() * 4.0F;
                float towardCore = Mth.cos((longitude > 180.0F ? longitude - 360.0F : longitude) * Mth.DEG_TO_RAD * 0.5F);
                float bulge = towardCore * towardCore * towardCore * towardCore;
                Vector3f at = galactic(longitude, (float) random.nextGaussian() * (4.0F + 5.0F * bulge));
                // Dorée vers le bulbe, bleutée ou violette dans les bras.
                int rgb = random.nextFloat() < bulge * 0.9F ? (random.nextBoolean() ? 0xFFE2B0 : 0xFFC890)
                        : random.nextFloat() < 0.5F ? 0x8FA8FF : random.nextFloat() < 0.5F ? 0xB58CFF : 0x6FD0FF;
                quad(builder, at, 9.0F + random.nextFloat() * 12.0F + 8.0F * bulge, random.nextFloat() * Mth.TWO_PI, 0,
                        tint(rgb, 0.055F + 0.06F * random.nextFloat() + 0.08F * bulge));
                quads++;
            }
            // ... puis les bandes de poussière, qui les entaillent près du plan.
            for (int i = 0; i < 150; i++) {
                float longitude = (float) random.nextGaussian() * 70.0F;
                Vector3f at = galactic(longitude, 1.5F * Mth.sin(longitude * 0.11F) + (float) random.nextGaussian() * 1.6F);
                quad(builder, at, 3.5F + random.nextFloat() * 5.0F, random.nextFloat() * Mth.TWO_PI, 0, tint(0x05060A, 0.22F + 0.2F * random.nextFloat()));
                quads++;
            }
            // 2. Trois nébuleuses à l'écart : une rose, une turquoise, une mauve.
            int[] nebulae = {0xFF7FB0, 0x5FE0D0, 0xB08CFF};
            for (int[] spot : new int[][] {{140, 38, 0}, {250, -44, 1}, {40, -52, 2}}) {
                for (int i = 0; i < 16; i++) {
                    Vector3f at = galactic(spot[0] + (float) random.nextGaussian() * 4.0F, spot[1] + (float) random.nextGaussian() * 3.0F);
                    quad(builder, at, 2.5F + random.nextFloat() * 4.5F, random.nextFloat() * Mth.TWO_PI, 0,
                            tint(i % 4 == 0 ? 0xFFFFFF : nebulae[spot[2]], 0.05F + 0.08F * random.nextFloat()));
                    quads++;
                }
            }
            // 3. La poussière d'étoiles : des milliers de points faibles, plus serrés dans la galaxie.
            for (int i = 0; i < 15_000; i++) {
                Vector3f at = i % 5 < 2 ? inGalaxy(random, 9.0F) : anywhere(random);
                quad(builder, at, 0.09F + random.nextFloat() * 0.12F, 0.0F, 0, tint(STAR_TINTS[random.nextInt(STAR_TINTS.length)], 0.6F + 0.4F * random.nextFloat()));
                quads++;
            }
            // 4. Les étoiles franches, de toutes les couleurs.
            for (int i = 0; i < 1_200; i++) {
                Vector3f at = i % 3 == 0 ? inGalaxy(random, 14.0F) : anywhere(random);
                quad(builder, at, 0.26F + random.nextFloat() * 0.3F, 0.0F, 0, tint(STAR_TINTS[random.nextInt(STAR_TINTS.length)], 0.85F + 0.15F * random.nextFloat()));
                quads++;
            }
            // 5. Les très brillantes, avec leurs aigrettes.
            for (int i = 0; i < 70; i++) {
                quad(builder, anywhere(random), 0.9F + random.nextFloat() * 0.9F, random.nextFloat() * 0.5F, 1,
                        tint(STAR_TINTS[random.nextInt(STAR_TINTS.length)], 1.0F));
                quads++;
            }
            // 6. Le Soleil : un grand halo, un halo serré, et son disque. Il est là où le place le monde.
            Vector3f sun = new Vector3f(0.0F, 1.0F, 0.0F).rotateX(sunAngle).rotateY(-Mth.HALF_PI);
            if (red) {
                // La naine rouge : plus grosse vue d'ici que le Soleil de la Terre, et rouge-orangé.
                quad(builder, sun, 34.0F, 0.0F, 0, tint(0xFF5A32, 0.4F));
                quad(builder, sun, 15.0F, 0.0F, 0, tint(0xFF8A50, 0.85F));
                quad(builder, sun, 11.0F, 0.4F, 1, tint(0xFFC8A0, 1.0F));
            } else {
                quad(builder, sun, 26.0F, 0.0F, 0, tint(0xFFE9C0, 0.35F));
                quad(builder, sun, 11.0F, 0.0F, 0, tint(0xFFF6E0, 0.8F));
                quad(builder, sun, 9.0F, 0.4F, 1, tint(0xFFFFFF, 1.0F));
            }
            quads += 3;

            try (MeshData data = builder.buildOrThrow()) {
                indexCount = data.drawState().indexCount();
                mesh = RenderSystem.getDevice().createBuffer(() -> "Orbitalis space sky", 32, data.vertexBuffer());
            }
        }
        Orbitalis.LOGGER.debug("Ciel de l'espace : {} éléments", quads);
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    /** Accès à Iris, s'il est installé : la méthode qui dit si un pack de shaders est actif. Cherché une seule fois. */
    private static java.lang.reflect.Method shaderQuery;
    private static Object shaderApi;
    private static boolean shaderLookedUp;

    /**
     * Vrai si un pack de shaders est actif. Il redessine alors tout ce qui passe par le ciel avec ses propres
     * programmes : notre ciel en sortirait blanc et flou. On lui laisse donc le ciel du jeu, qu'il sait traiter.
     */
    public static boolean shadersActive() {
        if (!shaderLookedUp) {
            shaderLookedUp = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                shaderApi = api.getMethod("getInstance").invoke(null);
                shaderQuery = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError absent) {
                shaderQuery = null;
            }
        }
        if (shaderQuery == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(shaderQuery.invoke(shaderApi));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return false;
        }
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState sky, Matrix4fc modelViewMatrix, Runnable setupFog) {
        if (shadersActive()) {
            return false;
        }
        // Le Soleil du ciel spatial a sa place à lui, dans chaque monde : l'angle solaire de la dimension, lui, est réglé
        // sous l'horizon, pour que les packs de shaders y dessinent la nuit de l'espace.
        Minecraft client = Minecraft.getInstance();
        float sunAngle = (client.level != null && com.orbitalis.phase2.survival.SpaceRules.isMoon(client.level) ? 40.0F : 55.0F) * Mth.DEG_TO_RAD;
        boolean red = client.level != null && com.orbitalis.phase2.survival.SpaceRules.isProximaOrbit(client.level);
        if (mesh == null || Math.abs(sunAngle - builtForSun) > 0.01F || red != builtRed) {
            build(sunAngle, red);
        }
        DynamicTexture image = atlas();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        float time = minecraft.level == null ? 0.0F : (minecraft.level.getGameTime() % (long) TURN_TICKS) / TURN_TICKS;
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        // Le ciel tourne très lentement autour de l'axe du monde.
        stack.rotateY(time * Mth.TWO_PI);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(stack));
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Orbitalis space sky",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.END_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", image.getTextureView(), image.getSampler());
            pass.setVertexBuffer(0, mesh.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
        stack.popMatrix();
        // Les astres : la planète sous l'orbite, la Lune, la planète bleue vue de la Lune, Jupiter vue d'Europe.
        SkyBodies.render(minecraft, new Vector3f(0.0F, 1.0F, 0.0F).rotateX(sunAngle).rotateY(-Mth.HALF_PI));
        // Le ciel du jeu n'est pas dessiné par-dessus.
        return true;
    }
}
