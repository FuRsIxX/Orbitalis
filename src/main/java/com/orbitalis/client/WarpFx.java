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
 * Le saut en distorsion, mis en scène pour l'équipage. Tout y est continu, sans à-coups, et reste dans l'esprit du jeu
 * (des traits, des anneaux en pointillés de gros pixels, des couleurs franches).
 * <ol>
 * <li><b>L'accélération</b> ({@link #STRETCH} ticks) : par les hublots, les étoiles se mettent à filer vers la poupe, de
 * plus en plus vite ; elles bleuissent devant, rougissent derrière. Le champ de vision s'ouvre, la caméra vibre. Juste
 * avant le départ, une onde de choc part de la proue et balaie l'espace autour du vaisseau.</li>
 * <li><b>L'éclair</b> : l'écran s'embrase puis s'éteint en douceur ; le serveur déplace le vaisseau pendant ce temps.</li>
 * <li><b>Le tunnel</b> ({@link #TUNNEL} ticks) : une seconde onde de choc, puis un tunnel qui tourne sur lui-même, ses
 * anneaux qui respirent, trois filaments en spirale le long des parois, des traînées d'étoiles et des étincelles qui
 * frôlent le vaisseau, un éclat au bout. Bleuté vers une étoile proche, violet et rose pour un saut galactique. On marche
 * librement à bord.</li>
 * <li><b>La sortie</b> : un second éclair, un coup de frein (le champ de vision se resserre), une onde de choc à l'arrivée,
 * les étoiles qui ralentissent jusqu'à se figer.</li>
 * <li><b>L'arrivée</b> : un bandeau, le nom du monde et sa ligne, qui s'efface.</li>
 * </ol>
 * Avec un pack de shaders actif, le ciel du mod n'est pas dessiné : seuls l'éclair, la caméra et le bandeau restent.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class WarpFx {
    /** L'accélération, avant l'éclair, en ticks : c'est aussi l'attente du serveur avant de déplacer le vaisseau (ShipJump.WARP_LEAD). */
    public static final int STRETCH = 60;
    /** Le tunnel, en ticks. */
    public static final int TUNNEL = 180;
    private static final int EXIT = STRETCH + TUNNEL;
    private static final int END = EXIT + 110;
    /** Les ondes de choc : au départ (juste avant l'éclair), à l'entrée du tunnel, à l'arrivée. */
    private static final float[] SHOCKS = {STRETCH - 16, STRETCH + 2, EXIT + 3};
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
        return galactic ? 0xE6C2FF : 0xD8F6FF;
    }

    private static int band() {
        return galactic ? 0xC86BFF : 0x5FD8FF;
    }

    private static int band2() {
        return galactic ? 0xFF7FC8 : 0x8FA8FF;
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

    /** La vitesse apparente, de 0 à 1 : elle monte pendant l'accélération, vaut 1 dans le tunnel, retombe à la sortie. */
    private static float speed(float t) {
        if (t < 0.0F) {
            return 0.0F;
        }
        if (t < STRETCH) {
            float k = t / STRETCH;
            return k * k;
        }
        if (t < EXIT) {
            return 1.0F;
        }
        return 1.0F - smooth((t - EXIT) / 45.0F);
    }

    /** Le chemin parcouru par les étoiles dans le ciel ordinaire : l'intégrale de la vitesse, pour qu'elles filent sans saut. */
    private static float flow(float t) {
        if (t < STRETCH) {
            return t * t * t / (3.0F * STRETCH * STRETCH);
        }
        float e = Math.min(t - EXIT, 45.0F);
        // À la sortie, la vitesse retombe en douceur : on intègre pas à pas, c'est court.
        float sum = 0.0F;
        for (float s = 0.0F; s < e; s += 1.0F) {
            sum += speed(EXIT + s);
        }
        return sum;
    }

    /** Un éclair : il s'embrase en quelques ticks, puis s'éteint en douceur. */
    private static float bloom(float t, float at) {
        if (t < at - 5.0F) {
            return 0.0F;
        }
        if (t < at) {
            return smooth((t - (at - 5.0F)) / 5.0F);
        }
        return (float) Math.exp(-(t - at) / 6.0);
    }

    private static float flash(float t) {
        return Math.max(bloom(t, STRETCH), bloom(t, EXIT));
    }

    /** L'à-coup d'une onde de choc sur la caméra : brutal, puis il s'amortit. */
    private static float kick(float t) {
        float kick = 0.0F;
        for (float at : SHOCKS) {
            if (t >= at) {
                kick = Math.max(kick, (float) Math.exp(-(t - at) / 5.0));
            }
        }
        return kick;
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
            sound(minecraft, SoundEvents.PORTAL_TRIGGER, 1.2F, 0.5F);
            sound(minecraft, SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.9F);
            lastSound = 0;
        } else if (t >= (int) SHOCKS[0] && lastSound < 1) {
            sound(minecraft, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.45F);
            lastSound = 1;
        } else if (t >= STRETCH - 2 && lastSound < 2) {
            sound(minecraft, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.6F);
            sound(minecraft, SoundEvents.TRIDENT_THUNDER.value(), 1.6F, 0.5F);
            lastSound = 2;
        } else if (t >= STRETCH && t < EXIT - 10 && (t - STRETCH) % 40 == 20) {
            // Le grondement du tunnel.
            sound(minecraft, SoundEvents.BEACON_AMBIENT, 0.5F, 0.8F);
        } else if (t >= EXIT - 2 && lastSound < 3) {
            sound(minecraft, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.5F);
            sound(minecraft, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.9F);
            lastSound = 3;
        }
    }

    private static void sound(Minecraft minecraft, SoundEvent event, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    // ------------------------------------------------------------------
    // La caméra : la vibration, les à-coups, le champ de vision
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float t = time((float) event.getPartialTick());
        if (t < 0.0F) {
            return;
        }
        float tunnel = t >= STRETCH && t < EXIT ? 0.35F : 0.0F;
        float amplitude = 0.45F * speed(t) * (t < STRETCH ? 1.0F : 0.0F) + tunnel + 2.4F * kick(t) + 0.8F * flash(t);
        // Une vibration faite de trois fréquences : elle ne se répète pas, comme une coque qui encaisse.
        float noiseA = Mth.sin(t * 1.9F) + 0.6F * Mth.sin(t * 3.7F + 1.0F) + 0.3F * Mth.sin(t * 7.3F + 2.0F);
        float noiseB = Mth.cos(t * 2.3F) + 0.6F * Mth.sin(t * 4.1F + 0.5F) + 0.3F * Mth.cos(t * 6.7F + 1.5F);
        event.setYaw(event.getYaw() + noiseA * amplitude * 0.35F);
        event.setPitch(event.getPitch() + noiseB * amplitude * 0.35F);
        event.setRoll(event.getRoll() + Mth.sin(t * 0.21F) * (tunnel > 0.0F ? 1.2F : 0.0F) + noiseA * kick(t) * 0.6F);
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        float t = time((float) event.getPartialTick());
        if (t < 0.0F) {
            return;
        }
        float factor;
        if (t < STRETCH) {
            factor = 1.0F + 0.30F * speed(t) + 0.10F * bloom(t, STRETCH);
        } else if (t < EXIT) {
            factor = 1.30F - 0.05F * smooth((t - STRETCH) / 40.0F) + 0.10F * bloom(t, STRETCH) + 0.03F * Mth.sin(t * 0.3F);
        } else {
            // Le coup de frein : le champ se resserre d'un coup, puis revient.
            float e = t - EXIT;
            float from = 1.25F + 0.03F * Mth.sin(EXIT * 0.3F);
            factor = e < 8.0F ? Mth.lerp(smooth(e / 8.0F), from, 0.93F) : Mth.lerp(smooth((e - 8.0F) / 30.0F), 0.93F, 1.0F);
        }
        event.setFOV(event.getFOV() * factor);
    }

    // ------------------------------------------------------------------
    // L'écran : l'éclair, le halo du tunnel, le bandeau d'arrivée
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
        // Le halo du tunnel, sur les bords de l'écran : il pulse doucement.
        float halo = t >= STRETCH && t < EXIT ? smooth((t - STRETCH) / 20.0F) * (1.0F - smooth((t - EXIT + 20.0F) / 20.0F)) : 0.0F;
        if (halo > 0.01F) {
            vignette(graphics, width, height, band(), halo * (0.22F + 0.06F * Mth.sin(t * 0.25F)));
        }
        float flash = flash(t);
        if (flash > 0.004F) {
            graphics.fill(0, 0, width, height, (int) (Math.min(1.0F, flash) * 240) << 24 | glow());
        }
        // Le bandeau d'arrivée : le nom du monde, sa ligne, au tiers de l'écran ; il paraît puis s'efface.
        float banner = Math.min(smooth((t - EXIT - 18.0F) / 14.0F), 1.0F - smooth((t - END + 24.0F) / 24.0F));
        if (banner > 0.01F) {
            String name = title.getString().toUpperCase(java.util.Locale.ROOT);
            String line = subtitle.getString();
            int y = height / 3;
            int w = Math.max(minecraft.font.width(name) * 2, minecraft.font.width(line)) + 40;
            int x = (width - w) / 2;
            graphics.fill(x, y - 8, x + w, y + 36, Ui.alpha(0xFF04060A, 0.72F * banner));
            int lit = Math.round(w * smooth((t - EXIT - 18.0F) / 20.0F));
            graphics.fill(x + (w - lit) / 2, y - 8, x + (w + lit) / 2, y - 7, Ui.alpha(Ui.ACCENT, banner));
            graphics.fill(x + (w - lit) / 2, y + 35, x + (w + lit) / 2, y + 36, Ui.alpha(Ui.ACCENT, banner));
            Ui.big(graphics, minecraft.font, name, (width - minecraft.font.width(name) * 2) / 2, y, 2.0F, Ui.alpha(0xFFFFFFFF, banner));
            graphics.text(minecraft.font, line, (width - minecraft.font.width(line)) / 2, y + 22, Ui.alpha(Ui.MUTED | 0xFF000000, banner), false);
        }
    }

    /** Un halo coloré sur les bords de l'écran, fondu vers le centre en bandes fines. */
    private static void vignette(GuiGraphicsExtractor graphics, int width, int height, int rgb, float strength) {
        int steps = 24;
        int depth = Math.max(width, height) / 6;
        for (int k = 0; k < steps; k++) {
            int inset = depth * k / steps;
            int next = depth * (k + 1) / steps;
            float a = strength * (1.0F - k / (float) steps);
            int color = (int) (Mth.clamp(a * a, 0.0F, 1.0F) * 255) << 24 | rgb;
            graphics.fill(inset, inset, width - inset, next, color);
            graphics.fill(inset, height - next, width - inset, height - inset, color);
            graphics.fill(inset, next, next, height - next, color);
            graphics.fill(width - next, next, width - inset, height - next, color);
        }
    }

    // ------------------------------------------------------------------
    // Le ciel : les étoiles qui filent, les ondes de choc, le tunnel
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

    private static Vector3f forward() {
        return new Vector3f(out.getStepX(), 0.0F, out.getStepZ());
    }

    private static Vector3f right(Vector3f forward) {
        return new Vector3f(forward).cross(0.0F, 1.0F, 0.0F).normalize();
    }

    /** Un point du tunnel : à {@code along} blocs devant (négatif derrière), sur un cercle de ce rayon, à cet angle. */
    private static Vector3f at(Vector3f forward, Vector3f right, float along, float radius, float angle) {
        return new Vector3f(forward).mul(along).add(new Vector3f(right).mul(Mth.cos(angle) * radius)).add(0.0F, Mth.sin(angle) * radius, 0.0F);
    }

    /** Une direction de la voûte, à {@code theta} de la proue, tournée de {@code phi} autour d'elle. */
    private static Vector3f dome(Vector3f forward, Vector3f right, float theta, float phi) {
        Vector3f around = new Vector3f(right).mul(Mth.cos(phi)).add(0.0F, Mth.sin(phi), 0.0F);
        return new Vector3f(forward).mul(Mth.cos(theta)).add(around.mul(Mth.sin(theta)));
    }

    /** Un carré, vu des deux côtés, une couleur par coin (pour les fondus). */
    private static void quad(BufferBuilder builder, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float v0, float u1, float v1,
            int ca, int cb, int cc, int cd) {
        builder.addVertex(a).setUv(u0, v0).setColor(ca);
        builder.addVertex(b).setUv(u1, v0).setColor(cb);
        builder.addVertex(c).setUv(u1, v1).setColor(cc);
        builder.addVertex(d).setUv(u0, v1).setColor(cd);
        builder.addVertex(d).setUv(u0, v1).setColor(cd);
        builder.addVertex(c).setUv(u1, v1).setColor(cc);
        builder.addVertex(b).setUv(u1, v0).setColor(cb);
        builder.addVertex(a).setUv(u0, v0).setColor(ca);
    }

    private static void quad(BufferBuilder builder, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float v0, float u1, float v1, int argb) {
        quad(builder, a, b, c, d, u0, v0, u1, v1, argb, argb, argb, argb);
    }

    /** Un trait fin, de {@code from} (sa queue, effacée) à {@code to} (sa tête), tourné vers la caméra. */
    private static void streak(BufferBuilder builder, Vector3f from, Vector3f to, float width, int argb) {
        Vector3f side = new Vector3f(to).sub(from).cross(new Vector3f(from).add(to)).normalize(width * 0.5F);
        if (!Float.isFinite(side.x)) {
            return;
        }
        int tail = argb & 0x00FFFFFF;
        quad(builder, new Vector3f(from).add(side), new Vector3f(to).add(side), new Vector3f(to).sub(side), new Vector3f(from).sub(side),
                0.0F, 0.0F, 0.45F, 1.0F, tail, argb, argb, tail);
    }

    private static int argb(float alpha, int rgb) {
        return (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24 | rgb;
    }

    /** Une couleur entre deux autres. */
    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
        int g = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return r << 16 | g << 8 | bl;
    }

    /**
     * Les ondes de choc : un anneau d'énergie qui naît devant la proue et s'ouvre, du centre de la vue jusqu'à ses
     * bords, en s'effaçant ; son bord extérieur est vif, il s'estompe vers l'intérieur. Par la verrière, on le voit
     * passer sur le vaisseau.
     */
    private static void shocks(BufferBuilder builder, float t, Vector3f forward, Vector3f right) {
        for (int k = 0; k < SHOCKS.length; k++) {
            float dt = t - SHOCKS[k];
            if (dt < 0.0F || dt > 45.0F) {
                continue;
            }
            // Lente au début, pour qu'on la voie s'ouvrir dans la verrière, puis de plus en plus large.
            float radius = 1.0F + 160.0F * (float) Math.pow(dt / 45.0F, 1.6F);
            float thickness = 1.2F + radius * 0.18F;
            float alpha = (float) Math.pow(1.0F - dt / 45.0F, 1.2F);
            float along = 40.0F - dt * 0.3F;
            int edge = k == 1 ? band2() : 0xFFFFFF;
            int body = band();
            int segments = 72;
            for (int s = 0; s < segments; s++) {
                float a0 = s * Mth.TWO_PI / segments;
                float a1 = (s + 1) * Mth.TWO_PI / segments;
                float inner = radius - thickness;
                float middle = radius - thickness * 0.3F;
                quad(builder, at(forward, right, along, inner, a0), at(forward, right, along, inner, a1),
                        at(forward, right, along, middle, a1), at(forward, right, along, middle, a0), 0.0F, 0.0F, 0.45F, 1.0F,
                        argb(0.0F, body), argb(0.0F, body), argb(alpha * 0.55F, body), argb(alpha * 0.55F, body));
                quad(builder, at(forward, right, along, middle, a0), at(forward, right, along, middle, a1),
                        at(forward, right, along, radius, a1), at(forward, right, along, radius, a0), 0.0F, 0.0F, 0.45F, 1.0F,
                        argb(alpha * 0.55F, body), argb(alpha * 0.55F, body), argb(alpha, edge), argb(alpha, edge));
            }
        }
    }

    /**
     * Par-dessus le ciel ordinaire, pendant l'accélération et à la sortie : les étoiles filent vers la poupe, plus ou
     * moins vite selon leur distance ; elles bleuissent devant et rougissent derrière, et l'onde de choc les balaie.
     */
    public static void renderStreaks(Minecraft minecraft) {
        float t = time(partial());
        float v = speed(t);
        boolean shock = false;
        for (float at : SHOCKS) {
            shock |= t >= at && t <= at + 45.0F;
        }
        if (v <= 0.01F && !shock) {
            return;
        }
        Vector3f forward = forward();
        Vector3f right = right(forward);
        float flow = flow(t);
        RandomSource random = RandomSource.createThreadLocalInstance(4_243L);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 1000)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            if (v > 0.01F) {
                for (int i = 0; i < 760; i++) {
                    float theta0 = (float) Math.acos(1.0F - 2.0F * random.nextFloat());
                    float phi = random.nextFloat() * Mth.TWO_PI;
                    float pace = 0.6F + 0.8F * random.nextFloat();
                    // L'étoile glisse de la proue vers la poupe ; arrivée derrière, elle revient devant.
                    float theta = (theta0 + flow * 0.012F * pace) % Mth.PI;
                    float length = (0.01F + 0.32F * v * pace) * Mth.sin(Math.max(0.15F, theta));
                    Vector3f head = dome(forward, right, theta, phi).mul(SKY);
                    Vector3f tail = dome(forward, right, Math.max(0.0F, theta - length), phi).mul(SKY);
                    // Bleuie devant, rougie derrière : d'autant plus que le vaisseau va vite.
                    float side = theta / Mth.PI;
                    int color = side < 0.5F ? mix(0xFFFFFF, galactic ? 0xD8A8FF : 0xA8D0FF, (0.5F - side) * 2.0F * v)
                            : mix(0xFFFFFF, 0xFFB088, (side - 0.5F) * 2.0F * v);
                    float fade = Mth.sin(theta) * Mth.clamp(v * 1.4F, 0.0F, 1.0F);
                    streak(builder, tail, head, 0.25F + 0.3F * v, argb(0.25F + 0.75F * fade, color));
                }
            }
            shocks(builder, t, forward, right);
            draw(minecraft, builder);
        }
    }

    /**
     * Le tunnel, à la place du ciel : une paroi sombre qui s'éclaire vers l'avant, des anneaux qui tournent et respirent,
     * trois filaments en spirale, des traînées d'étoiles, des étincelles tout près, un éclat au bout, et l'onde de choc
     * de l'entrée.
     */
    public static void renderTunnel(Minecraft minecraft) {
        float t = time(partial());
        float inside = t - STRETCH;
        Vector3f forward = forward();
        Vector3f right = right(forward);
        int deep = galactic ? 0x14061F : 0x030B1A;
        int band = band();
        int band2 = band2();
        // Le chemin parcouru : la vitesse monte en vingt ticks, puis reste.
        float travelled = inside < 20.0F ? inside * inside / 40.0F : inside - 10.0F;
        float rush = 1.8F * travelled;
        float spin = inside * 0.035F;
        float flicker = 0.88F + 0.12F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.63F);
        RandomSource random = RandomSource.createThreadLocalInstance(1_616L);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 4200)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // La paroi : un long cylindre, sombre derrière, bleuté vers l'avant ; et le fond, au bout, qui rayonne.
            int sides = 28;
            float wall = 46.0F;
            float[] stations = {-100.0F, -40.0F, 10.0F, 55.0F, 88.0F};
            for (int k = 0; k < sides; k++) {
                float a0 = k * Mth.TWO_PI / sides + spin * 0.3F;
                float a1 = (k + 1) * Mth.TWO_PI / sides + spin * 0.3F;
                for (int s = 0; s < stations.length - 1; s++) {
                    int c0 = 0xFF000000 | mix(deep, band, Mth.clamp((stations[s] + 40.0F) / 260.0F, 0.0F, 1.0F) * 0.22F * flicker);
                    int c1 = 0xFF000000 | mix(deep, band, Mth.clamp((stations[s + 1] + 40.0F) / 260.0F, 0.0F, 1.0F) * 0.22F * flicker);
                    quad(builder, at(forward, right, stations[s], wall, a0), at(forward, right, stations[s], wall, a1),
                            at(forward, right, stations[s + 1], wall, a1), at(forward, right, stations[s + 1], wall, a0),
                            0.0F, 0.0F, 0.45F, 1.0F, c0, c0, c1, c1);
                }
                // Le fond : du bord de la paroi vers le cœur, de plus en plus clair ; derrière, la nuit.
                int rim = 0xFF000000 | mix(deep, band, 0.12F * flicker);
                int core = 0xFF000000 | mix(deep, band, 0.55F);
                quad(builder, at(forward, right, 88.0F, wall, a0), at(forward, right, 88.0F, wall, a1),
                        at(forward, right, 89.0F, 7.0F, a1), at(forward, right, 89.0F, 7.0F, a0), 0.0F, 0.0F, 0.45F, 1.0F, rim, rim, core, core);
                quad(builder, at(forward, right, 89.0F, 7.0F, a0), at(forward, right, 89.0F, 7.0F, a1),
                        at(forward, right, 89.5F, 0.0F, a1), at(forward, right, 89.5F, 0.0F, a0), 0.0F, 0.0F, 0.45F, 1.0F,
                        core, core, 0xFF000000 | mix(band, 0xFFFFFF, 0.3F), 0xFF000000 | mix(band, 0xFFFFFF, 0.3F));
                quad(builder, at(forward, right, -100.0F, wall, a0), at(forward, right, -100.0F, wall, a1),
                        at(forward, right, -100.0F, 0.0F, a1), at(forward, right, -100.0F, 0.0F, a0), 0.0F, 0.0F, 0.45F, 1.0F,
                        0xFF000000 | deep);
            }
            // L'éclat au bout du tunnel : trois losanges emboîtés qui palpitent, et qui tournent.
            float beat = 1.0F + 0.14F * Mth.sin(t * 0.45F);
            for (int k = 0; k < 3; k++) {
                float size = (10.0F - 3.2F * k) * beat;
                Vector3f centre = new Vector3f(forward).mul(86.0F);
                float turn = spin * (k % 2 == 0 ? 1.5F : -1.5F);
                Vector3f up = at(forward, right, 0.0F, size, Mth.HALF_PI + turn);
                Vector3f side = at(forward, right, 0.0F, size, turn);
                int color = argb(k == 0 ? 0.25F : k == 1 ? 0.5F : 0.9F, k == 2 ? 0xFFFFFF : band);
                quad(builder, new Vector3f(centre).sub(up), new Vector3f(centre).add(side), new Vector3f(centre).add(up),
                        new Vector3f(centre).sub(side), 0.0F, 0.0F, 0.45F, 1.0F, color);
            }
            // Les anneaux : ils viennent de l'avant, passent autour du vaisseau en tournant, et respirent.
            float spacing = 9.0F;
            float span = 130.0F;
            int segments = 40;
            for (int r = 0; r < 15; r++) {
                float along = Mth.positiveModulo(r * spacing - rush, span) - span * 0.32F;
                float fade = 1.0F - Math.abs(along) / (span * 0.68F);
                if (fade <= 0.0F) {
                    continue;
                }
                float radius = 12.0F + 1.8F * Mth.sin(travelled * 0.08F + r * 1.3F);
                float twist = spin * (r % 2 == 0 ? 1.0F : -0.7F) + r * 0.4F;
                int color = argb(Mth.clamp(fade, 0.0F, 1.0F) * 0.8F * flicker, r % 3 == 0 ? band2 : band);
                for (int k = 0; k < segments; k++) {
                    float a0 = k * Mth.TWO_PI / segments + twist;
                    float a1 = (k + 1) * Mth.TWO_PI / segments + twist;
                    quad(builder, at(forward, right, along, radius, a0), at(forward, right, along, radius, a1),
                            at(forward, right, along + 3.0F, radius, a1), at(forward, right, along + 3.0F, radius, a0),
                            0.5F, 0.0F, 1.0F, 1.0F, color);
                }
            }
            // Trois filaments d'énergie en spirale, le long de la paroi.
            for (int f = 0; f < 3; f++) {
                float phase = f * Mth.TWO_PI / 3.0F;
                for (int k = 0; k < 90; k++) {
                    float a0 = -70.0F + k * 1.8F;
                    float a1 = a0 + 1.8F;
                    float angle0 = a0 * 0.07F + spin * 3.0F + phase - rush * 0.07F;
                    float angle1 = a1 * 0.07F + spin * 3.0F + phase - rush * 0.07F;
                    float fade = 1.0F - Math.abs(a0 - 10.0F) / 85.0F;
                    streak(builder, at(forward, right, a0, 15.5F, angle0), at(forward, right, a1, 15.5F, angle1), 0.7F,
                            argb(fade * 0.7F * flicker, f == 1 ? band2 : band));
                }
            }
            // Les traînées d'étoiles, qui filent de l'avant vers l'arrière.
            for (int i = 0; i < 420; i++) {
                float radius = 5.0F + random.nextFloat() * 38.0F;
                float angle = random.nextFloat() * Mth.TWO_PI + spin * 0.5F;
                float length = 6.0F + random.nextFloat() * 20.0F;
                float along = Mth.positiveModulo(random.nextFloat() * span - rush * (2.2F + random.nextFloat() * 2.0F), span) - span * 0.4F;
                float fade = 1.0F - Math.abs(along) / (span * 0.65F);
                if (fade <= 0.0F) {
                    continue;
                }
                int color = random.nextFloat() < 0.3F ? band : 0xFFFFFF;
                streak(builder, at(forward, right, along, radius, angle), at(forward, right, along + length, radius, angle), 0.28F,
                        argb(fade * 0.55F, color));
            }
            // Des étincelles tout près de la coque, très rapides : la vitesse se sent par les hublots.
            for (int i = 0; i < 80; i++) {
                float radius = 2.5F + random.nextFloat() * 4.0F;
                float angle = random.nextFloat() * Mth.TWO_PI;
                float along = Mth.positiveModulo(random.nextFloat() * 60.0F - rush * 5.0F, 60.0F) - 25.0F;
                streak(builder, at(forward, right, along, radius, angle), at(forward, right, along + 3.0F, radius, angle), 0.12F,
                        argb(0.8F, 0xFFFFFF));
            }
            shocks(builder, t, forward, right);
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
