package com.orbitalis.client;

import java.util.Locale;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.FlightHudPayload;
import com.orbitalis.client.screen.Ui;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Affichage tête haute du cockpit : ce que le pilote d'une fusée a sous les yeux, assis dans sa cabine.
 * <p>
 * Au sol, il résume la séquence de lancement : le plein, la pression, le bras, le déluge, la poussée, et l'étape à
 * faire maintenant. En montée, la fusée suit sa trajectoire d'elle-même (voir {@link #drawFlight}) : à gauche, la
 * frise des étapes ; au centre, la décision à prendre ou la manœuvre de stabilisation demandée ; tout en bas, une ligne
 * de télémétrie.
 * <p>
 * Une fois la cabine séparée de sa fusée, il suit le retour : la phase, la hauteur, la vitesse de chute, et la touche
 * de l'action possible ; pour une descente au moteur, la boule d'attitude et ses jauges. Il reprend les couleurs de
 * l'écran de séquence : panneaux sombres, bleu clair, vert, jaune, rouge.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class CockpitHud {
    private static final int COLOR_PANEL = 0xC014181E;
    private static final int COLOR_EDGE = 0xFF2A3038;
    private static final int COLOR_ACCENT = 0xFF5FB0F5;
    private static final int COLOR_TEXT = 0xFFE6E9EE;
    private static final int COLOR_MUTED = 0xFF8D97A3;
    private static final int COLOR_OK = 0xFF5BC27A;
    private static final int COLOR_BAD = 0xFFE0655F;
    private static final int COLOR_WARNING = 0xFFFFC857;

    private static FlightHudPayload flight;
    private static LaunchStatusPayload status;
    /** Ticks écoulés depuis la dernière nouvelle du vol, et de la séquence au sol : au-delà d'une seconde, elles sont périmées. */
    private static int flightAge = 1000;
    private static int statusAge = 1000;
    private static int clock;
    /**
     * Les alertes que l'affichage vient de constater (voir {@link CockpitAlerts}) : une manœuvre qui presse, un crash
     * imminent, un vaisseau trop penché, des parachutes à armer. Chacune sonne de son côté.
     */
    private static int alerts;
    /** Pour l'aperçu du mod : montre l'affichage sans être assis dans une cabine. */
    public static boolean forceShow;

    private CockpitHud() {
    }

    public static void onFlight(FlightHudPayload payload) {
        flight = payload;
        flightAge = 0;
    }

    public static void onStatus(LaunchStatusPayload payload) {
        status = payload;
        statusAge = 0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        flightAge++;
        statusAge++;
        clock++;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isPaused()) {
            CockpitAlerts.tick(minecraft.level == null ? 0 : alerts);
        }
        // L'affichage les redéclare à chaque image tant que le danger dure.
        alerts = 0;
        // La vue carte s'ouvre et se ferme d'un appui ; elle se referme quand on quitte la cabine.
        if (PilotControls.cabin() == null) {
            OrbitMap.open = false;
        } else {
            while (PilotControls.MAP.consumeClick()) {
                OrbitMap.open = !OrbitMap.open;
            }
        }
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null
                || !(forceShow || minecraft.player.getVehicle() instanceof CapsuleEntity)) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        CapsuleEntity capsule = PilotControls.cabin();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (OrbitMap.open && capsule != null) {
            OrbitMap.draw(graphics, minecraft.font, width, height, capsule, flight, flight != null && flightAge < 20, partial);
            return;
        }
        if (flight != null && flightAge < 20) {
            drawFlight(graphics, minecraft.font, width, height, capsule, partial);
        } else if (capsule != null && capsule.getPhase() == CapsuleEntity.Phase.POWERED_DESCENT) {
            drawDescent(graphics, minecraft.font, width, height, capsule, partial);
        } else if (capsule != null && capsule.getPhase() != CapsuleEntity.Phase.PAD && capsule.getPhase() != CapsuleEntity.Phase.ASCENT) {
            drawReturn(graphics, minecraft.font, width, height, capsule);
        } else if (status != null && statusAge < 40 && status.phase() != LaunchTableBlockEntity.Phase.FLIGHT.ordinal()) {
            drawGround(graphics, minecraft.font, width, height);
        }
    }

    /** Un panneau : fond sombre et translucide, coins adoucis, contour fin, comme le bloc d'instruments du vol. */
    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Ui.round(graphics, x - 1, y - 1, w + 2, h + 2, 0xE00C1015);
        Ui.frame(graphics, x - 1, y - 1, w + 2, h + 2, 0x80394450);
    }

    private static void row(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String key, String value, int color) {
        graphics.text(font, Component.translatable(key), x, y, COLOR_MUTED, false);
        graphics.text(font, value, x + w - font.width(value), y, color, false);
    }

    private static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, float fill, int color) {
        graphics.fill(x, y, x + w, y + 3, 0xFF0B0E12);
        graphics.fill(x, y, x + Math.round(w * Mth.clamp(fill, 0.0F, 1.0F)), y + 3, color);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }

    // ------------------------------------------------------------------
    // En vol
    // ------------------------------------------------------------------

    private static final int COLOR_SKY = 0xFF3A6688;
    private static final int COLOR_GROUND = 0xFF54473A;
    private static final int COLOR_GOLD = 0xFFFFD23E;

    /** Pour l'aperçu du mod, sans pas de tir : la fusée et l'avancement du vol à montrer. */
    private static RocketStats previewStats;
    private static double previewProgress;

    public static void preview(RocketStats stats, double progress) {
        previewStats = stats;
        previewProgress = progress;
    }

    /** Les décisions du vol, dans l'ordre de leurs bits. */
    private static final String[] DECISIONS = {"throttle", "stage", "fairing", "cutoff"};
    /** La décision ouverte en ce moment (ses bits), l'instant (en ticks du client) où elle s'est ouverte, et la durée de sa fenêtre. */
    private static int openSeen;
    private static int openedAt;
    private static int openTicks = 1;
    /** Ce qui était fait, et manqué, au dernier passage ; le dernier retour à montrer (rang de la décision, réussie ou non, instant). */
    private static int doneSeen;
    private static int missedSeen;
    private static int lastSeconds;
    private static int feedbackStep = -1;
    private static boolean feedbackOk;
    private static int feedbackAt;

    /**
     * L'écran de vol, en montée. La fusée suit sa trajectoire d'elle-même ; le joueur prend les décisions du vol, chacune
     * dans sa fenêtre, d'une seule touche : réduire les gaz au passage de la pression maximale, séparer l'étage vide,
     * larguer la coiffe, couper les moteurs. À gauche, la frise de la montée ; au centre, en bas, la décision à prendre
     * (sa touche, ce qu'elle fait, le temps qui reste), son résultat, ou la prochaine ; tout en bas, une ligne de
     * télémétrie. La carte (M) garde le détail de l'orbite.
     */
    private static void drawFlight(GuiGraphicsExtractor graphics, Font font, int width, int height, CapsuleEntity capsule, float partial) {
        FlightHudPayload data = flight;
        RocketStats stats = previewStats;
        double progress = previewProgress;
        Ascent ascent = ascent(capsule, partial);
        if (ascent != null) {
            stats = ascent.stats();
            progress = ascent.progress();
        }
        int bits = data.flightBits();
        boolean auto = data.assist() >= 2;
        if (data.stress() > 0.0F) {
            alerts |= CockpitAlerts.CRASH;
        }
        trackDecisions(data);

        if (stats != null) {
            // La frise s'arrête au-dessus de la carte de décision, qui ne la recouvre jamais.
            drawTimeline(graphics, font, 18, 44, height - 90, Math.min(110, width / 4 + 4), stats, progress);
        }
        drawDecision(graphics, font, width, height, data, stats, progress, auto);
        drawQte(graphics, font, width, height, data);
        drawTelemetry(graphics, font, width, height, data, stats, progress);

        String clockText = String.format(Locale.ROOT, "T+%02d:%02d", data.seconds() / 60, data.seconds() % 60);
        graphics.text(font, clockText, width - 10 - font.width(clockText), 8, COLOR_MUTED, true);
    }

    /** La fusée en montée et l'avancement de son vol, de 0 à 1. */
    record Ascent(RocketStats stats, double progress) {
    }

    /**
     * La fusée que suit la capsule, et où en est son vol : d'après le pas de tir s'il est chargé, sinon d'après la
     * capsule et le dernier paquet de vol, recalé sur le temps écoulé depuis. {@code null} si on ne sait pas.
     */
    static Ascent ascent(CapsuleEntity capsule, float partial) {
        if (capsule == null) {
            return null;
        }
        if (capsule.getTable() != null && capsule.level().getBlockEntity(capsule.getTable()) instanceof LaunchTableBlockEntity table
                && table.hasRocket() && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT) {
            return new Ascent(table.rocketStats(), Mth.clamp((table.phaseTicks() + partial) / table.flightTicks(), 0.0F, 1.0F));
        }
        RocketStats stats = capsule.flightStats();
        if (stats == null || flight == null) {
            return null;
        }
        double ticks = flight.ticks() + Math.min(flightAge, 20) + partial;
        return new Ascent(stats, Mth.clamp(ticks / FlightProfile.flightTicks(stats), 0.0, 1.0));
    }

    /** Suit les décisions d'un tick à l'autre : celle qui s'ouvre, celle qui vient d'être prise ou manquée. */
    private static void trackDecisions(FlightHudPayload data) {
        int bits = data.flightBits();
        if (data.seconds() < lastSeconds) {
            // Un nouveau vol : on oublie le précédent.
            doneSeen = missedSeen = openSeen = 0;
            feedbackStep = -1;
        }
        lastSeconds = data.seconds();
        int open = bits >> 8 & 15;
        if (open != openSeen) {
            openSeen = open;
            openedAt = clock;
            openTicks = Math.max(20, (data.flightWindow() + 1) * 20);
        }
        int done = bits >> 4 & 15;
        int missed = bits >> 12 & 15;
        int newDone = done & ~doneSeen;
        int newMissed = missed & ~missedSeen;
        if (newDone != 0) {
            feedbackStep = Integer.numberOfTrailingZeros(newDone);
            feedbackOk = true;
            feedbackAt = clock;
        } else if (newMissed != 0) {
            feedbackStep = Integer.numberOfTrailingZeros(newMissed);
            feedbackOk = false;
            feedbackAt = clock;
        }
        doneSeen = done;
        missedSeen = missed;
    }

    /**
     * La décision, au centre en bas. Ouverte : une carte avec la touche en grand, ce qu'elle fait, et une jauge qui se
     * vide pendant la fenêtre (bleue, puis orange, puis rouge). Juste prise : « fait » en vert ; manquée : en rouge.
     * Sinon, discrètement, la prochaine décision et le temps qu'il reste avant elle.
     */
    private static void drawDecision(GuiGraphicsExtractor graphics, Font font, int width, int height, FlightHudPayload data, RocketStats stats,
            double progress, boolean auto) {
        int bits = data.flightBits();
        int open = bits >> 8 & 15;
        int cardW = Math.min(240, width - 40);
        int cardX = (width - cardW) / 2;
        int cardY = height - 82;
        if (open != 0 && !auto) {
            int k = Integer.numberOfTrailingZeros(open);
            float left = Mth.clamp(1.0F - (clock - openedAt) / (float) openTicks, 0.0F, 1.0F);
            int color = left > 0.5F ? COLOR_ACCENT : left > 0.25F ? COLOR_WARNING : COLOR_BAD;
            if (data.flightWindow() <= 3) {
                alerts |= CockpitAlerts.MANEUVER;
            }
            float pulse = 0.5F + 0.5F * Mth.sin(clock * 0.35F);
            Ui.round(graphics, cardX, cardY, cardW, 44, 0xD80C1015);
            Ui.frame(graphics, cardX, cardY, cardW, 44, Ui.alpha(color, 0.55F + 0.45F * pulse));
            // La touche, en grand.
            String key = PilotControls.name(PilotControls.STEP).getString().toUpperCase(Locale.ROOT);
            int keyW = Math.max(44, font.width(key) + 14);
            Ui.round(graphics, cardX + 8, cardY + 8, keyW, 22, Ui.alpha(color, 0.25F + 0.2F * pulse));
            Ui.frame(graphics, cardX + 8, cardY + 8, keyW, 22, color);
            graphics.text(font, key, cardX + 8 + (keyW - font.width(key)) / 2, cardY + 15, COLOR_TEXT, false);
            // Ce qu'elle fait, et le temps qui reste.
            int tx = cardX + 16 + keyW;
            int room = cardX + cardW - 8 - tx;
            graphics.text(font, Ui.fit(font, Component.translatable("sequence.orbitalis.step." + DECISIONS[k]).getString(), room), tx, cardY + 9,
                    COLOR_TEXT, false);
            String now = Component.translatable("pilot.orbitalis.decide.now").getString() + " · " + data.flightWindow() + " s";
            graphics.text(font, Ui.fit(font, now, room), tx, cardY + 21, color, false);
            // La fenêtre qui se referme.
            graphics.fill(cardX + 8, cardY + 36, cardX + cardW - 8, cardY + 38, 0x40FFFFFF);
            graphics.fill(cardX + 8, cardY + 36, cardX + 8 + Math.round((cardW - 16) * left), cardY + 38, color);
            return;
        }
        // Le résultat de la dernière décision, deux secondes, en s'effaçant.
        // Un QTE prend la place : ni le résultat de la dernière décision, ni l'annonce de la suivante ne se glissent dessous.
        if ((data.qte() & 3) != 0) {
            return;
        }
        if (feedbackStep >= 0 && clock - feedbackAt < 50) {
            float fade = 1.0F - Math.max(0, clock - feedbackAt - 30) / 20.0F;
            int color = feedbackOk ? COLOR_OK : COLOR_BAD;
            String name = Component.translatable("sequence.orbitalis.step." + DECISIONS[feedbackStep]).getString();
            String text = Component.translatable(feedbackOk ? "pilot.orbitalis.decide.done" : "pilot.orbitalis.decide.missed", name).getString();
            text = Ui.fit(font, text, cardW - 20);
            int w = font.width(text) + 24;
            int x = (width - w) / 2;
            int y = cardY + 16;
            Ui.round(graphics, x, y, w, 16, Ui.alpha(0xFF0C1015, 0.85F * fade));
            Ui.frame(graphics, x, y, w, 16, Ui.alpha(color, 0.8F * fade));
            graphics.fill(x + 6, y + 6, x + 10, y + 10, Ui.alpha(color, fade));
            graphics.text(font, text, x + 15, y + 4, Ui.alpha(color, Math.max(0.1F, fade)), false);
            return;
        }
        // Sinon : l'ordinateur décide, ou la prochaine décision, ou ce que fait la fusée entre deux étages.
        String text;
        int color = COLOR_MUTED;
        if (auto) {
            text = Component.translatable("pilot.orbitalis.decide.auto").getString();
        } else if ((bits >> 24 & 1) != 0) {
            text = Component.translatable("pilot.orbitalis.coast").getString();
            color = COLOR_ACCENT;
        } else {
            int pending = bits & 15 & ~(bits >> 4 & 15) & ~(bits >> 12 & 15);
            if (pending == 0) {
                return;
            }
            int k = Integer.numberOfTrailingZeros(pending);
            String name = Component.translatable("sequence.orbitalis.step." + DECISIONS[k]).getString();
            int eta = stats == null ? -1 : decisionEta(k, stats, progress, data.seconds());
            text = Component.translatable("pilot.orbitalis.decide.next", name).getString() + (eta > 0 ? " · " + eta + " s" : "");
        }
        text = Ui.fit(font, text, cardW - 20);
        int w = font.width(text) + 16;
        int x = (width - w) / 2;
        int y = cardY + 16;
        Ui.round(graphics, x, y, w, 15, 0xA00C1015);
        graphics.text(font, text, x + 8, y + 4, color, false);
    }

    /** Les touches des QTE, dans l'ordre de leur numéro : W, S, A, D (telles que le joueur les a réglées). */
    private static final net.minecraft.client.KeyMapping[] QTE_KEYS = {PilotControls.PITCH_DOWN, PilotControls.PITCH_UP, PilotControls.YAW_LEFT,
            PilotControls.YAW_RIGHT};

    /**
     * Le QTE de stabilisation, au-dessus de la décision. En cours : « STABILISEZ LA FUSÉE », la touche en grand (elle se
     * remplit de vert pendant qu'on la tient), ce qu'il faut faire, et le temps qui reste. Réussi : « trajectoire
     * tenue ». Manqué : « la fusée dévie », et l'alerte de décrochage sonne tant qu'elle est de travers.
     */
    private static void drawQte(GuiGraphicsExtractor graphics, Font font, int width, int height, FlightHudPayload data) {
        int qte = data.qte();
        int state = qte & 3;
        if (state == 0) {
            return;
        }
        int key = qte >> 2 & 3;
        boolean hold = (qte >> 4 & 1) != 0;
        int left = qte >> 5 & 255;
        float held = (qte >> 13 & 127) / 100.0F;
        int cardW = Math.min(240, width - 40);
        int cardX = (width - cardW) / 2;
        // Au-dessus de la carte des décisions, si une étape est ouverte en même temps (ce que le tirage évite).
        int cardY = (data.flightBits() >> 8 & 15) != 0 ? height - 132 : height - 82;
        if (state == LaunchTableBlockEntity.QTE_ACTIVE) {
            alerts |= CockpitAlerts.MANEUVER;
            float pulse = 0.5F + 0.5F * Mth.sin(clock * 0.45F);
            Ui.round(graphics, cardX, cardY, cardW, 44, 0xFA1A1408);
            Ui.frame(graphics, cardX, cardY, cardW, 44, Ui.alpha(COLOR_WARNING, 0.55F + 0.45F * pulse));
            String name = PilotControls.name(QTE_KEYS[key]).getString().toUpperCase(Locale.ROOT);
            int keyW = Math.max(28, font.width(name) + 14);
            int kx = cardX + 8;
            int ky = cardY + 8;
            Ui.round(graphics, kx, ky, keyW, 22, 0xC00C1015);
            if (hold && held > 0.0F) {
                int fill = Math.round(22 * held);
                graphics.fill(kx + 1, ky + 22 - fill, kx + keyW - 1, ky + 22, Ui.alpha(COLOR_OK, 0.7F));
            }
            Ui.frame(graphics, kx, ky, keyW, 22, COLOR_WARNING);
            graphics.text(font, name, kx + (keyW - font.width(name)) / 2, ky + 7, COLOR_TEXT, false);
            int tx = kx + keyW + 8;
            int room = cardX + cardW - 8 - tx;
            graphics.text(font, Ui.fit(font, Component.translatable("pilot.orbitalis.qte.title").getString(), room), tx, cardY + 9, COLOR_WARNING, false);
            String what = Component.translatable(hold ? "pilot.orbitalis.qte.hold" : "pilot.orbitalis.qte.press", name).getString();
            graphics.text(font, Ui.fit(font, what, room), tx, cardY + 21, COLOR_TEXT, false);
            float share = Mth.clamp(left / (float) LaunchTableBlockEntity.QTE_WINDOW, 0.0F, 1.0F);
            int color = share > 0.5F ? COLOR_WARNING : COLOR_BAD;
            graphics.fill(cardX + 8, cardY + 36, cardX + cardW - 8, cardY + 38, 0x40FFFFFF);
            graphics.fill(cardX + 8, cardY + 36, cardX + 8 + Math.round((cardW - 16) * share), cardY + 38, color);
            return;
        }
        boolean ok = state == LaunchTableBlockEntity.QTE_DONE;
        if (!ok) {
            alerts |= CockpitAlerts.STALL;
        }
        String text = Ui.fit(font, Component.translatable(ok ? "pilot.orbitalis.qte.done" : "pilot.orbitalis.qte.failed").getString(), cardW - 20);
        int color = ok ? COLOR_OK : COLOR_BAD;
        int w = font.width(text) + 24;
        int x = (width - w) / 2;
        int y = cardY + 16;
        Ui.round(graphics, x, y, w, 16, 0xFA0C1015);
        Ui.frame(graphics, x, y, w, 16, ok ? Ui.alpha(color, 0.8F) : Ui.alpha(color, 0.5F + 0.5F * Mth.sin(clock * 0.5F)));
        graphics.fill(x + 6, y + 6, x + 10, y + 10, color);
        graphics.text(font, text, x + 15, y + 4, color, false);
    }

    /** Dans combien de secondes s'ouvre cette décision, d'après le vol calculé ; -1 si on ne le sait pas. */
    private static int decisionEta(int decision, RocketStats stats, double progress, int seconds) {
        double flightSeconds = FlightProfile.flightTicks(stats) / 20.0;
        if (decision == 0) {
            return Math.max(0, LaunchTableBlockEntity.maxQOpenTick() / 20 - seconds);
        }
        String key = decision == 1 ? "pilot.orbitalis.track.stage" : decision == 2 ? "pilot.orbitalis.track.space" : "pilot.orbitalis.track.orbit";
        for (Object[] step : milestones(stats)) {
            if (key.equals(step[1]) && (Double) step[0] > progress) {
                return (int) Math.ceil(((Double) step[0] - progress) * flightSeconds);
            }
        }
        return -1;
    }

    /** Tout en bas : la vitesse, l'altitude, l'apogée et l'étage, sur une ligne ; la structure, si elle souffre. */
    private static void drawTelemetry(GuiGraphicsExtractor graphics, Font font, int width, int height, FlightHudPayload data, RocketStats stats,
            double progress) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        parts.add(number(data.speed()) + " m/s");
        parts.add(number(data.altitudeKm()) + " km");
        if (stats != null) {
            double apoapsis = FlightProfile.orbitKm(stats, progress)[0];
            parts.add("Ap " + (Double.isInfinite(apoapsis) ? "∞" : number(apoapsis) + " km"));
            java.util.List<float[]> stack = stageStack(stats, progress);
            for (float[] stage : stack) {
                if (stage[2] == 1.0F && stack.size() > 1) {
                    parts.add(Component.translatable("pilot.orbitalis.visor.stage", (int) stage[0], stack.size()).getString());
                }
            }
        }
        String line = Ui.fit(font, String.join("   ·   ", parts), width - 40);
        int w = font.width(line) + 20;
        int x = (width - w) / 2;
        int y = height - 22;
        Ui.round(graphics, x, y, w, 15, 0xA00C1015);
        graphics.text(font, line, x + 10, y + 4, COLOR_TEXT, false);
        if (data.stress() > 0.0F) {
            String structure = Component.translatable("pilot.orbitalis.visor.structure", Math.round(100.0F - data.stress() * 100.0F)).getString();
            graphics.text(font, structure, (width - font.width(structure)) / 2, y - 12, data.stress() > 0.6F ? COLOR_BAD : COLOR_WARNING, true);
        }
    }

    /** Les étapes de la montée de la fusée qu'on a calculées en dernier : la fusée, et chaque étape (avancement, nom). */
    private static RocketStats milestonesFor;
    private static java.util.List<Object[]> milestones = java.util.List.of();

    /**
     * Les étapes de la montée, de l'avancement du vol où elles arrivent : le décollage, la pression maximale, chaque
     * séparation, l'entrée dans l'espace, et l'orbite (ou l'apogée, ou l'extinction des moteurs). Calculées une fois par
     * fusée, depuis son vol calculé.
     */
    private static java.util.List<Object[]> milestones(RocketStats stats) {
        if (stats == milestonesFor) {
            return milestones;
        }
        java.util.List<Object[]> list = new java.util.ArrayList<>();
        list.add(new Object[] {0.0, "pilot.orbitalis.track.liftoff", 0});
        list.add(new Object[] {LaunchTableBlockEntity.maxQTick() / (double) FlightProfile.flightTicks(stats), "pilot.orbitalis.track.maxq", 0});
        boolean ended = false;
        for (com.orbitalis.phase1.spaceport.FlightSimulation.Event event : FlightProfile.flight(stats).events()) {
            String key = switch (event.type()) {
                case STAGING -> "pilot.orbitalis.track.stage";
                case SPACE -> "pilot.orbitalis.track.space";
                case ORBIT -> "pilot.orbitalis.track.orbit";
                case APOGEE -> "pilot.orbitalis.track.apogee";
                case BURNOUT -> "pilot.orbitalis.track.burnout";
            };
            if (event.type() == com.orbitalis.phase1.spaceport.FlightSimulation.EventType.BURNOUT && ended) {
                continue;
            }
            ended |= event.type() == com.orbitalis.phase1.spaceport.FlightSimulation.EventType.ORBIT;
            // L'instant de l'événement, dans le vol calculé, ramené à l'avancement du vol en jeu.
            double at = 1.0;
            for (int k = 0; k <= 400; k++) {
                if (FlightProfile.simSeconds(stats, k / 400.0) >= event.time()) {
                    at = k / 400.0;
                    break;
                }
            }
            list.add(new Object[] {at, key, event.type() == com.orbitalis.phase1.spaceport.FlightSimulation.EventType.STAGING
                    ? event.stage() - 1 : event.stage()});
        }
        list.sort(java.util.Comparator.comparingDouble(row -> (Double) row[0]));
        milestonesFor = stats;
        milestones = list;
        return list;
    }

    /**
     * La montée, à gauche, dans un panneau sombre : un titre, puis une ligne verticale du décollage (en bas) à l'orbite
     * (en haut), ponctuée des étapes du vol. La part franchie se remplit, parcourue d'un reflet qui monte ; la fusée est
     * un repère lumineux qui pulse. Une étape franchie passe au vert, et un anneau s'en échappe ; la suivante pulse
     * doucement, avec le temps qu'il reste pour l'atteindre ; les autres attendent. Le texte est à sa taille réelle :
     * réduit, il se déformerait.
     */
    private static void drawTimeline(GuiGraphicsExtractor graphics, Font font, int x, int top, int bottom, int labelRoom, RocketStats stats,
            double now) {
        int cardW = Math.min(124, labelRoom + 24);
        if (bottom - top < 90 || cardW < 70) {
            return;
        }
        int cardX = x - 10;
        Ui.round(graphics, cardX, top, cardW, bottom - top, 0xB00C1015);
        Ui.frame(graphics, cardX, top, cardW, bottom - top, 0x80394450);
        graphics.text(font, Component.translatable("pilot.orbitalis.chart.title"), cardX + 6, top + 5, COLOR_MUTED, false);
        String percent = Math.round(now * 100.0) + " %";
        graphics.text(font, percent, cardX + cardW - 6 - font.width(percent), top + 5, COLOR_TEXT, false);
        graphics.fill(cardX + 6, top + 16, cardX + cardW - 6, top + 17, 0x40394450);
        int lineX = cardX + 10;
        int lineTop = top + 26;
        int lineBottom = bottom - 10;
        int length = lineBottom - lineTop;
        java.util.List<Object[]> steps = milestones(stats);
        double flightSeconds = FlightProfile.flightTicks(stats) / 20.0;
        int fill = lineBottom - (int) Math.round(length * Mth.clamp(now, 0.0, 1.0));
        // Le trait, puis sa part franchie, et le reflet qui la remonte.
        graphics.fill(lineX, lineTop, lineX + 2, lineBottom, 0x30FFFFFF);
        graphics.fill(lineX, fill, lineX + 2, lineBottom, Ui.alpha(COLOR_ACCENT, 0.85F));
        int run = lineBottom - fill;
        if (run > 12) {
            // Le reflet ne quitte jamais la part franchie : hors d'elle, il dessinerait une bande blanche parasite.
            int shine = lineBottom - (clock * 2 % (run + 8));
            int from = Math.max(fill, shine - 8);
            int to = Math.min(lineBottom, shine);
            if (to > from) {
                graphics.fill(lineX, from, lineX + 2, to, 0xC0FFFFFF);
            }
        }
        Object[] next = null;
        for (Object[] step : steps) {
            if ((Double) step[0] > now) {
                next = step;
                break;
            }
        }
        int textX = lineX + 9;
        int textRoom = cardX + cardW - 6 - textX;
        // Les étiquettes se rangent du haut vers le bas : deux étapes trop proches, la seconde descend d'un cran. Aucune
        // ne se perd, ni ne chevauche une autre.
        int[] labelYs = new int[steps.size()];
        int below = Integer.MIN_VALUE;
        for (int k = steps.size() - 1; k >= 0; k--) {
            int sy = lineBottom - (int) Math.round(length * (Double) steps.get(k)[0]);
            labelYs[k] = Math.max(sy - 3, below + 10);
            below = labelYs[k];
        }
        int index = 0;
        for (Object[] step : steps) {
            double at = (Double) step[0];
            int sy = lineBottom - (int) Math.round(length * at);
            int labelY = labelYs[index++];
            boolean done = at <= now;
            boolean upcoming = step == next;
            int color = done ? COLOR_OK : upcoming ? COLOR_ACCENT : 0xFF5A636E;
            if (done) {
                graphics.fill(lineX - 2, sy - 2, lineX + 4, sy + 4, 0xFF07090C);
                graphics.fill(lineX - 1, sy - 1, lineX + 3, sy + 3, color);
                // L'étape vient d'être franchie : un anneau s'en échappe, s'élargit et s'efface.
                double since = (now - at) * flightSeconds;
                if (since < 1.2) {
                    float t = (float) (since / 1.2);
                    int r = 3 + Math.round(t * 7.0F);
                    int ring = Ui.alpha(COLOR_OK, 0.8F * (1.0F - t));
                    graphics.fill(lineX + 1 - r, sy + 1 - r, lineX + 1 + r, sy + 2 - r, ring);
                    graphics.fill(lineX + 1 - r, sy + r, lineX + 1 + r, sy + r + 1, ring);
                    graphics.fill(lineX + 1 - r, sy + 1 - r, lineX + 2 - r, sy + 1 + r, ring);
                    graphics.fill(lineX + r, sy + 1 - r, lineX + r + 1, sy + 1 + r, ring);
                }
            } else {
                float glow = upcoming ? 0.55F + 0.45F * Mth.sin(clock * 0.25F) : 1.0F;
                graphics.fill(lineX - 2, sy - 2, lineX + 4, sy + 4, Ui.alpha(color, glow));
                graphics.fill(lineX - 1, sy - 1, lineX + 3, sy + 3, 0xFF0C1015);
            }
            if (labelY > lineBottom + 2) {
                continue;
            }
            Component name = (Integer) step[2] > 0 && "pilot.orbitalis.track.stage".equals(step[1])
                    ? Component.translatable((String) step[1], (Integer) step[2]) : Component.translatable((String) step[1]);
            String when = upcoming ? Math.max(0, Math.round((at - now) * flightSeconds)) + " s" : "";
            String label = Ui.fit(font, name.getString(), textRoom - (when.isEmpty() ? 0 : font.width(when) + 4));
            graphics.text(font, label, textX, labelY, done ? COLOR_TEXT : upcoming ? COLOR_ACCENT : COLOR_MUTED, false);
            if (!when.isEmpty()) {
                graphics.text(font, when, cardX + cardW - 6 - font.width(when), labelY, COLOR_MUTED, false);
            }
        }
        // La fusée : un repère lumineux sur le trait, qui pulse.
        float pulse = 0.5F + 0.5F * Mth.sin(clock * 0.3F);
        graphics.fill(lineX - 3, fill - 3, lineX + 5, fill + 4, Ui.alpha(COLOR_ACCENT, 0.2F + 0.25F * pulse));
        graphics.fill(lineX - 1, fill - 1, lineX + 3, fill + 2, 0xFFFFFFFF);
    }

    /**
     * La pile d'étages, du premier au dernier : pour chacun, son numéro, son delta-v, son état (0 largué, 1 allumé,
     * 2 à venir) et la part de ses ergols qui reste. L'allumage de chaque étage vient du vol calculé de la fusée.
     */
    private static java.util.List<float[]> stageStack(RocketStats stats, double progress) {
        double now = FlightProfile.simSeconds(stats, progress);
        int current = FlightProfile.stage(stats, progress);
        java.util.List<com.orbitalis.phase1.spaceport.FlightSimulation.Event> events = FlightProfile.flight(stats).events();
        java.util.Map<Integer, float[]> stages = new java.util.LinkedHashMap<>();
        for (RocketStats.StageStats stage : stats.stages()) {
            if (stage.propellant().isLowThrust()) {
                continue;
            }
            float[] row = stages.computeIfAbsent(stage.number(), number -> new float[] {number, 0.0F, 2.0F, 1.0F, 0.0F});
            row[1] += (float) stage.deltaV();
            row[4] = Math.max(row[4], (float) stage.burnTime());
        }
        for (float[] row : stages.values()) {
            int number = (int) row[0];
            row[2] = number < current ? 0.0F : number == current ? 1.0F : 2.0F;
            if (number == current) {
                double start = 0.0;
                for (var event : events) {
                    if (event.type() == com.orbitalis.phase1.spaceport.FlightSimulation.EventType.STAGING && event.stage() == number) {
                        start = event.time();
                    }
                }
                row[3] = row[4] <= 0.0F ? 0.0F : (float) Mth.clamp(1.0 - (now - start) / row[4], 0.0, 1.0);
            }
        }
        return java.util.List.copyOf(stages.values());
    }

    /** Une ligne de commande : le nom de l'axe, et ses deux touches. */
    private static void keys(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String key, net.minecraft.client.KeyMapping first,
            net.minecraft.client.KeyMapping second) {
        row(graphics, font, x, y, w, key, PilotControls.name(first).getString() + " / " + PilotControls.name(second).getString(), COLOR_ACCENT);
    }

    /** Jauge verticale à côté de la boule : une piste, son remplissage, et son nom au-dessus. */
    private static void dial(GuiGraphicsExtractor graphics, Font font, int x, int y, int h, float fill, int color, String key, boolean right) {
        graphics.fill(x - 1, y - 1, x + 9, y + h + 1, COLOR_EDGE);
        graphics.fill(x, y, x + 8, y + h, 0xFF0B0E12);
        int filled = Math.round(h * Mth.clamp(fill, 0.0F, 1.0F));
        graphics.fill(x + 1, y + h - filled, x + 7, y + h, color);
        for (int k = 1; k < 4; k++) {
            graphics.fill(x, y + h * k / 4, x + 3, y + h * k / 4 + 1, COLOR_EDGE);
        }
        Component label = Component.translatable(key);
        graphics.text(font, label, right ? x + 11 : x - 3 - font.width(label), y + h - 8, COLOR_MUTED, true);
    }

    /**
     * La boule d'attitude. Chaque point de son disque est un rayon partant de la fusée : on cherche où il pointe dans
     * le monde, et on le peint de la couleur de ce qui s'y trouve (ciel, sol, horizon, graduations). Le centre du
     * disque est le nez de la fusée ; le haut du disque est à l'opposé du sens du tir, si bien que piquer fait monter
     * la boule, comme sur un vrai instrument.
     *
     * @param down   sens du tir, à l'horizontale
     * @param course direction de la trajectoire à tenir
     */
    private static void navball(GuiGraphicsExtractor graphics, Font font, int cx, int cy, int radius, Quaternionf attitude, Vector3f down,
            Vector3f course, int courseColor, Vector3f prograde) {
        // Les trois axes de l'instrument, dans le monde : sa droite, son haut, et son centre (le nez).
        Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).cross(down);
        Vector3f right = attitude.transform(new Vector3f(side));
        Vector3f up = attitude.transform(new Vector3f(down).negate());
        Vector3f nose = attitude.transform(new Vector3f(0.0F, 1.0F, 0.0F));

        // Le cerclage : un anneau fin.
        disc(graphics, cx, cy, radius + 2, 0xFF59636E);
        disc(graphics, cx, cy, radius + 1, 0xFF0B0E12);
        float perPixel = 57.3F / radius;
        for (int j = -radius; j <= radius; j++) {
            int half = (int) Math.sqrt(radius * radius - j * j);
            int runStart = -half;
            int runColor = 0;
            for (int i = -half; i <= half + 1; i++) {
                int color = 0;
                if (i <= half) {
                    float vx = i / (float) radius;
                    float vy = -j / (float) radius;
                    float vz = (float) Math.sqrt(Math.max(0.0F, 1.0F - vx * vx - vy * vy));
                    float wx = right.x * vx + up.x * vy + nose.x * vz;
                    float wy = right.y * vx + up.y * vy + nose.y * vz;
                    float wz = right.z * vx + up.z * vy + nose.z * vz;
                    float elevation = (float) Math.toDegrees(Math.asin(Mth.clamp(wy, -1.0F, 1.0F)));
                    float along = wx * down.x + wz * down.z;
                    float across = wx * side.x + wz * side.z;
                    float azimuth = (float) Math.toDegrees(Math.atan2(across, along));
                    int base = wy >= 0.0F ? COLOR_SKY : COLOR_GROUND;
                    float light = 1.0F;
                    if (Math.abs(elevation) < perPixel * 0.7F) {
                        base = 0xFFFFFFFF;
                    } else {
                        // Un parallèle tous les 30°, un méridien tous les 45° (sauf près des pôles, où ils se rejoignent).
                        float toParallel = Math.abs(elevation - Math.round(elevation / 30.0F) * 30.0F);
                        float toMeridian = Math.abs(azimuth - Math.round(azimuth / 45.0F) * 45.0F) * Mth.cos(elevation * Mth.DEG_TO_RAD);
                        if (toParallel < perPixel * 0.5F || (Math.abs(elevation) < 76.0F && toMeridian < perPixel * 0.5F)) {
                            // Le méridien du sens du tir est marqué en doré : c'est le long de lui que se couche la fusée.
                            light = Math.abs(azimuth) < 2.0F && toMeridian < perPixel * 0.5F ? -1.0F : 1.3F;
                        }
                    }
                    // La boule est une sphère : elle s'assombrit vers son bord, par paliers.
                    float shade = 0.62F + 0.38F * Math.round(vz * 4.0F) / 4.0F;
                    color = light < 0.0F ? scale(COLOR_GOLD, shade) : scale(base, Math.min(1.0F, shade * light));
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
        // Les graduations chiffrées, le long du méridien du sens du tir et de son opposé.
        for (int side2 = 0; side2 < 1; side2++) {
            for (int degrees = -60; degrees <= 60; degrees += 30) {
                if (degrees == 0) {
                    continue;
                }
                double r = Math.toRadians(degrees);
                Vector3f point = new Vector3f(down).mul((float) Math.cos(r) * (side2 == 0 ? 1.0F : -1.0F)).add(0.0F, (float) Math.sin(r), 0.0F);
                float vz = point.dot(nose);
                if (vz > 0.45F) {
                    String label = Integer.toString(Math.abs(degrees));
                    int px = cx + Math.round(point.dot(right) * radius) + 3;
                    int py = cy - Math.round(point.dot(up) * radius) - 4;
                    graphics.text(font, label, px, py, 0xB0E6E9EE, false);
                }
            }
        }
        // La trajectoire à tenir : un cercle, à amener sous le repère. Hors de vue, une flèche sur le bord dit où elle est.
        float tx = course.dot(right);
        float ty = course.dot(up);
        float tz = course.dot(nose);
        if (tz > 0.05F) {
            int px = cx + Math.round(tx * radius);
            int py = cy - Math.round(ty * radius);
            // Le chemin à faire : des points du repère du nez vers le cercle, qui défilent vers lui.
            float reachPx = (float) Math.hypot(px - cx, py - cy);
            if (reachPx > 9.0F) {
                for (int k = 0; k < 4; k++) {
                    float t = ((k + (clock % 8) / 8.0F) / 4.0F);
                    float along = 6.0F + t * (reachPx - 12.0F);
                    int dx = cx + Math.round((px - cx) * along / reachPx);
                    int dy = cy + Math.round((py - cy) * along / reachPx);
                    graphics.fill(dx, dy, dx + 2, dy + 2, Ui.alpha(courseColor, 0.4F + 0.6F * t));
                }
            }
            ring(graphics, px, py, 5, 0xFF0B0E12);
            ring(graphics, px, py, 4, courseColor);
            ring(graphics, px, py, 3, courseColor);
        } else {
            float length = Math.max(0.001F, (float) Math.sqrt(tx * tx + ty * ty));
            int px = cx + Math.round(tx / length * (radius - 4));
            int py = cy - Math.round(ty / length * (radius - 4));
            if (clock / 3 % 2 == 0) {
                graphics.fill(px - 3, py - 3, px + 4, py + 4, COLOR_BAD);
                graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
            }
        }
        // Le prograde (la direction du mouvement : un rond et trois branches) et le rétrograde (un rond barré d'une croix).
        if (prograde.lengthSquared() > 0.25F) {
            marker(graphics, cx, cy, radius, prograde.dot(right), prograde.dot(up), prograde.dot(nose), false);
            marker(graphics, cx, cy, radius, -prograde.dot(right), -prograde.dot(up), -prograde.dot(nose), true);
        }
        // Le repère du nez : deux ailes et un point, fixes au centre.
        graphics.fill(cx - 14, cy - 1, cx - 5, cy + 1, 0xFF14181E);
        graphics.fill(cx + 6, cy - 1, cx + 15, cy + 1, 0xFF14181E);
        graphics.fill(cx - 13, cy - 1, cx - 5, cy, COLOR_GOLD);
        graphics.fill(cx + 6, cy - 1, cx + 14, cy, COLOR_GOLD);
        graphics.fill(cx - 5, cy - 1, cx - 4, cy + 3, COLOR_GOLD);
        graphics.fill(cx + 5, cy - 1, cx + 6, cy + 3, COLOR_GOLD);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, COLOR_GOLD);
    }

    /** Un marqueur de la boule, s'il est sur sa face visible : le prograde, ou (barré) le rétrograde. */
    private static void marker(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float x, float y, float z, boolean retro) {
        if (z <= 0.05F) {
            return;
        }
        int px = cx + Math.round(x * radius);
        int py = cy - Math.round(y * radius);
        int color = retro ? 0xFFE6E9EE : 0xFF9FE07A;
        for (int k = 0; k <= 4; k++) {
            graphics.fill(px - 4 + k, py - k, px - 3 + k, py - k + 1, color);
            graphics.fill(px + 4 - k, py - k, px + 5 - k, py - k + 1, color);
            graphics.fill(px - 4 + k, py + k, px - 3 + k, py + k + 1, color);
            graphics.fill(px + 4 - k, py + k, px + 5 - k, py + k + 1, color);
        }
        if (retro) {
            graphics.fill(px - 2, py, px + 3, py + 1, color);
            graphics.fill(px, py - 2, px + 1, py + 3, color);
        } else {
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Un disque plein, ligne par ligne. */
    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int j = -r; j <= r; j++) {
            int half = (int) Math.sqrt(r * r - j * j);
            graphics.fill(cx - half, cy + j, cx + half + 1, cy + j + 1, color);
        }
    }

    /** Un cercle d'un pixel d'épaisseur. */
    private static void ring(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int j = -r; j <= r; j++) {
            int half = (int) Math.round(Math.sqrt(r * r - j * j));
            int inner = Math.abs(j) == r ? -1 : (int) Math.round(Math.sqrt(Math.max(0, (r - 1) * (r - 1) - j * j)));
            if (inner < 0) {
                graphics.fill(cx - half, cy + j, cx + half + 1, cy + j + 1, color);
            } else {
                graphics.fill(cx - half, cy + j, cx - inner, cy + j + 1, color);
                graphics.fill(cx + inner + 1, cy + j, cx + half + 1, cy + j + 1, color);
            }
        }
    }

    /** La même couleur, plus sombre ou plus claire. */
    private static int scale(int color, float factor) {
        int r = Math.min(255, Math.round((color >> 16 & 255) * factor));
        int g = Math.min(255, Math.round((color >> 8 & 255) * factor));
        int b = Math.min(255, Math.round((color & 255) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------
    // Descente propulsée : l'atterrissage piloté
    // ------------------------------------------------------------------

    /**
     * L'atterrissage piloté. Le pilote, couché sur le dos, ne voit que le ciel : il se pose aux instruments. La boule
     * d'attitude lui dit s'il est debout (le cercle est la verticale) ; le panneau, à quelle hauteur il est, à quelle
     * vitesse il descend et dérive, et ce qu'il lui reste d'ergols ; et, sous la boule, ce qu'il faut faire maintenant.
     */
    private static void drawDescent(GuiGraphicsExtractor graphics, Font font, int width, int height, CapsuleEntity capsule, float partial) {
        Quaternionf attitude = capsule.attitude(partial);
        Vector3f down = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f vertical = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f nose = attitude.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        float tilt = (float) Math.toDegrees(Math.acos(Mth.clamp(nose.y, -1.0F, 1.0F)));
        int ground = capsule.level().getHeight(Heightmap.Types.MOTION_BLOCKING, capsule.getBlockX(), capsule.getBlockZ());
        double above = Math.max(0.0, capsule.getY() - ground);
        // Vitesses, en blocs par tick : la descente, et la dérive de côté.
        double sink = capsule.yo - capsule.getY();
        double slide = Math.hypot(capsule.getX() - capsule.xo, capsule.getZ() - capsule.zo);
        // La vitesse de descente qu'il est raisonnable d'avoir à cette hauteur : celle que tiendrait l'ordinateur de bord.
        double wanted = CapsuleEntity.safeDescentSpeed(above);
        boolean manual = capsule.isManualChute();
        float fuel = capsule.fuelShare();
        int tiltColor = tilt <= 12.0F ? COLOR_OK : tilt <= CapsuleEntity.SAFE_TILT ? COLOR_WARNING : COLOR_BAD;
        int sinkColor = sink <= Math.max(CapsuleEntity.SAFE_SINK, wanted) ? COLOR_OK : sink <= wanted * 1.25 + 0.05 ? COLOR_WARNING : COLOR_BAD;
        int slideColor = slide <= CapsuleEntity.SAFE_SLIDE * 0.6 ? COLOR_OK : slide <= CapsuleEntity.SAFE_SLIDE ? COLOR_WARNING : COLOR_BAD;
        int fuelColor = fuel > 0.3F ? COLOR_OK : fuel > 0.12F ? COLOR_WARNING : COLOR_BAD;

        boolean outside = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
        int radius = outside ? Mth.clamp(height / 11, 20, 32) : Mth.clamp(height / 8, 26, 44);
        int bx = outside ? width - radius - 34 : width / 2;
        int by = height - 22 - radius;
        // Le mouvement de la capsule : le rétrograde, sur la boule, est le repère pour freiner avant de se poser.
        Vector3f motion = new Vector3f((float) (capsule.getX() - capsule.xo), (float) (capsule.getY() - capsule.yo), (float) (capsule.getZ() - capsule.zo));
        if (motion.lengthSquared() > 1.0E-6F) {
            motion.normalize();
        } else {
            motion.zero();
        }
        navball(graphics, font, bx, by, radius, attitude, down, vertical, tiltColor, motion);
        // Au-dessus : la hauteur. Au-dessous : ce qu'il faut faire.
        String heightText = number(above) + " m";
        int topWidth = Math.max(62, font.width(heightText) + 12);
        panel(graphics, bx - topWidth / 2, by - radius - 26, topWidth, 22);
        Component heightLabel = Component.translatable("pilot.orbitalis.height");
        graphics.text(font, heightLabel, bx - font.width(heightLabel) / 2, by - radius - 24, COLOR_MUTED, false);
        graphics.text(font, heightText, bx - font.width(heightText) / 2, by - radius - 14, above < 30.0 ? COLOR_WARNING : COLOR_TEXT, false);
        String under;
        int underColor;
        // Temps avant le sol si rien ne change : au-dessous de trois secondes en descendant trop vite, c'est l'urgence.
        boolean urgent = sink > wanted * 1.25 + 0.05;
        if (!manual) {
            under = Component.translatable("pilot.orbitalis.land.auto").getString();
            underColor = COLOR_ACCENT;
        } else if (tilt > 15.0F && above < 60.0) {
            under = Component.translatable("pilot.orbitalis.land.upright").getString();
            underColor = tilt > CapsuleEntity.SAFE_TILT ? COLOR_BAD : COLOR_WARNING;
        } else if (sink > wanted) {
            // Au-delà de la vitesse que le moteur peut encore arrêter avec de la marge : il faut pousser, tout de suite.
            under = Component.translatable("pilot.orbitalis.land.burn", PilotControls.name(PilotControls.STEP)).getString();
            underColor = urgent ? COLOR_BAD : COLOR_WARNING;
        } else if (slide > CapsuleEntity.SAFE_SLIDE * 0.8 && above < 80.0) {
            under = Component.translatable("pilot.orbitalis.land.drift").getString();
            underColor = COLOR_WARNING;
        } else if (sink < wanted * 0.6 && above > 6.0 && capsule.isBurning()) {
            under = Component.translatable("pilot.orbitalis.land.coast").getString();
            underColor = COLOR_OK;
        } else {
            under = Component.translatable("pilot.orbitalis.land.steady").getString();
            underColor = COLOR_OK;
        }
        int underWidth = font.width(under) + 10;
        panel(graphics, bx - underWidth / 2, by + radius + 5, underWidth, 11);
        graphics.text(font, under, bx - font.width(under) / 2, by + radius + 7, underColor, false);
        dial(graphics, font, bx - radius - 19, by - radius, 2 * radius, fuel, fuelColor, "pilot.orbitalis.fuel_short", false);
        dial(graphics, font, bx + radius + 11, by - radius, 2 * radius, (float) (sink / 1.5), sinkColor, "pilot.orbitalis.sink_short", true);
        if (manual && (urgent || fuel <= 0.0F)) {
            alerts |= CockpitAlerts.CRASH;
        }
        if (manual && tilt > 15.0F && above < 60.0) {
            alerts |= CockpitAlerts.STALL;
        }

        int pw = 132;
        int px = 10;
        int py = outside ? 4 : Math.max(4, height / 2 - 70);
        panel(graphics, px, py, pw, manual ? 132 : 92);
        int x = px + 6;
        int w = pw - 12;
        int y = py + 5;
        graphics.text(font, Component.translatable("pilot.orbitalis.land.title"), x, y, COLOR_ACCENT, false);
        y += 13;
        row(graphics, font, x, y, w, "pilot.orbitalis.height", number(above) + " m", COLOR_TEXT);
        y += 11;
        row(graphics, font, x, y, w, "pilot.orbitalis.fall", String.format(Locale.ROOT, "%.1f m/s", sink * 20.0), sinkColor);
        y += 11;
        row(graphics, font, x, y, w, "pilot.orbitalis.drift", String.format(Locale.ROOT, "%.1f m/s", slide * 20.0), slideColor);
        y += 11;
        row(graphics, font, x, y, w, "pilot.orbitalis.tilt", Math.round(tilt) + "°", tiltColor);
        y += 11;
        row(graphics, font, x, y, w, "pilot.orbitalis.fuel", Math.round(fuel * 100.0F) + " %", fuelColor);
        y += 10;
        bar(graphics, x, y, w, fuel, fuelColor);
        y += 8;
        // Ce qu'il faut au contact pour ne pas briser le vaisseau.
        graphics.text(font, Component.translatable("pilot.orbitalis.land.safe", Math.round(CapsuleEntity.SAFE_SINK * 20.0), Math.round(CapsuleEntity.SAFE_TILT)),
                x, y, COLOR_MUTED, false);
        if (manual) {
            y += 14;
            row(graphics, font, x, y, w, "pilot.orbitalis.land.thrust", PilotControls.name(PilotControls.STEP).getString(), COLOR_ACCENT);
            y += 10;
            keys(graphics, font, x, y, w, "pilot.orbitalis.axis.pitch", PilotControls.PITCH_DOWN, PilotControls.PITCH_UP);
            y += 10;
            keys(graphics, font, x, y, w, "pilot.orbitalis.axis.yaw", PilotControls.YAW_LEFT, PilotControls.YAW_RIGHT);
        }
    }

    // ------------------------------------------------------------------
    // Cabine seule : orbite, rentrée, descente
    // ------------------------------------------------------------------

    private static void drawReturn(GuiGraphicsExtractor graphics, Font font, int width, int height, CapsuleEntity capsule) {
        CapsuleEntity.Phase phase = capsule.getPhase();
        int tw = 132;
        int tx = width - tw - 10;
        int ty = height / 2 - 40;
        panel(graphics, tx, ty, tw, 62);
        int x = tx + 6;
        int w = tw - 12;
        int y = ty + 5;
        graphics.text(font, Component.translatable(phase.messageKey()), x, y, phase == CapsuleEntity.Phase.ABORT ? COLOR_BAD : COLOR_ACCENT, false);
        y += 14;
        boolean space = phase == CapsuleEntity.Phase.ORBIT || phase == CapsuleEntity.Phase.COAST;
        if (space) {
            row(graphics, font, x, y, w, "pilot.orbitalis.altitude", number(FlightProfile.ORBIT_ALTITUDE_KM) + " km", COLOR_TEXT);
            y += 11;
            row(graphics, font, x, y, w, "pilot.orbitalis.speed", number(FlightProfile.ORBIT_SPEED) + " m/s", COLOR_TEXT);
        } else {
            int ground = capsule.level().getHeight(Heightmap.Types.MOTION_BLOCKING, capsule.getBlockX(), capsule.getBlockZ());
            double above = Math.max(0.0, capsule.getY() - ground);
            double fall = Math.abs(capsule.getY() - capsule.yo) * 20.0;
            row(graphics, font, x, y, w, "pilot.orbitalis.height", number(above) + " m", COLOR_TEXT);
            y += 11;
            row(graphics, font, x, y, w, "pilot.orbitalis.fall", String.format(Locale.ROOT, "%.1f m/s", fall), fall > 20.0 ? COLOR_WARNING : COLOR_OK);
        }
        y += 11;
        boolean open = capsule.hasOpenParachutes() || phase == CapsuleEntity.Phase.LANDED;
        row(graphics, font, x, y, w, "pilot.orbitalis.chutes",
                open ? Component.translatable("cockpit.orbitalis.open").getString()
                        : capsule.isChuteArmed() ? Component.translatable("cockpit.orbitalis.armed").getString() : "—",
                open || capsule.isChuteArmed() ? COLOR_OK : phase == CapsuleEntity.Phase.REENTRY || phase == CapsuleEntity.Phase.ABORT ? COLOR_BAD : COLOR_MUTED);
        // Rentrée, parachutes à la main : trop haut, on attend ; dans la fenêtre, il faut les armer, et vite.
        if ((phase == CapsuleEntity.Phase.REENTRY || phase == CapsuleEntity.Phase.ABORT) && capsule.isManualChute() && !capsule.isChuteArmed()) {
            int ground = capsule.level().getHeight(Heightmap.Types.MOTION_BLOCKING, capsule.getBlockX(), capsule.getBlockZ());
            int above = (int) Math.max(0.0, capsule.getY() - ground);
            if (above <= CapsuleEntity.CHUTE_WINDOW) {
                alerts |= CockpitAlerts.DEPLOY;
                if (above <= CapsuleEntity.CHUTE_WINDOW * 2 / 5) {
                    alerts |= CockpitAlerts.CRASH;
                }
                Component now = Component.translatable("pilot.orbitalis.chute_now", PilotControls.name(PilotControls.CHUTE));
                int bw = font.width(now) + 16;
                int x0 = (width - bw) / 2;
                int y0 = height / 2 + 30;
                graphics.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 15, COLOR_BAD);
                graphics.fill(x0, y0, x0 + bw, y0 + 14, clock / 3 % 2 == 0 ? 0xF04A1612 : 0xF014181E);
                graphics.text(font, now, x0 + 8, y0 + 3, COLOR_TEXT, false);
            } else {
                Component wait = Component.translatable("pilot.orbitalis.chute_wait", CapsuleEntity.CHUTE_WINDOW);
                graphics.text(font, wait, (width - font.width(wait)) / 2, height / 2 + 33, COLOR_MUTED, true);
            }
        }
        y += 11;
        if (phase == CapsuleEntity.Phase.ORBIT && capsule.getNode() != CapsuleEntity.NODE_NONE) {
            drawNode(graphics, font, width, height, capsule);
        }
        // La touche de l'action possible : rentrer depuis l'orbite, redécoller de la Lune.
        if (phase == CapsuleEntity.Phase.ORBIT) {
            row(graphics, font, x, y, w, "pilot.orbitalis.action.return", PilotControls.name(PilotControls.RETURN).getString(), COLOR_ACCENT);
        } else if (phase == CapsuleEntity.Phase.LANDED && capsule.isLunar()) {
            row(graphics, font, x, y, w, "pilot.orbitalis.action.liftoff", PilotControls.name(PilotControls.LAUNCH).getString(), COLOR_ACCENT);
        }
    }

    /**
     * La manœuvre préparée, au centre de l'écran : son nom et son delta-v ; avant la fenêtre, le compte à rebours ; la
     * fenêtre ouverte, l'ordre d'allumer ; pendant la poussée, la jauge du delta-v accompli, avec la plage où couper.
     */
    private static void drawNode(GuiGraphicsExtractor graphics, Font font, int width, int height, CapsuleEntity capsule) {
        int node = capsule.getNode();
        boolean deorbit = node == CapsuleEntity.NODE_DEORBIT;
        Component key = PilotControls.name(deorbit ? PilotControls.RETURN : PilotControls.MOON);
        Component other = PilotControls.name(deorbit ? PilotControls.MOON : PilotControls.RETURN);
        float target = capsule.nodeDeltaV();
        float done = capsule.nodeDone();
        int countdown = capsule.nodeCountdown();
        // Au centre, mais à gauche du panneau de l'orbite (à 142 pixels du bord droit), sans jamais le chevaucher.
        int limit = width - 142 - 8;
        int pw = Math.min(220, limit - 10);
        int px = Math.max(10, Math.min((width - pw) / 2, limit - pw));
        // Au-dessus du milieu de l'écran : plus bas, le message du jeu pour descendre de la cabine passerait dessous.
        int py = Math.max(30, height / 2 - 74);
        panel(graphics, px, py, pw, 50);
        int x = px + 6;
        int w = pw - 12;
        String title = Ui.fit(font, Component.translatable("pilot.orbitalis.node." + node).getString(), w - 70);
        graphics.text(font, title, x, py + 5, COLOR_ACCENT, false);
        String dv = number(target) + " m/s";
        graphics.text(font, "Δv " + dv, x + w - font.width("Δv " + dv), py + 5, COLOR_TEXT, false);
        String line;
        int color;
        if (countdown > 0) {
            line = Component.translatable("pilot.orbitalis.node.wait", (countdown + 19) / 20, key).getString();
            color = COLOR_TEXT;
        } else if (done <= 0.0F) {
            line = Component.translatable("pilot.orbitalis.node.go", key, Math.max(0, (CapsuleEntity.NODE_WINDOW + countdown + 19) / 20)).getString();
            color = COLOR_WARNING;
            alerts |= CockpitAlerts.MANEUVER;
        } else {
            line = Component.translatable("pilot.orbitalis.node.burn").getString() + "  ·  " + Math.round(done / target * 100.0F) + " %";
            color = done >= target * CapsuleEntity.NODE_MIN ? COLOR_OK : COLOR_WARNING;
        }
        graphics.text(font, Ui.fit(font, line, w), x, py + 18, color, false);
        // La jauge : jusqu'à 120 % ; la plage où couper, entre 95 et 110 %, est marquée en vert.
        int barY = py + 31;
        float scale = w / (1.2F * target);
        graphics.fill(x, barY, x + w, barY + 5, COLOR_EDGE);
        int lo = x + Math.round(CapsuleEntity.NODE_MIN * target * scale);
        int hi = x + Math.round(CapsuleEntity.NODE_MAX * target * scale);
        graphics.fill(lo, barY, hi, barY + 5, 0xFF2E5A3A);
        graphics.fill(x, barY + 1, x + Math.round(Math.min(done, 1.2F * target) * scale), barY + 4, color);
        graphics.fill(x + Math.round(target * scale), barY - 2, x + Math.round(target * scale) + 1, barY + 7, COLOR_TEXT);
        if (done <= 0.0F) {
            String cancel = Ui.fit(font, Component.translatable("pilot.orbitalis.node.cancel", other).getString(), w);
            graphics.text(font, cancel, x, py + 39, COLOR_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // Au sol
    // ------------------------------------------------------------------

    private static void drawGround(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        LaunchStatusPayload data = status;
        if (!data.hasRocket()) {
            return;
        }
        int tw = 132;
        int tx = width - tw - 10;
        int ty = height / 2 - 60;
        boolean deluge = data.hasDeluge();
        panel(graphics, tx, ty, tw, deluge ? 106 : 94);
        int x = tx + 6;
        int w = tw - 12;
        int y = ty + 5;
        graphics.text(font, Component.translatable("pilot.orbitalis.ground"), x, y, COLOR_ACCENT, false);
        y += 14;
        if (data.liquid()) {
            row(graphics, font, x, y, w, "pilot.orbitalis.fuel", data.fuelPercent() + " %", data.fuelPercent() >= 100 ? COLOR_OK : COLOR_WARNING);
            y += 10;
            bar(graphics, x, y, w, data.fuelPercent() / 100.0F, data.fuelPercent() >= 100 ? COLOR_OK : COLOR_ACCENT);
            y += 7;
            row(graphics, font, x, y, w, "pilot.orbitalis.pressure", data.pressure() > 0 ? data.pressure() + " s" : "—",
                    data.pressure() > 20 ? COLOR_OK : data.pressure() > 0 ? COLOR_WARNING : COLOR_MUTED);
            y += 11;
        }
        row(graphics, font, x, y, w, "pilot.orbitalis.arm",
                Component.translatable(data.retracted() ? "sequence.orbitalis.value.retracted" : "sequence.orbitalis.value.connected").getString(),
                data.retracted() ? COLOR_OK : COLOR_MUTED);
        y += 11;
        if (deluge) {
            row(graphics, font, x, y, w, "pilot.orbitalis.deluge", data.deluge() > 0 ? data.deluge() + " s" : data.water() + " %",
                    data.deluge() > 0 ? COLOR_OK : data.water() >= 100 ? COLOR_MUTED : COLOR_BAD);
            y += 11;
        }
        row(graphics, font, x, y, w, "pilot.orbitalis.thrust", data.thrust() + " %", data.thrust() >= 100 ? COLOR_OK : COLOR_TEXT);
        y += 10;
        bar(graphics, x, y, w, data.thrust() / 100.0F, data.thrust() >= 100 ? COLOR_OK : COLOR_WARNING);
        y += 8;
        row(graphics, font, x, y, w, "pilot.orbitalis.assist",
                Component.translatable("sequence.orbitalis.assist." + Mth.clamp(data.assist(), 0, 2)).getString(), data.assist() > 0 ? COLOR_OK : COLOR_TEXT);

        // Le bandeau du bas : l'étape à faire, et la touche qui ouvre la séquence.
        String next = nextGroundStep(data);
        // Moteurs à pleine poussée, attaches encore fermées : il faut lâcher sans traîner.
        if ("release".equals(next)) {
            alerts |= CockpitAlerts.MANEUVER;
        }
        Component open = Component.translatable("pilot.orbitalis.ground_open", PilotControls.name(PilotControls.LAUNCH));
        Component line = next == null ? open
                : Component.translatable("pilot.orbitalis.ground_next", Component.translatable("sequence.orbitalis.step." + next));
        int bw = font.width(line) + 16;
        int bx = (width - bw) / 2;
        int by = height - 44;
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + 15, COLOR_EDGE);
        graphics.fill(bx, by, bx + bw, by + 14, COLOR_PANEL);
        graphics.text(font, line, bx + 8, by + 3, next == null ? COLOR_MUTED : COLOR_ACCENT, false);
        if (next != null) {
            graphics.text(font, open, (width - font.width(open)) / 2, by + 18, COLOR_MUTED, true);
        }
    }

    /** L'étape de la séquence au sol à faire maintenant, dans l'ordre ; {@code null} s'il n'y a qu'à attendre. */
    private static String nextGroundStep(LaunchStatusPayload data) {
        boolean ignited = data.phase() == LaunchTableBlockEntity.Phase.COUNTDOWN.ordinal();
        if (ignited) {
            return data.thrust() >= 100 ? "release" : null;
        }
        if (data.liquid() && data.fuelPercent() < 100) {
            return data.valvesOpen() ? null : "valves";
        }
        if (data.liquid() && data.pressure() <= 0) {
            return "pressurize";
        }
        if (data.chill() == 0) {
            return "chill";
        }
        if (!data.retracted()) {
            return "retract";
        }
        if (data.internal() == 0) {
            return "internal";
        }
        if (data.busyStep() >= 0 || (data.poll() >= 0 && data.poll() < 100)) {
            // Une opération se déroule : il n'y a qu'à attendre.
            return null;
        }
        if (data.poll() != 100) {
            return "poll";
        }
        if (data.hasDeluge() && data.deluge() <= 0) {
            return "deluge";
        }
        return "ignite";
    }
}
