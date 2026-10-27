package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.orbitalis.core.network.LaunchCommandPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.phase1.spaceport.LaunchStep;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * La check-list de la séquence de lancement : une ligne par étape, à dérouler de haut en bas. Sa pastille dit où
 * elle en est, et l'étape à faire maintenant porte le bouton qui la commande. Une opération prend quelques secondes
 * (les vannes s'ouvrent, la pression monte, le bras s'écarte) : sa jauge avance, et rien d'autre ne se commande tant
 * qu'elle dure. En vol, la liste devient celle des étapes du vol.
 * <p>
 * Elle se dessine dans un rectangle donné : la fenêtre de la séquence, dans la cabine, ou le volet du centre de
 * contrôle. Dans la cabine, elle porte aussi le tour de table et les essais du pas de tir ; au centre de contrôle,
 * ceux-ci sont dans la fenêtre « Go / No-Go ».
 * <p>
 * Rien n'interdit de revenir sur une ligne (rouvrir les vannes, remettre le bras) : il suffit de cliquer dessus.
 * <p>
 * Tout bouge en douceur : les jauges glissent vers leur valeur, le survol s'allume en fondu, l'étape à faire palpite,
 * et une étape qui vient d'être faite s'illumine un instant. En tête, l'avancement de la séquence ; au survol d'une
 * ligne, l'alerte dit à quoi sert l'étape. La touche Entrée commande l'étape à faire.
 */
final class SequencePanel {
    static final int ROW = 26;
    /** Hauteur de l'avancement, en tête de la liste. */
    private static final int HEADER = 16;
    private static final int ACTION_WIDTH = 70;
    /** Postes du tour de table, dans l'ordre où ils répondent (le même que côté serveur). */
    static final String[] STATIONS = {"propellant", "pressure", "engines", "pad", "weather", "vehicle"};

    /** État d'une étape. */
    enum State {
        TODO,
        ACTIVE,
        DONE,
        NOT_NEEDED,
        /** Un essai du pas de tir : il se lance quand on veut, et n'est jamais « l'étape suivante ». */
        OPTIONAL
    }

    /**
     * Une ligne de la check-list.
     *
     * @param step   l'étape qu'un clic commande
     * @param state  où elle en est
     * @param value  ce que la ligne affiche à droite
     * @param gauge  remplissage de sa jauge, de 0 à 1 ; négatif s'il n'y en a pas
     */
    record Line(LaunchStep step, State state, Component value, float gauge) {
    }

    private final Font font;
    /** Vrai pour la liste complète, tour de table et essais compris (la fenêtre de la cabine). */
    private final boolean complete;
    private LaunchStatusPayload status;
    private int age;
    /** Dernier refus du serveur, affiché dans l'alerte. */
    private Component notice = Component.empty();
    private int noticeAge;
    /** Défilement de la liste, en pixels, quand elle est plus haute que son rectangle. */
    private int scroll;
    /** Vrai une fois que le joueur a fait défiler la liste lui-même : elle ne revient alors plus d'elle-même sur l'étape à faire. */
    private boolean scrolledByHand;
    /** Ce que montre chaque jauge à l'écran : elle glisse vers sa vraie valeur. */
    private final Map<LaunchStep, Float> shownGauge = new EnumMap<>(LaunchStep.class);
    /** Le fondu du survol de chaque ligne, de 0 à 1. */
    private final Map<LaunchStep, Float> hoverFade = new EnumMap<>(LaunchStep.class);
    /** L'état de chaque ligne à l'image précédente, et l'instant où elle a été faite (pour son éclat). */
    private final Map<LaunchStep, State> lastState = new EnumMap<>(LaunchStep.class);
    private final Map<LaunchStep, Integer> doneAt = new EnumMap<>(LaunchStep.class);
    /** La ligne survolée à la dernière image, ou rien. */
    private LaunchStep hoveredStep;
    private long lastFrame;
    /** Le temps écoulé depuis l'image précédente, en secondes (pour les animations). */
    private float frameSeconds;
    /** Le rectangle de la liste, à la dernière image : x, y, largeur, hauteur. */
    private int x;
    private int y;
    private int w;
    private int h;

    SequencePanel(Font font, LaunchStatusPayload status, boolean complete) {
        this.font = font;
        this.status = status;
        this.complete = complete;
    }

    void update(LaunchStatusPayload newStatus) {
        status = newStatus;
    }

    /** Le serveur a refusé une étape : on dit pourquoi. */
    void refuse(Component reason) {
        notice = reason;
        noticeAge = 0;
    }

    void tick() {
        age++;
        noticeAge++;
    }

    int age() {
        return age;
    }

    private LaunchTableBlockEntity.Phase phase() {
        LaunchTableBlockEntity.Phase[] phases = LaunchTableBlockEntity.Phase.values();
        return phases[Math.floorMod(status.phase(), phases.length)];
    }

    // ------------------------------------------------------------------
    // La check-list
    // ------------------------------------------------------------------

    /** Les étapes du vol : chacune n'apparaît que si ce vol la demande, et s'allume quand sa fenêtre s'ouvre. */
    private List<Line> flightLines() {
        List<Line> lines = new ArrayList<>();
        int bits = status.flightBits();
        LaunchStep[] steps = {LaunchStep.THROTTLE, LaunchStep.STAGE, LaunchStep.FAIRING, LaunchStep.CUTOFF};
        for (int k = 0; k < steps.length; k++) {
            int bit = 1 << k;
            if ((bits & bit) == 0) {
                continue;
            }
            boolean done = (bits >> 4 & bit) != 0;
            boolean open = (bits >> 8 & bit) != 0;
            boolean missed = (bits >> 12 & bit) != 0;
            Component value;
            if (steps[k] == LaunchStep.STAGE && !open) {
                value = Component.translatable(missed ? "sequence.orbitalis.value.missed" : "sequence.orbitalis.value.stages",
                        bits >> 16 & 15, bits >> 20 & 15);
            } else {
                value = Component.translatable(open ? "sequence.orbitalis.value.now" : missed ? "sequence.orbitalis.value.missed"
                        : done ? "sequence.orbitalis.value.done" : "sequence.orbitalis.value.later", status.flightWindow());
            }
            // Ouverte : c'est l'étape à faire (TODO, mise en avant). Pas encore ouverte : sans objet pour l'instant.
            lines.add(new Line(steps[k], open ? State.TODO : done && !missed ? State.DONE : State.NOT_NEEDED, value, -1.0F));
        }
        return lines;
    }

    /** Réponse d'un poste du tour de table, d'après l'état reçu : les mêmes règles que côté serveur. */
    static boolean stationGo(LaunchStatusPayload status, int station) {
        return switch (station) {
            case 0 -> !status.liquid() || status.fuelPercent() >= 100;
            case 1 -> !status.liquid() || status.pressure() > 0;
            case 2 -> status.chill() != 0;
            case 3 -> status.retracted() || status.testPad();
            case 4 -> status.weatherGo();
            default -> status.hasRocket() && status.internal() != 0;
        };
    }

    /** Nombre de postes du tour de table qui ont déjà répondu. */
    static int stationsHeard(LaunchStatusPayload status) {
        int poll = status.poll();
        return poll < 0 ? 0 : poll >= 100 ? STATIONS.length : Math.min(STATIONS.length, poll * STATIONS.length / 100);
    }

    /** La ligne du tour de table : facultatif, mais c'est lui qui dit ce qui manque. */
    static Line pollLine(LaunchStatusPayload status, boolean ignited) {
        int poll = status.poll();
        return new Line(LaunchStep.POLL, ignited || poll == 100 ? State.DONE : poll >= 0 && poll < 100 ? State.ACTIVE : State.TODO,
                Component.translatable(ignited || poll == 100 ? "sequence.orbitalis.value.poll_go" : poll == 101 ? "sequence.orbitalis.value.poll_no_go"
                        : poll >= 0 ? "sequence.orbitalis.value.poll_running" : "sequence.orbitalis.value.no_pressure"),
                poll >= 0 && poll < 100 ? poll / 100.0F : -1.0F);
    }

    /** Les essais du pas de tir, tant que rien n'est allumé : pour voir le déluge, les bras et l'alarme fonctionner. */
    static List<Line> testLines(LaunchStatusPayload status) {
        List<Line> lines = new ArrayList<>();
        if (status.hasDeluge()) {
            lines.add(new Line(LaunchStep.TEST_DELUGE, State.OPTIONAL, Component.translatable("sequence.orbitalis.value.water", status.water()), -1.0F));
        }
        if (!status.testPad()) {
            lines.add(new Line(LaunchStep.TEST_ARMS, State.OPTIONAL,
                    Component.translatable(status.retracted() ? "sequence.orbitalis.value.retracted" : "sequence.orbitalis.value.connected"), -1.0F));
        }
        lines.add(new Line(LaunchStep.TEST_ALARM, State.OPTIONAL, Component.empty(), -1.0F));
        return lines;
    }

    List<Line> lines() {
        // Un pupitre sans pas de tir en service n'a rien à commander : l'alerte dit pourquoi.
        if (!status.linked() || !status.commissioned() || status.radius() == 0) {
            return List.of();
        }
        if (phase() == LaunchTableBlockEntity.Phase.FLIGHT) {
            return flightLines();
        }
        List<Line> lines = new ArrayList<>();
        boolean liquid = status.liquid();
        boolean ignited = phase() == LaunchTableBlockEntity.Phase.COUNTDOWN;
        boolean full = status.fuelPercent() >= 100;

        // Seules les étapes que cette fusée demande sont listées : plus sa technique avance, plus la liste est longue.
        if (liquid) {
            // Le plein : vannes ouvertes tant que les réservoirs se remplissent.
            // Plein fait, vannes encore ouvertes : la pression monte dans les réservoirs.
            lines.add(new Line(LaunchStep.VALVES, full && !status.valvesOpen() ? State.DONE : status.valvesOpen() && !full ? State.ACTIVE : State.TODO,
                    status.overpressure() > 0 ? Component.translatable("sequence.orbitalis.value.overpressure", status.overpressure())
                            : Component.translatable(status.valvesOpen() ? "sequence.orbitalis.value.valves_open" : "sequence.orbitalis.value.valves_closed",
                                    status.fuelPercent()),
                    status.overpressure() > 0 ? status.overpressure() / 100.0F : status.fuelPercent() / 100.0F));
            // La pression : elle ne tient que deux minutes.
            lines.add(new Line(LaunchStep.PRESSURIZE, status.pressure() > 0 || ignited ? State.DONE : State.TODO,
                    status.pressure() > 0 ? Component.translatable("sequence.orbitalis.value.pressure", status.pressure())
                            : Component.translatable("sequence.orbitalis.value.no_pressure"),
                    status.pressure() > 0 ? Math.min(1.0F, status.pressure() / 120.0F) : -1.0F));
        }
        if (status.chill() >= 0) {
            // La mise en froid des moteurs à turbopompes : elle aussi ne tient que deux minutes, et se fait par le bras.
            lines.add(new Line(LaunchStep.CHILL, status.chill() > 0 || ignited ? State.DONE : State.TODO,
                    status.chill() > 0 ? Component.translatable("sequence.orbitalis.value.chill", status.chill())
                            : Component.translatable("sequence.orbitalis.value.no_pressure"),
                    status.chill() > 0 ? Math.min(1.0F, status.chill() / 120.0F) : -1.0F));
        }
        // Le bras et la passerelle : le pas de tir d'essai n'en a pas.
        if (!status.testPad()) {
            lines.add(new Line(LaunchStep.RETRACT, status.retracted() ? State.DONE : State.TODO,
                    Component.translatable(status.retracted() ? "sequence.orbitalis.value.retracted" : "sequence.orbitalis.value.connected"), -1.0F));
        }
        if (status.internal() >= 0) {
            // Les lanceurs avancés quittent le courant du sol pour leurs propres batteries.
            lines.add(new Line(LaunchStep.INTERNAL, status.internal() > 0 || ignited ? State.DONE : State.TODO,
                    Component.translatable(status.internal() > 0 || ignited ? "sequence.orbitalis.value.internal_on" : "sequence.orbitalis.value.internal_off"),
                    -1.0F));
        }
        if (complete) {
            lines.add(pollLine(status, ignited));
        }
        if (status.hasDeluge()) {
            // Le déluge, sur les pas de tir qui en ont un.
            lines.add(new Line(LaunchStep.DELUGE, status.deluge() > 0 ? State.DONE : State.TODO,
                    status.deluge() > 0 ? Component.translatable("sequence.orbitalis.value.deluge", status.deluge())
                            : Component.translatable("sequence.orbitalis.value.water", status.water()),
                    -1.0F));
        }
        // L'allumage : la poussée monte en trois secondes.
        lines.add(new Line(LaunchStep.IGNITE, ignited && status.thrust() >= 100 ? State.DONE : ignited ? State.ACTIVE : State.TODO,
                Component.translatable("sequence.orbitalis.value.thrust", status.thrust()), ignited ? status.thrust() / 100.0F : -1.0F));
        // Les attaches.
        lines.add(new Line(LaunchStep.RELEASE, State.TODO,
                ignited && status.thrust() >= 100 ? Component.translatable("sequence.orbitalis.value.hold", status.hold())
                        : Component.translatable("sequence.orbitalis.value.clamped"),
                ignited && status.thrust() >= 100 ? status.hold() / 12.0F : -1.0F));
        // Les essais du pas de tir ne sont pas des étapes du lancement : ils se font depuis la fenêtre Go / No-Go.

        // L'opération en cours : sa ligne est « en cours », et sa jauge avance.
        if (status.busyStep() >= 0) {
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (line.step().ordinal() == status.busyStep() && line.step() != LaunchStep.POLL) {
                    lines.set(i, new Line(line.step(), State.ACTIVE, Component.translatable("sequence.orbitalis.value.working"), status.busyPercent() / 100.0F));
                }
            }
        }
        return lines;
    }

    /**
     * L'étape à faire maintenant : la première qui reste à faire, dans l'ordre. Tant qu'une étape est en cours (le
     * plein qui se fait, la pression qui monte, la poussée qui monte), il n'y a rien à faire qu'attendre : -1.
     */
    int next(List<Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).state() == State.ACTIVE) {
                // Le plein se fait : c'est en refermant les vannes, une fois à 100 %, qu'on passe à la suite.
                return lines.get(i).step() == LaunchStep.VALVES && status.busyStep() < 0 && status.fuelPercent() >= 100 ? i : -1;
            }
            if (lines.get(i).state() == State.TODO) {
                return i;
            }
        }
        return -1;
    }

    static Component stepName(LaunchStep step) {
        return Component.translatable("sequence.orbitalis.step." + step.name().toLowerCase(Locale.ROOT));
    }

    /** Ce qu'il faut savoir maintenant : un refus du serveur, un oubli, à quoi sert l'étape survolée, ou la suite. */
    Component alert() {
        List<Line> lines = lines();
        if (noticeAge < 100 && !notice.getString().isEmpty()) {
            return notice.copy().withStyle(ChatFormatting.GOLD);
        }
        if (hoveredStep != null) {
            String key = "sequence.orbitalis.step." + hoveredStep.name().toLowerCase(Locale.ROOT) + ".desc";
            if (Language.getInstance().has(key)) {
                return Component.translatable(key).withStyle(ChatFormatting.GRAY);
            }
        }
        if (!status.linked()) {
            return Component.translatable("sequence.orbitalis.alert.no_pad", com.orbitalis.phase1.spaceport.LaunchConsoleBlock.RANGE)
                    .withStyle(ChatFormatting.GOLD);
        }
        if (!status.commissioned() || status.radius() == 0) {
            return Component.translatable("sequence.orbitalis.alert.pad_not_ready").withStyle(ChatFormatting.GOLD);
        }
        if (status.overpressure() > 0 && status.valvesOpen()) {
            return Component.translatable("sequence.orbitalis.alert.overpressure", status.overpressure()).withStyle(ChatFormatting.RED);
        }
        if (!status.hasRocket()) {
            // Les essais du pas de tir ne se font que depuis le grand pupitre, qui porte la liste courte.
            return Component.translatable(status.busyStep() >= 0 ? "sequence.orbitalis.alert.testing"
                    : complete ? "sequence.orbitalis.alert.no_rocket" : "sequence.orbitalis.alert.no_rocket_tests").withStyle(ChatFormatting.GRAY);
        }
        if (phase() == LaunchTableBlockEntity.Phase.COUNTDOWN) {
            if (!status.retracted() && !status.testPad()) {
                return Component.translatable("sequence.orbitalis.alert.arm").withStyle(ChatFormatting.RED);
            }
            if (status.hasDeluge() && status.deluge() <= 0) {
                return Component.translatable("sequence.orbitalis.alert.deluge").withStyle(ChatFormatting.RED);
            }
            if (status.liquid() && status.fuelPercent() < 100) {
                return Component.translatable("sequence.orbitalis.alert.fuel", status.fuelPercent()).withStyle(ChatFormatting.RED);
            }
            return Component.translatable(status.thrust() >= 100 ? "sequence.orbitalis.alert.release" : "sequence.orbitalis.alert.wait")
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (phase() == LaunchTableBlockEntity.Phase.FLIGHT) {
            int next = next(lines);
            return next < 0 ? Component.translatable(lines.isEmpty() ? "sequence.orbitalis.alert.flight_none" : "sequence.orbitalis.alert.flight_wait")
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("sequence.orbitalis.alert.flight_now", stepName(lines.get(next).step()), status.flightWindow())
                            .withStyle(ChatFormatting.YELLOW);
        }
        if (status.busyStep() >= 0) {
            return Component.translatable("sequence.orbitalis.alert.working", stepName(LaunchStep.values()[status.busyStep()])).withStyle(ChatFormatting.AQUA);
        }
        if (status.poll() == 101) {
            // Le tour de table a dit non : on nomme le premier poste qui n'est pas prêt.
            for (int station = 0; station < STATIONS.length; station++) {
                if (!stationGo(status, station)) {
                    return Component.translatable("sequence.orbitalis.alert.poll", Component.translatable("sequence.orbitalis.station." + STATIONS[station]))
                            .withStyle(ChatFormatting.GOLD);
                }
            }
            return Component.translatable("sequence.orbitalis.alert.poll_again").withStyle(ChatFormatting.GOLD);
        }
        // Un satellite météo de l'agence voit le temps tourner : mieux vaut le savoir avant de mettre sous pression.
        if (status.forecast() == 0 && status.forecastTicks() < 1800) {
            return Component.translatable("sequence.orbitalis.alert.window", Math.max(1, status.forecastTicks() / 20)).withStyle(ChatFormatting.GOLD);
        }
        if (status.forecast() == 2) {
            return Component.translatable("sequence.orbitalis.alert.storm", (status.forecastTicks() + 1199) / 1200).withStyle(ChatFormatting.GOLD);
        }
        // La pluie retient le lancement elle aussi, sauf sur un pas de tir où il ne tombe jamais rien.
        if (status.forecast() == 1 && !status.weatherGo()) {
            return Component.translatable("sequence.orbitalis.alert.rain", (status.forecastTicks() + 1199) / 1200).withStyle(ChatFormatting.GOLD);
        }
        if (!status.weatherGo()) {
            return Component.translatable("launch.orbitalis.scrubbed_weather").withStyle(ChatFormatting.GOLD);
        }
        int next = next(lines);
        return next < 0 ? Component.translatable("sequence.orbitalis.alert.filling").withStyle(ChatFormatting.AQUA)
                : Component.translatable("sequence.orbitalis.alert.next", stepName(lines.get(next).step())).withStyle(ChatFormatting.AQUA);
    }

    // ------------------------------------------------------------------
    // Mise en page
    // ------------------------------------------------------------------

    /** Hauteur d'une ligne : celle du tour de table s'allonge pour montrer les réponses des postes. */
    private int rowHeight(Line line, int width) {
        return line.step() == LaunchStep.POLL && status.poll() >= 0 && phase() == LaunchTableBlockEntity.Phase.IDLE ? ROW + 2 + 13 * pollRows(width) : ROW;
    }

    /** Nombre de rangées que prennent les réponses des postes, sur cette largeur. */
    int pollRows(int width) {
        int rows = 1;
        int at = 0;
        for (String station : STATIONS) {
            int pill = Ui.pillWidth(font, Component.translatable("sequence.orbitalis.station." + station).getString());
            if (at > 0 && at + pill > width - 40) {
                rows++;
                at = 0;
            }
            at += pill + 4;
        }
        return rows;
    }

    /** Hauteur de la liste entière, sur cette largeur. */
    private int listHeight(int width) {
        int height = 0;
        for (Line line : lines()) {
            height += rowHeight(line, width);
        }
        return Math.max(height, ROW);
    }

    /** Hauteur de tout le panneau, l'avancement en tête compris, sur cette largeur. */
    int fullHeight(int width) {
        return HEADER + listHeight(width);
    }

    private int rowY(List<Line> lines, int index) {
        int at = y - scroll;
        for (int i = 0; i < index; i++) {
            at += rowHeight(lines.get(i), w);
        }
        return at;
    }

    private int maxScroll() {
        return Math.max(0, listHeight(w) - h);
    }

    /** Rapproche une valeur animée de sa cible, d'autant plus vite que {@code speed} est grand. */
    private float approach(Map<LaunchStep, Float> values, LaunchStep step, float target, float speed) {
        float shown = values.getOrDefault(step, target);
        shown += (target - shown) * Math.min(1.0F, frameSeconds * speed);
        if (Math.abs(target - shown) < 0.002F) {
            shown = target;
        }
        values.put(step, shown);
        return shown;
    }

    // ------------------------------------------------------------------
    // Dessin et clics
    // ------------------------------------------------------------------

    /**
     * Dessine l'avancement puis la liste dans ce rectangle ; la liste y défile si elle dépasse, l'étape à faire amenée
     * d'elle-même à l'écran.
     */
    void extract(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        long now = net.minecraft.util.Util.getMillis();
        frameSeconds = lastFrame == 0 ? 0.0F : Math.min(0.25F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        List<Line> lines = lines();
        int next = next(lines);
        extractProgress(graphics, lines, next, x, y, w);
        y += HEADER;
        h -= HEADER;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        // Une étape qui vient d'être faite : on note l'instant, pour son éclat.
        for (Line line : lines) {
            State before = lastState.put(line.step(), line.state());
            if (line.state() == State.DONE && before != null && before != State.DONE) {
                doneAt.put(line.step(), age);
            }
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
        if (next >= 0 && !scrolledByHand) {
            int nextTop = rowY(lines, next);
            int nextBottom = nextTop + rowHeight(lines.get(next), w);
            if (nextBottom > y + h) {
                scroll = Math.min(maxScroll(), scroll + nextBottom - (y + h));
            } else if (nextTop < y) {
                scroll = Math.max(0, scroll - (y - nextTop));
            }
        }
        hoveredStep = null;
        graphics.enableScissor(x, y - 1, x + w, y + h + 1);
        boolean overList = mouseY >= y && mouseY < y + h;
        for (int i = 0; i < lines.size(); i++) {
            extractLine(graphics, overList ? mouseX : -1, mouseY, lines, i, i == next);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int thumb = Math.max(10, h * h / listHeight(w));
            int thumbY = y + (h - thumb) * scroll / maxScroll();
            graphics.fill(x + w + 2, thumbY, x + w + 3, thumbY + thumb, Ui.LINE_HI);
        }
    }

    /** L'avancement : combien d'étapes sont faites, et une case par étape (faite, en cours, à faire maintenant, à venir). */
    private void extractProgress(GuiGraphicsExtractor graphics, List<Line> lines, int next, int x, int y, int w) {
        int total = 0;
        int done = 0;
        for (Line line : lines) {
            if (line.state() != State.OPTIONAL && line.state() != State.NOT_NEEDED) {
                total++;
                done += line.state() == State.DONE ? 1 : 0;
            }
        }
        if (total == 0) {
            return;
        }
        String text = Component.translatable("sequence.orbitalis.progress", done, total).getString();
        graphics.text(font, text, x + 4, y + 2, done == total ? Ui.OK : Ui.MUTED, false);
        int barX = x + 12 + font.width(text);
        int barW = x + w - 4 - barX;
        int gap = 2;
        int cell = Math.max(2, (barW - gap * (total - 1)) / total);
        int k = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.state() == State.OPTIONAL || line.state() == State.NOT_NEEDED) {
                continue;
            }
            int color = line.state() == State.DONE ? Ui.OK : line.state() == State.ACTIVE ? Ui.WARN
                    : i == next ? Ui.alpha(Ui.ACCENT, 0.55F + 0.45F * (float) Math.sin(age * 0.3)) : Ui.LINE;
            graphics.fill(barX + k * (cell + gap), y + 5, barX + k * (cell + gap) + cell, y + 8, color);
            k++;
        }
    }

    private void extractLine(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<Line> lines, int index, boolean isNext) {
        Line line = lines.get(index);
        int top = rowY(lines, index);
        int height = rowHeight(line, w);
        boolean flying = phase() == LaunchTableBlockEntity.Phase.FLIGHT;
        boolean clickable = line.state() != State.NOT_NEEDED && (status.busyStep() < 0 || flying);
        boolean over = Ui.inside(mouseX, mouseY, x, top, w, height - 2);
        if (over) {
            hoveredStep = line.step();
        }
        boolean hovered = clickable && over;
        float fade = approach(hoverFade, line.step(), hovered ? 1.0F : 0.0F, 14.0F);
        // En vol, l'étape ouverte clignote : c'est maintenant.
        boolean urgent = flying && line.state() == State.TODO && age / 5 % 2 == 0;
        // Surpression : la ligne des vannes clignote en rouge tant qu'elles restent ouvertes.
        boolean danger = line.step() == LaunchStep.VALVES && status.overpressure() > 0 && status.valvesOpen();
        if (danger) {
            Ui.round(graphics, x, top, w, height - 2, age / 5 % 2 == 0 ? 0xFF3A1210 : Ui.SURFACE_HI);
            Ui.frame(graphics, x, top, w, height - 2, Ui.BAD);
        } else if (isNext || urgent) {
            // L'étape à faire : son cadre palpite doucement.
            float pulse = 0.5F + 0.5F * (float) Math.sin((age + frameSeconds) * 0.25);
            Ui.round(graphics, x, top, w, height - 2, urgent ? 0xFF3A2C0C : Ui.SURFACE_HI);
            Ui.frame(graphics, x, top, w, height - 2, urgent ? Ui.WARN : Ui.alpha(Ui.ACCENT, 0.35F + 0.4F * pulse));
        } else if (fade > 0.01F) {
            Ui.round(graphics, x, top, w, height - 2, Ui.alpha(Ui.SURFACE_HI, fade));
        }
        // Faite à l'instant : un éclat vert qui s'efface en une seconde.
        Integer at = doneAt.get(line.step());
        if (at != null && age - at < 20) {
            float glow = 1.0F - (age - at) / 20.0F;
            Ui.round(graphics, x, top, w, height - 2, Ui.alpha(Ui.OK, 0.28F * glow));
        }
        // Le fil de la séquence, et la pastille de l'étape.
        int color = switch (line.state()) {
            case DONE -> Ui.OK;
            case ACTIVE -> Ui.WARN;
            case NOT_NEEDED -> Ui.DIM;
            case OPTIONAL -> Ui.LINE_HI;
            default -> isNext ? Ui.ACCENT : Ui.MUTED;
        };
        if (index > 0) {
            graphics.fill(x + 11, top - 2, x + 12, top + 6, Ui.LINE);
        }
        if (index < lines.size() - 1) {
            graphics.fill(x + 11, top + 13, x + 12, top + height, Ui.LINE);
        }
        Ui.dot(graphics, x + 9, top + 7, color);
        if (line.state() != State.DONE && line.state() != State.ACTIVE) {
            // À faire : un anneau vide.
            graphics.fill(x + 10, top + 8, x + 13, top + 11, isNext || urgent ? Ui.SURFACE_HI : 0xFF07090C);
        }
        // À droite : le bouton de l'étape à faire, sinon rien ; la valeur passe sous le nom.
        int right = x + w - 6;
        int buttonY = top + (ROW - 2 - 14) / 2;
        if (line.state() == State.OPTIONAL) {
            // Un essai : son bouton est toujours là, discret.
            Ui.button(graphics, font, Component.translatable("sequence.orbitalis.action.test"), right - ACTION_WIDTH, buttonY, ACTION_WIDTH, 14,
                    hovered, clickable, false, Ui.MUTED);
            right -= ACTION_WIDTH + 8;
        } else if ((isNext || line.step() == LaunchStep.IGNITE && line.state() == State.TODO && status.hasRocket()) && clickable) {
            // L'allumage a toujours son bouton : rien n'oblige à finir le plein ni à faire le tour de table.
            Component action = Component.translatable(line.step() == LaunchStep.VALVES && status.valvesOpen() ? "sequence.orbitalis.action.close_valves"
                    : "sequence.orbitalis.action." + line.step().name().toLowerCase(Locale.ROOT));
            Ui.button(graphics, font, action, right - ACTION_WIDTH, buttonY, ACTION_WIDTH, 14, hovered, true, true,
                    line.step() == LaunchStep.IGNITE || line.step() == LaunchStep.RELEASE ? Ui.WARN : Ui.ACCENT);
            right -= ACTION_WIDTH + 8;
        }
        int valueColor = danger ? Ui.BAD : line.state() == State.DONE ? Ui.OK : line.state() == State.ACTIVE ? Ui.WARN
                : line.step() == LaunchStep.POLL && status.poll() == 101 ? Ui.BAD : Ui.MUTED;
        int textW = right - (x + 22) - 2;
        graphics.text(font, Ui.fit(font, stepName(line.step()).getString(), textW), x + 22, top + 4,
                line.state() == State.NOT_NEEDED ? Ui.DIM : Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, line.value().getString(), textW), x + 22, top + 14, valueColor, false);
        if (line.gauge() >= 0.0F) {
            float gauge = approach(shownGauge, line.step(), line.gauge(), 8.0F);
            Ui.bar(graphics, x + 22, top + ROW - 4, textW, 1, gauge,
                    danger ? Ui.BAD : line.state() == State.DONE ? Ui.OK : line.state() == State.ACTIVE ? Ui.WARN : Ui.ACCENT);
        } else {
            shownGauge.remove(line.step());
        }
        // Le tour de table : les postes répondent l'un après l'autre.
        if (line.step() == LaunchStep.POLL && height > ROW) {
            pollPills(graphics, font, status, x + 22, top + ROW, w - 40);
        }
    }

    /** Les réponses des postes du tour de table, en pastilles, l'une après l'autre ; rend la hauteur prise. */
    static int pollPills(GuiGraphicsExtractor graphics, Font font, LaunchStatusPayload status, int x, int y, int width) {
        int heard = stationsHeard(status);
        int at = 0;
        int pillY = y;
        for (int station = 0; station < STATIONS.length; station++) {
            String name = Component.translatable("sequence.orbitalis.station." + STATIONS[station]).getString();
            int stationColor = station >= heard ? Ui.DIM : stationGo(status, station) ? Ui.OK : Ui.BAD;
            if (at > 0 && at + Ui.pillWidth(font, name) > width) {
                at = 0;
                pillY += 13;
            }
            at += Ui.pill(graphics, font, name, x + at, pillY, stationColor) + 4;
        }
        return pillY + 13 - y;
    }

    /** Un clic sur la liste : il commande l'étape de la ligne. Vrai si le clic était pour elle. */
    boolean click(double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + h || mouseX < x || mouseX >= x + w) {
            return false;
        }
        List<Line> lines = lines();
        for (int i = 0; i < lines.size(); i++) {
            if (Ui.inside(mouseX, mouseY, x, rowY(lines, i), w, rowHeight(lines.get(i), w) - 2) && lines.get(i).state() != State.NOT_NEEDED) {
                send(lines.get(i).step());
                return true;
            }
        }
        return true;
    }

    /** La touche Entrée : elle commande l'étape à faire, s'il y en a une. Vrai si elle a servi. */
    boolean runNext() {
        List<Line> lines = lines();
        int next = next(lines);
        boolean flying = phase() == LaunchTableBlockEntity.Phase.FLIGHT;
        if (next < 0 || !(status.busyStep() < 0 || flying)) {
            return false;
        }
        send(lines.get(next).step());
        return true;
    }

    /** La molette fait défiler la liste. Vrai si la souris était dessus. */
    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, x, y, w, h)) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) (amount * ROW), 0, maxScroll());
        scrolledByHand = true;
        return true;
    }

    /** Commande une étape ; la liste reprend son suivi de l'étape à faire. */
    void send(LaunchStep step) {
        scrolledByHand = false;
        ClientPacketDistributor.sendToServer(new LaunchCommandPayload(status.console(), LaunchCommandPayload.STEP + step.ordinal()));
        Ui.click();
    }
}
