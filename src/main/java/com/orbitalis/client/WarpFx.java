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
    private static DynamicTexture wallTexture;

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

    /**
     * L'atlas, quatre images de 16 pixels dessinées comme des textures du jeu : en haut à gauche un carré plein (les
     * traits, les fonds) ; en haut à droite la bande d'énergie des anneaux (un cœur blanc, des bords sombres, des
     * segments) ; en bas à gauche l'étoile à quatre branches du bout du tunnel ; en bas à droite le bord crépitant des
     * ondes de choc, piqueté d'étincelles.
     */
    private static DynamicTexture texture() {
        if (texture == null) {
            texture = new DynamicTexture(() -> "Orbitalis warp", 32, 32, false) {
                {
                    sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                }
            };
            NativeImage image = texture.getPixels();
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    image.setPixel(x, y, atlasPixel(x, y));
                }
            }
            texture.upload();
        }
        return texture;
    }

    private static int grey(int alpha, int light) {
        return Mth.clamp(alpha, 0, 255) << 24 | light << 16 | light << 8 | light;
    }

    private static int atlasPixel(int x, int y) {
        int u = x & 15;
        int v = y & 15;
        if (x < 16 && y < 16) {
            return grey(255, 255);
        }
        if (y < 16) {
            // La bande d'énergie : transparente aux bords, un liseré sombre, le corps en segments, le cœur blanc.
            if (v <= 1 || v >= 14) {
                return grey(0, 255);
            }
            if (v == 2 || v == 13) {
                return grey(110, 190);
            }
            if (v == 3 || v == 12) {
                return grey(200, 215);
            }
            boolean core = v >= 6 && v <= 9;
            boolean dash = (u / 4) % 2 == 0;
            return core ? grey(255, 255) : grey(dash ? 240 : 185, dash ? 235 : 175);
        }
        if (x < 16) {
            // L'étoile : un cœur plein, quatre branches qui s'effilent, quatre petits rayons en diagonale.
            float dx = Math.abs(u - 7.5F);
            float dy = Math.abs(v - 7.5F);
            if (dx < 1.5F && dy < 1.5F) {
                return grey(255, 255);
            }
            if (dx < 0.6F && dy < 7.5F) {
                return grey(Math.round((1.0F - dy / 8.0F) * 4.0F) * 60, 255);
            }
            if (dy < 0.6F && dx < 7.5F) {
                return grey(Math.round((1.0F - dx / 8.0F) * 4.0F) * 60, 255);
            }
            if (Math.abs(dx - dy) < 0.6F && dx < 3.5F) {
                return grey(150, 255);
            }
            return grey(0, 255);
        }
        // L'onde de choc, de l'intérieur (en haut) vers son front (en bas) : un voile qui s'efface vers l'intérieur, des
        // filaments d'énergie de longueurs inégales qui partent du front, quelques étincelles, une crête vive, et le front
        // lui-même, blanc et cranté.
        int column = (u * 7 + 3) % 16;
        if (v >= 14) {
            return (u % 5 == 0 && v == 15) ? grey(0, 255) : grey(255, 255);
        }
        if (v >= 12) {
            return grey(230, 240);
        }
        int filament = 3 + (column * 5 + 1) % 8;
        if (v >= 12 - filament && u % 2 == column % 2) {
            float strength = 1.0F - (12 - v) / (float) (filament + 1);
            return grey(90 + Math.round(strength * 4.0F) * 40, 225);
        }
        if ((u * 31 + v * 17) % 23 == 0 && v > 3) {
            return grey(255, 255);
        }
        return grey(20 + v * 9, 200);
    }

    /**
     * La paroi du tunnel : un plasma veiné, en quatre tons, qui se répète tout autour et le long du tunnel ; on le fait
     * défiler vers l'arrière.
     */
    private static DynamicTexture wallTexture() {
        if (wallTexture == null) {
            wallTexture = new DynamicTexture(() -> "Orbitalis warp wall", 32, 32, false) {
                {
                    sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
                }
            };
            NativeImage image = wallTexture.getPixels();
            RandomSource random = RandomSource.createThreadLocalInstance(3_210L);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    float a = x / 32.0F * Mth.TWO_PI;
                    float b = y / 32.0F * Mth.TWO_PI;
                    float vein = 0.5F + 0.5F * Mth.sin(a * 2.0F + 2.2F * Mth.sin(b) + Mth.sin(b * 2.0F + a));
                    vein = vein * vein + (random.nextFloat() - 0.5F) * 0.12F;
                    int level = Mth.clamp(Math.round(vein * 3.0F), 0, 3);
                    int light = new int[] {80, 130, 190, 255}[level];
                    if (random.nextInt(60) == 0) {
                        light = 255;
                    }
                    image.setPixel(x, y, grey(255, light));
                }
            }
            wallTexture.upload();
        }
        return wallTexture;
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
                0.0F, 0.0F, 0.45F, 0.45F, tail, argb, argb, tail);
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
    /** La durée d'une onde de choc, en ticks. */
    private static final float SHOCK_LIFE = 30.0F;

    private static void shocks(BufferBuilder builder, float t, Vector3f forward, Vector3f right) {
        for (int k = 0; k < SHOCKS.length; k++) {
            float dt = t - SHOCKS[k];
            if (dt < 0.0F || dt > SHOCK_LIFE) {
                continue;
            }
            // Rapide : elle traverse la verrière en quelques ticks, puis s'élargit encore en ralentissant.
            float life = dt / SHOCK_LIFE;
            float radius = 1.0F + 175.0F * (float) Math.pow(life, 1.15F);
            float thickness = 2.0F + radius * 0.22F;
            float alpha = (float) Math.pow(1.0F - life, 1.1F);
            float along = 40.0F - dt * 0.6F;
            int edge = k == 1 ? band2() : 0xFFFFFF;
            int body = band();
            ring(builder, forward, right, along, radius, thickness, alpha, body, edge, dt);
            // L'écho : une onde plus faible, un peu en retrait.
            ring(builder, forward, right, along + 3.0F, radius * 0.8F, thickness * 0.5F, alpha * 0.4F, body, body, dt + 7.0F);
            // Des éclats projetés vers l'extérieur, au passage du front.
            RandomSource random = RandomSource.createThreadLocalInstance(700L + k);
            for (int i = 0; i < 40; i++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                float reach = 1.05F + 0.1F * random.nextFloat();
                streak(builder, at(forward, right, along, radius * 0.97F, angle), at(forward, right, along, radius * reach, angle),
                        0.35F + radius * 0.004F, argb(alpha * 0.8F, edge));
            }
        }
    }

    /**
     * Un anneau d'onde de choc : une bande, de l'intérieur effacé à son front blanc ; son bord ondule un peu, comme un
     * souffle, au lieu d'être un cercle parfait.
     */
    private static void ring(BufferBuilder builder, Vector3f forward, Vector3f right, float along, float radius, float thickness,
            float alpha, int body, int edge, float phase) {
        int segments = 48;
        for (int s = 0; s < segments; s++) {
            float a0 = s * Mth.TWO_PI / segments;
            float a1 = (s + 1) * Mth.TWO_PI / segments;
            float r0 = radius * wobble(a0, phase);
            float r1 = radius * wobble(a1, phase);
            quad(builder, at(forward, right, along, r0 - thickness, a0), at(forward, right, along, r1 - thickness, a1),
                    at(forward, right, along, r1, a1), at(forward, right, along, r0, a0), 0.5F, 0.5F, 1.0F, 1.0F,
                    argb(alpha * 0.35F, body), argb(alpha * 0.35F, body), argb(alpha, edge), argb(alpha, edge));
        }
    }

    private static float wobble(float angle, float phase) {
        return 1.0F + 0.035F * Mth.sin(angle * 9.0F + phase * 0.8F) + 0.02F * Mth.sin(angle * 23.0F - phase * 1.3F);
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
            shock |= t >= at && t <= at + SHOCK_LIFE;
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
        // La paroi : un long cylindre de plasma veiné, sombre derrière, bleuté vers l'avant ; sa texture tourne avec le
        // tunnel et file vers l'arrière.
        int sides = 28;
        float wall = 46.0F;
        float[] stations = {-100.0F, -40.0F, 10.0F, 55.0F, 88.0F};
        try (ByteBufferBuilder wallBytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 140)) {
            BufferBuilder walls = new BufferBuilder(wallBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float scroll = rush / 22.0F;
            for (int k = 0; k < sides; k++) {
                float a0 = k * Mth.TWO_PI / sides + spin * 0.3F;
                float a1 = (k + 1) * Mth.TWO_PI / sides + spin * 0.3F;
                float u0 = k * 6.0F / sides;
                float u1 = (k + 1) * 6.0F / sides;
                for (int s = 0; s < stations.length - 1; s++) {
                    int c0 = 0xFF000000 | mix(deep, band, Mth.clamp((stations[s] + 40.0F) / 260.0F, 0.0F, 1.0F) * 0.42F * flicker);
                    int c1 = 0xFF000000 | mix(deep, band, Mth.clamp((stations[s + 1] + 40.0F) / 260.0F, 0.0F, 1.0F) * 0.42F * flicker);
                    quad(walls, at(forward, right, stations[s], wall, a0), at(forward, right, stations[s], wall, a1),
                            at(forward, right, stations[s + 1], wall, a1), at(forward, right, stations[s + 1], wall, a0),
                            u0, stations[s] / 24.0F + scroll, u1, stations[s + 1] / 24.0F + scroll, c0, c0, c1, c1);
                }
            }
            draw(minecraft, walls, wallTexture());
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 8 * 4200)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int k = 0; k < sides; k++) {
                float a0 = k * Mth.TWO_PI / sides + spin * 0.3F;
                float a1 = (k + 1) * Mth.TWO_PI / sides + spin * 0.3F;
                // Le fond : du bord de la paroi vers le cœur, de plus en plus clair ; derrière, la nuit.
                int rim = 0xFF000000 | mix(deep, band, 0.12F * flicker);
                int core = 0xFF000000 | mix(deep, band, 0.55F);
                quad(builder, at(forward, right, 88.0F, wall, a0), at(forward, right, 88.0F, wall, a1),
                        at(forward, right, 89.0F, 7.0F, a1), at(forward, right, 89.0F, 7.0F, a0), 0.0F, 0.0F, 0.45F, 0.45F, rim, rim, core, core);
                quad(builder, at(forward, right, 89.0F, 7.0F, a0), at(forward, right, 89.0F, 7.0F, a1),
                        at(forward, right, 89.5F, 0.0F, a1), at(forward, right, 89.5F, 0.0F, a0), 0.0F, 0.0F, 0.45F, 0.45F,
                        core, core, 0xFF000000 | mix(band, 0xFFFFFF, 0.3F), 0xFF000000 | mix(band, 0xFFFFFF, 0.3F));
                quad(builder, at(forward, right, -100.0F, wall, a0), at(forward, right, -100.0F, wall, a1),
                        at(forward, right, -100.0F, 0.0F, a1), at(forward, right, -100.0F, 0.0F, a0), 0.0F, 0.0F, 0.45F, 0.45F,
                        0xFF000000 | deep);
            }
            // L'éclat au bout du tunnel : un halo en losange, et par-dessus une étoile à quatre branches, en pixels, qui
            // palpite et tourne lentement.
            float beat = 1.0F + 0.14F * Mth.sin(t * 0.45F);
            Vector3f centre = new Vector3f(forward).mul(86.0F);
            Vector3f haloUp = at(forward, right, 0.0F, 11.0F * beat, Mth.HALF_PI + spin);
            Vector3f haloSide = at(forward, right, 0.0F, 11.0F * beat, spin);
            quad(builder, new Vector3f(centre).sub(haloUp), new Vector3f(centre).add(haloSide), new Vector3f(centre).add(haloUp),
                    new Vector3f(centre).sub(haloSide), 0.0F, 0.0F, 0.45F, 0.45F, argb(0.28F, band));
            float star = 10.0F * beat;
            float turn = spin * 0.6F;
            Vector3f starUp = at(forward, right, 0.0F, star, Mth.HALF_PI + turn);
            Vector3f starSide = at(forward, right, 0.0F, star, turn);
            quad(builder, new Vector3f(centre).sub(starSide).sub(starUp), new Vector3f(centre).add(starSide).sub(starUp),
                    new Vector3f(centre).add(starSide).add(starUp), new Vector3f(centre).sub(starSide).add(starUp),
                    0.0F, 0.5F, 0.5F, 1.0F, argb(0.95F, mix(band, 0xFFFFFF, 0.7F)));
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
                            0.5F, 0.0F, 1.0F, 0.5F, color);
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
        draw(minecraft, builder, texture());
    }

    private static void draw(Minecraft minecraft, BufferBuilder builder, DynamicTexture image) {
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
