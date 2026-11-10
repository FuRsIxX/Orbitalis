package com.orbitalis.client;

import java.util.Optional;
import java.util.OptionalDouble;

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
import com.orbitalis.Orbitalis;
import com.orbitalis.client.screen.Ui;
import com.orbitalis.core.network.WarpPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Le saut en distorsion, mis en scène pour l'équipage. Tout reste dans l'esprit du jeu : des traits et des anneaux en
 * gros pixels, des éclairs saccadés, rien de lisse.
 * <ol>
 * <li><b>Les étoiles s'étirent</b> ({@link #STRETCH} ticks) : par les hublots, chaque étoile devient un trait qui file
 * vers la proue ; le champ de vision s'élargit, la caméra tremble un peu.</li>
 * <li><b>L'éclair</b> : l'écran blanchit par à-coups, un anneau de lumière s'ouvre au centre. Le serveur déplace le
 * vaisseau pendant ce temps.</li>
 * <li><b>Le tunnel</b> ({@link #TUNNEL} ticks) : le ciel n'est plus qu'un tunnel sombre, où défilent des anneaux de
 * lumière et des traînées d'étoiles ; on marche librement dans le vaisseau. Bleuté vers une étoile proche, violet et
 * rose pour un saut galactique.</li>
 * <li><b>La sortie</b> : un second éclair, puis les traits se rétractent en étoiles autour du monde d'arrivée.</li>
 * <li><b>L'arrivée</b> : un bandeau, le nom du monde et sa ligne, qui s'efface.</li>
 * </ol>
 * Avec un pack de shaders actif, le ciel du mod n'est pas dessiné : seuls les éclairs, le tremblement et le bandeau
 * restent.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class WarpFx {
    /** Les étoiles qui s'étirent, avant l'éclair, en ticks : c'est aussi l'attente du serveur avant de déplacer le vaisseau. */
    public static final int STRETCH = 40;
    /** Le tunnel, en ticks. */
    public static final int TUNNEL = 160;
    /** La sortie, puis le bandeau : fin de l'effet, en ticks depuis le départ. */
    private static final int EXIT = STRETCH + TUNNEL;
    private static final int END = EXIT + 100;
    private static final float SKY = 90.0F;

    private static boolean active;
    private static long clock;
    private static long start;
    private static Direction out = Direction.EAST;
    private static boolean galactic;
    private static Component title = Component.empty();
    private static Component subtitle = Component.empty();
    private static int lastSound = -1;
    private static DynamicTexture texture;

    private WarpFx() {
    }

    /** Le serveur annonce le départ (ou l'annulation) du saut. */
    public static void receive(WarpPayload payload) {
        if (!payload.start()) {
            active = false;
            return;
        }
        active = true;
        start = clock;
        out = Direction.from3DDataValue(payload.out());
        if (out.getAxis() == Direction.Axis.Y) {
            out = Direction.EAST;
        }
        galactic = payload.galactic();
        title = Component.translatable(payload.title());
        subtitle = Component.translatable(payload.subtitle());
        lastSound = -1;
    }

    /** Vrai pendant toute la mise en scène. */
    public static boolean active() {
        return active;
    }

    /** La couleur de l'éclair : bleu-blanc, ou mauve pour un saut galactique. */
    public static int glow() {
        return galactic ? 0xE0B0FF : 0xCFF4FF;
    }

    private static float time(float partial) {
        return active ? clock - start + partial : -1.0F;
    }

    private static float partial() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Vrai pendant le tunnel : le ciel du mod laisse alors la place au tunnel. */
    public static boolean inTunnel() {
        float t = time(partial());
        return t >= STRETCH && t < EXIT;
    }

    /** L'étirement des étoiles, de 0 à 1 : il monte avant l'éclair, et retombe après la sortie. */
    private static float stretch(float t) {
        if (t < 0.0F) {
            return 0.0F;
        }
        if (t < STRETCH) {
            return smooth(t / STRETCH);
        }
        if (t >= EXIT) {
            return 1.0F - smooth((t - EXIT) / 36.0F);
        }
        return 1.0F;
    }

    /** L'éclair, de 0 à 1, par paliers (un éclair saccadé, comme une image de jeu) : autour du départ et de la sortie. */
    private static float flash(float t) {
        float value = Math.max(pulse(t, STRETCH), pulse(t, EXIT));
        return Math.round(value * 4.0F) / 4.0F;
    }

    private static float pulse(float t, float at) {
        if (t < at - 6.0F || t > at + 14.0F) {
            return 0.0F;
        }
        return t < at ? (t - (at - 6.0F)) / 6.0F : 1.0F - (t - at) / 14.0F;
    }

    // ------------------------------------------------------------------
    // Le temps, et les sons (ceux du jeu, en attendant les siens)
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        clock++;
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int t = (int) (clock - start);
        if (minecraft.level == null || t > END) {
            active = false;
            return;
        }
        if (t == 0 && lastSound < 0) {
            sound(minecraft, SoundEvents.PORTAL_TRIGGER, 1.6F, 0.5F);
            sound(minecraft, SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.9F);
            lastSound = 0;
        } else if (t >= STRETCH - 2 && lastSound < 1) {
            sound(minecraft, SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 0.5F);
            lastSound = 1;
        } else if (t >= EXIT - 2 && lastSound < 2) {
            sound(minecraft, SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 0.4F);
            sound(minecraft, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.9F);
            lastSound = 2;
        }
    }

    private static void sound(Minecraft minecraft, SoundEvent event, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    // ------------------------------------------------------------------
    // La caméra : un tremblement, et le champ de vision qui s'élargit
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float t = time((float) event.getPartialTick());
        if (t < 0.0F) {
            return;
        }
        float shake = 0.6F * stretch(t) + 1.6F * flash(t) + (inTunnelAt(t) ? 0.25F : 0.0F);
        event.setYaw(event.getYaw() + Mth.sin(t * 1.7F) * shake * 0.5F);
        event.setPitch(event.getPitch() + Mth.cos(t * 2.3F) * shake * 0.5F);
    }

    private static boolean inTunnelAt(float t) {
        return t >= STRETCH && t < EXIT;
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        float t = time((float) event.getPartialTick());
        if (t < 0.0F) {
            return;
        }
        float widen = inTunnelAt(t) ? 0.12F : 0.18F * stretch(t);
        event.setFOV(event.getFOV() * (1.0F + widen));
    }

    // ------------------------------------------------------------------
    // L'écran : les éclairs, l'anneau de lumière, le bandeau d'arrivée
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        float t = time(partial());
        if (t < 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int glow = glow();
        // L'anneau de lumière, en gros pixels : il s'ouvre depuis le centre autour de chaque éclair.
        for (float at : new float[] {STRETCH, EXIT}) {
            float k = (t - (at - 8.0F)) / 18.0F;
            if (k > 0.0F && k < 1.0F) {
                ring(graphics, width / 2, height / 2, (int) (k * Math.max(width, height) * 0.75F), 4, Ui.alpha(0xFF000000 | glow, 1.0F - k));
            }
        }
        float flash = flash(t);
        if (flash > 0.0F) {
            graphics.fill(0, 0, width, height, (int) (flash * 235) << 24 | glow);
        }
        // Le bandeau d'arrivée : le nom du monde, sa ligne, au tiers de l'écran ; il paraît puis s'efface.
        float banner = Math.min(smooth((t - EXIT - 14.0F) / 12.0F), 1.0F - smooth((t - END + 24.0F) / 24.0F));
        if (banner > 0.01F) {
            String name = title.getString().toUpperCase(java.util.Locale.ROOT);
            String line = subtitle.getString();
            int y = height / 3;
            int w = Math.max(minecraft.font.width(name) * 2, minecraft.font.width(line)) + 40;
            int x = (width - w) / 2;
            graphics.fill(x, y - 8, x + w, y + 36, Ui.alpha(0xFF04060A, 0.72F * banner));
            graphics.fill(x, y - 8, x + w, y - 7, Ui.alpha(Ui.ACCENT, banner));
            graphics.fill(x, y + 35, x + w, y + 36, Ui.alpha(Ui.ACCENT, banner));
            Ui.big(graphics, minecraft.font, name, (width - minecraft.font.width(name) * 2) / 2, y, 2.0F, Ui.alpha(0xFFFFFFFF, banner));
            graphics.text(minecraft.font, line, (width - minecraft.font.width(line)) / 2, y + 22, Ui.alpha(Ui.MUTED | 0xFF000000, banner), false);
        }
    }

    /** Un anneau fait de carrés de {@code cell} pixels, centré, de ce rayon. */
    private static void ring(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int cell, int color) {
        int steps = Math.max(12, radius / 2);
        for (int k = 0; k < steps; k++) {
            float a = k * Mth.TWO_PI / steps;
            int x = Math.round((cx + Mth.cos(a) * radius) / cell) * cell;
            int y = Math.round((cy + Mth.sin(a) * radius) / cell) * cell;
            graphics.fill(x, y, x + cell, y + cell, color);
        }
    }

    // ------------------------------------------------------------------
    // Le ciel : les traits des étoiles, et le tunnel
    // ------------------------------------------------------------------

    /** L'image : à gauche un carré plein, à droite une bande en pointillés de gros pixels, pour les anneaux. */
    private static DynamicTexture texture() {
        if (texture == null) {
            texture = new DynamicTexture(() -> "Orbitalis warp", 16, 16, false) {
                {
                    sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
                }
            };
            NativeImage image = texture.getPixels();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    boolean on = x < 8 || ((y / 2) % 2 == 0 && x != 8 && x != 15);
                    int alpha = x < 8 ? 255 : on ? (x == 9 || x == 14 ? 150 : 255) : 40;
                    image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
                }
            }
            texture.upload();
        }
        return texture;
    }

    /** Les axes du tunnel : la proue, le haut, la droite. */
    private static Vector3f forward() {
        return new Vector3f(out.getStepX(), 0.0F, out.getStepZ());
    }

    /** Un point du tunnel : à {@code along} blocs devant (négatif derrière), sur un cercle de ce rayon, à cet angle. */
    private static Vector3f at(Vector3f forward, Vector3f right, float along, float radius, float angle) {
        return new Vector3f(forward).mul(along).add(new Vector3f(right).mul(Mth.cos(angle) * radius)).add(0.0F, Mth.sin(angle) * radius, 0.0F);
    }

    /** Un carré des quatre points donnés, vu des deux côtés. */
    private static void quad(BufferBuilder builder, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float v0, float u1, float v1, int argb) {
        builder.addVertex(a).setUv(u0, v0).setColor(argb);
        builder.addVertex(b).setUv(u1, v0).setColor(argb);
        builder.addVertex(c).setUv(u1, v1).setColor(argb);
        builder.addVertex(d).setUv(u0, v1).setColor(argb);
        builder.addVertex(d).setUv(u0, v1).setColor(argb);
        builder.addVertex(c).setUv(u1, v1).setColor(argb);
        builder.addVertex(b).setUv(u1, v0).setColor(argb);
        builder.addVertex(a).setUv(u0, v0).setColor(argb);
    }

    /** Un trait fin, de {@code from} à {@code to}, tourné vers la caméra (au centre du ciel). */
    private static void streak(BufferBuilder builder, Vector3f from, Vector3f to, float width, int argb) {
        Vector3f side = new Vector3f(to).sub(from).cross(new Vector3f(from).add(to)).normalize(width * 0.5F);
        if (!Float.isFinite(side.x)) {
            return;
        }
        quad(builder, new Vector3f(from).add(side), new Vector3f(to).add(side), new Vector3f(to).sub(side), new Vector3f(from).sub(side),
                0.0F, 0.0F, 0.45F, 1.0F, argb);
    }

    /**
     * Par-dessus le ciel ordinaire, avant l'éclair et après la sortie : chaque étoile s'étire en un trait vers la proue,
     * d'autant plus long que le vaisseau accélère.
     */
    public static void renderStreaks(Minecraft minecraft) {
        float s = stretch(time(partial()));
        if (s <= 0.01F) {
            return;
        }
        Vector3f forward = forward();
        RandomSource random = RandomSource.createThreadLocalInstance(4_243L);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 700)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < 650; i++) {
                float y = random.nextFloat() * 2.0F - 1.0F;
                float around = random.nextFloat() * Mth.TWO_PI;
                float flat = Mth.sqrt(1.0F - y * y);
                Vector3f star = new Vector3f(flat * Mth.cos(around), y, flat * Mth.sin(around));
                float length = (0.15F + 0.5F * random.nextFloat()) * s;
                // Le trait part de l'étoile et file vers la proue (devant), ou s'en éloigne (derrière) : c'est le
                // mouvement du vaisseau qui l'étire.
                Vector3f toward = new Vector3f(star).add(new Vector3f(forward).mul(length)).normalize();
                int color = random.nextFloat() < 0.3F ? (galactic ? 0xE6B8FF : 0xB8DCFF) : 0xFFFFFF;
                streak(builder, new Vector3f(star).mul(SKY), toward.mul(SKY), 0.22F + 0.2F * s, (int) (Mth.clamp(s * 1.2F, 0.0F, 1.0F) * 230) << 24 | color);
            }
            draw(minecraft, builder);
        }
    }

    /** Le tunnel, à la place du ciel : un fond sombre, des anneaux de lumière qui passent, des traînées d'étoiles. */
    public static void renderTunnel(Minecraft minecraft) {
        float t = time(partial());
        Vector3f forward = forward();
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F).normalize();
        int deep = galactic ? 0x12051F : 0x020A18;
        int band = galactic ? 0xC86BFF : 0x5FD8FF;
        int band2 = galactic ? 0xFF7FC8 : 0x8FA8FF;
        RandomSource random = RandomSource.createThreadLocalInstance(1_616L);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 1800)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // Le fond : une boîte sombre autour de la caméra.
            float b = SKY;
            Vector3f[] corner = new Vector3f[8];
            for (int k = 0; k < 8; k++) {
                corner[k] = new Vector3f((k & 1) == 0 ? -b : b, (k & 2) == 0 ? -b : b, (k & 4) == 0 ? -b : b);
            }
            int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
            for (int[] f : faces) {
                quad(builder, corner[f[0]], corner[f[1]], corner[f[2]], corner[f[3]], 0.0F, 0.0F, 0.45F, 1.0F, 0xFF000000 | deep);
            }
            // Le bout du tunnel : un éclat à la proue, trois losanges emboîtés, du plus large et pâle au plus vif ; il palpite.
            float beat = 1.0F + 0.12F * Mth.sin(t * 0.45F);
            for (int k = 0; k < 3; k++) {
                float size = (11.0F - 4.0F * k) * beat;
                Vector3f centre = new Vector3f(forward).mul(SKY * 0.95F);
                Vector3f up = new Vector3f(0.0F, size, 0.0F);
                Vector3f side = new Vector3f(right).mul(size);
                int color = (k == 0 ? 0x30 : k == 1 ? 0x60 : 0xD0) << 24 | (k == 2 ? 0xFFFFFF : band);
                quad(builder, new Vector3f(centre).sub(up), new Vector3f(centre).add(side), new Vector3f(centre).add(up),
                        new Vector3f(centre).sub(side), 0.0F, 0.0F, 0.45F, 1.0F, color);
            }
            // Les anneaux : ils viennent de la proue et passent autour du vaisseau, de plus en plus vite au début.
            float speed = 1.6F;
            float travelled = t - STRETCH;
            float spacing = 10.0F;
            float span = 120.0F;
            int segments = 32;
            for (int r = 0; r < 12; r++) {
                float along = Mth.positiveModulo(r * spacing - travelled * speed, span) - span * 0.35F;
                float fade = 1.0F - Math.abs(along) / (span * 0.7F);
                if (fade <= 0.0F) {
                    continue;
                }
                float radius = 13.0F + 2.0F * Mth.sin(r * 1.3F);
                int color = (int) (Mth.clamp(fade, 0.0F, 1.0F) * 255) << 24 | (r % 3 == 0 ? band2 : band);
                for (int k = 0; k < segments; k++) {
                    float a0 = k * Mth.TWO_PI / segments + r * 0.4F;
                    float a1 = (k + 1) * Mth.TWO_PI / segments + r * 0.4F;
                    quad(builder, at(forward, right, along, radius, a0), at(forward, right, along, radius, a1),
                            at(forward, right, along + 3.0F, radius, a1), at(forward, right, along + 3.0F, radius, a0),
                            0.5F, 0.0F, 1.0F, 1.0F, color);
                }
            }
            // Les traînées : des milliers de points d'étoiles devenus des traits, qui filent de la proue vers la poupe.
            for (int i = 0; i < 520; i++) {
                float radius = 6.0F + random.nextFloat() * 40.0F;
                float angle = random.nextFloat() * Mth.TWO_PI;
                float length = 5.0F + random.nextFloat() * 16.0F;
                float along = Mth.positiveModulo(random.nextFloat() * span - travelled * speed * (2.5F + random.nextFloat() * 2.0F), span) - span * 0.4F;
                float fade = 1.0F - Math.abs(along) / (span * 0.65F);
                if (fade <= 0.0F) {
                    continue;
                }
                int color = random.nextFloat() < 0.25F ? band : 0xFFFFFF;
                streak(builder, at(forward, right, along, radius, angle), at(forward, right, along + length, radius, angle), 0.35F,
                        (int) (Mth.clamp(fade, 0.0F, 1.0F) * 220) << 24 | color);
            }
            draw(minecraft, builder);
        }
    }

    private static void draw(Minecraft minecraft, BufferBuilder builder) {
        DynamicTexture image = texture();
        try (MeshData data = builder.build()) {
            if (data == null) {
                return;
            }
            int indexCount = data.drawState().indexCount();
            RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewStack()));
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indexBuffer = indices.getBuffer(indexCount);
            try (GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Orbitalis warp", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
                    RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Orbitalis warp",
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
}
