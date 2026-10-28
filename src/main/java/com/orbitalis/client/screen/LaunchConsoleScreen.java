package com.orbitalis.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.orbitalis.client.render.IsoMap;
import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.network.LaunchCommandPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;
import com.orbitalis.phase1.spaceport.LaunchStep;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran du centre de contrôle de mission, ouvert depuis un pupitre de lancement.
 * <p>
 * Il s'organise comme une salle de contrôle :
 * <ul>
 * <li>en haut, l'horloge de mission (T- pendant le compte à rebours, T+ pendant le vol) et la phase en cours ;</li>
 * <li>à gauche, la séquence de lancement elle-même (voir {@link SequencePanel}) : on la déroule d'ici, étape par
 * étape, sans autre fenêtre ;</li>
 * <li>à droite, la vue : la maquette du pas de tir, en cubes comme la carte, de trois quarts et dans son orientation,
 * animée comme dans le monde (bras qui s'écartent, moteurs qui s'allument, fusée qui s'élève), ou la carte de
 * navigation ; et dessous, la télémétrie ;</li>
 * <li>en bas, le journal des événements, le bouton « Go / No-Go » et l'arrêt.</li>
 * </ul>
 * La fenêtre « Go / No-Go » rassemble les postes du centre de contrôle, le tour de table et les essais du pas de tir.
 * L'écran redemande l'état au serveur cinq fois par seconde : il reste à jour tant qu'il est ouvert.
 */
public class LaunchConsoleScreen extends Screen implements BlockScreen {
    private static final int COLOR_BORDER = 0xFF04060A;
    private static final int COLOR_EDGE = Ui.LINE;
    private static final int COLOR_ACCENT = Ui.ACCENT;
    private static final int COLOR_TEXT = Ui.TEXT;
    private static final int COLOR_MUTED = Ui.MUTED;
    private static final int COLOR_OK = Ui.OK;
    private static final int COLOR_BAD = Ui.BAD;
    private static final int COLOR_WARNING = Ui.WARN;
    private static final int COLOR_OPTIONAL = Ui.DIM;
    private static final int COLOR_OCEAN = 0xFF0E1C2B;

    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 400;
    private static final int TOP_BAR = 30;
    private static final int BOTTOM_BAR = 50;
    /** Hauteur du bandeau de télémétrie, en bas de la vue. */
    private static final int TELEMETRY = 40;
    private static final int LINE_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TAB_HEIGHT = 12;
    private static final int LOG_LINES = 3;
    /** Intervalle entre deux demandes d'actualisation, en ticks : assez court pour suivre la montée en poussée. */
    private static final int REFRESH_TICKS = 4;
    /** Durée du vol simulé, en secondes : sert à placer la fusée le long de sa trajectoire. */

    /** Poste du tableau « Go / No-Go ». */
    private record Station(String key, boolean go, boolean relevant) {
    }

    /** Point de la courbe d'altitude : secondes depuis le décollage, altitude en km. */
    private record Sample(int seconds, float altitudeKm) {
    }

    /** Une zone cliquable de l'image en cours, et ce qu'elle fait. */
    private record Action(int x, int y, int w, int h, Runnable run) {
    }

    private LaunchStatusPayload status;
    private SequencePanel sequence;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int age;
    private float framePartial;
    private int lastMouseX;
    private int lastMouseY;

    /** Journal des événements, du plus ancien au plus récent. */
    private final Deque<Component> log = new ArrayDeque<>();
    /** Altitudes relevées depuis le décollage, pour la courbe. */
    private final List<Sample> altitudeHistory = new ArrayList<>();
    /** Les zones cliquables posées pendant l'image en cours. */
    private final List<Action> actions = new ArrayList<>();

    /** La vue : la maquette du pas de tir (0) ou la carte de navigation (1) ; vrai si le joueur l'a choisie lui-même. */
    private int view;
    private boolean viewChosen;
    /** La vue montrée à l'image précédente, et l'instant où elle a changé : la nouvelle apparaît en fondu. */
    private int shownView = -1;
    private long viewChangedAt;
    /** Le cadre du schéma du pas de tir, à l'image en cours. */
    private int[] padArea;
    /** Vrai quand la fenêtre « Go / No-Go » est ouverte ; l'instant où elle s'est ouverte (elle apparaît en fondu). */
    private boolean goWindow;
    private long goOpenedAt;
    /** Le poste choisi d'un clic dans la fenêtre « Go / No-Go », ou -1 : son explication reste affichée. */
    private int pickedStation = -1;

    public LaunchConsoleScreen(LaunchStatusPayload status) {
        super(Component.translatable("spaceport.orbitalis.console.title"));
        this.status = status;
    }

    /** Ouvre ou ferme la fenêtre « Go / No-Go » (sert aux captures de vérification). */
    public void showGoWindow(boolean open) {
        if (open) {
            openGoWindow();
        } else {
            goWindow = false;
        }
    }

    private void openGoWindow() {
        goWindow = true;
        goOpenedAt = net.minecraft.util.Util.getMillis();
        pickedStation = -1;
    }

    /** Choisit la vue : la maquette du pas de tir (0) ou la carte (1) (sert aux captures de vérification). */
    public void showView(int which) {
        view = which;
        viewChosen = true;
    }

    /** Reçoit un nouvel état du serveur, s'il concerne bien ce pupitre, et en tire les événements du journal. */
    public void update(LaunchStatusPayload newStatus) {
        if (!newStatus.console().equals(status.console())) {
            return;
        }
        recordEvents(status, newStatus);
        status = newStatus;
        if (sequence != null) {
            sequence.update(newStatus);
        }
    }

    /** Compare l'ancien état au nouveau : chaque changement marquant devient une ligne du journal. */
    private void recordEvents(LaunchStatusPayload before, LaunchStatusPayload after) {
        LaunchTableBlockEntity.Phase previous = phaseOf(before);
        LaunchTableBlockEntity.Phase current = phaseOf(after);
        if (current == LaunchTableBlockEntity.Phase.COUNTDOWN) {
            if (previous != LaunchTableBlockEntity.Phase.COUNTDOWN) {
                altitudeHistory.clear();
                addEvent(after, "countdown");
            } else {
                // Étapes de la chronologie : on les note quand le compte à rebours les franchit.
                noteIfCrossed(before, after, 20, "poll");
                noteIfCrossed(before, after, 10, "arms");
                noteIfCrossed(before, after, 3, "ignition");
            }
        } else if (current == LaunchTableBlockEntity.Phase.FLIGHT) {
            if (previous != LaunchTableBlockEntity.Phase.FLIGHT) {
                altitudeHistory.clear();
                addEvent(after, "liftoff");
            } else if (before.seconds() < flightTicks() / 40.0F && after.seconds() >= flightTicks() / 40.0F) {
                addEvent(after, isOrbital(after) ? "staging" : "burnout");
            }
            if (altitudeHistory.isEmpty() || altitudeHistory.get(altitudeHistory.size() - 1).seconds() != after.seconds()) {
                altitudeHistory.add(new Sample(after.seconds(), after.altitudeKm()));
            }
        } else if (previous != LaunchTableBlockEntity.Phase.IDLE) {
            // Retour au repos : l'issue du lancement.
            LaunchTableBlockEntity.Result[] results = LaunchTableBlockEntity.Result.values();
            LaunchTableBlockEntity.Result result = results[Math.floorMod(after.result(), results.length)];
            if (result != LaunchTableBlockEntity.Result.NONE) {
                addEvent(after, result.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private void noteIfCrossed(LaunchStatusPayload before, LaunchStatusPayload after, int seconds, String event) {
        if (before.seconds() > seconds && after.seconds() <= seconds) {
            addEvent(after, event);
        }
    }

    private void addEvent(LaunchStatusPayload at, String event) {
        log.addLast(Component.translatable("launch.orbitalis.log.entry", missionClock(at), Component.translatable("launch.orbitalis.log." + event)));
        while (log.size() > LOG_LINES) {
            log.removeFirst();
        }
    }

    @Override
    protected void init() {
        if (sequence == null) {
            sequence = new SequencePanel(font, status, false);
        }
        panelWidth = Math.min(width - 16, MAX_WIDTH);
        panelHeight = Math.min(height - 16, MAX_HEIGHT);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        sequence.tick();
        if (++age % REFRESH_TICKS == 0) {
            send(LaunchCommandPayload.REFRESH);
        }
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new LaunchCommandPayload(status.console(), action));
    }

    // ------------------------------------------------------------------
    // État
    // ------------------------------------------------------------------

    private static LaunchTableBlockEntity.Phase phaseOf(LaunchStatusPayload status) {
        LaunchTableBlockEntity.Phase[] phases = LaunchTableBlockEntity.Phase.values();
        return phases[Math.floorMod(status.phase(), phases.length)];
    }

    private LaunchTableBlockEntity.Phase phase() {
        return phaseOf(status);
    }

    private static boolean isOrbital(LaunchStatusPayload status) {
        return status.reach() >= RocketStats.Reach.LOW_ORBIT.ordinal();
    }

    private boolean safe() {
        return status.distance() >= LaunchConsoleBlock.SAFE_DISTANCE;
    }

    private double maxMass() {
        return LaunchComplex.maxRocketMass(status.capacityRadius());
    }

    private boolean armOk() {
        return !status.needsFuel() || (status.towerAuthorized() && status.arms() > 0 && status.towerHeight() >= status.requiredTower());
    }

    /** Postes du tableau « Go / No-Go », dans l'ordre d'affichage. Le directeur de vol résume tous les autres. */
    private List<Station> stations() {
        List<Station> stations = new ArrayList<>();
        boolean rocket = status.hasRocket();
        stations.add(new Station("pad", status.linked() && status.commissioned() && status.radius() > 0, true));
        stations.add(new Station("range", status.linked() && safe() && status.skyClear(), true));
        stations.add(new Station("weather", status.weatherGo(), true));
        stations.add(new Station("ground", armOk(), status.needsFuel()));
        stations.add(new Station("propellant", status.fuelPercent() >= 100, rocket && status.needsFuel()));
        stations.add(new Station("booster", rocket && status.rocketMass() <= maxMass(), true));
        stations.add(new Station("guidance", rocket, true));
        boolean all = true;
        for (Station station : stations) {
            all &= station.go() || !station.relevant();
        }
        stations.add(new Station("flight", all, true));
        return stations;
    }

    /** Vrai si tout est en ordre pour démarrer le compte à rebours. */
    private boolean goForLaunch() {
        List<Station> stations = stations();
        return stations.get(stations.size() - 1).go();
    }

    /** La table reliée, connue du client : elle porte la maquette et la carte. */
    /** Durée du vol de la fusée de ce pupitre, en ticks. */
    private int flightTicks() {
        LaunchTableBlockEntity table = table();
        return table != null ? table.flightTicks() : LaunchTableBlockEntity.DEFAULT_FLIGHT_TICKS;
    }

    private LaunchTableBlockEntity table() {
        return minecraft.level == null ? null : LaunchTableBlockEntity.nearest(minecraft.level, status.console(), LaunchConsoleBlock.RELAY_RANGE);
    }

    // ------------------------------------------------------------------
    // Clics
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        // La fenêtre « Go / No-Go » passe devant tout : un clic hors d'elle la referme.
        if (goWindow) {
            for (Action action : List.copyOf(actions)) {
                if (Ui.inside(mx, my, action.x(), action.y(), action.w(), action.h())) {
                    action.run().run();
                    return true;
                }
            }
            int[] box = goWindowBox();
            if (!Ui.inside(mx, my, box[0], box[1], box[2], box[3])) {
                goWindow = false;
            }
            return true;
        }
        for (Action action : List.copyOf(actions)) {
            if (Ui.inside(mx, my, action.x(), action.y(), action.w(), action.h())) {
                action.run().run();
                return true;
            }
        }
        if (sequence.click(mx, my)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (goWindow) {
            return true;
        }
        return sequence.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Échap referme d'abord la fenêtre « Go / No-Go ».
        if (goWindow && event.key() == 256) {
            goWindow = false;
            return true;
        }
        // Entrée : l'étape à faire de la séquence.
        if (!goWindow && (event.key() == 257 || event.key() == 335) && status.linked() && sequence.runNext()) {
            return true;
        }
        return super.keyPressed(event);
    }

    /** Pose une zone cliquable, et la dessine en bouton. */
    private void button(GuiGraphicsExtractor graphics, Component label, int x, int y, int w, int h, boolean enabled, boolean primary, int color,
            Runnable run) {
        boolean hovered = enabled && Ui.inside(lastMouseX, lastMouseY, x, y, w, h);
        Ui.button(graphics, font, label, x, y, w, h, hovered, enabled, primary, color);
        if (enabled) {
            actions.add(new Action(x, y, w, h, () -> {
                Ui.click();
                run.run();
            }));
        }
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, panelWidth, panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        framePartial = partialTick;
        // Sous la fenêtre « Go / No-Go », le reste de l'écran ne réagit pas à la souris.
        lastMouseX = goWindow ? -1 : mouseX;
        lastMouseY = goWindow ? -1 : mouseY;
        actions.clear();
        // La vue suit la phase, tant que le joueur n'en a pas choisi une : la maquette au sol, la carte en vol.
        if (!viewChosen) {
            view = phase() == LaunchTableBlockEntity.Phase.FLIGHT ? 1 : 0;
        }
        extractTopBar(graphics);

        int contentTop = top + TOP_BAR + 4;
        int contentHeight = panelHeight - TOP_BAR - BOTTOM_BAR - 8;
        int sequenceWidth = Mth.clamp(panelWidth * 2 / 5, 236, 300);
        int leftX = left + 6;
        int rightX = leftX + sequenceWidth + 6;
        int rightWidth = left + panelWidth - 6 - rightX;
        extractSequence(graphics, leftX, contentTop, sequenceWidth, contentHeight);
        extractView(graphics, rightX, contentTop, rightWidth, contentHeight);
        extractBottomBar(graphics);
        if (goWindow) {
            actions.clear();
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            extractGoWindow(graphics);
        }
    }

    /** Cadre d'un volet : fond sombre et titre. Renvoie la hauteur à laquelle commence son contenu. */
    private int panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String titleKey) {
        Ui.card(graphics, x, y, w, h, false);
        Ui.heading(graphics, font, Component.translatable(titleKey), x + 6, y + 6);
        return y + 19;
    }

    /** Bandeau du haut : le titre, l'horloge de mission en grand, la phase en cours. */
    private void extractTopBar(GuiGraphicsExtractor graphics) {
        String clock = missionClock(status);
        int color = switch (phase()) {
            case COUNTDOWN -> COLOR_WARNING;
            case FLIGHT -> COLOR_OK;
            default -> COLOR_MUTED;
        };
        // Trois éléments sur la barre : le titre à gauche, l'étiquette de phase à droite, l'horloge entre les deux.
        // Le titre cède la place qu'il faut à l'horloge : il est coupé plutôt que recouvert.
        String phaseName = phaseText().getString().toUpperCase(Locale.ROOT);
        int pillLeft = left + panelWidth - 10 - Ui.pillWidth(font, phaseName);
        int clockWidth = font.width(clock) * 2;
        String name = title.getString();
        while (name.length() > 1 && left + 10 + font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) + 10 + clockWidth > pillLeft - 10) {
            name = name.substring(0, name.length() - 2).stripTrailing() + "…";
        }
        int nameEnd = left + 10 + font.width(Component.literal(name).withStyle(ChatFormatting.BOLD));
        graphics.text(font, Component.literal(name).withStyle(ChatFormatting.BOLD), left + 10, top + 11, COLOR_TEXT, false);
        int clockX = Math.max(nameEnd + 10, Math.min(left + (panelWidth - clockWidth) / 2, pillLeft - 10 - clockWidth));
        Ui.big(graphics, font, clock, clockX, top + 7, 2.0F, color);
        Ui.pill(graphics, font, phaseName, pillLeft, top + 10, phase() == LaunchTableBlockEntity.Phase.IDLE ? (goForLaunch() ? COLOR_OK : COLOR_MUTED) : color);
        Ui.hline(graphics, left + 1, top + TOP_BAR, panelWidth - 2);
    }

    private Component phaseText() {
        return switch (phase()) {
            case COUNTDOWN -> {
                int seconds = status.seconds();
                String stage = seconds > 20 ? "systems" : seconds > 10 ? "poll" : seconds > 3 ? "arms" : "ignition";
                yield Component.translatable("launch.orbitalis.console.stage." + stage);
            }
            case FLIGHT -> Component.translatable("launch.orbitalis.console.in_flight");
            default -> Component.translatable(goForLaunch() ? "launch.orbitalis.console.go" : "launch.orbitalis.console.standby");
        };
    }

    /** La séquence de lancement, dans son volet : l'aide de l'ordinateur de bord en tête, la liste, puis ce qu'il faut savoir. */
    private void extractSequence(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int listTop = panel(graphics, x, y, w, h, "launch.orbitalis.console.heading_sequence");
        if (!status.linked()) {
            int lineY = listTop;
            for (var line : font.split(Component.translatable("spaceport.orbitalis.console.not_linked", LaunchConsoleBlock.RANGE), w - 12)) {
                graphics.text(font, line, x + 6, lineY, COLOR_BAD, false);
                lineY += LINE_HEIGHT;
            }
            return;
        }
        // Niveau d'aide de l'ordinateur de bord : un clic passe au suivant.
        Component assist = Component.translatable("sequence.orbitalis.assist.label",
                Component.translatable("sequence.orbitalis.assist." + Math.clamp(status.assist(), 0, 2)));
        int assistWidth = font.width(assist.getString().toUpperCase(Locale.ROOT)) + 12;
        button(graphics, assist, x + w - 6 - assistWidth, y + 4, assistWidth, TAB_HEIGHT, phase() == LaunchTableBlockEntity.Phase.IDLE, false,
                status.assist() > 0 ? COLOR_OK : COLOR_MUTED, () -> sequence.send(LaunchStep.ASSIST));
        // Ce qu'il faut savoir maintenant, en pied du volet ; la liste prend le reste.
        List<net.minecraft.util.FormattedCharSequence> alert = font.split(sequence.alert(), w - 14);
        int alertLines = Math.min(3, alert.size());
        int alertTop = y + h - 6 - alertLines * 10;
        sequence.extract(graphics, x + 4, listTop + 2, w - 12, alertTop - 6 - (listTop + 2), lastMouseX, lastMouseY);
        Ui.hline(graphics, x + 1, alertTop - 4, w - 2);
        for (int k = 0; k < alertLines; k++) {
            graphics.text(font, alert.get(k), x + 7, alertTop + k * 10, COLOR_TEXT, false);
        }
    }

    /** La vue : la maquette du pas de tir ou la carte de navigation, au choix, par deux onglets. */
    private void extractView(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Ui.card(graphics, x, y, w, h, false);
        // Les deux onglets, en tête du volet.
        int tabX = x + 6;
        String[] tabs = {"launch.orbitalis.console.tab.pad", "launch.orbitalis.console.tab.map"};
        for (int k = 0; k < tabs.length; k++) {
            Component label = Component.translatable(tabs[k]);
            int tabWidth = font.width(label.getString().toUpperCase(Locale.ROOT)) + 12;
            int which = k;
            button(graphics, label, tabX, y + 4, tabWidth, TAB_HEIGHT, true, view == k, COLOR_ACCENT, () -> {
                view = which;
                viewChosen = true;
            });
            tabX += tabWidth + 4;
        }
        // L'atlas : la carte du monde, en grand, que l'on parcourt librement.
        Component atlas = Component.translatable("atlas.orbitalis.open");
        int atlasWidth = font.width(atlas.getString().toUpperCase(Locale.ROOT)) + 12;
        if (x + w - 6 - atlasWidth > tabX + 4) {
            button(graphics, atlas, x + w - 6 - atlasWidth, y + 4, atlasWidth, TAB_HEIGHT, true, false, COLOR_ACCENT, () -> {
                LaunchTableBlockEntity table = table();
                Minecraft.getInstance().gui.setScreen(new AtlasScreen(this, status.console(), table == null ? status.console() : table.getBlockPos()));
            });
        }
        int contentX = x + 4;
        int contentY = y + 19;
        int contentW = w - 8;
        int contentH = h - 23;
        padArea = null;
        LaunchTableBlockEntity table = table();
        if (table == null || contentW <= 0 || contentH <= 0) {
            graphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, COLOR_BORDER);
            Component none = Component.translatable("launch.orbitalis.console.no_pad");
            graphics.text(font, none, contentX + (contentW - font.width(none)) / 2, contentY + contentH / 2 - 4, COLOR_MUTED, false);
            return;
        }
        if (view == 0) {
            extractPad(graphics, table, contentX, contentY, contentW, contentH - TELEMETRY);
        } else {
            extractMap(graphics, table, contentX, contentY, contentW, contentH);
        }
        graphics.nextStratum();
        extractTelemetry(graphics, contentX, contentY + contentH - TELEMETRY, contentW, TELEMETRY);
        // Au changement de vue, la nouvelle sort de l'ombre en un quart de seconde.
        long now = net.minecraft.util.Util.getMillis();
        if (shownView != view) {
            if (shownView >= 0) {
                viewChangedAt = now;
            }
            shownView = view;
        }
        float fade = 1.0F - Mth.clamp((now - viewChangedAt) / 250.0F, 0.0F, 1.0F);
        if (fade > 0.0F) {
            graphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, Math.round(fade * fade * 230.0F) << 24 | 0x04060A);
        }
    }

    /**
     * Le pas de tir, comme sur les écrans de contrôle de la NASA et de SpaceX : à gauche, son schéma de profil en traits
     * fins sur fond de nuit (la tour en treillis et ses bras, la fusée étage par étage, ses réservoirs qui se remplissent
     * de la couleur de leurs ergols ; au décollage, la flamme, la montée, les étages et les boosters qui se séparent) ;
     * à droite, le chargement de chaque ergol et les voyants des systèmes du pas de tir.
     */
    private void extractPad(GuiGraphicsExtractor graphics, LaunchTableBlockEntity table, int x, int y, int w, int h) {
        padArea = new int[] {x, y, w, h};
        graphics.fillGradient(x, y, x + w, y + h, 0xFF08131F, 0xFF03070C);
        for (int gx = x + 8; gx < x + w; gx += 12) {
            graphics.fill(gx, y, gx + 1, y + h, 0xFF0B1828);
        }
        for (int gy = y + 8; gy < y + h; gy += 12) {
            graphics.fill(x, gy, x + w, gy + 1, 0xFF0B1828);
        }
        int loadsWidth = Mth.clamp(w * 2 / 5, 96, 160);
        int schematicWidth = w - loadsWidth - 4;
        graphics.enableScissor(x, y, x + schematicWidth, y + h);
        extractSchematic(graphics, table, x, y, schematicWidth, h);
        graphics.disableScissor();
        graphics.fill(x + schematicWidth + 1, y + 4, x + schematicWidth + 2, y + h - 4, COLOR_EDGE);
        extractLoads(graphics, table, x + schematicWidth + 7, y + 5, loadsWidth - 11, h - 10);
    }

    /** Teinte des ergols d'un étage, dans ses réservoirs. */
    private static int propellantColor(com.orbitalis.phase1.rocket.Propellant propellant) {
        return switch (propellant) {
            case KEROLOX -> 0xFFE89A3C;
            case HYDROLOX -> 0xFF7FD3FF;
            case HYDROGEN, FUSION -> 0xFFA8E6FF;
            case SOLID -> 0xFFC9B48A;
            default -> 0xFFD9A93A;
        };
    }

    /** Le schéma de profil : le sol, le tablier, la tour et ses bras, la fusée. */
    private void extractSchematic(GuiGraphicsExtractor graphics, LaunchTableBlockEntity table, int x, int y, int w, int h) {
        com.orbitalis.phase1.spaceport.RocketShape shape = table.hasRocket() ? table.rocketShape() : null;
        int towerBlocks = Math.max(3, table.getComplex().towerHeight());
        int rocketBlocks = shape == null ? 0 : shape.height();
        int ground = y + h - 10;
        float unit = Mth.clamp((h - 22) / (float) Math.max(towerBlocks, rocketBlocks + 1), 1.5F, 6.0F);
        boolean wide = shape != null && shape.isWide();
        int half = Math.max(3, Math.round(unit * (wide ? 1.5F : 0.6F)));
        int axis = x + w * 3 / 5;
        int line = 0xFFCFE3F5;
        // Le sol, le tablier et la bouche du carneau sous la fusée.
        graphics.fill(x + 2, ground + 3, x + w - 2, ground + 4, 0xFF1C2733);
        int deckHalf = Math.max(half + 10, Math.round(unit * (table.getComplex().radius() + 0.5F)));
        graphics.fill(axis - deckHalf, ground, axis + deckHalf, ground + 3, 0xFF3A4654);
        graphics.fill(axis - half - 1, ground, axis + half + 1, ground + 3, 0xFF141B23);
        // La tour de service, en treillis, du côté gauche.
        int towerTop = ground - Math.round(towerBlocks * unit);
        int towerWidth = Math.max(5, Math.round(unit * 1.4F));
        int towerRight = axis - half - Math.max(3, Math.round(unit * 0.8F));
        int towerLeft = towerRight - towerWidth;
        graphics.fill(towerLeft, towerTop, towerLeft + 1, ground, 0xFFD24A3A);
        graphics.fill(towerRight - 1, towerTop, towerRight, ground, 0xFFD24A3A);
        graphics.fill(towerLeft, towerTop, towerRight, towerTop + 1, 0xFFD24A3A);
        int bay = Math.max(4, towerWidth);
        for (int by = ground; by > towerTop; by -= bay) {
            graphics.fill(towerLeft, by - 1, towerRight, by, 0xFF8E3A30);
            for (int k = 0; k < bay && by - k > towerTop; k++) {
                int dx = Math.round(k * (towerWidth - 1) / (float) bay);
                graphics.fill(towerLeft + dx, by - k - 1, towerLeft + dx + 1, by - k, 0xFF8E3A30);
            }
        }
        // Le paratonnerre au sommet de la tour.
        graphics.fill((towerLeft + towerRight) / 2, towerTop - 4, (towerLeft + towerRight) / 2 + 1, towerTop, 0xFF8C949E);
        if (shape == null) {
            return;
        }
        boolean flying = phase() == LaunchTableBlockEntity.Phase.FLIGHT;
        float lift = flying ? table.renderAscent(framePartial) * unit : 0.0F;
        // Les bras ombilicaux : branchés à la fusée, ou repliés contre la tour.
        if (!flying) {
            int[] levels = {Math.max(1, Math.round(rocketBlocks * 0.4F)), Math.max(2, rocketBlocks - 2)};
            for (int level : levels) {
                int ay = ground - Math.round(level * unit);
                if (ay <= towerTop) {
                    continue;
                }
                int reach = status.retracted() ? towerRight + 3 : axis - half;
                graphics.fill(towerRight, ay, reach, ay + 2, 0xFFB9C0C7);
            }
        }
        // Les étages déjà largués ne sont plus dessinés.
        int dropped = 0;
        for (int k = 0; k < 16; k++) {
            if (table.stageAge(k, framePartial) >= 0.0F) {
                dropped = k + 1;
            }
        }
        // Le remplissage de chaque étage : au sol, ce qui est chargé ; en vol, l'étage allumé se vide.
        int[] loaded = table.fuelSnapshot();
        com.orbitalis.phase1.rocket.RocketFuel need = table.requiredFuel();
        float rp1 = need.rp1() > 0 ? loaded[0] / (float) need.rp1() : 1.0F;
        float lox = need.oxygen() > 0 ? loaded[1] / (float) need.oxygen() : 1.0F;
        float lh2 = need.hydrogen() > 0 ? loaded[2] / (float) need.hydrogen() : 1.0F;
        float ch4 = need.methane() > 0 && loaded.length > 4 ? loaded[4] / (float) need.methane() : 1.0F;
        float he3 = need.helium3() > 0 && loaded.length > 5 ? loaded[5] / (float) need.helium3() : 1.0F;
        List<Integer> staging = flying ? table.stagingTicks() : List.of();
        float now = flying ? table.phaseTicks() + framePartial : 0.0F;
        java.util.List<com.orbitalis.phase1.spaceport.RocketShape.Segment> segments = shape.segments();
        int count = segments.size();
        float[] bottoms = new float[count];
        for (int i = 0; i < count; i++) {
            bottoms[i] = ground - i * unit - lift;
        }
        int lowest = -1;
        for (int s = 0; s < com.orbitalis.phase1.rocket.RocketDesign.MAX_STAGES; s++) {
            if (s < dropped) {
                continue;
            }
            float stageBottom = Float.NaN;
            float stageTop = Float.NaN;
            float bodyBottom = Float.NaN;
            float bodyTop = Float.NaN;
            for (int i = 0; i < count; i++) {
                if (segments.get(i).stage() != s) {
                    continue;
                }
                float b0 = bottoms[i];
                float t0 = b0 - unit;
                stageBottom = Float.isNaN(stageBottom) ? b0 : Math.max(stageBottom, b0);
                stageTop = Float.isNaN(stageTop) ? t0 : Math.min(stageTop, t0);
                if (segments.get(i).role() == com.orbitalis.phase1.spaceport.RocketShape.Role.BODY) {
                    bodyBottom = Float.isNaN(bodyBottom) ? b0 : Math.max(bodyBottom, b0);
                    bodyTop = Float.isNaN(bodyTop) ? t0 : Math.min(bodyTop, t0);
                }
            }
            if (Float.isNaN(stageBottom)) {
                continue;
            }
            if (lowest < 0) {
                lowest = Math.round(stageBottom);
            }
            com.orbitalis.phase1.rocket.RocketDesign.Stage designStage = com.orbitalis.phase1.rocket.RocketItem.design(table.getRocket()).stages().get(s);
            com.orbitalis.phase1.rocket.RocketParts.Part enginePart = com.orbitalis.phase1.rocket.RocketParts.get(designStage.engines());
            com.orbitalis.phase1.rocket.Propellant propellant = enginePart == null || enginePart.engine() == null
                    ? com.orbitalis.phase1.rocket.Propellant.SOLID : enginePart.engine().propellant();
            float fill = switch (propellant) {
                case KEROLOX -> Math.min(rp1, lox);
                case HYDROLOX -> Math.min(lh2, lox);
                case HYDROGEN -> lh2;
                case METHALOX -> Math.min(ch4, lox);
                case FUSION -> Math.min(lh2, he3);
                default -> 1.0F;
            };
            if (flying && s == dropped) {
                float start = s == 0 ? 0.0F : s - 1 < staging.size() ? staging.get(s - 1) : 0.0F;
                float end = s < staging.size() ? staging.get(s) : table.flightTicks();
                fill *= 1.0F - Mth.clamp((now - start) / Math.max(1.0F, end - start), 0.0F, 1.0F);
            }
            int y0 = Math.round(stageTop);
            int y1 = Math.round(stageBottom);
            graphics.fill(axis - half, y0, axis + half, y1, 0xFF0D1824);
            if (!Float.isNaN(bodyBottom)) {
                int fillTop = Math.round(bodyBottom - (bodyBottom - bodyTop) * Mth.clamp(fill, 0.0F, 1.0F));
                graphics.fill(axis - half + 1, fillTop, axis + half - 1, Math.round(bodyBottom), Ui.alpha(propellantColor(propellant), 0.85F));
            }
            // La baie de propulsion, plus sombre, et la tuyère dessous.
            graphics.fill(axis - half, y1 - Math.max(2, Math.round(unit * 0.6F)), axis + half, y1, 0xFF3A4048);
            graphics.fill(axis - half / 2, y1, axis + half / 2 + 1, y1 + 2, 0xFF8C949E);
            frame(graphics, axis - half, y0, 2 * half, y1 - y0, line);
            graphics.text(font, String.valueOf(s + 1), axis + half + 3, (y0 + y1) / 2 - 4, COLOR_MUTED, false);
        }
        // Ce qui coiffe la fusée : un fût, puis une pointe en gradins.
        int topCount = 0;
        float topBottom = Float.NaN;
        for (int i = 0; i < count; i++) {
            if (segments.get(i).stage() < 0) {
                topCount++;
                topBottom = Float.isNaN(topBottom) ? bottoms[i] : Math.max(topBottom, bottoms[i]);
            }
        }
        if (!Float.isNaN(topBottom)) {
            int coneH = Math.max(4, Math.round(topCount * unit));
            int base = Math.round(topBottom);
            for (int k = 0; k < coneH; k++) {
                int halfK = Math.max(1, Math.round(half * (k < coneH / 2 ? 1.0F : (coneH - k) / (float) Math.max(1, coneH - coneH / 2))));
                graphics.fill(axis - halfK, base - k - 1, axis + halfK, base - k, 0xFFE9EBED);
            }
        }
        // Les boosters, sur les flancs du premier étage, jusqu'à leur largage.
        if (shape.boosters() > 0 && dropped == 0 && table.boosterAge(framePartial) < 0.0F) {
            int boosterH = Math.round(shape.boosterHeight() * unit);
            int base = Math.round(ground - lift);
            int bw = Math.max(2, half / 2);
            for (int sign = -1; sign <= 1; sign += 2) {
                int bx = sign < 0 ? axis - half - bw - 1 : axis + half + 1;
                graphics.fill(bx, base - boosterH + 2, bx + bw, base, 0xFFC9B48A);
                graphics.fill(bx, base - boosterH, bx + bw, base - boosterH + 2, 0xFFE9EBED);
            }
        }
        // La flamme, tant que les moteurs poussent.
        if (lowest >= 0 && status.thrust() > 0 && !table.enginesOut()) {
            float flicker = 0.85F + 0.15F * Mth.sin((age + framePartial) * 1.7F);
            int length = Math.round(unit * 5.0F * flicker * Mth.clamp(status.thrust() / 100.0F, 0.2F, 1.0F)) + 3;
            for (int k = 0; k < length; k++) {
                float t = k / (float) length;
                int halfK = Math.max(1, Math.round(half * (1.0F - t)));
                int color = t < 0.3F ? 0xFFFFF2B0 : t < 0.65F ? 0xFFFFB347 : 0xC0FF6A2A;
                graphics.fill(axis - halfK, lowest + 2 + k, axis + halfK, lowest + 3 + k, color);
            }
        }
        // Le déluge : des gerbes d'eau autour du tablier.
        if (table.isDelugeOn()) {
            for (int k = 0; k < 14; k++) {
                int dx = (int) ((Math.sin(k * 12.9898 + age * 0.7) * 0.5 + 0.5) * deckHalf * 2) - deckHalf;
                int dy = (int) ((Math.cos(k * 4.1414 + age * 0.9) * 0.5 + 0.5) * 6);
                graphics.fill(axis + dx, ground - dy - 1, axis + dx + 1, ground - dy, 0xC07FD3FF);
            }
        }
        // En vol, l'altitude, en haut à gauche.
        if (flying) {
            graphics.text(font, Ui.fit(font, "ALT " + formatAltitude(status.altitudeKm()), w - 8), x + 4, y + 4, COLOR_OK, false);
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Le chargement des ergols, jauge par jauge, puis les voyants des systèmes du pas de tir. */
    private void extractLoads(GuiGraphicsExtractor graphics, LaunchTableBlockEntity table, int x, int y, int w, int h) {
        int bottom = y + h;
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, Component.translatable("launch.orbitalis.console.loads").getString().toUpperCase(Locale.ROOT), w)), x, y);
        y += 12;
        if (!table.hasRocket()) {
            graphics.text(font, Ui.fit(font, Component.translatable("launch.orbitalis.console.no_rocket_pad").getString(), w), x, y, COLOR_OPTIONAL, false);
            y += 12;
        } else {
            com.orbitalis.phase1.rocket.RocketFuel need = table.requiredFuel();
            int[] loaded = table.fuelSnapshot();
            String[] names = {"RP-1", "LOX", "LH2", "CH4", "He-3"};
            int[] needed = {need.rp1(), need.oxygen(), need.hydrogen(), need.methane(), need.helium3()};
            int[] colors = {0xFFE89A3C, 0xFF3FA9F5, 0xFFA8E6FF, 0xFF7FD9A8, 0xFFE8DC9A};
            // Le méthane et l'hélium-3 sont rangés après l'eau dans le relevé de la table.
            int[] slots = {0, 1, 2, 4, 5};
            boolean any = false;
            for (int k = 0; k < 5 && y + 14 <= bottom; k++) {
                if (needed[k] <= 0) {
                    continue;
                }
                any = true;
                float share = Mth.clamp((slots[k] < loaded.length ? loaded[slots[k]] : 0) / (float) needed[k], 0.0F, 1.0F);
                graphics.text(font, names[k], x, y, COLOR_TEXT, false);
                String percent = Math.round(share * 100.0F) + " %";
                graphics.text(font, percent, x + w - font.width(percent), y, share >= 1.0F ? COLOR_OK : COLOR_WARNING, false);
                Ui.bar(graphics, x, y + 10, w, 3, share, colors[k]);
                y += 17;
            }
            if (!any) {
                for (var piece : Ui.lines(font, Component.translatable("launch.orbitalis.console.no_liquid"), w, 2)) {
                    graphics.text(font, piece, x, y, COLOR_OPTIONAL, false);
                    y += 10;
                }
            }
        }
        y += 3;
        if (y + 22 > bottom) {
            return;
        }
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, Component.translatable("launch.orbitalis.console.systems").getString().toUpperCase(Locale.ROOT), w)), x, y);
        y += 12;
        // Les voyants : vert si c'est en ordre, ambre si c'est à faire, gris s'il n'y a rien à faire.
        String[][] lights = {
                {"launch.orbitalis.console.sys.arms", status.retracted() ? "launch.orbitalis.console.state.retracted" : "launch.orbitalis.console.state.connected",
                        status.retracted() ? "ok" : "warn"},
                {"launch.orbitalis.console.sys.pressure", status.pressure() > 0 ? "launch.orbitalis.console.state.on" : "launch.orbitalis.console.state.off",
                        !status.liquid() ? "dim" : status.pressure() > 0 ? "ok" : "warn"},
                {"launch.orbitalis.console.sys.chill", status.chill() > 0 ? "launch.orbitalis.console.state.on" : "launch.orbitalis.console.state.off",
                        status.chill() < 0 ? "dim" : status.chill() > 0 ? "ok" : "warn"},
                {"launch.orbitalis.console.sys.deluge", !status.hasDeluge() ? "launch.orbitalis.console.state.off"
                        : status.deluge() > 0 ? "launch.orbitalis.console.state.on" : status.water() >= 100 ? "launch.orbitalis.console.state.ready"
                        : "launch.orbitalis.console.state.dry", !status.hasDeluge() ? "dim" : status.deluge() > 0 || status.water() >= 100 ? "ok" : "bad"},
                {"launch.orbitalis.console.sys.weather", status.weatherGo() ? "launch.orbitalis.console.state.go" : "launch.orbitalis.console.state.nogo",
                        status.weatherGo() ? "ok" : "bad"}};
        for (String[] light : lights) {
            if (y + 9 > bottom) {
                break;
            }
            int color = switch (light[2]) {
                case "ok" -> COLOR_OK;
                case "warn" -> COLOR_WARNING;
                case "bad" -> COLOR_BAD;
                default -> COLOR_OPTIONAL;
            };
            graphics.fill(x, y + 2, x + 4, y + 6, color);
            String state = Component.translatable(light[1]).getString();
            graphics.text(font, state, x + w - font.width(state), y, color, false);
            graphics.text(font, Ui.fit(font, Component.translatable(light[0]).getString(), w - font.width(state) - 12), x + 7, y, COLOR_TEXT, false);
            y += 10;
        }
    }

    /** La carte de navigation (voir {@link IsoMap}) : elle remplit le volet ; sa prise de vue est refaite à chaque image, à son format. */
    private void extractMap(GuiGraphicsExtractor graphics, LaunchTableBlockEntity table, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_EDGE);
        graphics.fill(x, y, x + w, y + h, COLOR_OCEAN);
        float unit = h / IsoMap.VIEW_HEIGHT;
        graphics.enableScissor(x, y, x + w, y + h);
        for (IsoMap.Prim prim : IsoMap.frame(table, framePartial, w / unit)) {
            int x0 = x + Math.round(prim.x0() * unit);
            int y0 = y + Math.round(prim.y0() * unit);
            int x1 = Math.max(x0 + 1, x + Math.round(prim.x1() * unit));
            int y1 = Math.max(y0 + 1, y + Math.round(prim.y1() * unit));
            if (prim.plain()) {
                graphics.fillGradient(x0, y0, x1, y1, prim.top(), prim.bottom());
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, prim.texture(), x0, y0, prim.u0() * prim.textureWidth(), prim.v0() * prim.textureHeight(),
                        x1 - x0, y1 - y0, Math.max(1, Math.round((prim.u1() - prim.u0()) * prim.textureWidth())),
                        Math.max(1, Math.round((prim.v1() - prim.v0()) * prim.textureHeight())), prim.textureWidth(), prim.textureHeight(), prim.top());
            }
        }
        graphics.disableScissor();
        // L'orientation de la carte : le coin du monde que l'on voit au fond, comme dans l'atlas.
        var turnTable = table.getBlockPos();
        Component corner = Component.translatable("atlas.orbitalis.corner." + IsoMap.CORNERS[IsoMap.turn(turnTable)]);
        Component viewLabel = Component.translatable("atlas.orbitalis.view", corner);
        Component label = font.width(viewLabel) + 12 <= w / 2 ? viewLabel : corner;
        int labelWidth = font.width(label.getString().toUpperCase(Locale.ROOT)) + 12;
        if (labelWidth <= w - 6 && h >= TAB_HEIGHT + 6) {
            button(graphics, label, x + 3, y + 3, labelWidth, TAB_HEIGHT, true, false, COLOR_ACCENT,
                    () -> IsoMap.turn(turnTable, IsoMap.turn(turnTable) + 1));
        }
        // Le site, en bas à gauche de la carte.
        String site = Component.translatable("launch.orbitalis.console.site", "X " + turnTable.getX(), "Z " + turnTable.getZ()).getString();
        int siteWidth = font.width(site) + 6;
        Ui.round(graphics, x + 3, y + h - 14, siteWidth, 11, 0xD004060A);
        graphics.text(font, site, x + 6, y + h - 12, COLOR_TEXT, false);
    }

    /**
     * Télémétrie, comme dans les retransmissions de SpaceX : deux cadrans ronds, la vitesse et l'altitude ; à droite,
     * l'étage allumé, la masse et le plein, puis la frise du vol et ses jalons (décollage, Max-Q, séparations, espace,
     * orbite ou apogée), où avance le curseur du temps.
     */
    private void extractTelemetry(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        // Un bandeau sombre, presque opaque, comme l'incrustation d'une retransmission.
        graphics.fillGradient(x, y, x + w, y + h, 0xC8040608, 0xF0040608);
        graphics.fill(x, y, x + w, y + 1, COLOR_EDGE);
        boolean flying = phase() == LaunchTableBlockEntity.Phase.FLIGHT;
        int radius = Math.max(10, (h - 6) / 2);
        int cy = y + 3 + radius;
        int c1 = x + 6 + radius;
        int c2 = c1 + 2 * radius + 6;
        dial(graphics, c1, cy, radius, flying ? status.speed() / 8000.0F : 0.0F, flying ? formatSpeed(status.speed()) : "---",
                "launch.orbitalis.console.speed");
        dial(graphics, c2, cy, radius, flying ? status.altitudeKm() / 400.0F : 0.0F, flying ? formatAltitude(status.altitudeKm()) : "---",
                "launch.orbitalis.console.altitude");

        int lx = c2 + radius + 10;
        int lw = x + w - 8 - lx;
        if (lw < 50) {
            return;
        }
        LaunchTableBlockEntity table = table();
        // En tête : l'étage allumé, la masse, le plein.
        List<String> facts = new ArrayList<>();
        if (status.hasRocket()) {
            int lit = 1;
            if (table != null) {
                for (int k = 0; k < 16; k++) {
                    if (table.stageAge(k, framePartial) >= 0.0F) {
                        lit = k + 2;
                    }
                }
            }
            facts.add(Component.translatable("launch.orbitalis.console.stage_now", lit).getString());
            facts.add(formatMass(status.rocketMass()));
            if (status.needsFuel()) {
                facts.add(Component.translatable("launch.orbitalis.console.fuel_short").getString() + " " + status.fuelPercent() + " %");
            }
        }
        graphics.text(font, Ui.fit(font, String.join("  ·  ", facts), lw), lx, y + 4, COLOR_TEXT, false);

        // La frise du vol.
        int ty = y + h - 15;
        graphics.fill(lx, ty, lx + lw, ty + 2, COLOR_EDGE);
        List<Object[]> marks = new ArrayList<>();
        marks.add(new Object[] {0, "launch.orbitalis.console.ms.liftoff", 0});
        if (table != null && table.hasRocket()) {
            RocketStats stats = table.rocketStats();
            if (stats.reach().ordinal() >= RocketStats.Reach.LOW_ORBIT.ordinal()) {
                marks.add(new Object[] {LaunchTableBlockEntity.maxQTick(), "launch.orbitalis.console.ms.maxq", 0});
            }
            List<Integer> staging = table.stagingTicks();
            for (int k = 0; k < staging.size(); k++) {
                marks.add(new Object[] {staging.get(k), "launch.orbitalis.console.ms.sep", k + 1});
            }
            for (var event : com.orbitalis.phase1.spaceport.FlightProfile.flight(stats).events()) {
                String key = switch (event.type()) {
                    case SPACE -> "launch.orbitalis.console.ms.space";
                    case ORBIT -> "launch.orbitalis.console.ms.orbit";
                    case APOGEE -> "launch.orbitalis.console.ms.apogee";
                    default -> null;
                };
                if (key != null) {
                    int tick = (int) (com.orbitalis.phase1.spaceport.FlightProfile.gameProgress(stats, event.time()) * table.flightTicks());
                    marks.add(new Object[] {tick, key, 0});
                }
            }
        }
        marks.sort(java.util.Comparator.comparingInt(mark -> (Integer) mark[0]));
        int total = table != null ? table.flightTicks() : LaunchTableBlockEntity.DEFAULT_FLIGHT_TICKS;
        float now = flying && table != null ? Math.min(total, table.phaseTicks() + framePartial) : 0.0F;
        int progress = lx + Math.round(lw * now / total);
        graphics.fill(lx, ty, progress, ty + 2, COLOR_OK);
        org.joml.Matrix3x2fStack pose = graphics.pose();
        // Où finit le dernier libellé écrit, au-dessous (0) et au-dessus (1) de la frise.
        float[] rowEnd = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int k = 0; k < marks.size(); k++) {
            int tick = (Integer) marks.get(k)[0];
            int mx = lx + Math.round(lw * Math.min(1.0F, tick / (float) total));
            boolean passed = now >= tick && flying;
            graphics.fill(mx - 1, ty - 2, mx + 2, ty + 4, passed ? COLOR_OK : COLOR_MUTED);
            String label = Component.translatable((String) marks.get(k)[1], marks.get(k)[2]).getString().toUpperCase(Locale.ROOT);
            if (Ui.inside(lastMouseX, lastMouseY, mx - 3, ty - 6, 7, 12)) {
                graphics.setTooltipForNextFrame(font, Component.literal(label), lastMouseX, lastMouseY);
            }
            // Les libellés alternent au-dessous et au-dessus de la frise ; celui qui empiéterait sur son voisin de la
            // même rangée n'est pas écrit (le jalon reste, et se lit au survol).
            int row = k % 2;
            float width = font.width(label) * 0.75F;
            float start = Mth.clamp(mx - width / 2.0F, lx, lx + lw - width);
            if (start < rowEnd[row] + 3) {
                continue;
            }
            rowEnd[row] = start + width;
            pose.pushMatrix();
            pose.translate(start, row == 0 ? ty + 6 : ty - 9);
            pose.scale(0.75F, 0.75F);
            graphics.text(font, label, 0, 0, passed ? COLOR_TEXT : COLOR_OPTIONAL, false);
            pose.popMatrix();
        }
        // Le curseur du temps.
        graphics.fill(progress - 1, ty - 4, progress + 1, ty + 6, COLOR_ACCENT);
    }

    /** Un cadran rond : un arc de trois quarts de tour, rempli à proportion, la valeur au centre, son nom dessous. */
    private void dial(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float share, String value, String labelKey) {
        float fill = Mth.clamp(share, 0.0F, 1.0F);
        for (int deg = 0; deg <= 270; deg += 5) {
            double angle = Math.toRadians(135 + deg);
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, deg <= fill * 270 && fill > 0 ? COLOR_ACCENT : COLOR_EDGE);
        }
        int cut = value.lastIndexOf(' ');
        String number = cut > 0 ? value.substring(0, cut) : value;
        String unit = cut > 0 ? value.substring(cut + 1) : "";
        org.joml.Matrix3x2fStack pose = graphics.pose();
        float scale = Math.min(1.0F, (2 * radius - 4) / (float) Math.max(1, font.width(number)));
        pose.pushMatrix();
        pose.translate(cx, cy - 5);
        pose.scale(scale, scale);
        graphics.text(font, number, -font.width(number) / 2, 0, value.equals("---") ? COLOR_MUTED : COLOR_TEXT, false);
        pose.popMatrix();
        // Sous la valeur, son unité ; dans l'ouverture du cadran, son nom.
        String label = Component.translatable(labelKey).getString().toUpperCase(Locale.ROOT);
        String below = unit.isEmpty() ? label : unit;
        pose.pushMatrix();
        pose.translate(cx, cy + 3);
        pose.scale(0.6F, 0.6F);
        graphics.text(font, below, -font.width(below) / 2, 0, COLOR_MUTED, false);
        pose.popMatrix();
        if (!unit.isEmpty()) {
            pose.pushMatrix();
            pose.translate(cx, cy + radius - 3);
            pose.scale(0.6F, 0.6F);
            graphics.text(font, label, -font.width(label) / 2, 0, COLOR_MUTED, false);
            pose.popMatrix();
        }
    }

    /** Bas de l'écran : le journal des événements à gauche, le « Go / No-Go » et l'arrêt à droite. */
    private void extractBottomBar(GuiGraphicsExtractor graphics) {
        int barTop = top + panelHeight - BOTTOM_BAR;
        int buttonY = barTop + (BOTTOM_BAR - 6 - BUTTON_HEIGHT) / 2;
        boolean countdown = phase() == LaunchTableBlockEntity.Phase.COUNTDOWN;
        Component stop = Component.translatable("launch.orbitalis.console.abort");
        int stopWidth = Math.max(90, font.width(stop.getString().toUpperCase(Locale.ROOT)) + 16);
        int stopX = left + panelWidth - 6 - stopWidth;
        Component go = Component.translatable("launch.orbitalis.console.gonogo");
        int goWidth = Math.max(110, font.width(go.getString().toUpperCase(Locale.ROOT)) + 26);
        int goX = stopX - 6 - goWidth;
        int logRight = goX - 6;
        Ui.card(graphics, left + 6, barTop, logRight - left - 6, BOTTOM_BAR - 6, false);
        int lineY = barTop + 5;
        if (log.isEmpty()) {
            graphics.text(font, Ui.fit(font, lastResultText().getString(), logRight - left - 16), left + 11, lineY, COLOR_MUTED, false);
        }
        int index = 0;
        for (Component entry : log) {
            // La ligne la plus récente est mise en valeur.
            boolean latest = ++index == log.size();
            graphics.text(font, Ui.fit(font, entry.getString(), logRight - left - 16), left + 11, lineY, latest ? COLOR_TEXT : COLOR_MUTED, false);
            lineY += LINE_HEIGHT - 1;
        }
        // Le « Go / No-Go » : un voyant dit d'un coup d'œil si tout est prêt.
        button(graphics, go, goX, buttonY, goWidth, BUTTON_HEIGHT, status.linked(), !countdown, COLOR_ACCENT, this::openGoWindow);
        if (status.linked()) {
            Ui.dot(graphics, goX + 6, buttonY + 8, goForLaunch() ? COLOR_OK : COLOR_BAD);
        }
        // L'arrêt : il coupe tout tant que la fusée est retenue.
        button(graphics, stop, stopX, buttonY, stopWidth, BUTTON_HEIGHT, countdown, countdown, COLOR_BAD, () -> send(LaunchCommandPayload.ABORT));
    }

    /** Texte affiché quand le journal est vide : l'issue du dernier lancement, ou l'état d'attente. */
    private Component lastResultText() {
        LaunchTableBlockEntity.Result[] results = LaunchTableBlockEntity.Result.values();
        LaunchTableBlockEntity.Result result = results[Math.floorMod(status.result(), results.length)];
        if (result == LaunchTableBlockEntity.Result.SUCCESS) {
            return Component.translatable("launch.orbitalis.console.result.success", status.points());
        }
        if (result != LaunchTableBlockEntity.Result.NONE) {
            return Component.translatable("launch.orbitalis.console.result." + result.name().toLowerCase(Locale.ROOT));
        }
        return Component.translatable(goForLaunch() ? "launch.orbitalis.console.go" : "launch.orbitalis.console.no_go");
    }

    // ------------------------------------------------------------------
    // La fenêtre « Go / No-Go »
    // ------------------------------------------------------------------

    /** La place de la fenêtre : x, y, largeur, hauteur. */
    private int[] goWindowBox() {
        int w = Math.min(panelWidth - 40, 440);
        int h = Math.min(panelHeight - 30, 250);
        return new int[] {left + (panelWidth - w) / 2, top + (panelHeight - h) / 2, w, h};
    }

    /**
     * La fenêtre « Go / No-Go » : à gauche, les postes du centre de contrôle, puis l'explication du poste survolé ou
     * choisi (à défaut, du premier poste au rouge) et les chiffres du complexe ; à droite, le tour de table, que l'on
     * lance d'ici, et les essais du pas de tir. Elle apparaît en fondu, en glissant un peu vers le haut.
     */
    private void extractGoWindow(GuiGraphicsExtractor graphics) {
        float t = Mth.clamp((net.minecraft.util.Util.getMillis() - goOpenedAt) / 180.0F, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        // L'écran s'assombrit derrière.
        graphics.fill(left, top, left + panelWidth, top + panelHeight, Ui.alpha(0xFF04060A, 0.69F * ease));
        int[] box = goWindowBox();
        int x = box[0];
        int y = box[1] + Math.round((1.0F - ease) * 12.0F);
        int w = box[2];
        int h = box[3];
        Ui.card(graphics, x, y, w, h, true);
        Component heading = Component.translatable("launch.orbitalis.console.gonogo");
        Ui.heading(graphics, font, heading, x + 8, y + 7);
        boolean go = goForLaunch();
        // Le verdict tient en un mot : les voyants à côté du titre disent le reste.
        String verdictText = Component.translatable(go ? "launch.orbitalis.console.station_go" : "launch.orbitalis.console.station_no_go")
                .getString().toUpperCase(Locale.ROOT);
        Ui.pillRight(graphics, font, verdictText, x + w - 8, y + 5, go ? COLOR_OK : COLOR_BAD);
        int verdictLeft = x + w - 8 - Ui.pillWidth(font, verdictText);
        // En tête, un voyant par poste concerné : on voit d'un coup d'œil combien sont au vert.
        List<Station> stations = stations();
        int relevant = 0;
        int green = 0;
        for (Station station : stations.subList(0, stations.size() - 1)) {
            if (station.relevant()) {
                relevant++;
                green += station.go() ? 1 : 0;
            }
        }
        int lightsX = x + 16 + font.width(heading.getString().toUpperCase(Locale.ROOT));
        int k = 0;
        for (Station station : stations.subList(0, stations.size() - 1)) {
            if (!station.relevant()) {
                continue;
            }
            graphics.fill(lightsX + k * 8, y + 8, lightsX + k * 8 + 6, y + 13, station.go() ? COLOR_OK : COLOR_BAD);
            k++;
        }
        String count = Component.translatable("launch.orbitalis.console.go_window.count", green, relevant).getString();
        if (lightsX + k * 8 + 4 + font.width(count) < verdictLeft - 8) {
            graphics.text(font, count, lightsX + k * 8 + 4, y + 7, green == relevant ? COLOR_OK : COLOR_MUTED, false);
        }
        Ui.hline(graphics, x + 1, y + 20, w - 2);
        int column = (w - 24) / 2;

        // À gauche : les postes, un voyant chacun ; on en survole ou on en choisit un pour savoir ce qu'il lui faut.
        int lineY = y + 26;
        Ui.heading(graphics, font, Component.translatable("launch.orbitalis.console.go_window.stations"), x + 8, lineY);
        lineY += 13;
        int shown = -1;
        int firstBad = -1;
        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            int light = !station.relevant() ? COLOR_OPTIONAL : station.go() ? COLOR_OK : COLOR_BAD;
            boolean over = Ui.inside(lastMouseX, lastMouseY, x + 4, lineY - 2, column + 6, LINE_HEIGHT);
            if (over) {
                shown = i;
            }
            if (firstBad < 0 && station.relevant() && !station.go()) {
                firstBad = i;
            }
            if (i == pickedStation) {
                Ui.round(graphics, x + 4, lineY - 2, column + 6, LINE_HEIGHT, Ui.alpha(light, 0.16F));
                Ui.frame(graphics, x + 4, lineY - 2, column + 6, LINE_HEIGHT, Ui.alpha(light, 0.6F));
            } else if (over) {
                Ui.round(graphics, x + 4, lineY - 2, column + 6, LINE_HEIGHT, Ui.SURFACE);
            }
            // Un poste au rouge palpite doucement.
            int dot = station.relevant() && !station.go() ? Ui.alpha(light, 0.6F + 0.4F * (float) Math.sin(age * 0.35 + i)) : light;
            Ui.dot(graphics, x + 8, lineY + 1, dot);
            Component verdict = Component.translatable(!station.relevant() ? "launch.orbitalis.console.na"
                    : station.go() ? "launch.orbitalis.console.station_go" : "launch.orbitalis.console.station_no_go");
            int verdictX = x + 8 + column - font.width(verdict);
            graphics.text(font, verdict, verdictX, lineY, light, false);
            String name = Ui.fit(font, Component.translatable("launch.orbitalis.station." + station.key()).getString(), verdictX - (x + 18) - 3);
            graphics.text(font, name, x + 18, lineY, station.relevant() ? COLOR_TEXT : COLOR_MUTED, false);
            int index = i;
            actions.add(new Action(x + 4, lineY - 2, column + 6, LINE_HEIGHT, () -> {
                Ui.click();
                pickedStation = pickedStation == index ? -1 : index;
            }));
            lineY += LINE_HEIGHT;
        }
        // L'explication : le poste survolé, sinon celui qu'on a choisi, sinon le premier au rouge, sinon le directeur de vol.
        if (shown < 0) {
            shown = pickedStation >= 0 ? pickedStation : firstBad >= 0 ? firstBad : stations.size() - 1;
        }
        Station about = stations.get(shown);
        int aboutColor = !about.relevant() ? COLOR_OPTIONAL : about.go() ? COLOR_OK : COLOR_BAD;
        List<String> lines = Ui.lines(font, Component.translatable(about.relevant() ? "launch.orbitalis.station." + about.key() + ".desc"
                : "launch.orbitalis.station.na_desc"), column - 10, Math.max(1, Math.min(3, (y + h - 34 - (lineY + 4) - 8) / 10)));
        lineY += 4;
        int boxH = 8 + lines.size() * 10;
        // Dans une petite fenêtre, l'explication passe avant les chiffres du complexe ; si elle ne tient pas, rien.
        if (lineY + boxH <= y + h - 30) {
            Ui.round(graphics, x + 6, lineY, column + 2, boxH, Ui.alpha(aboutColor, 0.10F));
            graphics.fill(x + 6, lineY + 1, x + 8, lineY + boxH - 1, aboutColor);
            int textY = lineY + 4;
            for (String line : lines) {
                graphics.text(font, line, x + 12, textY, COLOR_TEXT, false);
                textY += 10;
            }
        }
        lineY += boxH + 5;
        int padSize = 2 * status.radius() + 1;
        Component[] details = {
                forecastText(),
                Component.translatable(status.distance() > LaunchConsoleBlock.RANGE ? "spaceport.orbitalis.console.relayed"
                        : "spaceport.orbitalis.console.linked", status.distance()),
                Component.translatable(status.capacityRadius() < status.radius() ? "spaceport.orbitalis.console.pad_limited"
                        : "spaceport.orbitalis.console.pad", padSize, padSize),
                status.towerHeight() > 0 ? Component.translatable("spaceport.orbitalis.console.tower_arms", status.towerHeight(), status.arms())
                        : Component.translatable("spaceport.orbitalis.console.no_tower"),
        };
        for (Component detail : details) {
            if (lineY + 9 > y + h - 30) {
                break;
            }
            graphics.text(font, Ui.fit(font, detail.getString(), column), x + 8, lineY, COLOR_MUTED, false);
            lineY += LINE_HEIGHT - 1;
        }

        // À droite : le tour de table, puis les essais du pas de tir.
        int rx = x + 16 + column;
        graphics.fill(rx - 8, y + 24, rx - 7, y + h - 30, Ui.LINE);
        int ry = y + 26;
        Ui.heading(graphics, font, Component.translatable("launch.orbitalis.console.go_window.poll"), rx, ry);
        ry += 13;
        boolean idle = phase() == LaunchTableBlockEntity.Phase.IDLE;
        SequencePanel.Line poll = SequencePanel.pollLine(status, !idle);
        boolean canPoll = idle && status.hasRocket() && status.busyStep() < 0 && !(status.poll() >= 0 && status.poll() < 100);
        int pollColor = poll.state() == SequencePanel.State.DONE ? COLOR_OK : poll.state() == SequencePanel.State.ACTIVE ? COLOR_WARNING
                : status.poll() == 101 ? COLOR_BAD : COLOR_MUTED;
        // Le bouton éteint dit pourquoi : pas de fusée, une opération en cours, ou un tour déjà lancé.
        Component pollText = canPoll || poll.state() != SequencePanel.State.TODO ? poll.value()
                : Component.translatable(!idle ? "launch.orbitalis.console.go_window.poll_flying"
                        : !status.hasRocket() ? "launch.orbitalis.console.go_window.poll_no_rocket" : "launch.orbitalis.console.go_window.poll_busy");
        graphics.text(font, Ui.fit(font, pollText.getString(), column - 90), rx, ry + 3, pollColor, false);
        button(graphics, Component.translatable("launch.orbitalis.console.go_window.run_poll"), rx + column - 84, ry, 84, 14, canPoll, true, COLOR_ACCENT,
                () -> sequence.send(LaunchStep.POLL));
        ry += 18;
        if (poll.gauge() >= 0.0F) {
            Ui.bar(graphics, rx, ry - 2, column, 1, poll.gauge(), COLOR_WARNING);
        }
        ry += SequencePanel.pollPills(graphics, font, status, rx, ry, column) + 8;

        Ui.heading(graphics, font, Component.translatable("launch.orbitalis.console.go_window.tests"), rx, ry);
        ry += 13;
        boolean canTest = idle && status.busyStep() < 0;
        for (SequencePanel.Line test : SequencePanel.testLines(status)) {
            if (ry + 14 > y + h - 30) {
                break;
            }
            boolean running = status.busyStep() == test.step().ordinal();
            // Sous le titre « Essais du pas de tir », le nom court suffit : le déluge, les bras, l'alarme.
            String name = Component.translatable("launch.orbitalis.console.test." + test.step().name().toLowerCase(Locale.ROOT)).getString();
            String value = running ? Component.translatable("sequence.orbitalis.value.working").getString() : test.value().getString();
            int room = column - 66 - 8;
            int nameWidth = Math.min(font.width(name), room / 2);
            graphics.text(font, Ui.fit(font, name, nameWidth), rx, ry + 3, COLOR_TEXT, false);
            String valueShown = Ui.fit(font, value, room - nameWidth - 6);
            graphics.text(font, valueShown, rx + room - font.width(valueShown), ry + 3, running ? COLOR_WARNING : COLOR_MUTED, false);
            if (running) {
                Ui.bar(graphics, rx, ry + 13, room, 1, status.busyPercent() / 100.0F, COLOR_WARNING);
            }
            LaunchStep step = test.step();
            button(graphics, Component.translatable("sequence.orbitalis.action.test"), rx + column - 66, ry, 66, 14, canTest, false, COLOR_MUTED,
                    () -> sequence.send(step));
            ry += 17;
        }

        // En pied : fermer (ou Échap).
        Ui.hline(graphics, x + 1, y + h - 26, w - 2);
        Component close = Component.translatable("sequence.orbitalis.close");
        int closeWidth = Math.max(80, font.width(close.getString().toUpperCase(Locale.ROOT)) + 16);
        graphics.text(font, Ui.fit(font, Component.translatable("launch.orbitalis.console.go_window.hint").getString(), w - closeWidth - 24), x + 8, y + h - 17,
                COLOR_OPTIONAL, false);
        button(graphics, close, x + w - 8 - closeWidth, y + h - 21, closeWidth, 16, true, false, COLOR_MUTED, () -> goWindow = false);
    }

    /** La fenêtre météo, vue par les satellites de l'agence ; sans satellite météo, on n'en sait rien. */
    private Component forecastText() {
        int minutes = (status.forecastTicks() + 1199) / 1200;
        return switch (status.forecast()) {
            case 0 -> Component.translatable("launch.orbitalis.console.forecast.clear", minutes);
            case 1 -> Component.translatable("launch.orbitalis.console.forecast.rain", minutes);
            case 2 -> Component.translatable("launch.orbitalis.console.forecast.thunder", minutes);
            default -> Component.translatable("launch.orbitalis.console.forecast.none");
        };
    }

    // ------------------------------------------------------------------
    // Mise en forme
    // ------------------------------------------------------------------

    /** Horloge de mission : « T-00:27 » pendant le compte à rebours, « T+00:12 » pendant le vol. */
    private static String missionClock(LaunchStatusPayload status) {
        return switch (phaseOf(status)) {
            case COUNTDOWN -> "T-" + clock(status.seconds());
            case FLIGHT -> "T+" + clock(status.seconds());
            default -> "T-00:00";
        };
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static boolean metric() {
        return ClientConfig.METRIC_UNITS.get();
    }

    private static String grouped(double value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }

    private static String formatMass(double kilograms) {
        if (!metric()) {
            return grouped(kilograms * 2.20462) + " lb";
        }
        return kilograms >= 10_000 ? String.format(Locale.ROOT, "%.1f t", kilograms / 1000.0) : grouped(kilograms) + " kg";
    }

    private static String formatAltitude(double kilometers) {
        return metric() ? grouped(kilometers) + " km" : grouped(kilometers * 0.621371) + " mi";
    }

    private static String formatSpeed(double metersPerSecond) {
        return metric() ? grouped(metersPerSecond) + " m/s" : grouped(metersPerSecond * 3.28084) + " ft/s";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
