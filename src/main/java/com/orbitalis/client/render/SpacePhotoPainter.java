package com.orbitalis.client.render;

/**
 * Le peintre des images de l'observatoire : une capture de chaque astre, comme si on l'avait vu dans le jeu, avec des
 * shaders. Tout est fait de blocs.
 * <ul>
 * <li>Les planètes et les lunes sont des sphères de cubes, avec leur relief, chaque face texturée de quelques pixels,
 * l'ombre douce des coins comme dans le jeu, et les vraies ombres que projette le soleil (un rayon par pixel).</li>
 * <li>Le ciel profond est fait de nuages de blocs : des blocs lumineux et translucides pour le gaz, des blocs sombres
 * pour la poussière. Les soleils sont des cubes qui brillent, les étoiles des pixels carrés.</li>
 * <li>Le développement est celui d'un shader : un halo autour de ce qui brille, une exposition douce, un léger
 * vignettage.</li>
 * </ul>
 * La scène est une grille de blocs, regardée de trois quarts ; chaque pixel lance un rayon qui la traverse, bloc par
 * bloc. Le même astre donne toujours la même image. Aucune dépendance au jeu : la classe se lance seule
 * ({@link #main}) pour écrire toutes les images dans un dossier.
 */
public final class SpacePhotoPainter {
    /** Taille d'une image, en pixels : un format de capture, seize neuvièmes. */
    public static final int WIDTH = 384;
    public static final int HEIGHT = 216;

    /** Natures d'un bloc (le vide vaut 0) : solide, solide lumineux, gaz lumineux, poussière. */
    private static final byte SOLID = 1;
    private static final byte LAMP = 2;
    private static final byte GLOW = 3;
    private static final byte DUST = 4;
    /** Pixels de texture par face de bloc, de chaque côté. */
    private static final int TEXELS = 2;

    private final int seed;
    private final float[] r = new float[WIDTH * HEIGHT];
    private final float[] g = new float[WIDTH * HEIGHT];
    private final float[] b = new float[WIDTH * HEIGHT];

    private SpacePhotoPainter(int seed) {
        this.seed = seed;
    }

    /** L'image d'un astre, en ARGB, rangée par ligne. */
    public static int[] paint(String body) {
        SpacePhotoPainter painter = new SpacePhotoPainter(body.hashCode());
        float exposure = painter.compose(body);
        return painter.develop(exposure);
    }

    // ------------------------------------------------------------------
    // Bruit
    // ------------------------------------------------------------------

    private static float hash(int x, int y, int z, int seed) {
        int h = x * 374_761_393 + y * 668_265_263 + z * 1_274_126_177 + seed * 144_665_237;
        h = (h ^ (h >>> 13)) * 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16_777_216.0F;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static float smoothstep(float lo, float hi, float v) {
        return smooth(clamp((v - lo) / (hi - lo), 0.0F, 1.0F));
    }

    /** Bruit de valeur, de 0 à 1, continu dans l'espace. */
    private float noise(float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float fx = smooth(x - x0);
        float fy = smooth(y - y0);
        float fz = smooth(z - z0);
        float a = lerp(fx, hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed));
        float c = lerp(fx, hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed));
        float e = lerp(fx, hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed));
        float f = lerp(fx, hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed));
        return lerp(fz, lerp(fy, a, c), lerp(fy, e, f));
    }

    /** Bruit fractal, de 0 à 1 environ. */
    private float fbm(float x, float y, float z, int octaves) {
        float sum = 0.0F;
        float amplitude = 0.5F;
        float total = 0.0F;
        for (int k = 0; k < octaves; k++) {
            sum += noise(x, y, z) * amplitude;
            total += amplitude;
            amplitude *= 0.5F;
            x = x * 2.03F + 17.1F;
            y = y * 2.03F - 9.7F;
            z = z * 2.03F + 3.3F;
        }
        return sum / total;
    }

    /** Bruit en crêtes : de fins filaments. */
    private float ridged(float x, float y, float z, int octaves) {
        float sum = 0.0F;
        float amplitude = 0.5F;
        float total = 0.0F;
        for (int k = 0; k < octaves; k++) {
            float n = 1.0F - Math.abs(noise(x, y, z) * 2.0F - 1.0F);
            sum += n * n * amplitude;
            total += amplitude;
            amplitude *= 0.5F;
            x = x * 2.1F + 5.3F;
            y = y * 2.1F + 1.7F;
            z = z * 2.1F - 7.1F;
        }
        return sum / total;
    }

    private static int rgb(float cr, float cg, float cb) {
        return Math.clamp(Math.round(cr * 255.0F), 0, 255) << 16 | Math.clamp(Math.round(cg * 255.0F), 0, 255) << 8
                | Math.clamp(Math.round(cb * 255.0F), 0, 255);
    }

    private static int mix(int a, int c, float t) {
        t = clamp(t, 0.0F, 1.0F);
        return rgb(lerp(t, (a >> 16 & 0xFF) / 255.0F, (c >> 16 & 0xFF) / 255.0F), lerp(t, (a >> 8 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F),
                lerp(t, (a & 0xFF) / 255.0F, (c & 0xFF) / 255.0F));
    }

    // ------------------------------------------------------------------
    // La grille de blocs
    // ------------------------------------------------------------------

    /** Une scène de blocs : pour chaque bloc, sa nature, sa couleur, et sa puissance (lumière ou densité). */
    private static final class Grid {
        final int nx;
        final int ny;
        final int nz;
        final byte[] kind;
        final int[] color;
        final float[] power;
        /**
         * Le centre de la sphère, pour une planète : le jour et la nuit y suivent sa forme ronde, et chaque face n'y
         * ajoute que son relief ; {@code null} pour une scène quelconque.
         */
        float[] sphere;

        Grid(int nx, int ny, int nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            kind = new byte[nx * ny * nz];
            color = new int[nx * ny * nz];
            power = new float[nx * ny * nz];
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz;
        }

        int index(int x, int y, int z) {
            return (z * ny + y) * nx + x;
        }

        void set(int x, int y, int z, byte what, int rgb, float strength) {
            if (inside(x, y, z)) {
                int i = index(x, y, z);
                kind[i] = what;
                color[i] = rgb;
                power[i] = strength;
            }
        }

        boolean solid(int x, int y, int z) {
            if (!inside(x, y, z)) {
                return false;
            }
            byte k = kind[index(x, y, z)];
            return k == SOLID || k == LAMP;
        }
    }

    /** La caméra : de trois quarts, en projection orthographique, comme un plan large du jeu. */
    private static final class Camera {
        final float[] right;
        final float[] up;
        final float[] forward;
        /** Pixels par bloc, et place du centre de la grille dans l'image. */
        final float scale;
        final float cx;
        final float cy;

        Camera(float yaw, float pitch, float scale, float cx, float cy) {
            // On tourne d'abord autour de l'axe vertical, puis on plonge vers la scène.
            float cy0 = (float) Math.cos(yaw);
            float sy0 = (float) Math.sin(yaw);
            float cp = (float) Math.cos(pitch);
            float sp = (float) Math.sin(pitch);
            right = new float[] {cy0, 0.0F, -sy0};
            up = new float[] {-sy0 * sp, cp, -cy0 * sp};
            forward = new float[] {-sy0 * cp, -sp, -cy0 * cp};
            this.scale = scale;
            this.cx = cx;
            this.cy = cy;
        }
    }

    /**
     * Une lumière : sa direction (vers elle) par rapport à la vue (vers la droite, vers le haut, vers nous), sa couleur, et
     * la lumière ambiante des faces à l'ombre.
     */
    private record Light(float[] direction, float[] color, float[] ambient) {
    }

    private static float[] normalize(float x, float y, float z) {
        float n = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / n, y / n, z / n};
    }

    /** Le soleil, venant de la gauche et d'un peu au-dessus, d'un blanc chaud ; les ombres, à peine bleutées. */
    private static final Light SUN = new Light(normalize(-0.75F, 0.45F, 0.5F), new float[] {1.12F, 1.04F, 0.92F}, new float[] {0.025F, 0.03F, 0.05F});

    /**
     * Rend la grille par-dessus ce qui est déjà peint : chaque pixel lance un rayon qui la traverse bloc par bloc. Le
     * gaz lumineux s'ajoute et voile un peu ce qui est derrière, la poussière l'assombrit, le premier bloc solide
     * arrête le rayon et prend la lumière du soleil (s'il n'est pas à l'ombre d'un autre).
     */
    private void render(Grid grid, Camera camera, Light light) {
        float mx = grid.nx / 2.0F;
        float my = grid.ny / 2.0F;
        float mz = grid.nz / 2.0F;
        float far = grid.nx + grid.ny + grid.nz;
        float[] d = camera.forward;
        // La lumière, donnée par rapport à la vue, ramenée dans la scène.
        float[] v0 = light.direction();
        float[] world = normalize(camera.right[0] * v0[0] + camera.up[0] * v0[1] - d[0] * v0[2],
                camera.right[1] * v0[0] + camera.up[1] * v0[1] - d[1] * v0[2], camera.right[2] * v0[0] + camera.up[2] * v0[1] - d[2] * v0[2]);
        light = new Light(world, light.color(), light.ambient());
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                float u = (px + 0.5F - camera.cx) / camera.scale;
                float v = -(py + 0.5F - camera.cy) / camera.scale;
                float ox = mx + camera.right[0] * u + camera.up[0] * v - d[0] * far;
                float oy = my + camera.right[1] * u + camera.up[1] * v - d[1] * far;
                float oz = mz + camera.right[2] * u + camera.up[2] * v - d[2] * far;
                int i = py * WIDTH + px;
                float[] hit = trace(grid, ox, oy, oz, d, light);
                if (hit == null) {
                    continue;
                }
                r[i] = hit[0] + r[i] * hit[3];
                g[i] = hit[1] + g[i] * hit[3];
                b[i] = hit[2] + b[i] * hit[3];
            }
        }
    }

    /**
     * Un rayon à travers la grille. Rend la lumière recueillie et la part du fond qui passe encore (rouge, vert, bleu,
     * transmission), ou {@code null} s'il ne la touche pas.
     */
    private float[] trace(Grid grid, float ox, float oy, float oz, float[] d, Light light) {
        // L'entrée et la sortie du rayon dans la boîte de la grille.
        float t0 = 0.0F;
        float t1 = Float.MAX_VALUE;
        float[] o = {ox, oy, oz};
        int[] n = {grid.nx, grid.ny, grid.nz};
        int entryAxis = 0;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1.0E-6F) {
                if (o[a] < 0 || o[a] > n[a]) {
                    return null;
                }
                continue;
            }
            float ta = (0 - o[a]) / d[a];
            float tb = (n[a] - o[a]) / d[a];
            float near = Math.min(ta, tb);
            float far = Math.max(ta, tb);
            if (near > t0) {
                t0 = near;
                entryAxis = a;
            }
            t1 = Math.min(t1, far);
        }
        if (t0 >= t1) {
            return null;
        }
        float t = t0 + 1.0E-4F;
        int[] cell = new int[3];
        int[] step = new int[3];
        float[] tMax = new float[3];
        float[] tDelta = new float[3];
        for (int a = 0; a < 3; a++) {
            float p = o[a] + d[a] * t;
            cell[a] = Math.clamp((int) Math.floor(p), 0, n[a] - 1);
            step[a] = d[a] > 0 ? 1 : -1;
            tDelta[a] = Math.abs(d[a]) < 1.0E-6F ? Float.MAX_VALUE : Math.abs(1.0F / d[a]);
            float boundary = d[a] > 0 ? cell[a] + 1 : cell[a];
            tMax[a] = Math.abs(d[a]) < 1.0E-6F ? Float.MAX_VALUE : t + (boundary - (o[a] + d[a] * t)) / d[a];
        }
        float cr = 0.0F;
        float cg = 0.0F;
        float cb = 0.0F;
        float through = 1.0F;
        int axis = entryAxis;
        float tEnter = t0;
        while (cell[0] >= 0 && cell[1] >= 0 && cell[2] >= 0 && cell[0] < n[0] && cell[1] < n[1] && cell[2] < n[2]) {
            int index = grid.index(cell[0], cell[1], cell[2]);
            byte kind = grid.kind[index];
            int next = tMax[0] < tMax[1] ? (tMax[0] < tMax[2] ? 0 : 2) : (tMax[1] < tMax[2] ? 1 : 2);
            float tExit = tMax[next];
            float length = tExit - tEnter;
            if (kind == SOLID || kind == LAMP) {
                float[] surface = face(grid, cell, axis, step, o, d, tEnter, kind, index, light);
                return new float[] {cr + surface[0] * through, cg + surface[1] * through, cb + surface[2] * through, 0.0F};
            }
            if (kind == GLOW || kind == DUST) {
                int c = grid.color[index];
                float density = grid.power[index] * length;
                if (kind == GLOW) {
                    cr += (c >> 16 & 0xFF) / 255.0F * density * through;
                    cg += (c >> 8 & 0xFF) / 255.0F * density * through;
                    cb += (c & 0xFF) / 255.0F * density * through;
                    through *= (float) Math.exp(-density * 0.25F);
                } else {
                    through *= (float) Math.exp(-density);
                }
                if (through < 0.01F) {
                    break;
                }
            }
            cell[next] += step[next];
            tMax[next] += tDelta[next];
            axis = next;
            tEnter = tExit;
        }
        if (cr == 0.0F && cg == 0.0F && cb == 0.0F && through > 0.999F) {
            return null;
        }
        return new float[] {cr, cg, cb, through};
    }

    /**
     * La face d'un bloc touchée par un rayon : sa couleur, ses pixels de texture, l'ombre douce de ses coins (des blocs
     * voisins), et la lumière du soleil, si un autre bloc ne lui fait pas d'ombre. Un bloc lumineux brille de lui-même.
     */
    private float[] face(Grid grid, int[] cell, int axis, int[] step, float[] o, float[] d, float t, byte kind, int index, Light light) {
        int c = grid.color[index];
        int[] normal = new int[3];
        normal[axis] = -step[axis];
        // Où le rayon touche la face : les deux coordonnées dans son plan, de 0 à 1.
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        float f1 = clamp(o[a1] + d[a1] * t - cell[a1], 0.0F, 0.999F);
        float f2 = clamp(o[a2] + d[a2] * t - cell[a2], 0.0F, 0.999F);
        int tx = (int) (f1 * TEXELS);
        int ty = (int) (f2 * TEXELS);
        float texel = 0.94F + 0.12F * hash(cell[0] * 7 + tx, cell[1] * 7 + ty, cell[2] * 7 + axis, seed);
        float cr = (c >> 16 & 0xFF) / 255.0F * texel;
        float cg = (c >> 8 & 0xFF) / 255.0F * texel;
        float cb = (c & 0xFF) / 255.0F * texel;
        if (kind == LAMP) {
            float glow = grid.power[index];
            return new float[] {cr * glow, cg * glow, cb * glow};
        }
        // L'ombre des coins : un bloc posé à côté, devant la face, l'assombrit près de l'arête commune.
        int nx = cell[0] + normal[0];
        int ny = cell[1] + normal[1];
        int nz = cell[2] + normal[2];
        float ao = 1.0F;
        int[] side = new int[3];
        for (int k = 0; k < 4; k++) {
            side[0] = 0;
            side[1] = 0;
            side[2] = 0;
            int along = k < 2 ? a1 : a2;
            side[along] = k % 2 == 0 ? -1 : 1;
            if (grid.solid(nx + side[0], ny + side[1], nz + side[2])) {
                float coord = along == a1 ? f1 : f2;
                float distance = k % 2 == 0 ? coord : 1.0F - coord;
                ao -= 0.2F * clamp(1.0F - distance * 2.0F, 0.0F, 1.0F);
            }
        }
        ao = clamp(ao, 0.62F, 1.0F);
        // Le soleil : selon l'angle de la face, et s'il n'y a pas un autre bloc entre lui et elle.
        float[] l = light.direction();
        float lambert = normal[0] * l[0] + normal[1] * l[1] + normal[2] * l[2];
        float hx = o[0] + d[0] * t;
        float hy = o[1] + d[1] * t;
        float hz = o[2] + d[2] * t;
        float sun;
        if (grid.sphere != null) {
            // Une planète : le jour et la nuit suivent sa forme ronde ; chaque face, selon son orientation, n'est qu'un peu
            // plus claire ou plus sombre, comme les faces d'un bloc dans le jeu. Seul un vrai relief, loin, fait de l'ombre.
            float[] round = normalize(hx - grid.sphere[0], hy - grid.sphere[1], hz - grid.sphere[2]);
            float day = smoothstep(-0.08F, 0.45F, round[0] * l[0] + round[1] * l[1] + round[2] * l[2]);
            sun = day * (0.74F + 0.26F * clamp(lambert * 0.5F + 0.5F, 0.0F, 1.0F));
        } else {
            sun = Math.max(0.0F, lambert);
        }
        // L'ombre portée : franche sur une scène, adoucie sur une planète, où la lumière diffuse du sol éclaire un peu le
        // fond des cratères.
        if (sun > 0.0F && shadowed(grid, hx + normal[0] * 0.01F, hy + normal[1] * 0.01F, hz + normal[2] * 0.01F, l)) {
            sun *= grid.sphere != null ? 0.45F : 0.12F;
        }
        float[] lc = light.color();
        float[] am = light.ambient();
        return new float[] {cr * (am[0] + lc[0] * sun) * ao, cg * (am[1] + lc[1] * sun) * ao, cb * (am[2] + lc[2] * sun) * ao};
    }

    /**
     * Vrai si un bloc solide se trouve entre ce point et le soleil. Les blocs tout proches ne comptent pas : sur une
     * sphère en marches d'escalier, chaque marche ombrerait sa voisine, en damier.
     */
    private static boolean shadowed(Grid grid, float x, float y, float z, float[] l) {
        for (float s = 2.5F; s < grid.nx + grid.ny + grid.nz; s += 0.35F) {
            int cx = (int) Math.floor(x + l[0] * s);
            int cy = (int) Math.floor(y + l[1] * s);
            int cz = (int) Math.floor(z + l[2] * s);
            if (!grid.inside(cx, cy, cz)) {
                return false;
            }
            if (grid.solid(cx, cy, cz)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Le fond, et les lumières
    // ------------------------------------------------------------------

    private void add(int x, int y, float cr, float cg, float cb) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
            return;
        }
        int i = y * WIDTH + x;
        r[i] += cr;
        g[i] += cg;
        b[i] += cb;
    }

    /** Une lueur douce, comme le halo d'un shader autour d'une lumière. */
    private void glow(float cx, float cy, float radius, float cr, float cg, float cb) {
        int reach = (int) Math.ceil(radius * 5.0F);
        for (int y = Math.max(0, (int) cy - reach); y <= Math.min(HEIGHT - 1, (int) cy + reach); y++) {
            for (int x = Math.max(0, (int) cx - reach); x <= Math.min(WIDTH - 1, (int) cx + reach); x++) {
                float dx = x + 0.5F - cx;
                float dy = y + 0.5F - cy;
                float w = (float) Math.exp(-(dx * dx + dy * dy) / (2.0F * radius * radius));
                add(x, y, cr * w, cg * w, cb * w);
            }
        }
    }

    /** Un soleil carré, comme celui du jeu, dans son halo. */
    private void squareSun(float cx, float cy, int half, float cr, float cg, float cb, float haloRadius) {
        glow(cx, cy, haloRadius, cr * 0.35F, cg * 0.35F, cb * 0.35F);
        for (int y = (int) cy - half; y < cy + half; y++) {
            for (int x = (int) cx - half; x < cx + half; x++) {
                add(x, y, cr * 3.0F, cg * 3.0F, cb * 3.0F);
            }
        }
    }

    /** Le ciel du jeu, la nuit : un noir bleuté, et des étoiles carrées, d'un ou deux pixels. */
    private void stars(int count, float strength) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                float t = y / (float) HEIGHT;
                int i = y * WIDTH + x;
                r[i] = 0.004F + 0.006F * t;
                g[i] = 0.006F + 0.008F * t;
                b[i] = 0.016F + 0.016F * t;
            }
        }
        for (int k = 0; k < count; k++) {
            int x = (int) (hash(k, 1, 7, seed) * WIDTH);
            int y = (int) (hash(k, 2, 7, seed) * HEIGHT);
            float bright = (0.15F + (float) Math.pow(hash(k, 3, 7, seed), 6.0) * 1.4F) * strength;
            int size = hash(k, 5, 7, seed) > 0.85F ? 2 : 1;
            float warm = hash(k, 4, 7, seed);
            float sr = warm < 0.3F ? 1.0F : warm > 0.8F ? 0.75F : 0.95F;
            float sb = warm < 0.3F ? 0.75F : warm > 0.8F ? 1.0F : 0.95F;
            for (int dy = 0; dy < size; dy++) {
                for (int dx = 0; dx < size; dx++) {
                    add(x + dx, y + dy, sr * bright, 0.92F * bright, sb * bright);
                }
            }
        }
    }

    /**
     * L'atmosphère d'une planète, à l'écran : un liseré lumineux autour de son disque, plus vif du côté du soleil, comme
     * le brouillard d'un shader.
     */
    private void atmosphere(float cx, float cy, float radius, float thick, float cr, float cg, float cb, float lx, float ly) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                float dx = x + 0.5F - cx;
                float dy = y + 0.5F - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / radius;
                if (d < 0.8F || d > 1.0F + thick * 4.0F) {
                    continue;
                }
                float facing = clamp((dx * lx - dy * ly) / (d * radius) * 0.8F + 0.45F, 0.0F, 1.0F);
                float w = (d < 1.0F ? smoothstep(0.8F, 1.0F, d) * 0.5F : (float) Math.exp(-(d - 1.0F) / thick)) * facing * 0.5F;
                add(x, y, cr * w, cg * w, cb * w);
            }
        }
    }

    // ------------------------------------------------------------------
    // Les planètes en blocs
    // ------------------------------------------------------------------

    /** La couleur du bloc à cette direction de la sphère unité (y vers le nord). */
    private interface Surface {
        int color(float x, float y, float z);
    }

    /** Le relief à cette direction, en blocs au-dessus (ou au-dessous) du rayon. */
    private interface Relief {
        float height(float x, float y, float z);
    }

    /** Une sphère de blocs, de ce rayon, au centre d'une grille assez grande pour elle et ce qui l'entoure. */
    private Grid planet(int radius, int margin, Surface surface, Relief relief) {
        int size = 2 * (radius + margin) + 1;
        Grid grid = new Grid(size, size, size);
        float centre = size / 2.0F;
        grid.sphere = new float[] {centre, centre, centre};
        for (int z = 0; z < size; z++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float px = x + 0.5F - centre;
                    float py = y + 0.5F - centre;
                    float pz = z + 0.5F - centre;
                    float d = (float) Math.sqrt(px * px + py * py + pz * pz);
                    if (d > radius + 3) {
                        continue;
                    }
                    float nx = px / d;
                    float ny = py / d;
                    float nz = pz / d;
                    float h = relief == null ? 0.0F : relief.height(nx, ny, nz);
                    if (d <= radius + h) {
                        grid.set(x, y, z, SOLID, surface.color(nx, ny, nz), 0.0F);
                    }
                }
            }
        }
        return grid;
    }

    /** Une cuvette de cratères : 0 hors d'un cratère, jusqu'à -1 au fond, et la lèvre un peu au-dessus. */
    private float crater(float x, float y, float z, float scale, float size) {
        float px = x * scale;
        float py = y * scale;
        float pz = z * scale;
        int cx = (int) Math.floor(px);
        int cy = (int) Math.floor(py);
        int cz = (int) Math.floor(pz);
        float depth = 0.0F;
        for (int k = 0; k < 27; k++) {
            int ix = cx + k % 3 - 1;
            int iy = cy + k / 3 % 3 - 1;
            int iz = cz + k / 9 - 1;
            if (hash(ix, iy, iz, seed + 91) > 0.5F) {
                continue;
            }
            float fx = ix + hash(ix, iy, iz, seed + 11);
            float fy = iy + hash(ix, iy, iz, seed + 23);
            float fz = iz + hash(ix, iy, iz, seed + 37);
            float dd = (float) Math.sqrt((px - fx) * (px - fx) + (py - fy) * (py - fy) + (pz - fz) * (pz - fz));
            float rad = size * (0.4F + 0.6F * hash(ix, iy, iz, seed + 53));
            float t = dd / rad;
            if (t < 1.0F) {
                depth = Math.min(depth, -(1.0F - t * t));
            } else if (t < 1.3F) {
                depth = Math.max(depth, 0.4F * (1.3F - t) / 0.3F);
            }
        }
        return depth;
    }

    /** Une planète vue de trois quarts, éclairée par le soleil de la gauche, avec son atmosphère si elle en a une. */
    private void showPlanet(Grid grid, int radius, Light light, int air, float thick) {
        float scale = 170.0F / (2 * radius + 1);
        Camera camera = new Camera(0.62F, 0.38F, scale, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, light);
        if (air != 0) {
            atmosphere(WIDTH * 0.5F, HEIGHT * 0.5F, (radius + 0.5F) * scale, thick, (air >> 16 & 0xFF) / 255.0F, (air >> 8 & 0xFF) / 255.0F,
                    (air & 0xFF) / 255.0F, -0.8F, 0.5F);
        }
    }

    // ------------------------------------------------------------------
    // Les astres
    // ------------------------------------------------------------------

    /** Peint l'astre ; rend l'exposition à laquelle développer l'image. */
    private float compose(String body) {
        switch (body) {
            case "moon" -> moon();
            case "mercury" -> mercury();
            case "mars" -> mars();
            case "venus" -> venus();
            case "jupiter" -> jupiter();
            case "saturn" -> saturn();
            case "europa" -> europa();
            case "titan" -> titan();
            case "comet" -> comet();
            case "pleiades" -> pleiades();
            case "orion_nebula" -> orion();
            case "eagle_nebula" -> eagle();
            case "crab_pulsar" -> crab();
            case "andromeda" -> andromeda();
            case "proxima_b" -> proxima();
            case "trappist_1e" -> trappist();
            case "sagittarius_a" -> blackHole();
            default -> stars(300, 1.0F);
        }
        return 1.3F;
    }

    private void moon() {
        stars(260, 0.8F);
        Grid grid = planet(28, 3, (x, y, z) -> {
            float maria = smoothstep(0.5F, 0.6F, fbm(x * 1.6F + 3, y * 1.6F, z * 1.6F, 5));
            float v = lerp(maria, 0.62F, 0.38F) * (0.92F + 0.12F * fbm(x * 9, y * 9, z * 9, 3));
            return rgb(v * 1.02F, v, v * 0.97F);
        }, (x, y, z) -> crater(x, y, z, 3.0F, 0.45F) * 2.6F + crater(x, y, z, 7.0F, 0.4F) * 1.4F);
        showPlanet(grid, 28, SUN, 0, 0.0F);
    }

    private void mercury() {
        stars(240, 0.7F);
        Grid grid = planet(26, 3, (x, y, z) -> {
            float v = 0.5F * (0.85F + 0.3F * fbm(x * 3, y * 3, z * 3, 5));
            return rgb(v * 1.06F, v * 0.97F, v * 0.86F);
        }, (x, y, z) -> crater(x, y, z, 4.0F, 0.42F) * 2.4F + crater(x, y, z, 9.0F, 0.4F) * 1.3F);
        showPlanet(grid, 26, SUN, 0, 0.0F);
    }

    private void mars() {
        stars(260, 0.75F);
        Grid grid = planet(28, 3, (x, y, z) -> {
            float n = fbm(x * 2.2F, y * 2.2F, z * 2.2F, 5);
            int c = mix(rgb(0.78F, 0.38F, 0.2F), rgb(0.45F, 0.22F, 0.13F), smoothstep(0.5F, 0.64F, n));
            c = mix(c, rgb(0.9F, 0.56F, 0.32F), smoothstep(0.6F, 0.42F, fbm(x * 6, y * 6, z * 6, 3)) * 0.35F);
            // Les calottes polaires : de la neige.
            float cap = smoothstep(0.82F, 0.86F, Math.abs(y) + (fbm(x * 8, y * 8, z * 8, 3) - 0.5F) * 0.1F);
            return mix(c, rgb(0.96F, 0.95F, 0.94F), cap);
        }, (x, y, z) -> (fbm(x * 2.5F + 9, y * 2.5F, z * 2.5F, 4) - 0.45F) * 2.5F + crater(x, y, z, 5.0F, 0.35F));
        showPlanet(grid, 28, SUN, rgb(1.0F, 0.6F, 0.45F), 0.04F);
    }

    private void venus() {
        stars(200, 0.65F);
        Grid grid = planet(28, 2, (x, y, z) -> {
            float w = fbm(x * 2, y * 2, z * 2, 4);
            float n = fbm(x * 1.5F + w * 2.5F, y * 6.0F + w * 1.5F, z * 1.5F, 5);
            return mix(rgb(0.98F, 0.88F, 0.64F), rgb(0.8F, 0.62F, 0.4F), smoothstep(0.35F, 0.7F, n));
        }, null);
        showPlanet(grid, 28, SUN, rgb(1.0F, 0.92F, 0.72F), 0.06F);
    }

    private void jupiter() {
        stars(200, 0.65F);
        int[] ramp = {rgb(0.97F, 0.94F, 0.86F), rgb(0.88F, 0.76F, 0.6F), rgb(0.72F, 0.53F, 0.38F), rgb(0.56F, 0.4F, 0.3F), rgb(0.92F, 0.86F, 0.74F)};
        Grid grid = planet(30, 2, (x, y, z) -> {
            float lon = (float) Math.atan2(x, z);
            float lat = y + (fbm(lon * 2.0F, y * 7.0F, 1.0F, 4) - 0.5F) * 0.07F;
            float t = noise(lat * 9.0F, 0.5F, 0.5F) * 0.55F + (0.5F + 0.5F * (float) Math.sin(lat * 13.0F)) * 0.45F;
            float pos = clamp(t, 0.0F, 0.999F) * (ramp.length - 1);
            int k = (int) pos;
            int c = mix(ramp[k], ramp[k + 1], pos - k);
            float sx = (lon - 0.6F) / 0.32F;
            float sy = (y + 0.36F) / 0.12F;
            if (sx * sx + sy * sy < 1.0F) {
                c = mix(c, rgb(0.8F, 0.38F, 0.24F), 0.85F);
            }
            return c;
        }, null);
        showPlanet(grid, 30, SUN, rgb(0.95F, 0.88F, 0.75F), 0.03F);
    }

    private void saturn() {
        stars(200, 0.65F);
        int radius = 18;
        int margin = 26;
        Grid grid = planet(radius, margin, (x, y, z) -> {
            float band = (float) Math.sin((y + (fbm(x * 5, y * 12, z * 5, 3) - 0.5F) * 0.04F) * 14.0F);
            return mix(rgb(0.84F, 0.74F, 0.52F), rgb(0.97F, 0.91F, 0.72F), band * 0.5F + 0.5F);
        }, null);
        // Les anneaux : une couronne de blocs d'une épaisseur, dans le plan de l'équateur, avec la division de Cassini.
        float centre = grid.nx / 2.0F;
        int mid = grid.ny / 2;
        for (int z = 0; z < grid.nz; z++) {
            for (int x = 0; x < grid.nx; x++) {
                float px = x + 0.5F - centre;
                float pz = z + 0.5F - centre;
                float d = (float) Math.sqrt(px * px + pz * pz);
                if (d < 24.0F || d > 43.0F || (d > 34.4F && d < 36.4F)) {
                    continue;
                }
                float tone = d < 27.0F ? 0.7F : d < 34.4F ? 1.0F : 0.88F;
                tone *= 0.9F + 0.15F * noise(d, 0, 0);
                grid.set(x, mid, z, SOLID, rgb(0.88F * tone, 0.8F * tone, 0.64F * tone), 0.0F);
            }
        }
        float scale = 250.0F / grid.nx;
        Camera camera = new Camera(0.55F, 0.42F, scale, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    private void europa() {
        stars(240, 0.75F);
        Grid grid = planet(28, 2, (x, y, z) -> {
            int c = mix(rgb(0.95F, 0.93F, 0.88F), rgb(0.8F, 0.66F, 0.52F), smoothstep(0.45F, 0.7F, fbm(x * 2.5F, y * 2.5F, z * 2.5F, 5)));
            float lines = (float) Math.pow(ridged(x * 3 + 2, y * 3, z * 3, 3), 5.0) * 1.5F;
            return mix(c, rgb(0.56F, 0.33F, 0.22F), clamp(lines, 0.0F, 0.9F));
        }, null);
        showPlanet(grid, 28, SUN, 0, 0.0F);
    }

    private void titan() {
        stars(200, 0.6F);
        Grid grid = planet(28, 2, (x, y, z) -> {
            float n = fbm(x * 2.5F, y * 2.5F, z * 2.5F, 5);
            return mix(rgb(0.88F, 0.62F, 0.28F), rgb(0.72F, 0.47F, 0.2F), smoothstep(0.45F, 0.65F, n) * 0.5F);
        }, null);
        showPlanet(grid, 28, SUN, rgb(1.0F, 0.72F, 0.36F), 0.1F);
        float scale = 170.0F / 57.0F;
        atmosphere(WIDTH * 0.5F, HEIGHT * 0.5F, 30.5F * scale, 0.04F, 0.45F, 0.6F, 1.0F, -0.8F, 0.5F);
    }

    private void comet() {
        stars(380, 0.9F);
        // Le noyau : un bloc de glace et de roche ; les queues : des blocs de gaz qui brillent, de plus en plus épars.
        Grid grid = new Grid(80, 26, 26);
        int hx = 66;
        int hy = 13;
        int hz = 13;
        for (int z = 0; z < 26; z++) {
            for (int y = 0; y < 26; y++) {
                for (int x = 0; x < 80; x++) {
                    float dx = x - hx;
                    float dy = y - hy;
                    float dz = z - hz;
                    float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < 2.6F + noise(x, y, z) * 0.8F) {
                        grid.set(x, y, z, SOLID, mix(rgb(0.86F, 0.9F, 0.95F), rgb(0.35F, 0.33F, 0.32F), noise(x * 0.7F, y * 0.7F, z * 0.7F)), 0.0F);
                        continue;
                    }
                    if (d < 6.0F) {
                        grid.set(x, y, z, GLOW, rgb(0.55F, 0.95F, 0.85F), 0.16F * (1.0F - d / 6.0F));
                        continue;
                    }
                    float along = hx - x;
                    if (along <= 0) {
                        continue;
                    }
                    float t = along / hx;
                    // La queue de poussière : courbe, large, dorée.
                    float dustY = hy + t * t * 9.0F;
                    float spread = 1.5F + t * 6.0F;
                    float dd = (float) Math.sqrt((y - dustY) * (y - dustY) + dz * dz) / spread;
                    if (dd < 1.0F && hash(x, y, z, seed) > 0.25F + t * 0.55F) {
                        grid.set(x, y, z, GLOW, rgb(1.0F, 0.85F, 0.55F), 0.12F * (1.0F - t) * (1.0F - dd));
                    }
                    // La queue d'ions : droite, fine, bleue.
                    float ionY = hy - t * 4.0F;
                    float di = (float) Math.sqrt((y - ionY) * (y - ionY) + dz * dz) / (0.8F + t * 2.0F);
                    if (di < 1.0F && hash(x, y, z, seed + 3) > 0.15F + t * 0.6F) {
                        grid.set(x, y, z, GLOW, rgb(0.4F, 0.65F, 1.0F), 0.2F * (1.0F - t));
                    }
                }
            }
        }
        Camera camera = new Camera(0.25F, 0.2F, 4.6F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    private void pleiades() {
        stars(460, 0.9F);
        // Les étoiles : des blocs qui brillent ; autour, un voile de blocs bleus, translucides.
        Grid grid = new Grid(56, 34, 34);
        float[][] bright = {{14, 18, 17, 1.5F}, {22, 23, 15, 1.2F}, {28, 16, 18, 1.35F}, {35, 21, 16, 1.0F}, {39, 12, 19, 1.25F}, {20, 9, 17, 0.95F},
                {31, 8, 15, 0.85F}, {10, 11, 18, 0.8F}, {44, 20, 17, 0.7F}};
        for (int z = 0; z < 34; z++) {
            for (int y = 0; y < 34; y++) {
                for (int x = 0; x < 56; x++) {
                    float near = 0.0F;
                    for (float[] s : bright) {
                        float dx = x - s[0];
                        float dy = y - s[1];
                        float dz = z - s[2];
                        near += s[3] * (float) Math.exp(-(dx * dx + dy * dy + dz * dz) / 60.0F);
                    }
                    float streak = fbm(x / 9.0F + y / 4.0F, y / 2.5F, z / 9.0F, 4);
                    float density = near * smoothstep(0.45F, 0.75F, streak);
                    if (density > 0.05F) {
                        grid.set(x, y, z, GLOW, rgb(0.35F, 0.55F, 1.0F), density * 0.09F);
                    }
                }
            }
        }
        for (float[] s : bright) {
            grid.set((int) s[0], (int) s[1], (int) s[2], LAMP, rgb(0.85F, 0.92F, 1.0F), 3.5F * s[3]);
        }
        Camera camera = new Camera(0.5F, 0.3F, 5.6F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    /** Une nébuleuse en blocs : du gaz lumineux, des bandes de poussière, et des étoiles au cœur. */
    private void orion() {
        stars(300, 0.75F);
        Grid grid = new Grid(64, 38, 40);
        for (int z = 0; z < 40; z++) {
            for (int y = 0; y < 38; y++) {
                for (int x = 0; x < 64; x++) {
                    float px = (x - 32) / 26.0F;
                    float py = (y - 19) / 17.0F;
                    float pz = (z - 20) / 18.0F;
                    float warp = fbm(px * 1.5F, py * 1.5F, pz * 1.5F, 3) - 0.5F;
                    float density = fbm(px * 2.2F + warp, py * 2.2F, pz * 2.2F, 5);
                    float falloff = (float) Math.exp(-(px * px + py * py * 1.3F + pz * pz) * 1.8F);
                    float gas = smoothstep(0.42F, 0.75F, density) * falloff;
                    float core = (float) Math.exp(-(px * px + py * py + pz * pz) * 8.0F);
                    float dust = smoothstep(0.62F, 0.8F, ridged(px * 2.5F + 7, py * 2.5F, pz * 2.5F, 3)) * smoothstep(-0.2F, 0.4F, px + py * 0.3F);
                    if (dust > 0.5F && falloff > 0.2F) {
                        grid.set(x, y, z, DUST, 0, 0.18F * dust);
                    } else if (gas + core > 0.05F) {
                        int c = mix(rgb(0.95F, 0.25F, 0.4F), rgb(0.4F, 0.95F, 0.9F), core * 2.0F);
                        c = mix(c, rgb(1.0F, 1.0F, 1.0F), core * core * 1.5F);
                        grid.set(x, y, z, GLOW, c, (gas + core * 0.8F) * 0.22F);
                    }
                }
            }
        }
        // Le Trapèze : quatre jeunes étoiles, des blocs qui brillent.
        int[][] trapezium = {{31, 19, 20}, {33, 21, 19}, {34, 18, 21}, {30, 17, 20}};
        for (int[] s : trapezium) {
            grid.set(s[0], s[1], s[2], LAMP, rgb(0.9F, 0.95F, 1.0F), 4.0F);
        }
        Camera camera = new Camera(0.35F, 0.25F, 6.0F, WIDTH * 0.5F, HEIGHT * 0.52F);
        render(grid, camera, SUN);
    }

    private void eagle() {
        stars(160, 0.6F);
        // Le gaz du fond, sur toute l'image, en gros pixels.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                float n = fbm((x / 5) / 11.0F, (y / 5) / 11.0F, 0.5F, 4);
                float t = 1.0F - y / (float) HEIGHT;
                int c = mix(rgb(0.25F, 0.6F, 0.6F), rgb(0.95F, 0.72F, 0.38F), t * 0.9F + (n - 0.5F));
                float w = 0.42F * (0.4F + n);
                add(x, y, (c >> 16 & 0xFF) / 255.0F * w, (c >> 8 & 0xFF) / 255.0F * w, (c & 0xFF) / 255.0F * w);
            }
        }
        // Devant le gaz doré et vert d'eau, trois piliers de poussière en blocs, éclairés d'en haut, la pointe brûlée par
        // la lumière des jeunes étoiles.
        Grid grid = new Grid(70, 40, 30);
        float[][] pillars = {{17, 33, 6.0F, 15}, {37, 25, 5.0F, 16}, {54, 18, 3.6F, 14}};
        for (float[] p : pillars) {
            for (int y = 0; y < (int) p[1]; y++) {
                float t = y / p[1];
                float sway = (fbm(t * 2.5F, p[0], 0.0F, 3) - 0.5F) * 6.0F * t;
                float width = p[2] * (1.0F - 0.45F * t);
                if (t > 0.85F) {
                    width *= (float) Math.sqrt(Math.max(0.0F, 1.0F - ((t - 0.85F) / 0.15F) * ((t - 0.85F) / 0.15F)));
                }
                for (int z = 0; z < 30; z++) {
                    for (int x = 0; x < 70; x++) {
                        float dx = x - p[0] - sway;
                        float dz = z - p[3];
                        float edge = width * (0.75F + 0.5F * fbm(x * 0.3F, y * 0.3F, z * 0.3F, 3));
                        if (dx * dx + dz * dz > edge * edge) {
                            continue;
                        }
                        float texture = 0.7F + 0.6F * fbm(x * 0.5F, y * 0.5F, z * 0.5F, 3);
                        boolean tip = t > 0.88F && hash(x, y, z, seed) > 0.4F;
                        if (tip) {
                            grid.set(x, y, z, LAMP, rgb(1.0F, 0.72F, 0.4F), 1.6F);
                        } else {
                            grid.set(x, y, z, SOLID, rgb(0.34F * texture, 0.22F * texture, 0.14F * texture), 0.0F);
                        }
                    }
                }
            }
        }
        Light top = new Light(normalize(-0.5F, 0.85F, 0.35F), new float[] {1.25F, 1.0F, 0.7F}, new float[] {0.05F, 0.07F, 0.08F});
        Camera camera = new Camera(0.18F, 0.1F, 5.4F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, top);
    }

    private void crab() {
        stars(300, 0.75F);
        Grid grid = new Grid(58, 40, 40);
        for (int z = 0; z < 40; z++) {
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 58; x++) {
                    float px = (x - 29) / 26.0F;
                    float py = (y - 20) / 17.0F;
                    float pz = (z - 20) / 17.0F;
                    float warp = fbm(px * 3, py * 3, pz * 3, 3) - 0.5F;
                    float d = (float) Math.sqrt(px * px + py * py + pz * pz) + warp * 0.25F;
                    if (d > 1.0F) {
                        continue;
                    }
                    float filaments = ridged(px * 4 + warp, py * 4, pz * 4, 4);
                    if (filaments > 0.66F && d > 0.45F) {
                        grid.set(x, y, z, GLOW, mix(rgb(1.0F, 0.35F, 0.12F), rgb(1.0F, 0.8F, 0.4F), filaments - 0.62F), 0.16F);
                    } else if (d < 0.55F) {
                        grid.set(x, y, z, GLOW, rgb(0.45F, 0.6F, 1.0F), 0.05F * (1.0F - d));
                    }
                }
            }
        }
        // Le pulsar, et ses deux faisceaux de blocs.
        for (int k = 1; k < 18; k++) {
            grid.set(29 + k, 20 + k / 2, 20, GLOW, rgb(0.5F, 0.7F, 1.0F), 0.5F * (1.0F - k / 18.0F));
            grid.set(29 - k, 20 - k / 2, 20, GLOW, rgb(0.5F, 0.7F, 1.0F), 0.5F * (1.0F - k / 18.0F));
        }
        grid.set(29, 20, 20, LAMP, rgb(0.85F, 0.92F, 1.0F), 5.0F);
        Camera camera = new Camera(0.3F, 0.3F, 5.8F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    private void andromeda() {
        stars(320, 0.75F);
        // Un disque de blocs qui brillent, en spirale, avec ses bandes de poussière ; au centre, un bulbe doré.
        Grid grid = new Grid(80, 9, 80);
        for (int z = 0; z < 80; z++) {
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 80; x++) {
                    float u = (x - 40) / 36.0F;
                    float v = (z - 40) / 36.0F;
                    float h = (y - 4) / 4.0F;
                    float rr = (float) Math.sqrt(u * u + v * v);
                    if (rr > 1.05F) {
                        continue;
                    }
                    float thickness = 0.25F + 0.75F * (float) Math.exp(-rr * rr / 0.02F);
                    if (Math.abs(h) > thickness) {
                        continue;
                    }
                    float theta = (float) Math.atan2(v, u);
                    float phase = theta - (float) Math.log(rr + 0.03F) * 3.0F;
                    float arm = (float) Math.pow(0.5F + 0.5F * Math.cos(2.0F * phase), 2.0);
                    float lane = (float) Math.pow(0.5F + 0.5F * Math.cos(2.0F * phase + 2.4F), 10.0) * smoothstep(0.12F, 0.3F, rr);
                    float bulge = (float) Math.exp(-rr * rr / 0.012F);
                    float disk = (float) Math.exp(-rr / 0.3F) * (0.35F + 0.9F * arm * (0.7F + 0.6F * fbm(u * 7, h, v * 7, 3)));
                    if (lane > 0.5F && Math.abs(h) < 0.3F) {
                        grid.set(x, y, z, DUST, 0, 0.5F);
                    } else if (disk + bulge > 0.03F) {
                        int c = mix(rgb(0.7F, 0.8F, 1.0F), rgb(1.0F, 0.86F, 0.6F), bulge * 1.5F + (1.0F - arm) * 0.3F);
                        if (arm > 0.85F && rr > 0.25F && hash(x, y, z, seed) > 0.985F) {
                            c = rgb(1.0F, 0.45F, 0.7F);
                        }
                        grid.set(x, y, z, GLOW, c, disk * 0.18F + bulge * 1.4F);
                    }
                }
            }
        }
        Camera camera = new Camera(0.9F, 0.62F, 3.9F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    private void proxima() {
        stars(260, 0.75F);
        // L'étoile, une naine rouge, à gauche : un soleil carré, comme celui du jeu, dans un halo pourpre.
        squareSun(WIDTH * 0.07F, HEIGHT * 0.36F, 14, 1.0F, 0.42F, 0.22F, 40);
        Grid grid = planet(24, 3, (x, y, z) -> {
            float n = fbm(x * 2.4F, y * 2.4F, z * 2.4F, 5);
            int c = mix(rgb(0.56F, 0.34F, 0.24F), rgb(0.3F, 0.18F, 0.15F), smoothstep(0.45F, 0.6F, n));
            return mix(c, rgb(0.9F, 0.86F, 0.86F), smoothstep(0.8F, 0.86F, Math.abs(y)));
        }, (x, y, z) -> (fbm(x * 2.6F, y * 2.6F, z * 2.6F, 4) - 0.45F) * 2.5F);
        Light red = new Light(normalize(-1.0F, 0.2F, 0.3F), new float[] {1.3F, 0.62F, 0.4F}, new float[] {0.03F, 0.015F, 0.02F});
        float scale = 150.0F / grid.nx;
        Camera camera = new Camera(0.62F, 0.3F, scale, WIDTH * 0.62F, HEIGHT * 0.55F);
        render(grid, camera, red);
        atmosphere(WIDTH * 0.62F, HEIGHT * 0.55F, 24.5F * scale, 0.03F, 1.0F, 0.45F, 0.3F, -1.0F, 0.2F);
    }

    private void trappist() {
        stars(260, 0.75F);
        squareSun(WIDTH * 0.9F, HEIGHT * 0.14F, 7, 1.0F, 0.56F, 0.3F, 26);
        // Un monde d'océans, des continents, et des nuages en blocs, comme ceux du jeu, une couche au-dessus.
        int radius = 24;
        Grid grid = planet(radius, 4, (x, y, z) -> {
            float land = smoothstep(0.55F, 0.6F, fbm(x * 2, y * 2, z * 2, 5));
            return mix(rgb(0.08F, 0.22F, 0.46F), rgb(0.36F, 0.48F, 0.24F), land);
        }, (x, y, z) -> smoothstep(0.55F, 0.62F, fbm(x * 2, y * 2, z * 2, 5)));
        float centre = grid.nx / 2.0F;
        for (int z = 0; z < grid.nz; z++) {
            for (int y = 0; y < grid.ny; y++) {
                for (int x = 0; x < grid.nx; x++) {
                    float px = x + 0.5F - centre;
                    float py = y + 0.5F - centre;
                    float pz = z + 0.5F - centre;
                    float d = (float) Math.sqrt(px * px + py * py + pz * pz);
                    if (d > radius + 2.0F && d <= radius + 3.0F && fbm(px / d * 3 + 4, py / d * 5, pz / d * 3, 4) > 0.56F) {
                        grid.set(x, y, z, SOLID, rgb(0.96F, 0.97F, 0.98F), 0.0F);
                    }
                }
            }
        }
        Light orange = new Light(normalize(0.6F, 0.5F, 0.6F), new float[] {1.25F, 0.9F, 0.65F}, new float[] {0.02F, 0.025F, 0.04F});
        float scale = 150.0F / grid.nx;
        Camera camera = new Camera(0.62F, 0.35F, scale, WIDTH * 0.45F, HEIGHT * 0.56F);
        render(grid, camera, orange);
        atmosphere(WIDTH * 0.45F, HEIGHT * 0.56F, (radius + 3.0F) * scale, 0.04F, 0.5F, 0.7F, 1.0F, 0.8F, 0.6F);
    }

    private void blackHole() {
        stars(340, 0.8F);
        // L'anneau dévié par la gravité, peint derrière : des carrés lumineux sur un cercle, plus épais en haut et en bas.
        for (int k = 0; k < 220; k++) {
            float a = k / 220.0F * (float) (Math.PI * 2);
            float rad = 48.0F + (float) Math.abs(Math.sin(a)) * 12.0F;
            int bx = Math.round(WIDTH * 0.5F + (float) Math.cos(a) * rad);
            int by = Math.round(HEIGHT * 0.5F + (float) Math.sin(a) * rad * 0.92F);
            float w = (0.35F + 0.65F * (float) Math.abs(Math.sin(a))) * (1.0F - 0.5F * (float) Math.cos(a));
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    add(bx + dx, by + dy, 0.5F * w, 0.32F * w, 0.12F * w);
                }
            }
        }
        // L'ombre : une boule de blocs noirs. Le disque : une couronne de blocs brûlants, presque vue par la tranche.
        Grid grid = new Grid(110, 40, 110);
        float c = 55.0F;
        float cy = 20.0F;
        for (int z = 0; z < 110; z++) {
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 110; x++) {
                    float dx = x + 0.5F - c;
                    float dy = y + 0.5F - cy;
                    float dz = z + 0.5F - c;
                    float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= 13.0F) {
                        grid.set(x, y, z, LAMP, 0, 0.0F);
                        continue;
                    }
                    float flat = (float) Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(dy) < 0.9F && flat > 16.0F && flat < 46.0F && hash(x, y, z, seed) > 0.08F) {
                        float heat = 1.0F - (flat - 16.0F) / 30.0F;
                        int col = mix(rgb(1.0F, 0.35F, 0.08F), rgb(1.0F, 0.92F, 0.75F), heat * heat);
                        float doppler = 1.0F - 0.6F * dx / flat;
                        grid.set(x, y, z, GLOW, col, (0.08F + heat * 0.3F) * doppler);
                    }
                }
            }
        }
        Camera camera = new Camera(0.0F, 0.16F, 3.3F, WIDTH * 0.5F, HEIGHT * 0.5F);
        render(grid, camera, SUN);
    }

    // ------------------------------------------------------------------
    // Développement
    // ------------------------------------------------------------------

    /** Halo des hautes lumières : ce qui dépasse est flouté et rajouté, comme le flou lumineux d'un shader. */
    private void bloom() {
        int w = WIDTH / 2;
        int h = HEIGHT / 2;
        float[][] full = {r, g, b};
        for (int c = 0; c < 3; c++) {
            float[] small = new float[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float sum = 0.0F;
                    for (int k = 0; k < 4; k++) {
                        sum += Math.max(0.0F, full[c][(y * 2 + (k >> 1)) * WIDTH + x * 2 + (k & 1)] - 0.6F);
                    }
                    small[y * w + x] = sum / 4.0F;
                }
            }
            for (int pass = 0; pass < 3; pass++) {
                small = blur(small, w, h, 4);
            }
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    full[c][y * WIDTH + x] += small[Math.min(h - 1, y / 2) * w + Math.min(w - 1, x / 2)] * 0.7F;
                }
            }
        }
    }

    private static float[] blur(float[] in, int w, int h, int radius) {
        float[] tmp = new float[w * h];
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0.0F;
                for (int k = -radius; k <= radius; k++) {
                    sum += in[y * w + Math.clamp(x + k, 0, w - 1)];
                }
                tmp[y * w + x] = sum / (2 * radius + 1);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0.0F;
                for (int k = -radius; k <= radius; k++) {
                    sum += tmp[Math.clamp(y + k, 0, h - 1) * w + x];
                }
                out[y * w + x] = sum / (2 * radius + 1);
            }
        }
        return out;
    }

    /** Développe l'image : halo, exposition douce, vignettage. */
    private int[] develop(float exposure) {
        bloom();
        int[] out = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int i = y * WIDTH + x;
                float vx = (x + 0.5F) / WIDTH - 0.5F;
                float vy = (y + 0.5F) / HEIGHT - 0.5F;
                float vignette = 1.0F - (vx * vx + vy * vy) * 0.5F;
                float[] c = {r[i], g[i], b[i]};
                int argb = 0xFF000000;
                for (int k = 0; k < 3; k++) {
                    float v = 1.0F - (float) Math.exp(-c[k] * exposure * vignette);
                    v = (float) Math.pow(clamp(v, 0.0F, 1.0F), 1.0 / 1.1);
                    argb |= Math.round(v * 255.0F) << (16 - 8 * k);
                }
                out[i] = argb;
            }
        }
        return out;
    }

    /** Écrit toutes les images dans un dossier : {@code java SpacePhotoPainter dossier corps...}. */
    public static void main(String[] args) throws Exception {
        java.io.File folder = new java.io.File(args.length > 0 ? args[0] : "photos");
        folder.mkdirs();
        String[] bodies = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length)
                : new String[] {"moon", "comet", "mars", "venus", "mercury", "jupiter", "europa", "saturn", "titan", "pleiades", "orion_nebula",
                        "eagle_nebula", "crab_pulsar", "andromeda", "proxima_b", "trappist_1e", "sagittarius_a"};
        for (String body : bodies) {
            long start = System.nanoTime();
            int[] pixels = paint(body);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(WIDTH, HEIGHT, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
            javax.imageio.ImageIO.write(image, "png", new java.io.File(folder, body + ".png"));
            System.out.println(body + " : " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
    }
}
