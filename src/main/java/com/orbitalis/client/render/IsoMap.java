package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.atlas.AtlasTiles;
import com.orbitalis.core.network.SurveyRequestPayload;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase1.spaceport.FlightSimulation;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Carte de navigation d'un pas de tir : le monde en cubes, vu de trois quarts, sous une caméra qui suit la fusée.
 * <p>
 * <b>Le sol est celui de l'atlas.</b> Ce sont les mêmes tuiles ({@link AtlasMap}), peintes de la même façon : le
 * vrai monde, bloc par bloc de près, puis à des échelles de plus en plus larges à mesure que la caméra recule.
 * <p>
 * <b>La prise de vue</b> est celle d'une carte en ligne dont on s'éloignerait peu à peu : la carte recule à mesure
 * que la fusée monte, d'un mouvement continu, selon une seule loi (l'échelle ne dépend que de l'altitude). En
 * reculant, on voit le bord de la planète entrer dans le cadre, avec son atmosphère, puis la planète entière : c'est
 * alors un globe, taillé en cubes, bâti avec le relevé du pays. La vue s'arrête là. La caméra suit la fusée sans la
 * figer : elle garde le pas de tir et la fusée dans le cadre, et la fusée s'y déplace sur sa trajectoire.
 * <p>
 * <b>La trajectoire</b> est celle du vol calculé, dans l'espace : la fusée monte droit, bascule peu à peu vers son
 * cap, et file vers l'horizon ; un lanceur orbital décrit ensuite son orbite, une ellipse autour du globe. Derrière
 * elle, la route suivie et son ombre au sol ; devant, la route prévue, avec ses séparations d'étages. Distances et
 * hauteurs gardent les proportions d'un vrai vol, sur une planète huit fois plus petite que la Terre.
 * <p>
 * <b>L'orientation</b> se change comme celle de l'atlas : la vue montre au fond l'un des quatre coins du monde.
 * <p>
 * Les écrans de contrôle et le pupitre reçoivent la prise de vue sous la forme d'une liste de rectangles
 * ({@link Prim}), à dessiner dans l'ordre.
 */
public final class IsoMap {
    /** Hauteur de la vue, en unités : un bloc en fait quatre de large au sol. Sa largeur suit le format de l'écran. */
    public static final float VIEW_HEIGHT = 200.0F;
    /** Taille de l'image propre à la carte : la planète, et un carré blanc pour les aplats. */
    private static final int ATLAS_WIDTH = 1024;
    private static final int ATLAS_HEIGHT = 512;

    private static final int FAR = LaunchTableBlockEntity.FAR_SIZE;
    /** Altitude à laquelle l'échelle de la vue est divisée par deux, en blocs : c'est toute la loi de la caméra. */
    private static final float HALVING = 38.0F;
    /** Au-delà de ce que le monde laisse monter, l'altitude du vol prend le relais : tant de blocs par km. */
    private static final float BLOCKS_PER_KM = (float) FlightProfile.BLOCKS_PER_KM;
    /**
     * Altitude à partir de laquelle la caméra recule plus vite encore, en blocs (100 km) : près du sol, elle suit la
     * fusée de près ; dans l'espace, elle prend du champ jusqu'à montrer la planète entière.
     */
    private static final float RECEDE = 1600.0F;
    /**
     * Rayon de la planète, en blocs, et rayon à l'écran sous lequel le disque est la planète elle-même. Avec la loi
     * de la caméra, son bord entre dans le cadre vers 30 km d'altitude, et on la voit entière à partir de 100 km.
     */
    private static final float PLANET_RADIUS = 3000.0F;
    /** Rayon à l'écran en dessous duquel la planète ne rapetisse plus : la vue s'arrête quand on la voit entière. */
    private static final float PLANET_SMALLEST = 72.0F;
    private static final float GLOBE_FROM = 150.0F;
    /** Taille à l'écran d'un bloc du sol, en travers, pour une échelle de 1 : la même sur les tuiles et sur le globe. */
    private static final float GROUND_SCALE = 2.8284F;
    /** Rayon de la Terre, en km : la distance au sol du vol calculé se reporte sur la planète en part de son tour. */
    private static final double EARTH_RADIUS_KM = 6371.0;
    /**
     * Blocs de carte que couvre un kilomètre parcouru au sol. La planète de la carte est huit fois plus petite que la
     * Terre, à cette échelle : un lanceur se met en orbite à un dixième de tour environ, et l'on voit sa route
     * s'incurver.
     */
    private static final double TRACK_PER_KM = 8.0 * PLANET_RADIUS / EARTH_RADIUS_KM;
    /**
     * Hauteur de la trajectoire dessinée : proportionnelle à l'altitude près du sol, puis de plus en plus resserrée,
     * pour qu'une orbite reste à sa place autour du globe (voir {@link #lift}). En blocs.
     */
    private static final double LIFT = 600.0;
    /** Part du chemin du pas de tir à la fusée que la caméra suit : le reste, la fusée le parcourt dans le cadre. */
    private static final float FOLLOW = 0.4F;
    /** Délai au-delà duquel une orientation demandée s'impose, même si son sol n'est pas tout à fait prêt, en ms. */
    private static final long TURN_WAIT_MS = 1500L;
    /** Les quatre orientations : le coin du monde que l'on voit au fond, comme dans l'atlas. */
    public static final String[] CORNERS = {"nw", "ne", "se", "sw"};
    /** L'atmosphère, autour du disque : sa couche claire, puis sa couche sombre. */
    private static final int AIR_INNER = 0x9CCBFA;
    private static final int AIR_OUTER = 0x4F86C8;

    private static final int GLOBE_X = 0;
    private static final int GLOBE_Y = 0;
    private static final int GLOBE_SIZE = 512;
    private static final float GLOBE_DISC = 220.0F;
    private static final int GLOBE_BLOCK = 8;
    private static final int GLOBE_CELL = 2;
    private static final int WHITE_X = GLOBE_SIZE + 16;
    private static final int WHITE_Y = 16;

    /** Largeur à l'écran, en unités de vue, en dessous de laquelle une case est trop petite : on passe à l'échelle suivante. */
    private static final float CELL_UNITS = 4.5F;
    /** Tuiles demandées au serveur par vingtième de seconde, au plus, toutes cartes confondues. */
    private static final int REQUESTS = 6;

    /**
     * Un rectangle de la prise de vue, en unités de vue.
     *
     * @param texture       l'image où il puise ; un aplat en prend le carré blanc
     * @param textureWidth  taille de cette image, en pixels
     * @param plain         vrai pour un aplat
     * @param top           couleur en haut (elle teinte l'image, s'il y en a une)
     * @param bottom        couleur en bas : différente de {@code top}, elle fait un dégradé
     * @param depth         rang du plan auquel il appartient, du fond (0) vers l'avant
     */
    public record Prim(Identifier texture, int textureWidth, int textureHeight, boolean plain, float x0, float y0, float x1, float y1, float u0,
            float v0, float u1, float v1, int top, int bottom, float depth) {
    }

    private static final Map<BlockPos, IsoMap> MAPS = new HashMap<>();
    private static final int[] BUDGET = {REQUESTS};
    private static long budgetAt;

    private final BlockPos table;
    private final Identifier id;
    private final DynamicTexture texture;
    /** Relevé du pays reçu du serveur, la peinture du globe qui en est tirée, et vrai une fois dans l'image. */
    private int[] country;
    private CompletableFuture<int[]> globePainting;
    private boolean globeReady;
    /** Orientation pour laquelle le globe a été peint, ou est en peinture. */
    private int globeTurn = -1;
    private long askedAt = Long.MIN_VALUE;
    /** Orientation de la vue, de 0 à 3 (voir {@link #CORNERS}), et celle que l'on a demandée, en préparation. */
    private int turn;
    private int turnWanted;
    private long turnAsked;

    /** Chemin réellement suivi par la fusée depuis le décollage : des points (x, altitude, z) comptés depuis la table. */
    private final List<float[]> trail = new ArrayList<>();

    private IsoMap(BlockPos table) {
        this.table = table;
        // L'orientation choisie pour ce pas de tir, gardée d'une partie à l'autre.
        this.turn = this.turnWanted = Math.floorMod(com.orbitalis.client.ViewPrefs.getInt(com.orbitalis.client.ViewPrefs.padKey("iso.turn", table), 0), 4);
        this.id = Orbitalis.id("iso_map/" + Long.toHexString(table.asLong()));
        // L'image est filtrée : agrandie ou réduite par la caméra, elle reste douce, sans pixels qui scintillent.
        this.texture = new DynamicTexture(() -> "Orbitalis navigation map", ATLAS_WIDTH, ATLAS_HEIGHT, true) {
            {
                sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
            }
        };
        NativeImage image = texture.getPixels();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                image.setPixel(GLOBE_SIZE + x, y, 0xFFFFFFFF);
            }
        }
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Le serveur envoie un relevé lointain de cette table : celui du pays (1) sert à bâtir la planète. */
    public static void acceptSurvey(BlockPos table, int lod, int[] cells) {
        // Le relevé d'une station de poursuite n'est pas pour la carte de navigation (voir StationGlobe).
        Level level = Minecraft.getInstance().level;
        if (lod != 1 || cells.length != FAR * FAR || level == null || !(level.getBlockEntity(table) instanceof LaunchTableBlockEntity)) {
            return;
        }
        IsoMap map = MAPS.computeIfAbsent(table.immutable(), IsoMap::new);
        if (map.country == null) {
            map.country = cells;
        }
    }

    /** Orientation demandée pour la carte de navigation de cette table, de 0 à 3 (voir {@link #CORNERS}). */
    public static int turn(BlockPos table) {
        IsoMap map = MAPS.get(table);
        return map == null ? Math.floorMod(com.orbitalis.client.ViewPrefs.getInt(com.orbitalis.client.ViewPrefs.padKey("iso.turn", table), 0), 4)
                : map.turnWanted;
    }

    /**
     * Change l'orientation de la carte de navigation de cette table. La vue bascule dès que le sol, vu de ce côté, est
     * prêt : on ne voit jamais la carte se vider.
     */
    public static void turn(BlockPos table, int corner) {
        IsoMap map = MAPS.computeIfAbsent(table.immutable(), IsoMap::new);
        map.turnWanted = Math.floorMod(corner, 4);
        com.orbitalis.client.ViewPrefs.set(com.orbitalis.client.ViewPrefs.padKey("iso.turn", table), map.turnWanted);
        map.turnAsked = Util.getMillis();
    }

    /** Place d'un point du monde dans la vue tournée : vers la droite du fond, puis vers la gauche de devant. */
    private static double viewX(int turn, double x, double z) {
        return turn == 0 ? x : turn == 1 ? z : turn == 2 ? -x : -z;
    }

    private static double viewZ(int turn, double x, double z) {
        return turn == 0 ? z : turn == 1 ? -x : turn == 2 ? -z : x;
    }

    /** Le point du monde qui est à cette place de la vue tournée : l'inverse de {@link #viewX} et {@link #viewZ}. */
    private static double worldX(int turn, double vx, double vz) {
        return turn == 0 ? vx : turn == 1 ? -vz : turn == 2 ? -vx : vz;
    }

    private static double worldZ(int turn, double vx, double vz) {
        return turn == 0 ? vz : turn == 1 ? vx : turn == 2 ? -vz : -vx;
    }

    /** Hauteur dessinée d'une altitude, en blocs : la même près du sol, de plus en plus resserrée en montant. */
    private static double lift(double altitude) {
        return LIFT * Math.log1p(Math.max(0.0, altitude) / LIFT);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1 ^ c * 0x9E3779B1;
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

    private static int mixRgb(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static int mix(int a, int b, float t) {
        return 0xFF000000 | mixRgb(a, b, t);
    }

    /**
     * Le globe : la planète vue de loin, centrée sur le site, bâtie avec le relevé du pays. C'est la carte elle-même,
     * enroulée sur une sphère : au centre du disque, elle est exactement celle que montraient les tuiles ; vers le
     * bord, le terrain se resserre, comme sur une sphère. Le disque est taillé en cubes : son bord est en marches
     * d'escalier, sa surface en pavés, chacun avec son arête, et un liseré d'atmosphère l'entoure.
     */
    private static int[] paintGlobe(int[] cells, int turn) {
        int[] image = new int[GLOBE_SIZE * GLOBE_SIZE];
        int step = LaunchTableBlockEntity.FAR_STEP[1];
        float centre = GLOBE_SIZE / 2.0F;
        for (int cy = 0; cy < GLOBE_SIZE; cy += GLOBE_CELL) {
            for (int cx = 0; cx < GLOBE_SIZE; cx += GLOBE_CELL) {
                // Le contour est taillé en gros cubes : c'est le centre du cube qui dit s'il est de la planète.
                int bx = cx / GLOBE_BLOCK * GLOBE_BLOCK;
                int by = cy / GLOBE_BLOCK * GLOBE_BLOCK;
                float blockX = (bx + GLOBE_BLOCK / 2.0F - centre) / GLOBE_DISC;
                float blockY = (by + GLOBE_BLOCK / 2.0F - centre) / GLOBE_DISC;
                float outline = Mth.sqrt(blockX * blockX + blockY * blockY);
                if (outline > 1.07F) {
                    continue;
                }
                int color;
                if (outline > 1.0F) {
                    // L'atmosphère : un cube bleu clair, tout autour.
                    color = outline > 1.035F ? 0x4F86C8 : 0x9CCBFA;
                } else {
                    // Le terrain, lui, est lu case par case, plus finement.
                    float nx = (cx + GLOBE_CELL / 2.0F - centre) / GLOBE_DISC;
                    float ny = (cy + GLOBE_CELL / 2.0F - centre) / GLOBE_DISC;
                    float radius = Mth.sqrt(nx * nx + ny * ny);
                    if (radius > 0.998F) {
                        nx *= 0.998F / radius;
                        ny *= 0.998F / radius;
                        radius = 0.998F;
                    }
                    float nz = Mth.sqrt(1.0F - radius * radius);
                    // L'endroit du relevé que l'on voit là. Près du centre, la carte telle qu'elle est, de trois
                    // quarts ; vers le bord, la sphère se dérobe, et le terrain se resserre.
                    float wrap = radius < 1.0E-4F ? 1.0F : (float) Math.asin(radius) / radius;
                    float across = PLANET_RADIUS * GROUND_SCALE * wrap * (nx / 2.0F + ny) / 2.0F;
                    float down = PLANET_RADIUS * GROUND_SCALE * wrap * (ny - nx / 2.0F) / 2.0F;
                    // Le globe est tourné comme la vue.
                    float east = (float) worldX(turn, across, down);
                    float south = (float) worldZ(turn, across, down);
                    int column = Math.round(east / step) + FAR / 2;
                    int row = Math.round(south / step) + FAR / 2;
                    int packed = column >= 0 && row >= 0 && column < FAR && row < FAR ? cells[row * FAR + column] : 0;
                    // Au-delà du relevé (l'autre face de la planète, à peine visible) : l'océan.
                    int raw = packed == 0 ? LaunchTableBlockEntity.FAR_DEEP : packed & 0xFFFFFF;
                    color = AtlasPainter.tone(raw);
                    int blocks = packed == 0 ? 0 : (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS;
                    if (packed != 0 && !LaunchTableBlockEntity.isFarWater(raw)) {
                        // Le relief éclaircit les hauteurs ; chaque case a sa nuance, comme les cubes des maquettes.
                        color = shade(color, 1.0F + Mth.clamp(blocks / 160.0F, -0.1F, 0.18F));
                        color = shade(color, 0.96F + hash(cx, cy, 33) % 5 * 0.02F);
                    }
                    // Le bord s'assombrit à peine : la sphère tourne.
                    color = shade(color, 0.80F + 0.20F * nz);
                }
                for (int dy = 0; dy < GLOBE_CELL; dy++) {
                    for (int dx = 0; dx < GLOBE_CELL; dx++) {
                        // Chaque gros cube a son arête sombre, en bas et à droite : la surface se lit en cubes.
                        boolean ridge = outline <= 1.0F && ((cx + dx) % GLOBE_BLOCK == GLOBE_BLOCK - 1 || (cy + dy) % GLOBE_BLOCK == GLOBE_BLOCK - 1);
                        image[(cy + dy) * GLOBE_SIZE + cx + dx] = 0xFF000000 | (ridge ? shade(color, 0.93F) : color);
                    }
                }
            }
        }
        return image;
    }

    /** Installe la planète quand sa peinture est finie, et demande au serveur le relevé du pays tant qu'il manque. */
    private void maintain(Level level) {
        // Le globe se peint (ou se repeint) dans l'orientation de la vue, une fois le relevé du pays reçu. L'ancienne
        // peinture reste en place jusqu'à ce que la nouvelle soit prête.
        if (country != null && globePainting == null && globeTurn != turn) {
            globeTurn = turn;
            int[] cells = country;
            int corner = turn;
            globePainting = CompletableFuture.supplyAsync(() -> paintGlobe(cells, corner));
        }
        if (globePainting != null && globePainting.isDone()) {
            int[] pixels = globePainting.isCompletedExceptionally() ? null : globePainting.getNow(null);
            globePainting = null;
            NativeImage target = texture.getPixels();
            if (pixels != null && target != null) {
                for (int y = 0; y < GLOBE_SIZE; y++) {
                    for (int x = 0; x < GLOBE_SIZE; x++) {
                        target.setPixel(GLOBE_X + x, GLOBE_Y + y, pixels[y * GLOBE_SIZE + x]);
                    }
                }
                texture.upload();
                globeReady = true;
            }
        }
        long now = level.getGameTime();
        if (country == null && now - askedAt > 100) {
            askedAt = now;
            ClientPacketDistributor.sendToServer(new SurveyRequestPayload(table));
        }
    }

    // ------------------------------------------------------------------
    // Prise de vue
    // ------------------------------------------------------------------

    /** Ajoute un aplat, s'il est dans le cadre. */
    private void flat(List<Prim> out, float width, float x0, float y0, float x1, float y1, int top, int bottom, float depth) {
        float left = Math.max(0.0F, x0);
        float up = Math.max(0.0F, y0);
        float right = Math.min(width, x1);
        float down = Math.min(VIEW_HEIGHT, y1);
        if (right > left && down > up) {
            float u = WHITE_X / (float) ATLAS_WIDTH;
            float v = WHITE_Y / (float) ATLAS_HEIGHT;
            out.add(new Prim(id, ATLAS_WIDTH, ATLAS_HEIGHT, true, left, up, right, down, u, v, u, v, top, bottom, depth));
        }
    }

    /** Ajoute un morceau d'une image, rogné au cadre. */
    private static void image(List<Prim> out, float width, Identifier texture, int textureWidth, int textureHeight, float sourceX, float sourceY,
            float sourceWidth, float sourceHeight, float x0, float y0, float x1, float y1, int tint, float depth) {
        float left = Math.max(0.0F, x0);
        float up = Math.max(0.0F, y0);
        float right = Math.min(width, x1);
        float down = Math.min(VIEW_HEIGHT, y1);
        if (right <= left || down <= up) {
            return;
        }
        float u0 = (sourceX + (left - x0) / (x1 - x0) * sourceWidth) / textureWidth;
        float u1 = (sourceX + (right - x0) / (x1 - x0) * sourceWidth) / textureWidth;
        float v0 = (sourceY + (up - y0) / (y1 - y0) * sourceHeight) / textureHeight;
        float v1 = (sourceY + (down - y0) / (y1 - y0) * sourceHeight) / textureHeight;
        out.add(new Prim(texture, textureWidth, textureHeight, false, left, up, right, down, u0, v0, u1, v1, tint, tint, depth));
    }

    /**
     * La prise de vue de cet instant, pour un écran de ce format : les rectangles à dessiner, dans l'ordre, dans un
     * cadre de {@code viewWidth} unités de large sur {@link #VIEW_HEIGHT} de haut.
     */
    public static List<Prim> frame(LaunchTableBlockEntity table, float partialTick, float viewWidth) {
        List<Prim> out = new ArrayList<>();
        Level level = table.getLevel();
        if (level == null) {
            return out;
        }
        // Les tuiles sont demandées au compte-gouttes, et installées à mesure qu'elles sont peintes.
        long now = Util.getMillis() / 50L;
        if (now != budgetAt) {
            budgetAt = now;
            BUDGET[0] = REQUESTS;
            AtlasMap.pump(now);
        }
        IsoMap map = MAPS.computeIfAbsent(table.getBlockPos().immutable(), IsoMap::new);
        map.compose(out, table, level, partialTick, viewWidth, now);
        return out;
    }

    /** Une tuile à dessiner : son plan (0 pour un fond plus grossier, 1 pour le reste), et son rang du fond vers l'avant. */
    private record Placed(AtlasMap.Tile tile, int layer, double depth) {
    }

    /**
     * Le sol : les tuiles de l'atlas que le cadre laisse voir, du fond vers l'avant, à l'échelle de la caméra et dans
     * l'orientation donnée. Celles qui manquent sont demandées ; en attendant, ce que l'on a déjà de l'endroit est
     * dessiné à leur place.
     *
     * @param siteX  place du site dans le cadre, en travers
     * @param seaY   hauteur, dans le cadre, du niveau de la mer au site
     * @param reach  distance au site au-delà de laquelle le sol est hors du disque de la planète, en blocs
     * @param draw   faux pour seulement demander les tuiles, sans rien dessiner
     * @return vrai si toutes les tuiles de cette échelle sont là
     */
    private boolean ground(List<Prim> out, Level level, float width, float zoom, float siteX, float seaY, double siteWorldX, double siteWorldZ,
            int lod, double reach, int tint, long now, int turn, boolean draw) {
        int cell = AtlasTiles.CELL[lod];
        int span = AtlasTiles.TILE * cell;
        float scale = 4.0F * cell * zoom / AtlasPainter.CELL;
        // Le rectangle du monde que couvre le cadre, élargi : un relief dépasse vers le haut, un creux vers le bas.
        double margin = AtlasPainter.HEAD * scale;
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double across = ((k & 1) == 0 ? -siteX : width - siteX) / (2.0 * zoom);
            double down = ((k >> 1) == 0 ? -margin - seaY : VIEW_HEIGHT + margin - seaY) / zoom;
            double vx = (across + down) / 2.0;
            double vz = (down - across) / 2.0;
            double x = siteWorldX + worldX(turn, vx, vz);
            double z = siteWorldZ + worldZ(turn, vx, vz);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        // Rien au-delà du disque de la planète : inutile de le demander.
        minX = Math.max(minX, table.getX() - reach);
        maxX = Math.min(maxX, table.getX() + reach);
        minZ = Math.max(minZ, table.getZ() - reach);
        maxZ = Math.min(maxZ, table.getZ() + reach);
        boolean ready = true;
        List<Placed> placed = new ArrayList<>();
        Set<AtlasMap.Tile> used = new HashSet<>();
        Set<Long> parents = new HashSet<>();
        for (int tz = Math.floorDiv((int) Math.floor(minZ), span); tz <= Math.floorDiv((int) Math.floor(maxZ), span); tz++) {
            for (int tx = Math.floorDiv((int) Math.floor(minX), span); tx <= Math.floorDiv((int) Math.floor(maxX), span); tx++) {
                parents.add((long) Math.floorDiv(tx, 2) << 32 | Math.floorDiv(tz, 2) & 0xFFFFFFFFL);
                AtlasMap.Tile tile = AtlasMap.want(level, table, lod, tx, tz, turn, now, BUDGET);
                if (tile != null && tile.state == AtlasMap.State.READY && tile.id != null) {
                    placed.add(new Placed(tile, 1, depth(tile, turn)));
                    continue;
                }
                if (tile != null && tile.state == AtlasMap.State.UNKNOWN) {
                    continue;
                }
                ready = false;
                if (!draw) {
                    continue;
                }
                // Pas encore là : la tuile plus grossière qui la couvre, sinon les plus fines qu'elle couvre.
                boolean covered = false;
                int px = tx;
                int pz = tz;
                for (int up = lod + 1; up < AtlasTiles.CELL.length && !covered; up++) {
                    px = Math.floorDiv(px, 2);
                    pz = Math.floorDiv(pz, 2);
                    AtlasMap.Tile parent = AtlasMap.peek(up, px, pz, turn);
                    if (parent != null && parent.state == AtlasMap.State.READY && parent.id != null) {
                        if (used.add(parent)) {
                            placed.add(new Placed(parent, 0, depth(parent, turn) - up * 1.0E7));
                        }
                        covered = true;
                    }
                }
                if (!covered && lod > 0) {
                    for (int k = 0; k < 4; k++) {
                        AtlasMap.Tile child = AtlasMap.peek(lod - 1, tx * 2 + (k & 1), tz * 2 + (k >> 1), turn);
                        if (child != null && child.state == AtlasMap.State.READY && child.id != null && used.add(child)) {
                            placed.add(new Placed(child, 1, depth(child, turn)));
                        }
                    }
                }
            }
        }
        if (!draw) {
            return ready;
        }
        // L'échelle d'après est demandée d'avance, s'il reste de quoi : quand la caméra recule, la carte est déjà là.
        if (lod + 1 < AtlasTiles.CELL.length) {
            for (long parent : parents) {
                if (BUDGET[0] <= 1) {
                    break;
                }
                AtlasMap.want(level, table, lod + 1, (int) (parent >> 32), (int) parent, turn, now, BUDGET);
            }
        }
        placed.sort((a, b) -> a.layer() != b.layer() ? Integer.compare(a.layer(), b.layer()) : Double.compare(a.depth(), b.depth()));
        int rank = 0;
        for (Placed image : placed) {
            AtlasMap.Tile tile = image.tile();
            AtlasMap.touch(tile, now);
            int size = AtlasTiles.CELL[tile.lod];
            float unit = 4.0F * size * zoom / AtlasPainter.CELL;
            double[] corner = corner(tile.lod, tile.tx, tile.tz, turn);
            double ox = corner[0] - siteWorldX;
            double oz = corner[1] - siteWorldZ;
            double vx = viewX(turn, ox, oz);
            double vz = viewZ(turn, ox, oz);
            float left = siteX + (float) ((vx - vz) * 2.0 * zoom) - AtlasPainter.WIDTH / 2.0F * unit;
            float top = seaY + (float) ((vx + vz) * zoom) - (AtlasPainter.HEAD + tile.base * AtlasPainter.STEP) * unit;
            // Chaque tuile est un rien devant la précédente : sur un écran du monde, c'est ce qui les ordonne.
            image(out, width, tile.id, AtlasPainter.WIDTH, AtlasPainter.HEIGHT, 0, 0, AtlasPainter.WIDTH, AtlasPainter.HEIGHT, left, top,
                    left + AtlasPainter.WIDTH * unit, top + AtlasPainter.HEIGHT * unit, tint, 1.0F + Math.min(0.95F, ++rank * 0.004F));
        }
        return ready;
    }

    /** Le coin du fond d'une tuile dans la vue tournée, débord compris : le point du monde où commence son image. */
    private static double[] corner(int lod, int tx, int tz, int turn) {
        int cell = AtlasTiles.CELL[lod];
        double x0 = ((double) tx * AtlasTiles.TILE - AtlasPainter.APRON) * cell;
        double z0 = ((double) tz * AtlasTiles.TILE - AtlasPainter.APRON) * cell;
        double x1 = x0 + (AtlasTiles.TILE + 2 * AtlasPainter.APRON) * cell;
        double z1 = z0 + (AtlasTiles.TILE + 2 * AtlasPainter.APRON) * cell;
        return turn == 0 ? new double[] {x0, z0} : turn == 1 ? new double[] {x1, z0} : turn == 2 ? new double[] {x1, z1} : new double[] {x0, z1};
    }

    /** Rang d'une tuile du fond vers l'avant : la place, dans la vue tournée, de son coin du fond. */
    private static double depth(AtlasMap.Tile tile, int turn) {
        double[] corner = corner(tile.lod, tile.tx, tile.tz, turn);
        return viewX(turn, corner[0], corner[1]) + viewZ(turn, corner[0], corner[1]);
    }

    private void compose(List<Prim> out, LaunchTableBlockEntity launchTable, Level level, float partialTick, float width, long now) {
        BlockPos pad = launchTable.getBlockPos();
        float time = level.getGameTime() + partialTick;
        boolean flying = launchTable.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT;
        Vec3 drift = flying ? launchTable.renderDrift(partialTick) : Vec3.ZERO;
        float ascent = flying ? launchTable.renderAscent(partialTick) : 0.0F;
        boolean earth = level.dimension() == Level.OVERWORLD;
        maintain(level);

        // L'altitude de la fusée, en blocs. Tant qu'on la voit monter dans le monde, c'est sa hauteur réelle, et
        // rien d'autre : la carte montre ce que l'on voit. Quand elle disparaît dans le ciel, l'altitude de son vol
        // calculé prend le relais, en douceur.
        float km = 0.0F;
        float progress = 0.0F;
        RocketStats stats = null;
        FlightSimulation flight = null;
        if (flying && launchTable.hasRocket()) {
            progress = Mth.clamp((launchTable.phaseTicks() + partialTick) / launchTable.flightTicks(), 0.0F, 1.0F);
            stats = launchTable.rocketStats();
            flight = FlightProfile.flight(stats);
            km = (float) FlightProfile.altitudeKm(stats, progress);
        }
        float gone = Mth.clamp((ascent - 0.6F * LaunchTableBlockEntity.ASCENT_LIMIT) / (0.4F * LaunchTableBlockEntity.ASCENT_LIMIT), 0.0F, 1.0F);
        float away = gone * gone * (3.0F - 2.0F * gone);
        float altitude = earth ? ascent + Math.max(0.0F, km * BLOCKS_PER_KM - ascent) * away : ascent;

        // Le cap : l'axe du carneau au départ, puis la route que la fusée suit dans le monde.
        Direction trench = launchTable.getTrenchDirection();
        double driftLength = Math.sqrt(drift.x * drift.x + drift.z * drift.z);
        double lean = Math.min(1.0, driftLength / 16.0);
        double headX = trench.getStepX() * (1.0 - lean) + (driftLength > 1.0E-3 ? drift.x / driftLength * lean : 0.0);
        double headZ = trench.getStepZ() * (1.0 - lean) + (driftLength > 1.0E-3 ? drift.z / driftLength * lean : 0.0);
        double headLength = Math.sqrt(headX * headX + headZ * headZ);
        headX = headLength > 1.0E-3 ? headX / headLength : 1.0;
        headZ = headLength > 1.0E-3 ? headZ / headLength : 0.0;
        // La fusée au-dessus du sol : tant qu'on la voit, là où elle est dans le monde ; ensuite, là où la met le vol
        // calculé, dans le même cap.
        boolean charted = earth && flight != null;
        double reached = charted ? FlightProfile.downrangeKm(stats, progress) * TRACK_PER_KM * away : 0.0;
        double rocketX = drift.x + headX * reached;
        double rocketZ = drift.z + headZ * reached;
        double rocketLift = lift(altitude);

        // Le chemin réellement suivi : un point chaque fois que la fusée a bougé.
        if (!flying) {
            trail.clear();
        } else {
            float[] last = trail.isEmpty() ? null : trail.get(trail.size() - 1);
            if (last == null || Math.abs(last[0] - rocketX) + Math.abs(last[1] - altitude) + Math.abs(last[2] - rocketZ) > 0.5 + altitude * 0.004) {
                trail.add(new float[] {(float) rocketX, altitude, (float) rocketZ});
                if (trail.size() > 6000) {
                    trail.remove(0);
                }
            }
        }

        // La caméra. L'échelle ne dépend que de l'altitude : la carte recule à mesure que la fusée monte, d'abord
        // doucement, puis de plus en plus, jusqu'à montrer la planète entière, où elle s'arrête. Le cadre suit la
        // fusée, mais pas tout à fait : il garde le pas de tir en vue, et la fusée s'y déplace, sur sa trajectoire.
        // Quand on voit la planète entière, il glisse vers son centre, pour qu'elle tienne dans le cadre.
        float zoom = Math.max(PLANET_SMALLEST / (PLANET_RADIUS * GROUND_SCALE), 1.0F / (1.0F + altitude / HALVING) / (1.0F + altitude / RECEDE));
        float centreX = width / 2.0F;
        float centreY = VIEW_HEIGHT * 0.5F;
        float planet = PLANET_RADIUS * GROUND_SCALE * zoom;
        boolean globe = earth && planet <= GLOBE_FROM && globeReady;
        float[] spot = new float[2];
        new View(turn, 0.0F, 0.0F, zoom, globe, planet, earth).place(spot, rocketX, rocketZ, rocketLift);
        float settle = globe ? Mth.clamp((GLOBE_FROM - planet) / (GLOBE_FROM - PLANET_SMALLEST), 0.0F, 1.0F) : 0.0F;
        float follow = FOLLOW * (1.0F - settle * settle * (3.0F - 2.0F * settle));
        float siteX = centreX - spot[0] * follow;
        float siteY = centreY - spot[1] * follow;
        View view = new View(turn, siteX, siteY, zoom, globe, planet, earth);

        // Le ciel : bleu au sol, sombre en altitude, noir et étoilé dans l'espace.
        float high = earth ? Mth.clamp(altitude / (70.0F * BLOCKS_PER_KM), 0.0F, 1.0F) : 1.0F;
        float night = (float) Math.pow(high, 0.7);
        int skyTop = mix(0x5E9BE0, 0x020308, night);
        int skyLow = mix(0xB9D4EE, 0x0E1A2E, high);
        // La brume d'altitude : elle bleuit et pâlit le sol, puis s'efface dans l'espace.
        int misty = mix(0xFFFFFF, 0x9DB4D0, Mth.clamp(altitude / 900.0F, 0.0F, 0.45F) * (1.0F - 0.6F * high));

        if (globe) {
            // De très loin : le ciel, et la planète elle-même, bâtie avec le relevé du pays. Elle a le même disque, la
            // même atmosphère et, en son centre, la même carte que le sol qu'elle remplace.
            flat(out, width, 0, 0, width, VIEW_HEIGHT, skyTop, skyLow, 0);
            float scale = planet / GLOBE_DISC;
            float left = siteX - GLOBE_SIZE / 2.0F * scale;
            float top = siteY - GLOBE_SIZE / 2.0F * scale;
            image(out, width, id, ATLAS_WIDTH, ATLAS_HEIGHT, GLOBE_X, GLOBE_Y, GLOBE_SIZE, GLOBE_SIZE, left, top, left + GLOBE_SIZE * scale,
                    top + GLOBE_SIZE * scale, misty, 2);
            // Vue de si loin, une nouvelle orientation s'applique tout de suite : le globe se repeint de lui-même.
            turn = turnWanted;
        } else {
            // Le fond : la mer, là où le sol n'est pas encore arrivé. Puis le sol, celui de l'atlas.
            int sea = 0xFF000000 | AtlasPainter.WATER_COLORS[2];
            flat(out, width, 0, 0, width, VIEW_HEIGHT, earth ? sea : skyTop, earth ? sea : skyLow, 0);
            int lod = 0;
            while (lod < AtlasTiles.CELL.length - 1 && 4.0F * AtlasTiles.CELL[lod] * zoom < CELL_UNITS) {
                lod++;
            }
            float unit = 4.0F * AtlasTiles.CELL[lod] * zoom / AtlasPainter.CELL;
            // Le pas de tir n'est pas au niveau de la mer : la mer est plus bas que lui dans le cadre.
            float seaY = siteY + Math.round((pad.getY() - level.getSeaLevel()) / AtlasPainter.RELIEF[lod]) * AtlasPainter.STEP * unit;
            double reach = earth ? PLANET_RADIUS * 1.15 : 1.0E9;
            ground(out, level, width, zoom, siteX, seaY, pad.getX() + 0.5, pad.getZ() + 0.5, lod, reach, misty, now, turn, true);
            // Une autre orientation est demandée : son sol est préparé en coulisse, et la vue bascule quand il est là.
            if (turnWanted != turn && (ground(out, level, width, zoom, siteX, seaY, pad.getX() + 0.5, pad.getZ() + 0.5, lod, reach, misty, now,
                    turnWanted, false) || Util.getMillis() - turnAsked > TURN_WAIT_MS)) {
                turn = turnWanted;
            }
            if (earth) {
                // L'horizon : hors du disque de la planète, le ciel ; à son bord, l'atmosphère, en deux couches.
                // D'abord hors du cadre, il y entre à mesure que la caméra recule, et se referme sur le sol.
                float thin = Math.min(planet * 0.035F, 7.0F);
                float band = 1.5F;
                for (float y = 0.0F; y < VIEW_HEIGHT; y += band) {
                    float dy = y + band / 2.0F - siteY;
                    float inner = Math.abs(dy) < planet ? Mth.sqrt(planet * planet - dy * dy) : -1.0F;
                    if (inner >= siteX + 1.0F && inner >= width - siteX + 1.0F) {
                        continue;
                    }
                    float r1 = planet + thin;
                    float r2 = planet + 2.0F * thin;
                    float middle = Math.abs(dy) < r1 ? Mth.sqrt(r1 * r1 - dy * dy) : -1.0F;
                    float outer = Math.abs(dy) < r2 ? Mth.sqrt(r2 * r2 - dy * dy) : -1.0F;
                    int sky = mix(skyTop & 0xFFFFFF, skyLow & 0xFFFFFF, (y + band / 2.0F) / VIEW_HEIGHT);
                    int airOut = mix(sky & 0xFFFFFF, AIR_OUTER, 0.85F);
                    int airIn = 0xFF000000 | AIR_INNER;
                    if (outer < 0.0F) {
                        flat(out, width, 0, y, width, y + band, sky, sky, 3);
                        continue;
                    }
                    flat(out, width, 0, y, siteX - outer, y + band, sky, sky, 3);
                    flat(out, width, siteX + outer, y, width, y + band, sky, sky, 3);
                    if (middle < 0.0F) {
                        flat(out, width, siteX - outer, y, siteX + outer, y + band, airOut, airOut, 3);
                        continue;
                    }
                    flat(out, width, siteX - outer, y, siteX - middle, y + band, airOut, airOut, 3);
                    flat(out, width, siteX + middle, y, siteX + outer, y + band, airOut, airOut, 3);
                    if (inner < 0.0F) {
                        flat(out, width, siteX - middle, y, siteX + middle, y + band, airIn, airIn, 3);
                        continue;
                    }
                    flat(out, width, siteX - middle, y, siteX - inner, y + band, airIn, airIn, 3);
                    flat(out, width, siteX + inner, y, siteX + middle, y + band, airIn, airIn, 3);
                }
            }
        }

        // Les étoiles : dans le ciel seulement, jamais devant la planète.
        float stars = Mth.clamp((night - 0.25F) / 0.4F, 0.0F, 1.0F);
        if (stars > 0.0F) {
            float clear = earth ? planet + 2.0F * Math.min(planet * 0.035F, 7.0F) + 1.0F : 0.0F;
            for (int i = 0; i < 150; i++) {
                int spark = hash(i, 17, 3);
                float sx = (spark % 10_007) / 10_007.0F * width;
                float sy = (spark / 7 % 9_973) / 9_973.0F * VIEW_HEIGHT;
                if ((sx - siteX) * (sx - siteX) + (sy - siteY) * (sy - siteY) < clear * clear) {
                    continue;
                }
                float size = spark % 9 == 0 ? 1.3F : 0.8F;
                // Les plus brillantes scintillent à peine.
                float glow = stars * (0.5F + (spark >> 6 & 0x3F) / 126.0F) * (spark % 9 == 0 ? 0.85F + 0.15F * Mth.sin(time * 0.13F + i) : 1.0F);
                int tone = spark % 5 == 0 ? 0xBFD4FF : spark % 5 == 1 ? 0xFFE9C4 : 0xFFFFFF;
                int lit = mix(mixRgb(skyTop, skyLow, sy / VIEW_HEIGHT), tone, Mth.clamp(glow, 0.0F, 1.0F));
                flat(out, width, sx, sy, sx + size, sy + size, lit, lit, 4);
            }
        }

        // Le pas de tir : un losange rouge, au sol, tant qu'on est assez près pour le voir.
        if (zoom > 0.05F) {
            float padY = siteY - 2.0F * zoom;
            float ring = Math.max(3.0F, 10.0F * zoom);
            for (int k = 0; k < 24; k++) {
                float along = k / 24.0F * 4.0F;
                float side = along % 1.0F;
                int quarter = (int) along;
                float px = quarter == 0 ? side : quarter == 1 ? 1 - side : quarter == 2 ? -side : side - 1;
                float py = quarter == 0 ? side - 1 : quarter == 1 ? side : quarter == 2 ? 1 - side : -side;
                flat(out, width, siteX + px * ring - 0.6F, padY + py * ring * 0.5F - 0.6F, siteX + px * ring + 0.6F, padY + py * ring * 0.5F + 0.6F,
                        0xFFFF5A52, 0xFFFF5A52, 6);
            }
        }

        // La route suivie : son ombre au sol, en pointillés pâles, puis la route elle-même, d'un trait plein, du pas de
        // tir jusqu'à la fusée ; un fil la relie à son ombre.
        int count = trail.size();
        int step = Math.max(1, count / 300);
        List<float[]> shadow = new ArrayList<>();
        List<float[]> flown = new ArrayList<>();
        double[] from = {0.0, 0.0, 0.0};
        float shadowRun = 0.0F;
        if (flying) {
            for (int i = 0; i <= count; i += step) {
                // Le dernier tronçon va jusqu'à la fusée, là où elle est à cet instant.
                double[] to = i >= count ? new double[] {rocketX, rocketZ, rocketLift}
                        : new double[] {trail.get(i)[0], trail.get(i)[2], lift(trail.get(i)[1])};
                shadowRun = route(shadow, view, new double[] {from[0], from[1], 0.0}, new double[] {to[0], to[1], 0.0}, 1.5F, shadowRun);
                route(flown, view, from, to, 0.0F, 0.0F);
                from = to;
                if (i < count && i + step > count) {
                    i = count - step;
                }
            }
            route(shadow, view, new double[] {rocketX, rocketZ, 0.0}, new double[] {rocketX, rocketZ, rocketLift}, 1.0F, 0.0F);
        }
        paint(out, width, shadow, 0xB0DCE6F2, false);
        paint(out, width, flown, 0xFFFFC857, true);

        // La route prévue, devant la fusée : celle du vol calculé, en tirets, jusqu'à la mise en orbite ou au sommet de
        // la course. Un lanceur orbital décrit ensuite son orbite, en pointillés : une ellipse autour du globe, qui
        // passe derrière lui. Chaque séparation d'étage y est marquée : creuse tant qu'elle est à venir, pleine ensuite.
        double[] heading = null;
        if (charted) {
            double now0 = FlightProfile.simProgress(stats, progress);
            double end = flight.downrange(1.0) / 1000.0 * TRACK_PER_KM;
            double endLift = lift(flight.altitude(1.0) / 1000.0 * BLOCKS_PER_KM);
            double[] last = {drift.x + headX * end, drift.z + headZ * end, endLift};
            boolean orbital = FlightProfile.isOrbital(stats);
            List<float[]> ahead = new ArrayList<>();
            if (now0 < 1.0) {
                double[] prev = {rocketX, rocketZ, rocketLift};
                float run = 0.0F;
                for (int k = 1; k <= 48; k++) {
                    double[] next = along(flight, now0 + (1.0 - now0) * k / 48.0, drift, headX, headZ);
                    run = route(ahead, view, prev, next, 2.5F, run);
                    prev = next;
                }
            }
            paint(out, width, ahead, 0xFFE9F2FF, true);
            // L'orbite : un tour complet du globe à l'altitude de la mise en orbite ; de près, jusqu'à l'horizon.
            float headView = (float) Math.sqrt(Math.pow((viewX(turn, headX, headZ) - viewZ(turn, headX, headZ)) * 2.0, 2)
                    + Math.pow(viewX(turn, headX, headZ) + viewZ(turn, headX, headZ), 2));
            double around = PLANET_RADIUS * GROUND_SCALE / headView;
            double span = globe ? 2.0 * Math.PI * around * 0.985 : Math.max(0.0, around - end);
            if (orbital && span > 0.0) {
                List<float[]> orbit = new ArrayList<>();
                double[] prev = last;
                float run = 0.0F;
                for (int k = 1; k <= 160; k++) {
                    double d = end + span * k / 160.0;
                    double[] next = {drift.x + headX * d, drift.z + headZ * d, endLift};
                    run = route(orbit, view, prev, next, 1.0F, run);
                    prev = next;
                }
                paint(out, width, orbit, 0xFF7FD8FF, true);
            }
            float[] mark = new float[2];
            for (FlightSimulation.Event event : flight.events()) {
                if (event.type() != FlightSimulation.EventType.STAGING) {
                    continue;
                }
                double[] at = along(flight, event.time() / flight.duration(), drift, headX, headZ);
                if (view.place(mark, at[0], at[1], at[2])) {
                    boolean passed = event.time() / flight.duration() <= now0;
                    flat(out, width, mark[0] - 1.6F, mark[1] - 1.6F, mark[0] + 1.6F, mark[1] + 1.6F, 0xFF1B2430, 0xFF1B2430, 5.6F);
                    int fill = passed ? 0xFFFFFFFF : 0xFF5F6B7A;
                    flat(out, width, mark[0] - 0.9F, mark[1] - 0.9F, mark[0] + 0.9F, mark[1] + 0.9F, fill, fill, 5.7F);
                }
            }
            // Le bout de la route : la mise en orbite (bleu clair), ou le sommet de la course (blanc).
            if (view.place(mark, last[0], last[1], last[2])) {
                int tone = orbital ? 0xFF7FD8FF : 0xFFE9F2FF;
                for (int k = 0; k < 16; k++) {
                    float angle = k / 16.0F * Mth.TWO_PI;
                    float cx = mark[0] + Mth.cos(angle) * 3.2F;
                    float cy = mark[1] + Mth.sin(angle) * 3.2F;
                    flat(out, width, cx - 0.5F, cy - 0.5F, cx + 0.5F, cy + 0.5F, tone, tone, 5.7F);
                }
            }
            // Le sens de la marche : la pente de la trajectoire à cet instant ; en orbite, celle de l'orbite.
            double[] before = along(flight, Math.max(0.0, now0 - 0.004), drift, headX, headZ);
            double[] after = now0 < 0.996 ? along(flight, Math.min(1.0, now0 + 0.004), drift, headX, headZ)
                    : new double[] {last[0] + headX * 40.0, last[1] + headZ * 40.0, endLift};
            float[] a = new float[2];
            float[] b = new float[2];
            view.place(a, before[0], before[1], before[2]);
            view.place(b, after[0], after[1], after[2]);
            heading = new double[] {b[0] - a[0], b[1] - a[1]};
        }

        // La fumée, juste derrière la fusée : elle se clairseme et se fond dans la couleur de l'air.
        float[] puffAt = new float[2];
        for (int i = Math.max(0, count - 300); i < count; i++) {
            int age = count - 1 - i;
            float[] point = trail.get(i);
            if (age > 14 && i % 2 != 0 || !view.place(puffAt, point[0], point[2], lift(point[1]))) {
                continue;
            }
            int tone = age < 5 ? 0xFFFFF0B0 : age < 14 ? 0xFFFF9A3C : mix(0xEEF1F4, mixRgb(0xA9BCD2, 0x3A4A62, high), Math.min(1.0F, age / 300.0F));
            float puff = age < 14 ? 1.0F : Math.min(2.0F, 1.0F + age / 150.0F) * Math.max(0.5F, (float) Math.sqrt(zoom));
            flat(out, width, puffAt[0] - puff / 2, puffAt[1] - puff / 2, puffAt[0] + puff / 2, puffAt[1] + puff / 2, tone, tone, 6);
        }

        // La fusée, sur sa trajectoire, tournée dans le sens de sa marche : droite au décollage, puis couchée à mesure
        // que sa route s'incline.
        if (launchTable.hasRocket() || flying) {
            float[] at = new float[2];
            view.place(at, rocketX, rocketZ, flying ? rocketLift : 0.0);
            float dirX = 0.0F;
            float dirY = -1.0F;
            if (heading != null) {
                float length = (float) Math.sqrt(heading[0] * heading[0] + heading[1] * heading[1]);
                if (length > 0.05F) {
                    dirX = (float) heading[0] / length;
                    dirY = (float) heading[1] / length;
                }
            }
            float body = Math.max(8.0F, 15.0F * (float) Math.sqrt(zoom));
            float thick = zoom > 0.6F ? 2.4F : 1.8F;
            float baseX = at[0];
            float baseY = flying ? at[1] : at[1] - 2.0F;
            if (flying) {
                float flame = 4.0F + 2.0F * Mth.sin(time * 1.7F);
                for (float k = 0.5F; k < flame; k += 1.0F) {
                    float fx = baseX - dirX * k;
                    float fy = baseY - dirY * k;
                    int tone = k < 2.0F ? 0xFFFFF4C8 : 0xFFFF9A3C;
                    flat(out, width, fx - thick * 0.4F, fy - thick * 0.4F, fx + thick * 0.4F, fy + thick * 0.4F, tone, tone, 6);
                }
            }
            for (float k = 0.0F; k < body; k += 0.75F) {
                float bx = baseX + dirX * k;
                float by = baseY + dirY * k;
                int tone = k >= body - 2.0F ? 0xFFE0453A : Math.abs(k - body * 0.45F) < 0.8F ? 0xFF2B3440 : 0xFFF6F8FA;
                flat(out, width, bx - thick / 2, by - thick / 2, bx + thick / 2, by + thick / 2, tone, tone, 6);
            }
        }
    }

    /**
     * Un point de la trajectoire du vol calculé, à cet avancement du vol calculé (de 0 à 1) : sa place au sol, comptée
     * depuis le site en blocs, dans le cap donné, et sa hauteur dessinée.
     */
    private static double[] along(FlightSimulation flight, double simProgress, Vec3 drift, double headX, double headZ) {
        double d = flight.downrange(simProgress) / 1000.0 * TRACK_PER_KM;
        return new double[] {drift.x + headX * d, drift.z + headZ * d, lift(flight.altitude(simProgress) / 1000.0 * BLOCKS_PER_KM)};
    }

    /**
     * La prise de vue d'un instant, pour placer un point dans le cadre.
     *
     * @param siteX  place du site dans le cadre, centre du disque de la planète
     * @param globe  vrai quand la planète est un globe : le sol s'y enroule sur la sphère
     * @param planet rayon du disque de la planète, en unités de vue
     * @param round  vrai sur une planète ronde : la verticale y penche vers le bord, et ce qui est derrière est caché
     */
    private record View(int turn, float siteX, float siteY, float zoom, boolean globe, float planet, boolean round) {
        /**
         * Place dans le cadre un point compté depuis le site, en blocs, à cette hauteur dessinée. Faux s'il est caché :
         * derrière la planète, ou au-delà de son horizon et plus bas que lui.
         */
        boolean place(float[] out, double x, double z, double height) {
            double vx = viewX(turn, x, z);
            double vz = viewZ(turn, x, z);
            float across = (float) ((vx - vz) * 2.0 * zoom);
            float down = (float) ((vx + vz) * zoom);
            float reach = Mth.sqrt(across * across + down * down);
            float arc = round ? reach / planet : 0.0F;
            float baseX = across;
            float baseY = down;
            if (globe) {
                // Sur le globe, la carte s'enroule comme sa peinture : un point s'y rapproche du bord à mesure qu'il
                // s'éloigne du site, et passe derrière au-delà d'un quart de tour.
                float wrap = arc < 1.0E-4F ? 1.0F : Mth.sin(arc) / arc;
                baseX *= wrap;
                baseY *= wrap;
            }
            // La verticale : vers le haut du cadre au site, elle penche vers le bord de la planète en s'en éloignant.
            float upX = 0.0F;
            float upY = -1.0F;
            if (arc > 1.0E-4F) {
                upX = across / reach * Mth.sin(arc);
                upY = down / reach * Mth.sin(arc) - Mth.cos(arc);
            }
            float lift = (float) (height * 2.0 * zoom);
            out[0] = siteX + baseX + upX * lift;
            out[1] = siteY + baseY + upY * lift;
            if (!round || arc < (globe ? Mth.HALF_PI : 1.0F)) {
                return true;
            }
            float ox = out[0] - siteX;
            float oy = out[1] - siteY;
            return ox * ox + oy * oy > planet * planet;
        }
    }

    /**
     * Relève les points d'une route, d'un point à l'autre (place au sol comptée depuis le site en blocs, puis hauteur
     * dessinée) : des points serrés, d'un trait plein, ou des tirets de {@code dash} unités. Ce qui est caché par la
     * planète n'est pas relevé.
     *
     * @param run longueur déjà parcourue sur la route, pour que les tirets s'enchaînent d'un tronçon à l'autre
     * @return la longueur parcourue au bout du tronçon
     */
    private static float route(List<float[]> dots, View view, double[] from, double[] to, float dash, float run) {
        float[] at = new float[2];
        float[] last = new float[2];
        boolean seen = view.place(last, from[0], from[1], from[2]);
        view.place(at, to[0], to[1], to[2]);
        float length = Math.abs(at[0] - last[0]) + Math.abs(at[1] - last[1]);
        int steps = Math.clamp((int) Math.ceil(length / 0.8F), 1, 400);
        for (int k = 1; k <= steps; k++) {
            double t = k / (double) steps;
            boolean visible = view.place(at, from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t);
            if (visible && seen) {
                run += Mth.sqrt((at[0] - last[0]) * (at[0] - last[0]) + (at[1] - last[1]) * (at[1] - last[1]));
                if (dash <= 0.0F || run % (2.0F * dash) < dash) {
                    dots.add(new float[] {at[0], at[1]});
                }
            }
            seen = visible;
            last[0] = at[0];
            last[1] = at[1];
        }
        return run;
    }

    /** Dessine une route relevée : un liseré sombre sous tout le trait, s'il en faut un, puis le trait. */
    private void paint(List<Prim> out, float width, List<float[]> dots, int color, boolean edged) {
        if (edged) {
            // Le liseré se lit sur la mer comme sur la neige.
            for (float[] dot : dots) {
                flat(out, width, dot[0] - 0.95F, dot[1] - 0.95F, dot[0] + 0.95F, dot[1] + 0.95F, 0x80142030, 0x80142030, 5.4F);
            }
        }
        float size = edged ? 0.6F : 0.4F;
        for (float[] dot : dots) {
            flat(out, width, dot[0] - size, dot[1] - size, dot[0] + size, dot[1] + size, color, color, 5.5F);
        }
    }
}
