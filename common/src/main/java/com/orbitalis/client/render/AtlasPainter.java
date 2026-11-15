package com.orbitalis.client.render;

import com.orbitalis.core.atlas.AtlasTiles;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.util.Mth;

/**
 * Peinture des cartes du mod : le monde tel qu'il est, en cubes, vu de trois quarts, sous un éclairage soigné. Les
 * tuiles de l'atlas et les maquettes de la carte de navigation ({@link IsoMap}) passent toutes par ici : elles ont
 * le même rendu.
 * <ul>
 * <li><b>Fidèle.</b> Chaque case est un cube, à sa vraie hauteur et de sa vraie couleur. À l'échelle la plus fine,
 * un cube est un bloc : on reconnaît un bâtiment, un arbre, un chemin. Rien n'est arrondi ni fondu dans le relief.</li>
 * <li><b>Propre.</b> L'image est peinte deux fois plus grande, puis réduite : les arêtes sont nettes, sans escalier.
 * Aucun grain, aucune strie : un dessus de cube est un aplat, seulement modelé par la lumière.</li>
 * <li><b>Éclairé comme par un shader.</b> Le soleil vient du fond à gauche. Chaque cube porte une ombre douce sur ce
 * qui est derrière lui ; au pied d'un mur, une ombre de contact ; sur l'arête qui regarde le soleil, un liseré de
 * lumière ; en haut des flancs, une lèvre claire. Les deux flancs n'ont pas la même teinte, et s'assombrissent
 * vers le pied.</li>
 * <li><b>Brillant.</b> Un reflet sur les sommets, et sur l'eau de longues moirures, avec une frange claire le long
 * des rives.</li>
 * </ul>
 * Pour l'atlas, le serveur envoie chaque tuile avec une marge de cases autour d'elle ({@code AtlasTiles.MARGIN}) :
 * ombres et arêtes se raccordent ainsi d'une tuile à l'autre. L'image, elle, déborde d'une case sur ses voisines
 * ({@link #APRON}) : elles se recouvrent au lieu de se toucher.
 */
public final class AtlasPainter {
    /** Débord de l'image d'une tuile sur ses voisines, en cases. */
    public static final int APRON = 1;
    /** Cases dessinées, de côté, et cases reçues. */
    private static final int N = AtlasTiles.TILE + 2 * APRON;
    private static final int G = AtlasTiles.SIDE;
    private static final int FIRST = AtlasTiles.MARGIN - APRON;
    /**
     * Relief, pour chaque échelle : hauteur d'un cran pour tant de blocs. Un cran est toujours haut comme sa case est
     * large : les cubes sont de vrais cubes, et le relief garde ses proportions. Vu de près, une colline se dresse ;
     * vue de l'espace, une montagne n'est plus qu'un ressaut. Rien n'est exagéré.
     */
    public static final float[] RELIEF = {1.0F, 2.0F, 4.0F, 8.0F, 16.0F, 32.0F, 64.0F, 128.0F, 256.0F};
    /** Dénivelé qu'une image de tuile peut montrer, en crans : au-delà, les sommets sont écrêtés. */
    private static final int RANGE = 64;

    /** L'image d'une tuile : une case y fait {@link #CELL} pixels de large, un cran de relief {@link #STEP} pixels de haut. */
    public static final int CELL = 8;
    public static final int STEP = CELL / 2;
    public static final int WIDTH = N * CELL;
    /** Hauteur, dans l'image, du point le plus bas de la tuile au coin du fond (débord compris). */
    public static final int HEAD = RANGE * STEP + 4;
    public static final int HEIGHT = HEAD + N * CELL / 2 + 20;

    public static final byte WATER = 1;
    public static final byte GRASS = 2;
    public static final byte LEAVES = 3;
    public static final byte SOLID = 4;

    /** Couleurs de la palette : l'herbe, le feuillage, et l'eau de la rive vers le large. */
    public static final int GRASS_COLOR = 0x7FBF4E;
    public static final int LEAVES_COLOR = 0x2F9440;
    public static final int[] WATER_COLORS = {0x5CB8F2, 0x3F9BE6, 0x2F82D8};

    /**
     * Une tuile peinte.
     *
     * @param pixels les pixels (0xAARRGGBB), ligne par ligne : {@link #WIDTH} de large, {@link #HEIGHT} de haut
     * @param base   hauteur, en crans au-dessus du niveau de la mer, du point le plus bas de la tuile : c'est celle
     *               de la ligne {@link #HEAD} de l'image
     */
    public record Painted(int[] pixels, int base) {
    }

    /**
     * Ce qu'il y a à peindre : une grille carrée de cases, dont on dessine un carré.
     *
     * @param grid     côté de la grille, en cases
     * @param first    première case dessinée, dans chaque sens ; celles d'avant ne servent qu'aux voisinages
     * @param count    nombre de cases dessinées, de côté
     * @param rise     hauteur de chaque case, en crans
     * @param color    couleur de chaque case
     * @param kind     nature de chaque case ({@link #WATER}, {@link #GRASS}, {@link #LEAVES}, {@link #SOLID}) ; 0 : rien
     * @param cell     largeur d'une case dans l'image, en pixels (un cran de relief en fait la moitié)
     * @param width    largeur de l'image, en pixels
     * @param height   hauteur de l'image
     * @param head     ligne de l'image où se trouve la hauteur {@code base}, pour la case du fond
     * @param base     hauteur la plus basse montrée, en crans
     * @param range    dénivelé montré au-dessus d'elle : plus haut, les cubes sont écrêtés
     * @param edgeDrop hauteur du flanc au bord de la grille, en crans
     * @param fray     vrai pour effilocher les bords : une maquette posée sur une autre n'y montre pas de coupure
     * @param waves    taille des moirures de l'eau : nombre de blocs que vaut une case, divisé par quatre
     * @param originX  place de la première case de la grille dans le monde, en cases : les moirures s'y repèrent
     * @param style    dessin propre à certaines cases ({@link #CONCRETE}, {@link #PAD_EDGE}, {@link #TRENCH}) ; 0 : aucun
     */
    public record Scene(int grid, int first, int count, int[] rise, int[] color, byte[] kind, int cell, int width, int height, int head,
            int base, int range, int edgeDrop, boolean fray, float waves, int originX, int originZ, byte[] style) {
    }

    /** Dessins des dalles d'un pas de tir : le béton et ses joints, le pourtour bordé de rouge, le carneau rayé. */
    public static final byte CONCRETE = 1;
    public static final byte PAD_EDGE = 2;
    public static final byte TRENCH = 3;
    private static final int PAD_RED = 0xD8403A;

    private AtlasPainter() {
    }

    // ------------------------------------------------------------------
    // Couleurs
    // ------------------------------------------------------------------

    private static int hash(int a, int b, int c) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1 ^ c * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }

    /** Bruit doux, de 0 à 1, continu d'une tuile à l'autre. */
    private static float noise(float x, float z) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        float fx = x - x0;
        float fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = (hash(x0, z0, 77) & 0xFFFF) / 65535.0F;
        float b = (hash(x0 + 1, z0, 77) & 0xFFFF) / 65535.0F;
        float c = (hash(x0, z0 + 1, 77) & 0xFFFF) / 65535.0F;
        float d = (hash(x0 + 1, z0 + 1, 77) & 0xFFFF) / 65535.0F;
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static int shade(int rgb, float factor) {
        int r = Math.clamp((int) ((rgb >> 16 & 0xFF) * factor), 0, 255);
        int g = Math.clamp((int) ((rgb >> 8 & 0xFF) * factor), 0, 255);
        int b = Math.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    /** La palette des cartes : les couleurs d'un relevé, reprises en plus franc et plus lumineux. */
    public static int tone(int rgb) {
        if (rgb == LaunchTableBlockEntity.FAR_GRASS) {
            return GRASS_COLOR;
        }
        if (rgb == LaunchTableBlockEntity.FAR_FOREST || rgb == AtlasTiles.LEAVES) {
            return LEAVES_COLOR;
        }
        if (rgb == AtlasTiles.PAD_INNER || rgb == AtlasTiles.PAD_BORDER) {
            return 0xC4C8CE;
        }
        if (rgb == AtlasTiles.PAD_TRENCH) {
            return 0x4A4F58;
        }
        if (rgb == LaunchTableBlockEntity.FAR_SAVANNA) {
            return 0xBDBB62;
        }
        if (rgb == LaunchTableBlockEntity.FAR_SAND) {
            return 0xEDDDA6;
        }
        if (rgb == LaunchTableBlockEntity.FAR_STONE) {
            return 0x8E949E;
        }
        if (rgb == LaunchTableBlockEntity.FAR_SNOW) {
            return 0xF5F8FB;
        }
        if (rgb == LaunchTableBlockEntity.FAR_BADLANDS) {
            return 0xD6853F;
        }
        if (rgb == LaunchTableBlockEntity.FAR_SHALLOW) {
            return WATER_COLORS[0];
        }
        if (rgb == LaunchTableBlockEntity.FAR_SEA) {
            return WATER_COLORS[1];
        }
        if (rgb == LaunchTableBlockEntity.FAR_DEEP) {
            return WATER_COLORS[2];
        }
        return rgb;
    }

    /** Couleur d'un flanc, à cette profondeur sous le dessus (en huitièmes de case) : sous l'herbe, la terre, puis la roche. */
    private static int flank(byte kind, int base, float depth) {
        if (kind == GRASS) {
            int earth = mix(0x9A7650, 0x868B94, (depth - 40) / 80.0F);
            return mix(shade(base, 0.92F), earth, (depth - 3) / 5.0F);
        }
        return kind == LEAVES ? shade(base, 0.84F) : base;
    }

    // ------------------------------------------------------------------
    // Tuiles de l'atlas
    // ------------------------------------------------------------------

    /**
     * Peint une tuile de l'atlas.
     *
     * @param cells les cases reçues du serveur, marge comprise (voir {@code AtlasTiles})
     * @param turn  orientation de la vue, de 0 à 3 : le coin du monde qui est au fond (nord-ouest, nord-est,
     *              sud-est, sud-ouest)
     */
    public static Painted paint(int lod, int tx, int tz, int[] cells, int turn) {
        int inner = AtlasTiles.TILE;
        int[] rise = new int[G * G];
        int[] color = new int[G * G];
        byte[] kind = new byte[G * G];
        byte[] style = new byte[G * G];
        for (int vj = 0; vj < G; vj++) {
            for (int vi = 0; vi < G; vi++) {
                // La case du monde qui se trouve à cette place de la vue.
                int i = turn == 0 ? vi : turn == 1 ? G - 1 - vj : turn == 2 ? G - 1 - vi : vj;
                int j = turn == 0 ? vj : turn == 1 ? vi : turn == 2 ? G - 1 - vj : G - 1 - vi;
                int packed = cells[j * G + i];
                if (packed == 0) {
                    continue;
                }
                int index = vj * G + vi;
                int rgb = packed & 0xFFFFFF;
                int blocks = (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS;
                rise[index] = Math.round(blocks / RELIEF[lod]);
                kind[index] = LaunchTableBlockEntity.isFarWater(rgb) ? WATER : rgb == LaunchTableBlockEntity.FAR_GRASS ? GRASS
                        : rgb == AtlasTiles.LEAVES || rgb == LaunchTableBlockEntity.FAR_FOREST ? LEAVES : SOLID;
                color[index] = tone(rgb);
                style[index] = rgb == AtlasTiles.PAD_INNER ? CONCRETE : rgb == AtlasTiles.PAD_BORDER ? PAD_EDGE : rgb == AtlasTiles.PAD_TRENCH ? TRENCH : 0;
                if (rgb == LaunchTableBlockEntity.FAR_FOREST) {
                    // Une forêt où personne n'est encore allé : le générateur n'en donne que le sol. Elle est montrée
                    // comme une canopée, à la hauteur des arbres, sans inventer chacun d'eux.
                    rise[index] += Math.round(5.0F / RELIEF[lod]);
                }
            }
        }
        // Le point le plus bas de ce qui est dessiné : l'image part de là.
        int base = Integer.MAX_VALUE;
        for (int j = FIRST; j < FIRST + N; j++) {
            for (int i = FIRST; i < FIRST + N; i++) {
                if (kind[j * G + i] != 0) {
                    base = Math.min(base, rise[j * G + i]);
                }
            }
        }
        if (base == Integer.MAX_VALUE) {
            base = 0;
        }
        // Place de la grille dans la vue, en cases : les moirures de l'eau se suivent d'une tuile à l'autre.
        int worldX = tx * inner - AtlasTiles.MARGIN;
        int worldZ = tz * inner - AtlasTiles.MARGIN;
        int originX = turn == 0 ? worldX : turn == 1 ? worldZ : turn == 2 ? -(worldX + G - 1) : -(worldZ + G - 1);
        int originZ = turn == 0 ? worldZ : turn == 1 ? -(worldX + G - 1) : turn == 2 ? -(worldZ + G - 1) : worldX;
        return new Painted(render(new Scene(G, FIRST, N, rise, color, kind, CELL, WIDTH, HEIGHT, HEAD, base, RANGE, 1, false,
                AtlasTiles.CELL[lod] / 4.0F, originX, originZ, style)), base);
    }

    // ------------------------------------------------------------------
    // Peinture
    // ------------------------------------------------------------------

    /** Peint une scène : les pixels (0xAARRGGBB), ligne par ligne. */
    public static int[] render(Scene scene) {
        int g = scene.grid;
        int c = scene.cell;
        int[] rise = scene.rise;
        byte[] kind = scene.kind;
        // 1. Couleur de chaque case, à peine fondue dans celle de ses voisines de même nature (l'eau, elle, l'est
        // tout à fait), et sa lumière : la pente l'éclaire ou l'assombrit, et ce qui est plus haut au fond à gauche
        // lui porte une ombre, douce sur ses bords.
        int[] tint = new int[g * g];
        float[] light = new float[g * g];
        for (int j = 0; j < g; j++) {
            for (int i = 0; i < g; i++) {
                int index = j * g + i;
                if (kind[index] == 0) {
                    continue;
                }
                int reach = kind[index] == WATER ? 2 : 1;
                int r = 0;
                int gr = 0;
                int b = 0;
                int count = 0;
                for (int dj = -reach; dj <= reach; dj++) {
                    for (int di = -reach; di <= reach; di++) {
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni < 0 || nj < 0 || ni >= g || nj >= g || kind[nj * g + ni] != kind[index]) {
                            continue;
                        }
                        int other = scene.color[nj * g + ni];
                        r += other >> 16 & 0xFF;
                        gr += other >> 8 & 0xFF;
                        b += other & 0xFF;
                        count++;
                    }
                }
                tint[index] = mix(scene.color[index], (r / count) << 16 | (gr / count) << 8 | b / count, kind[index] == WATER ? 1.0F : 0.25F);
                if (kind[index] == WATER) {
                    light[index] = 1.0F;
                    continue;
                }
                int h = rise[index];
                int north = j > 0 && kind[index - g] != 0 ? rise[index - g] : h;
                int west = i > 0 && kind[index - 1] != 0 ? rise[index - 1] : h;
                float slope = 1.0F + Math.clamp(2 * h - north - west, -4, 4) * 0.02F;
                float shadow = 0.0F;
                for (int k = 1; k <= 8; k++) {
                    if (i - k >= 0 && j - k >= 0 && kind[(j - k) * g + i - k] != 0) {
                        shadow = Math.max(shadow, Mth.clamp((rise[(j - k) * g + i - k] - h - (k - 1)) / 1.5F, 0.0F, 1.0F));
                    }
                }
                light[index] = slope * (1.0F - 0.30F * shadow);
            }
        }

        // 2. Peinture, deux fois plus grande que l'image, du fond vers l'avant. Dans cette peinture, une case fait
        // deux fois « c » de large, « c » de haut, et un cran de relief « c ».
        int wide = scene.width * 2;
        int tall = scene.height * 2;
        int[] big = new int[wide * tall];
        int first = scene.first;
        int count = scene.count;
        for (int j = first; j < first + count; j++) {
            for (int i = first; i < first + count; i++) {
                int index = j * g + i;
                byte what = kind[index];
                if (what == 0) {
                    continue;
                }
                if (scene.fray) {
                    int edge = Math.min(Math.min(i - first, j - first), Math.min(first + count - 1 - i, first + count - 1 - j));
                    if (edge < 14 && hash(i, j, 9) % 14 >= edge) {
                        continue;
                    }
                }
                int h = rise[index];
                int hN = j > 0 && kind[index - g] != 0 ? rise[index - g] : h;
                int hW = i > 0 && kind[index - 1] != 0 ? rise[index - 1] : h;
                int hS = j < g - 1 && kind[index + g] != 0 ? rise[index + g] : h - scene.edgeDrop;
                int hE = i < g - 1 && kind[index + 1] != 0 ? rise[index + 1] : h - scene.edgeDrop;
                int hNW = i > 0 && j > 0 && kind[index - g - 1] != 0 ? rise[index - g - 1] : h;
                int level = Math.clamp(h - scene.base, 0, scene.range);
                int cx = ((i - first) - (j - first)) * c + count * c;
                int cy = ((i - first) + (j - first)) * (c / 2) + scene.head * 2 - level * c;
                int own = tint[index];
                float lit = light[index];
                boolean summit = what != WATER && (hN < h || hW < h);
                // Le dessus : un losange.
                for (int py = 0; py < c; py++) {
                    for (int px = -c; px < c; px++) {
                        float fx = px + 0.5F;
                        float fy = py + 0.5F - c / 2.0F;
                        if (Math.abs(fx) / c + Math.abs(fy) / (c / 2.0F) > 1.0F) {
                            continue;
                        }
                        // Place du pixel dans la case, de -0,5 à 0,5 : vers la droite du fond, puis vers la gauche de devant.
                        float u = fx / (c * 2.0F) + fy / c;
                        float v = fy / c - fx / (c * 2.0F);
                        int pixel;
                        if (what == WATER) {
                            float wx = (scene.originX + i + u + 0.5F) * scene.waves;
                            float wz = (scene.originZ + j + v + 0.5F) * scene.waves;
                            // De longues moirures, et un reflet de loin en loin.
                            float wave = noise(wx * 0.33F, wz * 0.33F) * 0.5F + noise(wx * 0.11F, wz * 0.11F) * 0.5F;
                            pixel = shade(own, 0.95F + 0.10F * wave);
                            float glint = (float) Math.pow(Math.max(0.0, Math.sin(wx * 0.9 + wz * 0.5 + wave * 7.0)), 14.0);
                            pixel = mix(pixel, 0xFFFFFF, glint * 0.14F);
                            // Une frange claire le long des rives, et l'ombre d'une berge plus haute au fond.
                            float shore = 0.0F;
                            if (i > 0 && kind[index - 1] > WATER) {
                                shore = Math.max(shore, 1.0F - (u + 0.5F) / 0.38F);
                            }
                            if (j > 0 && kind[index - g] > WATER) {
                                shore = Math.max(shore, 1.0F - (v + 0.5F) / 0.38F);
                            }
                            if (i < g - 1 && kind[index + 1] > WATER) {
                                shore = Math.max(shore, 1.0F - (0.5F - u) / 0.38F);
                            }
                            if (j < g - 1 && kind[index + g] > WATER) {
                                shore = Math.max(shore, 1.0F - (0.5F - v) / 0.38F);
                            }
                            pixel = mix(pixel, 0xD8F1FF, Math.max(0.0F, shore) * 0.45F);
                            if (hNW > h + 1) {
                                pixel = shade(pixel, 0.86F);
                            }
                        } else {
                            float tone = lit;
                            // Au pied d'un cube plus haut, une ombre de contact ; au bord d'un vide, la lumière accroche
                            // l'arête du côté du soleil, et l'autre côté s'assombrit à peine.
                            if (hW > h) {
                                tone *= Mth.lerp(Mth.clamp((u + 0.5F) / 0.50F, 0.0F, 1.0F), 0.74F, 1.0F);
                            } else if (hW < h) {
                                tone *= Mth.lerp(Mth.clamp((u + 0.5F) / 0.20F, 0.0F, 1.0F), 1.12F, 1.0F);
                            }
                            if (hN > h) {
                                tone *= Mth.lerp(Mth.clamp((v + 0.5F) / 0.50F, 0.0F, 1.0F), 0.74F, 1.0F);
                            } else if (hN < h) {
                                tone *= Mth.lerp(Mth.clamp((v + 0.5F) / 0.20F, 0.0F, 1.0F), 1.12F, 1.0F);
                            }
                            if (hW <= h && hN <= h && hNW > h) {
                                // Un coin rentrant : l'ombre s'y loge.
                                tone *= Mth.lerp(Mth.clamp((u + v + 1.0F) / 0.5F, 0.0F, 1.0F), 0.80F, 1.0F);
                            }
                            if (hE < h) {
                                tone *= Mth.lerp(Mth.clamp((0.5F - u) / 0.20F, 0.0F, 1.0F), 0.92F, 1.0F);
                            }
                            if (hS < h) {
                                tone *= Mth.lerp(Mth.clamp((0.5F - v) / 0.20F, 0.0F, 1.0F), 0.92F, 1.0F);
                            }
                            pixel = shade(own, tone);
                            byte look = scene.style == null ? 0 : scene.style[index];
                            if (look != 0) {
                                // Une dalle de pas de tir, telle qu'on la voit de près : ses joints, le filet rouge
                                // de son pourtour du côté du dehors, les rayures jaunes du carneau.
                                boolean fine = scene.waves <= 0.3F;
                                if (fine && Math.max(Math.abs(u), Math.abs(v)) > 0.44F) {
                                    pixel = shade(pixel, 0.88F);
                                }
                                if (look == TRENCH && fine && Mth.floor((u - v + 2.0F) * 3.0F) % 2 == 0 && Math.max(Math.abs(u), Math.abs(v)) < 0.40F) {
                                    pixel = shade(0xE8B93A, tone);
                                }
                                if (look == PAD_EDGE) {
                                    boolean west = i > 0 && scene.style[index - 1] == 0 && u < -0.24F;
                                    boolean north = j > 0 && scene.style[index - g] == 0 && v < -0.24F;
                                    boolean east = i < g - 1 && scene.style[index + 1] == 0 && u > 0.24F;
                                    boolean south = j < g - 1 && scene.style[index + g] == 0 && v > 0.24F;
                                    if (west || north || east || south) {
                                        pixel = shade(PAD_RED, Math.min(1.05F, tone));
                                    }
                                }
                            }
                            if (summit) {
                                // Le reflet d'un sommet.
                                float sheen = Mth.clamp(1.0F - (Math.abs(u + 0.14F) + Math.abs(v + 0.14F)) / 0.55F, 0.0F, 1.0F);
                                pixel = mix(pixel, 0xFFFFFF, sheen * sheen * 0.09F);
                            }
                        }
                        plot(big, wide, tall, cx + px, cy + py, pixel);
                    }
                }
                if (what == WATER) {
                    continue;
                }
                // Les flancs : celui de gauche plus clair que celui de droite, tous deux plus sombres vers le pied,
                // avec une lèvre claire en haut.
                int dropLeft = Math.min(level + scene.edgeDrop, Math.max(0, h - hS)) * c;
                int dropRight = Math.min(level + scene.edgeDrop, Math.max(0, h - hE)) * c;
                float side = Math.min(1.0F, lit + 0.1F);
                // Profondeur comptée en huitièmes de case : les couleurs d'un flanc ne dépendent pas de la taille de l'image.
                float grain = 8.0F / c;
                for (int xo = -c; xo < 0; xo++) {
                    int start = cy + c / 2 + (xo + c + 1) / 2;
                    for (int d = 0; d < dropLeft; d++) {
                        float depth = d * grain;
                        float tone = 0.82F * (1.0F - 0.22F * Math.min(1.0F, depth / 60.0F)) * (depth < 2.0F ? 1.10F : 1.0F);
                        plot(big, wide, tall, cx + xo, start + d, shade(flank(what, own, depth), tone * side));
                    }
                }
                for (int xo = 0; xo < c; xo++) {
                    int start = cy + c / 2 + (c - xo) / 2;
                    for (int d = 0; d < dropRight; d++) {
                        float depth = d * grain;
                        float tone = 0.64F * (1.0F - 0.22F * Math.min(1.0F, depth / 60.0F)) * (depth < 2.0F ? 1.08F : 1.0F);
                        plot(big, wide, tall, cx + xo, start + d, shade(flank(what, own, depth), tone * side));
                    }
                }
            }
        }

        // 3. Réduction de moitié : chaque pixel de l'image est la moyenne de quatre pixels peints.
        int width = scene.width;
        int height = scene.height;
        int[] image = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int gr = 0;
                int b = 0;
                for (int k = 0; k < 4; k++) {
                    int argb = big[(y * 2 + (k >> 1)) * wide + x * 2 + (k & 1)];
                    if (argb != 0) {
                        a++;
                        r += argb >> 16 & 0xFF;
                        gr += argb >> 8 & 0xFF;
                        b += argb & 0xFF;
                    }
                }
                image[y * width + x] = a == 0 ? 0 : (a * 255 / 4) << 24 | (r / a) << 16 | (gr / a) << 8 | b / a;
            }
        }
        // L'image est lissée à l'affichage : un pixel vide voisin d'un pixel peint lui prête sa couleur (en restant
        // transparent), sans quoi le bord de l'image tirerait vers le noir.
        for (int pass = 0; pass < 2; pass++) {
            int[] before = image.clone();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int at = y * width + x;
                    if (before[at] != 0) {
                        continue;
                    }
                    int near = x > 0 && before[at - 1] != 0 ? before[at - 1] : x < width - 1 && before[at + 1] != 0 ? before[at + 1]
                            : y > 0 && before[at - width] != 0 ? before[at - width] : y < height - 1 && before[at + width] != 0 ? before[at + width] : 0;
                    // Un rien d'opacité le distingue d'un pixel vide, pour la passe suivante ; il reste invisible.
                    image[at] = near == 0 ? 0 : near & 0xFFFFFF | 0x01000000;
                }
            }
        }
        return image;
    }

    private static void plot(int[] image, int wide, int tall, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < wide && y < tall) {
            image[y * wide + x] = 0xFF000000 | rgb;
        }
    }
}
