package com.orbitalis.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.atlas.StationSurvey;
import com.orbitalis.core.network.SurveyRequestPayload;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * La planète de l'écran d'une station de poursuite, dans le style de la carte : un globe en cubes, bâti avec le
 * relevé de la planète entière (voir {@link StationSurvey}), que l'on fait tourner dans tous les sens.
 * <p>
 * Le globe est repeint à chaque image, pixel par pixel, selon l'angle de vue : chaque pixel de l'écran retrouve le
 * point de la sphère qu'il montre, puis la case du relevé qui y est. Chaque case a sa nuance et le relief qui
 * l'éclaire ; les pôles sont pris par les glaces ; un liseré d'atmosphère en deux tons entoure le globe, et le côté
 * nuit s'assombrit par paliers. Tant que le relevé n'est pas arrivé, la planète est un océan ; les terres y
 * apparaissent en fondu.
 */
public final class StationGlobe {
    /** Rayon du globe, en pixels d'interface. */
    public static final int RADIUS = 21;
    /** Épaisseur de l'atmosphère, en pixels. */
    private static final float AIR = 2.4F;
    private static final int SIZE = 2 * (RADIUS + 3);
    /** Durée du fondu des terres, à l'arrivée du relevé, en ms. */
    private static final long APPEAR_MS = 400L;
    /** Latitude au-delà de laquelle la banquise couvre tout, en radians (72°). */
    private static final double ICE = 1.26;
    private static final int AIR_INNER = 0x9CCBFA;
    private static final int AIR_OUTER = 0x4F86C8;
    private static final int ICE_COLOR = 0xEAF2F8;
    /** Direction du Soleil, dans le repère de la vue : en haut à gauche, un peu devant. */
    private static final float SUN_X = -0.55F;
    private static final float SUN_Y = 0.35F;
    private static final float SUN_Z = 0.76F;

    /**
     * L'angle de vue.
     *
     * @param yaw   rotation autour de l'axe des pôles, en radians
     * @param pitch inclinaison de l'axe des pôles vers l'avant, en radians
     * @param spin  rotation de la planète sur elle-même, en radians : elle tourne au rythme d'un satellite
     *              géostationnaire
     */
    public record View(float yaw, float pitch, float spin) {
        /** Un point de la planète, en longitude et latitude, dans le repère de la vue : x à droite, y en haut, z vers soi. */
        public float[] project(double longitude, double latitude) {
            double lon = longitude + spin;
            double px = Math.cos(latitude) * Math.sin(lon);
            double py = Math.sin(latitude);
            double pz = Math.cos(latitude) * Math.cos(lon);
            return rotate(px, py, pz);
        }

        /** Un point de l'espace, dans le repère de la planète (y vers le pôle nord), ramené dans celui de la vue. */
        public float[] rotate(double px, double py, double pz) {
            double x1 = px * Math.cos(yaw) + pz * Math.sin(yaw);
            double z1 = -px * Math.sin(yaw) + pz * Math.cos(yaw);
            double y2 = py * Math.cos(pitch) - z1 * Math.sin(pitch);
            double z2 = py * Math.sin(pitch) + z1 * Math.cos(pitch);
            return new float[] {(float) x1, (float) y2, (float) z2};
        }
    }

    private static final Map<BlockPos, StationGlobe> GLOBES = new HashMap<>();

    private final BlockPos station;
    private Identifier id;
    private DynamicTexture texture;
    private int[] cells;
    private long readyAt;
    private long askedAt = Long.MIN_VALUE;

    private StationGlobe(BlockPos station) {
        this.station = station;
    }

    /** Le serveur envoie un relevé : s'il est celui de la planète vue d'une station dont l'écran l'attend, il est gardé. */
    public static void accept(BlockPos pos, int lod, int[] cells) {
        StationGlobe globe = GLOBES.get(pos);
        if (globe == null || lod != StationSurvey.LOD || cells.length != StationSurvey.WIDTH * StationSurvey.HEIGHT) {
            return;
        }
        if (globe.cells == null) {
            globe.readyAt = Util.getMillis();
        }
        globe.cells = cells;
    }

    /** Demande le relevé de cette station dès l'ouverture de son écran : il arrive pendant qu'on regarde la liste. */
    public static void prepare(BlockPos station) {
        GLOBES.computeIfAbsent(station.immutable(), StationGlobe::new).ask(true);
    }

    /** Dessine le globe de cette station, centré sur ce point de l'écran, sous cet angle. */
    public static void draw(GuiGraphicsExtractor graphics, BlockPos station, int centerX, int centerY, View view) {
        StationGlobe globe = GLOBES.computeIfAbsent(station.immutable(), StationGlobe::new);
        globe.ask(false);
        if (globe.texture == null) {
            globe.id = Orbitalis.id("station_globe/" + Long.toHexString(station.asLong()));
            globe.texture = new DynamicTexture(() -> "Orbitalis station globe", SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(globe.id, globe.texture);
        }
        globe.paint(view);
        graphics.blit(RenderPipelines.GUI_TEXTURED, globe.id, centerX - SIZE / 2, centerY - SIZE / 2, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE,
                0xFFFFFFFF);
    }

    /** Demande le relevé au serveur tant qu'il manque : tout de suite à l'ouverture, puis de temps en temps. */
    private void ask(boolean now) {
        if (cells != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (now || askedAt == Long.MIN_VALUE || Math.abs(time - askedAt) > 100) {
            askedAt = time;
            ClientPacketDistributor.sendToServer(new SurveyRequestPayload(station));
        }
    }

    private static int hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }

    private static int shade(int rgb, float factor) {
        int r = Math.clamp((int) ((rgb >> 16 & 0xFF) * factor), 0, 255);
        int g = Math.clamp((int) ((rgb >> 8 & 0xFF) * factor), 0, 255);
        int b = Math.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    /** Éclairage du Soleil, par paliers : jour, deux crans de crépuscule, nuit. */
    private static float daylight(float nx, float ny, float nz) {
        float lit = nx * SUN_X + ny * SUN_Y + nz * SUN_Z;
        return lit > 0.30F ? 1.0F : lit > 0.12F ? 0.82F : lit > -0.05F ? 0.62F : 0.42F;
    }

    /** Repeint le globe sous cet angle, pixel par pixel. */
    private void paint(View view) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        float appear = cells == null ? 0.0F : Mth.clamp((Util.getMillis() - readyAt) / (float) APPEAR_MS, 0.0F, 1.0F);
        appear = appear * appear * (3.0F - 2.0F * appear);
        float centre = SIZE / 2.0F;
        double cosYaw = Math.cos(view.yaw());
        double sinYaw = Math.sin(view.yaw());
        double cosPitch = Math.cos(view.pitch());
        double sinPitch = Math.sin(view.pitch());
        int ocean = AtlasPainter.tone(LaunchTableBlockEntity.FAR_DEEP);
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                float dx = px + 0.5F - centre;
                float dy = py + 0.5F - centre;
                float distance = Mth.sqrt(dx * dx + dy * dy);
                int argb = 0;
                if (distance <= RADIUS + AIR && distance > RADIUS) {
                    // L'atmosphère : claire contre la planète, plus sombre au-dehors ; la nuit l'éteint aussi.
                    float light = daylight(dx / distance, -dy / distance, 0.0F);
                    boolean inner = distance <= RADIUS + AIR / 2.0F;
                    argb = (inner ? 0xFF000000 : 0xC0000000) | shade(inner ? AIR_INNER : AIR_OUTER, Math.max(0.5F, light));
                } else if (distance <= RADIUS) {
                    float nx = dx / RADIUS;
                    float ny = -dy / RADIUS;
                    float nz = Mth.sqrt(Math.max(0.0F, 1.0F - nx * nx - ny * ny));
                    // Le point de la sphère que montre ce pixel : on défait l'inclinaison, puis la rotation.
                    double y1 = ny * cosPitch + nz * sinPitch;
                    double z1 = -ny * sinPitch + nz * cosPitch;
                    double x2 = nx * cosYaw - z1 * sinYaw;
                    double z2 = nx * sinYaw + z1 * cosYaw;
                    double latitude = Math.asin(Math.clamp(y1, -1.0, 1.0));
                    double longitude = Math.atan2(x2, z2) - view.spin();
                    int column = Math.floorMod((int) Math.floor((longitude / (2.0 * Math.PI) + 0.5) * StationSurvey.WIDTH), StationSurvey.WIDTH);
                    int row = Math.clamp((int) Math.floor((0.5 - latitude / Math.PI) * StationSurvey.HEIGHT), 0, StationSurvey.HEIGHT - 1);
                    int color = ocean;
                    if (cells != null) {
                        int packed = cells[row * StationSurvey.WIDTH + column];
                        int raw = packed == 0 ? LaunchTableBlockEntity.FAR_DEEP : packed & 0xFFFFFF;
                        int land = AtlasPainter.tone(raw);
                        if (packed != 0 && !LaunchTableBlockEntity.isFarWater(raw)) {
                            // Le relief éclaircit les hauteurs ; chaque case a sa nuance, comme les cubes des maquettes.
                            int blocks = (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS;
                            land = shade(land, 1.0F + Mth.clamp(blocks / 160.0F, -0.1F, 0.18F));
                            land = shade(land, 0.96F + hash(column, row) % 5 * 0.02F);
                        } else {
                            land = shade(land, 0.98F + hash(column, row) % 3 * 0.02F);
                        }
                        if (Math.abs(latitude) > ICE) {
                            land = shade(ICE_COLOR, 0.96F + hash(column, row) % 4 * 0.015F);
                        }
                        color = mix(ocean, land, appear);
                    }
                    // Le bord s'assombrit à peine, la sphère tourne ; puis le jour et la nuit.
                    color = shade(color, (0.80F + 0.20F * nz) * daylight(nx, ny, nz));
                    argb = 0xFF000000 | color;
                }
                image.setPixel(px, py, argb);
            }
        }
        texture.upload();
    }
}
