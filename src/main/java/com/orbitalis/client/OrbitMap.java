package com.orbitalis.client;

import java.util.Locale;

import com.orbitalis.client.screen.Ui;
import com.orbitalis.core.network.FlightHudPayload;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * La vue carte du pilote (touche M), à la manière des jeux de vol spatial : la planète vue de loin, et la trajectoire de
 * la capsule autour d'elle. Elle se pose par-dessus l'écran ; les commandes restent en main.
 * <ul>
 * <li><b>En montée</b>, la trajectoire prévue de la fusée, en pointillés, et le chemin déjà parcouru ; le point le plus
 * haut (l'apogée), et pour un vol orbital l'orbite visée.</li>
 * <li><b>En orbite</b>, l'orbite et la capsule qui la parcourt. Une manœuvre préparée a son repère, là où se fera
 * l'allumage, et l'orbite qu'elle donnera : calculée par la mécanique céleste (l'équation de la force vive), elle
 * grandit en direct pendant la poussée. Un départ vers la Lune étire l'orbite jusqu'à elle ; vers les planètes, la
 * trajectoire s'échappe ; un freinage plonge le périgée dans l'atmosphère.</li>
 * </ul>
 * Les altitudes sont à l'échelle d'un logarithme : une orbite basse se détache de la planète, et la Lune tient encore
 * dans l'écran.
 */
public final class OrbitMap {
    /** Rayon de la planète, en km, son paramètre gravitationnel, en km³/s², et la distance de la Lune, en km. */
    private static final double PLANET_KM = 6371.0;
    private static final double MU = 398_600.0;
    private static final double MOON_KM = 384_400.0;
    /** Altitude de l'orbite d'attente, en km, et durée d'un tour, en ticks (deux minutes de jeu). */
    private static final double PARKING_KM = FlightProfile.ORBIT_ALTITUDE_KM;
    private static final int ORBIT_TICKS = 2400;
    private static final int COLOR_PATH = 0xFF5FB0F5;
    private static final int COLOR_PLAN = 0xFFFFC857;

    /** Vrai quand la carte est ouverte. */
    public static boolean open;

    private OrbitMap() {
    }

    /** Distance au centre de la planète, en pixels, pour une altitude en km : linéaire au ras du sol, logarithmique au loin. */
    private static float radius(float planet, double altitudeKm) {
        return planet * (1.0F + 0.35F * (float) Math.log10(1.0 + Math.max(0.0, altitudeKm) / 40.0));
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, int width, int height, CapsuleEntity capsule, FlightHudPayload flight,
            boolean flightFresh, float partial) {
        graphics.fill(0, 0, width, height, 0xF6070A10);
        // Quelques étoiles, toujours les mêmes.
        for (int k = 0; k < 90; k++) {
            int sx = (int) ((k * 7919L) % width);
            int sy = (int) ((k * 104729L) % height);
            graphics.fill(sx, sy, sx + 1, sy + 1, k % 7 == 0 ? 0xFFE6E9EE : 0xFF6A7380);
        }
        long time = capsule.level().getGameTime();
        boolean lunar = capsule.isLunar();
        boolean inOrbit = capsule.getPhase() == CapsuleEntity.Phase.ORBIT;
        int node = inOrbit ? capsule.getNode() : CapsuleEntity.NODE_NONE;
        // Le plus lointain à montrer fixe l'échelle : la Lune pour un vaisseau lunaire ou un départ, sinon l'orbite basse.
        double reach = lunar || node >= CapsuleEntity.NODE_MOON ? MOON_KM : 4_000.0;
        float half = Math.min(width, height) * 0.44F;
        float planet = half / (1.0F + 0.35F * (float) Math.log10(1.0 + reach / 40.0));
        int cx = width / 2 + 40;
        int cy = height / 2;

        drawPlanet(graphics, cx, cy, planet);
        // L'atmosphère : jusqu'à cent kilomètres, la limite de l'espace.
        circle(graphics, cx, cy, radius(planet, 100.0), 0x605FB0F5, 1);
        if (reach >= MOON_KM) {
            // L'orbite de la Lune, et la Lune.
            float moon = radius(planet, MOON_KM - PLANET_KM);
            circle(graphics, cx, cy, moon, 0x40B9C0C7, 2);
            double angle = time / 24000.0 * Mth.TWO_PI;
            int mx = cx + Math.round((float) Math.cos(angle) * moon);
            int my = cy - Math.round((float) Math.sin(angle) * moon);
            disc(graphics, mx, my, 4, 0xFFB9BBBE);
            label(graphics, font, Component.translatable("map.orbitalis.moon").getString(), mx + 6, my - 4, 0xFFB9BBBE);
        }

        // Ce qu'on sait du vol, pour le panneau.
        String title;
        double altitude;
        double apoapsis;
        double periapsis;
        double period = 0.0;
        if (flightFresh && flight != null) {
            title = Component.translatable("map.orbitalis.ascent").getString();
            double[] ascent = drawAscent(graphics, font, cx, cy, planet, capsule, partial);
            altitude = flight.altitudeKm();
            apoapsis = ascent[0];
            periapsis = ascent.length > 1 ? ascent[1] : -1.0;
        } else if (inOrbit) {
            title = Component.translatable("map.orbitalis.orbit").getString();
            // La capsule fait le tour de son orbite, dans le sens des aiguilles d'une montre.
            double at = Math.PI / 2 - (time + partial) / ORBIT_TICKS * Mth.TWO_PI;
            float r = radius(planet, PARKING_KM);
            circle(graphics, cx, cy, r, COLOR_PATH, 1);
            altitude = apoapsis = periapsis = PARKING_KM;
            period = Mth.TWO_PI * Math.sqrt(Math.pow(PLANET_KM + PARKING_KM, 3) / MU);
            if (node != CapsuleEntity.NODE_NONE) {
                double[] after = drawNode(graphics, font, cx, cy, planet, capsule, node, at);
                apoapsis = after[0];
                periapsis = after[1];
                period = after[2];
            }
            marker(graphics, cx + Math.round((float) Math.cos(at) * r), cy - Math.round((float) Math.sin(at) * r), 0xFFFFFFFF);
        } else {
            title = Component.translatable(capsule.getPhase().messageKey()).getString();
            altitude = Math.max(0.0, capsule.getY() - capsule.level().getSeaLevel()) / 1000.0;
            apoapsis = periapsis = -1.0;
        }

        // Le panneau des chiffres, à gauche.
        int pw = 150;
        int px = 10;
        int py = 10;
        int rows = 5 + (node != CapsuleEntity.NODE_NONE ? 3 : 0);
        Ui.round(graphics, px, py, pw, 12 + rows * 11, 0xD014181E);
        Ui.frame(graphics, px, py, pw, 12 + rows * 11, 0xFF2A3038);
        int x = px + 6;
        int w = pw - 12;
        int y = py + 5;
        graphics.text(font, Ui.fit(font, title, w), x, y, COLOR_PATH, false);
        y += 13;
        row(graphics, font, x, y, w, "map.orbitalis.altitude", km(altitude), 0xFFE6E9EE);
        y += 11;
        row(graphics, font, x, y, w, "map.orbitalis.apoapsis", apoapsis < 0.0 ? "—" : apoapsis == Double.POSITIVE_INFINITY
                ? Component.translatable("map.orbitalis.escape").getString() : km(apoapsis), COLOR_PLAN);
        y += 11;
        String periText = flightFresh && periapsis < 0.0 ? Component.translatable("map.orbitalis.below").getString()
                : periapsis < 0.0 ? "—" : periapsis < 0.5 ? Component.translatable("map.orbitalis.reentry").getString() : km(periapsis);
        row(graphics, font, x, y, w, "map.orbitalis.periapsis", periText, periapsis >= 0.0 && periapsis < 100.0 ? 0xFFE0655F
                : periapsis >= 100.0 ? 0xFF5BC27A : COLOR_PLAN);
        y += 11;
        row(graphics, font, x, y, w, "map.orbitalis.period", period <= 0.0 || Double.isInfinite(period) ? "—" : duration(period), 0xFFE6E9EE);
        if (node != CapsuleEntity.NODE_NONE) {
            y += 14;
            graphics.text(font, Ui.fit(font, Component.translatable("pilot.orbitalis.node." + node).getString(), w), x, y, COLOR_PLAN, false);
            y += 11;
            int countdown = capsule.nodeCountdown();
            row(graphics, font, x, y, w, "map.orbitalis.ignition", countdown > 0 ? "T-" + (countdown + 19) / 20 + " s" : "T+" + (-countdown) / 20 + " s",
                    countdown > 0 ? 0xFFE6E9EE : 0xFFFFC857);
            y += 11;
            row(graphics, font, x, y, w, "map.orbitalis.dv", Math.round(capsule.nodeDone()) + " / " + Math.round(capsule.nodeDeltaV()) + " m/s", 0xFFE6E9EE);
        }
        Component close = Component.translatable("map.orbitalis.close", PilotControls.name(PilotControls.MAP));
        graphics.text(font, close, (width - font.width(close)) / 2, height - 14, 0xFF8D97A3, true);
    }

    /**
     * La montée : la trajectoire prévue, d'après le vol calculé de la fusée, et le chemin déjà parcouru ; le pas de tir est
     * en haut de la planète, la fusée part vers la droite. Rend l'apogée de la trajectoire, en km.
     */
    private static double[] drawAscent(GuiGraphicsExtractor graphics, Font font, int cx, int cy, float planet, CapsuleEntity capsule, float partial) {
        CockpitHud.Ascent ascent = CockpitHud.ascent(capsule, partial);
        if (ascent == null) {
            return new double[] {-1.0, -1.0};
        }
        RocketStats stats = ascent.stats();
        double now = ascent.progress();
        double top = 0.0;
        double topAngle = 0.0;
        int prevX = Integer.MIN_VALUE;
        int prevY = 0;
        int nowX = cx;
        int nowY = cy;
        for (int k = 0; k <= 120; k++) {
            double progress = k / 120.0;
            double altitude = FlightProfile.altitudeKm(stats, progress);
            double angle = Math.PI / 2 - FlightProfile.downrangeKm(stats, progress) / PLANET_KM;
            float r = radius(planet, altitude);
            int px = cx + Math.round((float) Math.cos(angle) * r);
            int py = cy - Math.round((float) Math.sin(angle) * r);
            boolean flown = progress <= now;
            if (prevX != Integer.MIN_VALUE && (flown || k % 2 == 0)) {
                line(graphics, prevX, prevY, px, py, flown ? COLOR_PATH : COLOR_PLAN);
            }
            if (altitude > top) {
                top = altitude;
                topAngle = angle;
            }
            if (flown) {
                nowX = px;
                nowY = py;
            }
            prevX = px;
            prevY = py;
        }
        if (FlightProfile.isOrbital(stats)) {
            circle(graphics, cx, cy, radius(planet, FlightProfile.targetAltitudeKm(stats)), 0x60FFC857, 3);
        }
        // L'orbite instantanée : celle qu'aurait la fusée si ses moteurs s'éteignaient maintenant. Elle part de sa position,
        // dans le sens du vol ; ce qui passe sous le sol n'est pas dessiné.
        double[] orbit = FlightProfile.orbitKm(stats, now);
        double here = Math.PI / 2 - FlightProfile.downrangeKm(stats, now) / PLANET_KM;
        if (orbit[3] > 1.0 && orbit[2] < 1.0) {
            double periAngle = here + orbit[4];
            int px0 = Integer.MIN_VALUE;
            int py0 = 0;
            for (int k = 0; k <= 240; k++) {
                double nu = k / 240.0 * Mth.TWO_PI;
                double rk = orbit[3] / (1.0 + orbit[2] * Math.cos(nu));
                if (rk < PLANET_KM) {
                    px0 = Integer.MIN_VALUE;
                    continue;
                }
                double angle = periAngle - nu;
                float rr = radius(planet, rk - PLANET_KM);
                int px = cx + Math.round((float) Math.cos(angle) * rr);
                int py = cy - Math.round((float) Math.sin(angle) * rr);
                if (px0 != Integer.MIN_VALUE) {
                    line(graphics, px0, py0, px, py, 0xA0E6E9EE);
                }
                px0 = px;
                py0 = py;
            }
            if (!Double.isInfinite(orbit[0])) {
                double apoAngle = periAngle - Math.PI;
                float ra = radius(planet, orbit[0]);
                int ax = cx + Math.round((float) Math.cos(apoAngle) * ra);
                int ay = cy - Math.round((float) Math.sin(apoAngle) * ra);
                disc(graphics, ax, ay, 2, COLOR_PLAN);
                label(graphics, font, "Ap " + km(orbit[0]), ax + 5, ay - 9, COLOR_PLAN);
            }
        }
        marker(graphics, nowX, nowY, 0xFFFFFFFF);
        return new double[] {orbit[0], orbit[1]};
    }

    /**
     * Une manœuvre préparée : son repère sur l'orbite, là où se fera l'allumage, et l'orbite qu'elle donnera, d'après la
     * force vive. Tant que rien n'est allumé, c'est l'orbite prévue (pour tout le delta-v), en pointillés ; pendant la
     * poussée, celle que donne ce qui est déjà fait, en trait plein. Rend l'apogée, le périgée (en km) et la période (s).
     */
    private static double[] drawNode(GuiGraphicsExtractor graphics, Font font, int cx, int cy, float planet, CapsuleEntity capsule, int node, double at) {
        int countdown = Math.max(0, capsule.nodeCountdown());
        double burnAngle = at - (double) countdown / ORBIT_TICKS * Mth.TWO_PI;
        float r = radius(planet, PARKING_KM);
        int nx = cx + Math.round((float) Math.cos(burnAngle) * r);
        int ny = cy - Math.round((float) Math.sin(burnAngle) * r);
        double done = capsule.nodeDone();
        boolean burning = done > 0.0;
        double dv = (burning ? done : capsule.nodeDeltaV()) / 1000.0;
        boolean retro = node == CapsuleEntity.NODE_DEORBIT;
        // L'orbite après la poussée : périgée (ou apogée, pour un freinage) au point d'allumage.
        double r0 = PLANET_KM + PARKING_KM;
        double v0 = Math.sqrt(MU / r0);
        double v = retro ? v0 - dv : v0 + dv;
        double energy = v * v / 2.0 - MU / r0;
        int color = burning ? COLOR_PLAN : 0xC0FFC857;
        double apo;
        double peri;
        double period;
        if (energy >= 0.0) {
            // Évasion : la trajectoire s'en va, et ne revient pas.
            apo = Double.POSITIVE_INFINITY;
            peri = PARKING_KM;
            period = Double.POSITIVE_INFINITY;
            double previousAngle = burnAngle;
            float previous = r;
            for (int k = 1; k <= 60; k++) {
                double angle = burnAngle - k * 0.035;
                float rr = r + k * k * 0.06F * planet / 20.0F;
                line(graphics, cx + Math.round((float) Math.cos(previousAngle) * previous), cy - Math.round((float) Math.sin(previousAngle) * previous),
                        cx + Math.round((float) Math.cos(angle) * rr), cy - Math.round((float) Math.sin(angle) * rr), color);
                previousAngle = angle;
                previous = rr;
            }
        } else {
            double a = -MU / (2.0 * energy);
            double far = 2.0 * a - r0;
            double e = Math.abs(far - r0) / (far + r0);
            // Le point d'allumage est le périgée d'une orbite agrandie, l'apogée d'une orbite réduite.
            apo = Math.max(r0, far) - PLANET_KM;
            peri = Math.min(r0, far) - PLANET_KM;
            period = Mth.TWO_PI * Math.sqrt(a * a * a / MU);
            double p = a * (1.0 - e * e);
            double periAngle = retro ? burnAngle + Math.PI : burnAngle;
            int px0 = Integer.MIN_VALUE;
            int py0 = 0;
            for (int k = 0; k <= 180; k++) {
                double angle = periAngle + k / 180.0 * Mth.TWO_PI;
                double rk = p / (1.0 + e * Math.cos(angle - periAngle));
                if (rk < PLANET_KM) {
                    px0 = Integer.MIN_VALUE;
                    continue;
                }
                float rr = radius(planet, rk - PLANET_KM);
                int px = cx + Math.round((float) Math.cos(angle) * rr);
                int py = cy - Math.round((float) Math.sin(angle) * rr);
                if (px0 != Integer.MIN_VALUE && (burning || k % 2 == 0)) {
                    line(graphics, px0, py0, px, py, color);
                }
                px0 = px;
                py0 = py;
            }
            double apoAngle = retro ? burnAngle : burnAngle + Math.PI;
            if (!retro) {
                float ra = radius(planet, apo);
                int ax = cx + Math.round((float) Math.cos(apoAngle) * ra);
                int ay = cy - Math.round((float) Math.sin(apoAngle) * ra);
                disc(graphics, ax, ay, 2, COLOR_PLAN);
                label(graphics, font, "Ap " + km(apo), ax + 5, ay - 9, COLOR_PLAN);
            } else if (peri < 100.0) {
                // Le périgée dans l'air : la rentrée, là où la trajectoire rencontre l'atmosphère.
                label(graphics, font, Component.translatable("map.orbitalis.reentry").getString(), cx - 20, cy + Math.round(planet) + 4, 0xFFE0655F);
            }
        }
        // Le repère de la manœuvre : un losange bleu.
        for (int k = 0; k <= 4; k++) {
            graphics.fill(nx - k, ny - 4 + k, nx + k + 1, ny - 3 + k, 0xFF5FD0FF);
            graphics.fill(nx - k, ny + 4 - k, nx + k + 1, ny + 5 - k, 0xFF5FD0FF);
        }
        return new double[] {apo, Math.max(0.0, peri), period};
    }

    private static void drawPlanet(GuiGraphicsExtractor graphics, int cx, int cy, float radius) {
        int r = Math.round(radius);
        // Une sphère éclairée d'en haut à gauche, par paliers ; des terres en cubes, toujours les mêmes.
        int cell = Math.max(3, r / 14);
        for (int j = -r; j <= r; j++) {
            int half = (int) Math.sqrt((double) r * r - (double) j * j);
            int runStart = -half;
            int runColor = 0;
            for (int i = -half; i <= half + 1; i++) {
                int color = 0;
                if (i <= half) {
                    float nx = i / (float) r;
                    float ny = -j / (float) r;
                    float nz = Mth.sqrt(Math.max(0.0F, 1.0F - nx * nx - ny * ny));
                    float light = Mth.clamp(-0.55F * nx + 0.45F * ny + 0.7F * nz, 0.0F, 1.0F);
                    int level = Math.round(light * 4.0F);
                    int gx = Math.floorDiv(i + r, cell);
                    int gy = Math.floorDiv(j + r, cell);
                    long h = gx * 73_856_093L ^ gy * 19_349_663L;
                    h ^= h >>> 13;
                    boolean land = ((h * 0x5bd1e995L) >>> 20 & 7) == 0 || ((gx * 3 + gy * 5) % 11 == 0 && (h & 3) == 0);
                    int base = land ? 0xFF4E8A36 : 0xFF1F69AE;
                    if (Math.abs(ny) > 0.88F) {
                        base = 0xFFEEF3F7;
                    }
                    color = scale(base, 0.35F + 0.65F * level / 4.0F);
                }
                if (color != runColor) {
                    if (runColor != 0) {
                        graphics.fill(cx + runStart, cy + j, cx + i, cy + j + 1, runColor);
                    }
                    runStart = i;
                    runColor = color;
                }
            }
        }
    }

    private static int scale(int color, float factor) {
        int r = Math.min(255, Math.round((color >> 16 & 255) * factor));
        int g = Math.min(255, Math.round((color >> 8 & 255) * factor));
        int b = Math.min(255, Math.round((color & 255) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Un cercle de points, autour du centre ; {@code gap} : un point tous les tant de degrés (1 pour un trait plein). */
    private static void circle(GuiGraphicsExtractor graphics, int cx, int cy, float radius, int color, int gap) {
        int steps = Math.max(48, Math.round(radius * 6.3F));
        for (int k = 0; k < steps; k += gap) {
            double angle = k * Mth.TWO_PI / steps;
            int x = cx + Math.round((float) Math.cos(angle) * radius);
            int y = cy - Math.round((float) Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int k = 0; k <= steps; k++) {
            int x = steps == 0 ? x0 : x0 + Math.round((x1 - x0) * k / (float) steps);
            int y = steps == 0 ? y0 : y0 + Math.round((y1 - y0) * k / (float) steps);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void disc(GuiGraphicsExtractor graphics, int x, int y, int radius, int color) {
        for (int j = -radius; j <= radius; j++) {
            int half = (int) Math.sqrt((double) radius * radius - (double) j * j);
            graphics.fill(x - half, y + j, x + half + 1, y + j + 1, color);
        }
    }

    /** La capsule : un petit carré blanc, cerné de sombre. */
    private static void marker(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF07090C);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    /** Une étiquette à droite de son point, ou à sa gauche si elle sortirait de l'écran. */
    private static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        int screen = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int width = font.width(text);
        int left = x + width > screen - 4 ? x - 10 - width : x;
        graphics.text(font, text, Math.max(2, left), Math.max(2, y), color, true);
    }

    private static void row(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String key, String value, int color) {
        Ui.row(graphics, font, Component.translatable(key), value, x, y, w, color);
    }

    private static String km(double value) {
        if (Double.isInfinite(value)) {
            return "∞";
        }
        return value >= 10_000.0 ? String.format(Locale.ROOT, "%,d km", Math.round(value)).replace(',', ' ') : Math.round(value) + " km";
    }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        return s >= 3600 ? String.format(Locale.ROOT, "%d h %02d min", s / 3600, s / 60 % 60) : String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
    }
}
