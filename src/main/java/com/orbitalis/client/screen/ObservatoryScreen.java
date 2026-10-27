package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.orbitalis.client.render.SpacePhotoPainter;
import com.orbitalis.client.render.SpacePhotos;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.core.network.ObservatoryPayload;
import com.orbitalis.core.network.SatelliteCommandPayload;
import com.orbitalis.phase1.satellite.CelestialBody;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * L'observatoire, ouvert depuis une station de poursuite : ce que les satellites de l'agence voient du ciel.
 * <ul>
 * <li><b>Observations</b> : à gauche, les astres que l'agence connaît ; à droite, celui qu'on a choisi. On y pointe
 * les instruments, on suit l'observation, puis on reçoit son image au sol.</li>
 * <li><b>Catalogue</b> : l'ouvrage des découvertes, une double page par astre dont l'image est reçue : la photographie
 * à gauche, la fiche à droite, et pour une planète ou une lune, comment s'y rendre et ce qu'il faut emporter.</li>
 * </ul>
 */
public class ObservatoryScreen extends Screen {
    private static final int HEADER = 30;
    private static final int TAB_HEIGHT = 13;
    private static final int ROW = 16;
    private static final int LIST_WIDTH = 176;
    private static final int BUTTON_HEIGHT = 16;
    /** Intervalle entre deux demandes d'état, en ticks. */
    private static final int REFRESH_TICKS = 20;

    private final Screen parent;
    private final BlockPos station;
    private ObservatoryPayload status;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int age;
    /** L'onglet : les observations (0) ou le catalogue (1). */
    private int tab;
    /** L'astre choisi dans la liste, et la page ouverte du catalogue. */
    private CelestialBody selected;
    private int page;
    private int scroll;
    /** Les zones cliquables de l'image en cours. */
    private record Action(int x, int y, int w, int h, Runnable run) {
    }

    private final List<Action> actions = new ArrayList<>();
    private int mouseX;
    private int mouseY;

    public ObservatoryScreen(Screen parent, BlockPos station) {
        super(Component.translatable("observatory.orbitalis.title"));
        this.parent = parent;
        this.station = station.immutable();
    }

    /** Le serveur envoie l'état de l'observatoire : il va à l'écran ouvert depuis cette station. */
    public static void accept(ObservatoryPayload payload) {
        if (Minecraft.getInstance().gui.screen() instanceof ObservatoryScreen screen && screen.station.equals(payload.station())) {
            screen.status = payload;
            if (screen.selected == null && !payload.entries().isEmpty()) {
                screen.selected = CelestialBody.values()[payload.entries().get(0).body()];
            }
        }
    }

    /** Choisit un astre dans la liste des observations (sert aux captures de vérification). */
    public void select(CelestialBody body) {
        tab = 0;
        selected = body;
    }

    /** Ouvre directement le catalogue, à la page de cet astre (sert aux captures de vérification). */
    public void showCatalogue(CelestialBody body) {
        tab = 1;
        List<CelestialBody> pages = catalogue();
        page = Math.max(0, pages.indexOf(body));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 540);
        panelHeight = Math.min(height - 16, 310);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        send(SatelliteCommandPayload.OBSERVATORY);
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % REFRESH_TICKS == 0) {
            send(SatelliteCommandPayload.OBSERVATORY);
        }
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new SatelliteCommandPayload(station, action));
    }

    private ObservatoryPayload.Entry entry(CelestialBody body) {
        if (status != null && body != null) {
            for (ObservatoryPayload.Entry entry : status.entries()) {
                if (entry.body() == body.ordinal()) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** Les astres au catalogue, dans l'ordre du ciel. */
    private List<CelestialBody> catalogue() {
        List<CelestialBody> pages = new ArrayList<>();
        if (status != null) {
            for (ObservatoryPayload.Entry entry : status.entries()) {
                if (entry.state() == ObservatoryPayload.IMAGED) {
                    pages.add(CelestialBody.values()[entry.body()]);
                }
            }
        }
        return pages;
    }

    // ------------------------------------------------------------------
    // Clics
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Action action : List.copyOf(actions)) {
            if (Ui.inside(event.x(), event.y(), action.x(), action.y(), action.w(), action.h())) {
                Ui.click();
                action.run().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, (status == null ? 0 : status.entries().size()) - listRows()));
            return true;
        }
        int pages = catalogue().size();
        if (pages > 0) {
            page = Mth.clamp(page - (int) Math.signum(scrollY), 0, pages - 1);
        }
        return true;
    }

    private void button(GuiGraphicsExtractor graphics, Component label, int x, int y, int w, int h, boolean enabled, boolean primary, int color,
            Runnable run) {
        boolean hovered = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        Ui.button(graphics, font, label, x, y, w, h, hovered, enabled, primary, color);
        if (enabled) {
            actions.add(new Action(x, y, w, h, run));
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        actions.clear();
        // En-tête : le titre, puis les instruments et la liaison.
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 11, Ui.TEXT, false);
        int right = left + panelWidth - 12;
        if (status != null) {
            String link = Component.translatable(status.link() ? "observatory.orbitalis.link_ok" : "observatory.orbitalis.link_lost").getString();
            Ui.pillRight(graphics, font, link, right, top + 10, status.link() ? Ui.OK : Ui.BAD);
            right -= Ui.pillWidth(font, link) + 5;
            String observers = Component.translatable("observatory.orbitalis.observers", status.observers()).getString();
            Ui.pillRight(graphics, font, observers, right, top + 10, status.observers() > 0 ? Ui.OK : Ui.DIM);
            right -= Ui.pillWidth(font, observers) + 5;
            String telescopes = Component.translatable("observatory.orbitalis.telescopes", status.telescopes()).getString();
            Ui.pillRight(graphics, font, telescopes, right, top + 10, status.telescopes() > 0 ? Ui.OK : Ui.DIM);
        }
        Ui.hline(graphics, left + 1, top + HEADER, panelWidth - 2);

        // Les onglets, et le retour.
        int tabX = left + 10;
        int tabY = top + HEADER + 6;
        String[] tabs = {"observatory.orbitalis.tab.observe", "observatory.orbitalis.tab.catalogue"};
        for (int k = 0; k < tabs.length; k++) {
            Component label = k == 1 ? Component.translatable(tabs[k], catalogue().size()) : Component.translatable(tabs[k]);
            int w = font.width(label.getString().toUpperCase(Locale.ROOT)) + 12;
            int which = k;
            button(graphics, label, tabX, tabY, w, TAB_HEIGHT, true, tab == k, Ui.ACCENT, () -> tab = which);
            tabX += w + 4;
        }
        Component back = Component.translatable("observatory.orbitalis.back");
        int backWidth = font.width(back.getString().toUpperCase(Locale.ROOT)) + 14;
        button(graphics, back, left + panelWidth - 10 - backWidth, tabY, backWidth, TAB_HEIGHT, true, false, Ui.MUTED, this::onClose);

        int contentTop = tabY + TAB_HEIGHT + 8;
        int contentHeight = top + panelHeight - 10 - contentTop;
        if (status == null) {
            Component waiting = Component.translatable("observatory.orbitalis.waiting");
            graphics.text(font, waiting, left + (panelWidth - font.width(waiting)) / 2, contentTop + contentHeight / 2, Ui.MUTED, false);
            return;
        }
        if (tab == 0) {
            extractList(graphics, left + 10, contentTop, LIST_WIDTH, contentHeight);
            extractDetail(graphics, left + 20 + LIST_WIDTH, contentTop, panelWidth - 30 - LIST_WIDTH, contentHeight);
        } else {
            extractCatalogue(graphics, left + 10, contentTop, panelWidth - 20, contentHeight);
        }
    }

    private int listRows() {
        return Math.max(1, (panelHeight - HEADER - TAB_HEIGHT - 30) / ROW);
    }

    private static int stateColor(int state) {
        return switch (state) {
            case ObservatoryPayload.OBSERVING -> Ui.WARN;
            case ObservatoryPayload.DISCOVERED -> Ui.ACCENT;
            case ObservatoryPayload.IMAGED -> Ui.OK;
            default -> Ui.DIM;
        };
    }

    private static String stateText(ObservatoryPayload.Entry entry) {
        // Hors de sa fenêtre, un astre qu'on n'a pas encore découvert dit quand il redeviendra visible.
        if (entry.state() < ObservatoryPayload.DISCOVERED && !entry.visible()) {
            return Component.translatable("observatory.orbitalis.window.short", span(entry.change())).getString();
        }
        return switch (entry.state()) {
            case ObservatoryPayload.OBSERVING -> entry.permille() / 10 + " %";
            case ObservatoryPayload.DISCOVERED -> Component.translatable("observatory.orbitalis.state.discovered").getString();
            case ObservatoryPayload.IMAGED -> Component.translatable("observatory.orbitalis.state.imaged").getString();
            default -> entry.permille() > 0 ? entry.permille() / 10 + " %" : Component.translatable("observatory.orbitalis.window.now").getString();
        };
    }

    /** La couleur de l'état d'un astre dans la liste : éteinte tant qu'il est hors de sa fenêtre, verte s'il est à saisir. */
    private static int stateTextColor(ObservatoryPayload.Entry entry) {
        if (entry.state() < ObservatoryPayload.DISCOVERED && !entry.visible()) {
            return Ui.DIM;
        }
        return entry.state() == ObservatoryPayload.LISTED && entry.permille() == 0 ? Ui.OK : stateColor(entry.state());
    }

    /** Un délai du jeu, en jours et heures (un jour du jeu compte vingt-quatre heures de mille ticks). */
    private static String span(int ticks) {
        int days = ticks / com.orbitalis.phase1.satellite.Observatory.DAY;
        int hours = Math.max(1, (ticks % com.orbitalis.phase1.satellite.Observatory.DAY + 999) / 1000);
        if (hours == 24) {
            days++;
            hours = 0;
        }
        return days == 0 ? Component.translatable("observatory.orbitalis.hours_only", hours).getString()
                : hours == 0 ? Component.translatable("observatory.orbitalis.days_only", days).getString()
                        : Component.translatable("observatory.orbitalis.days_hours", days, hours).getString();
    }

    /** La liste des astres connus : une pastille de l'état, le nom, et où en est l'observation. */
    private void extractList(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Ui.card(graphics, x, y, w, h, false);
        List<ObservatoryPayload.Entry> entries = status.entries();
        int rows = Math.min(entries.size() - scroll, h / ROW);
        for (int k = 0; k < rows; k++) {
            ObservatoryPayload.Entry entry = entries.get(k + scroll);
            CelestialBody body = CelestialBody.values()[entry.body()];
            int rowY = y + 2 + k * ROW;
            boolean chosen = body == selected;
            boolean hovered = Ui.inside(mouseX, mouseY, x + 2, rowY, w - 4, ROW - 1);
            if (chosen) {
                Ui.round(graphics, x + 2, rowY, w - 4, ROW - 1, Ui.SURFACE_HI);
                graphics.fill(x + 2, rowY + 2, x + 4, rowY + ROW - 3, Ui.ACCENT);
            } else if (hovered) {
                Ui.round(graphics, x + 2, rowY, w - 4, ROW - 1, Ui.SURFACE);
            }
            Ui.dot(graphics, x + 9, rowY + 5, stateColor(entry.state()));
            String state = stateText(entry);
            int stateX = x + w - 7 - font.width(state);
            graphics.text(font, state, stateX, rowY + 4, stateTextColor(entry), false);
            graphics.text(font, Ui.fit(font, Component.translatable(body.nameKey()).getString(), stateX - (x + 19) - 4), x + 19, rowY + 4,
                    entry.state() == ObservatoryPayload.LISTED && entry.power() == 0 ? Ui.MUTED : Ui.TEXT, false);
            if (entry.state() == ObservatoryPayload.OBSERVING) {
                Ui.bar(graphics, x + 19, rowY + ROW - 3, w - 26, 1, entry.permille() / 1000.0F, Ui.WARN);
            }
            actions.add(new Action(x + 2, rowY, w - 4, ROW - 1, () -> selected = body));
        }
        // La suite, révélée par les découvertes.
        if (rows < h / ROW - 1) {
            graphics.text(font, Ui.fit(font, Component.translatable("observatory.orbitalis.more").getString(), w - 16), x + 8, y + 6 + rows * ROW,
                    Ui.DIM, false);
        }
    }

    /** L'astre choisi : son image (ou ce qu'on en voit), où en est son observation, et ce qu'on peut faire. */
    private void extractDetail(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Ui.card(graphics, x, y, w, h, false);
        ObservatoryPayload.Entry entry = entry(selected);
        if (entry == null) {
            return;
        }
        CelestialBody body = selected;
        Ui.heading(graphics, font, Component.translatable(body.nameKey()), x + 8, y + 7);
        String kind = Component.translatable(body.kind().nameKey()).getString();
        Ui.right(graphics, font, kind, x + w - 8, y + 7, Ui.MUTED);
        // L'image : celle de l'astre une fois reçue ; avant, ce que montre l'écran de la station. Elle prend toute la
        // place que laissent la phrase (deux lignes) et le bouton.
        int photoW = w - 16;
        int photoH = Math.min(photoW * SpacePhotoPainter.HEIGHT / SpacePhotoPainter.WIDTH, h - 20 - 8 - 2 * 11 - 8 - BUTTON_HEIGHT - 12);
        int px = x + 8;
        int py = y + 20;
        photo(graphics, body, entry, px, py, photoW, photoH);

        // La phrase : où en est l'astre, et ce qu'il y a à faire, en clair. Le détail (instrument, durée, fenêtre,
        // prime) vient au survol.
        int remaining = entry.power() <= 0 ? -1 : (int) ((1000 - entry.permille()) / 1000.0F * body.effort() / entry.power() / 20.0F);
        boolean waiting = entry.state() < ObservatoryPayload.DISCOVERED && !entry.visible();
        Component sentence;
        int color;
        if (entry.state() == ObservatoryPayload.IMAGED) {
            sentence = Component.translatable(entry.visible() ? "observatory.orbitalis.say.imaged_visible" : "observatory.orbitalis.say.imaged_hidden",
                    span(entry.change()));
            color = Ui.OK;
        } else if (entry.state() == ObservatoryPayload.DISCOVERED) {
            sentence = status.link() ? Component.translatable("observatory.orbitalis.say.discovered", body.reward())
                    : Component.translatable("observatory.orbitalis.say.discovered_no_link");
            color = Ui.ACCENT;
        } else if (remaining < 0) {
            sentence = Component.translatable(body.deepSky() ? "observatory.orbitalis.say.need_telescope" : "observatory.orbitalis.say.need_instrument");
            color = Ui.BAD;
        } else if (waiting) {
            sentence = Component.translatable(entry.state() == ObservatoryPayload.OBSERVING ? "observatory.orbitalis.say.paused" : "observatory.orbitalis.say.hidden",
                    span(entry.change()));
            color = Ui.MUTED;
        } else if (entry.state() == ObservatoryPayload.OBSERVING) {
            sentence = Component.translatable("observatory.orbitalis.say.observing", duration(remaining), span(entry.change()));
            color = Ui.WARN;
        } else {
            sentence = Component.translatable("observatory.orbitalis.say.ready", span(entry.change()), duration(remaining));
            color = Ui.TEXT;
        }
        int textY = py + photoH + 7;
        Ui.dot(graphics, x + 8, textY + 1, color);
        // Les durées ne se coupent pas en fin de ligne (« 2 j 9 h » reste d'un bloc), et le texte s'arrête avant le ⓘ.
        for (String line : Ui.lines(font, unbroken(sentence), w - 25 - font.width("ⓘ") - 6, 2)) {
            graphics.text(font, line, x + 17, textY, color == Ui.TEXT ? Ui.TEXT : color, false);
            textY += 11;
        }
        Ui.right(graphics, font, "ⓘ", x + w - 8, py + photoH + 7, Ui.DIM);
        if (Ui.inside(mouseX, mouseY, x + 4, py + photoH + 4, w - 8, 24)) {
            String progress = entry.state() >= ObservatoryPayload.DISCOVERED ? Component.translatable("observatory.orbitalis.done").getString()
                    : remaining < 0 ? Component.translatable("observatory.orbitalis.no_instrument").getString()
                            : waiting ? Component.translatable("observatory.orbitalis.window.waiting").getString()
                                    : Component.translatable("observatory.orbitalis.remaining", duration(remaining)).getString();
            String window = entry.visible() ? Component.translatable("observatory.orbitalis.window.open", span(entry.change())).getString()
                    : Component.translatable("observatory.orbitalis.window.closed", span(entry.change())).getString();
            List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable(body.nameKey()).withStyle(net.minecraft.ChatFormatting.BOLD).getVisualOrderText());
            detail(lines, "observatory.orbitalis.instrument",
                    Component.translatable(body.deepSky() ? "observatory.orbitalis.instrument.telescope" : "observatory.orbitalis.instrument.any").getString());
            detail(lines, "observatory.orbitalis.observation", progress);
            detail(lines, "observatory.orbitalis.window", window);
            detail(lines, "observatory.orbitalis.reward", Component.translatable("observatory.orbitalis.points", body.reward()).getString());
            graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        }

        // L'action du moment.
        int by = y + h - BUTTON_HEIGHT - 6;
        int bw = w - 16;
        switch (entry.state()) {
            case ObservatoryPayload.LISTED -> button(graphics, Component.translatable("observatory.orbitalis.action.observe"), x + 8, by, bw, BUTTON_HEIGHT,
                    entry.power() > 0, true, Ui.ACCENT, () -> send(SatelliteCommandPayload.OBSERVE + body.ordinal()));
            case ObservatoryPayload.OBSERVING -> {
                Ui.bar(graphics, x + 8, by - 5, bw, 2, entry.permille() / 1000.0F, Ui.WARN);
                button(graphics, Component.translatable("observatory.orbitalis.action.observing"), x + 8, by, bw, BUTTON_HEIGHT, false, false, Ui.WARN, () -> {
                });
            }
            case ObservatoryPayload.DISCOVERED -> button(graphics, Component.translatable("observatory.orbitalis.action.receive"), x + 8, by, bw,
                    BUTTON_HEIGHT, status.link(), true, Ui.OK, () -> send(SatelliteCommandPayload.RECEIVE + body.ordinal()));
            default -> button(graphics, Component.translatable("observatory.orbitalis.action.open"), x + 8, by, bw, BUTTON_HEIGHT, true, false, Ui.ACCENT,
                    () -> showCatalogue(body));
        }
    }

    /** La phrase, dont les nombres restent collés à leur unité : on ne coupe une ligne qu'entre deux mots. */
    private static Component unbroken(Component sentence) {
        return Component.literal(sentence.getString().replaceAll("(\\d) ", "$1\u00A0"));
    }

    /** Une ligne du détail : son nom en gris, sa valeur en blanc. */
    private static void detail(List<net.minecraft.util.FormattedCharSequence> lines, String key, String value) {
        lines.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY).append(" : ")
                .append(Component.literal(value).withStyle(net.minecraft.ChatFormatting.WHITE)).getVisualOrderText());
    }

    /** Une durée lisible : en secondes, puis en minutes. */
    private static String duration(int seconds) {
        return seconds < 60 ? Component.translatable("atlas.orbitalis.seconds", Math.max(1, seconds)).getString()
                : Component.translatable("atlas.orbitalis.minutes", (seconds + 59) / 60).getString();
    }

    /**
     * L'image d'un astre, ou ce qui en tient lieu : un ciel où il n'est encore qu'un point (connu), un balayage qui le
     * dessine peu à peu (en observation), sa silhouette floue (découvert, image à recevoir).
     */
    private void photo(GuiGraphicsExtractor graphics, CelestialBody body, ObservatoryPayload.Entry entry, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, Ui.LINE);
        graphics.fill(x, y, x + w, y + h, 0xFF02030A);
        Identifier id = SpacePhotos.get(body.id());
        float progress = entry.state() >= ObservatoryPayload.DISCOVERED ? 1.0F : entry.permille() / 1000.0F;
        if (id != null && progress > 0.0F) {
            // L'image se révèle de haut en bas, au fil de l'observation ; reçue, elle est nette ; à recevoir, voilée.
            int shown = entry.state() >= ObservatoryPayload.DISCOVERED ? h : Math.round(h * progress);
            int alpha = entry.state() == ObservatoryPayload.IMAGED ? Math.round(255 * SpacePhotos.appear(body.id())) : 110;
            // Le cadre n'a pas toujours les proportions de l'image : on la recadre au centre, sans jamais l'étirer, mais
            // jamais au point de couper l'astre, qui tient au milieu de l'image. Un cadre plus large encore : l'image
            // rétrécit, et des bandes de ciel étoilé la bordent.
            float frame = w / (float) h;
            float image = SpacePhotoPainter.WIDTH / (float) SpacePhotoPainter.HEIGHT;
            float srcW;
            float srcFullH;
            int drawX = x;
            int drawW = w;
            if (frame >= image) {
                srcFullH = Math.max(SpacePhotoPainter.WIDTH / frame, Math.min(SpacePhotoPainter.HEIGHT, MIN_PHOTO_SPAN));
                srcW = Math.min(SpacePhotoPainter.WIDTH, srcFullH * frame);
                drawW = Math.min(w, Math.round(h * srcW / srcFullH));
                drawX = x + (w - drawW) / 2;
            } else {
                srcW = SpacePhotoPainter.HEIGHT * frame;
                srcFullH = SpacePhotoPainter.HEIGHT;
            }
            float u0 = (SpacePhotoPainter.WIDTH - srcW) / 2.0F;
            float v0 = (SpacePhotoPainter.HEIGHT - srcFullH) / 2.0F;
            int srcH = Math.max(1, Math.round(srcFullH * shown / (float) h));
            graphics.blit(RenderPipelines.GUI_TEXTURED, id, drawX, y, u0, v0, drawW, shown, Math.round(srcW), srcH, SpacePhotoPainter.WIDTH,
                    SpacePhotoPainter.HEIGHT, alpha << 24 | 0xFFFFFF);
            if (drawW < w) {
                bandStars(graphics, body, x, y, drawX - x, h, shown, alpha);
                bandStars(graphics, body, drawX + drawW, y, x + w - drawX - drawW, h, shown, alpha);
            }
            if (entry.state() == ObservatoryPayload.OBSERVING) {
                // La ligne de balayage, qui descend.
                graphics.fill(x, y + shown - 1, x + w, y + shown, 0xC0FFC24A);
            }
        } else {
            // Rien encore : un point qui scintille, au centre d'un réticule.
            float twinkle = 0.6F + 0.4F * Mth.sin(Util.getMillis() / 300.0F);
            int c = Math.round(200 * twinkle) << 24 | 0xFFFFFF;
            graphics.fill(x + w / 2, y + h / 2, x + w / 2 + 1, y + h / 2 + 1, c);
            graphics.fill(x + w / 2 - 12, y + h / 2, x + w / 2 - 5, y + h / 2 + 1, 0x40FFFFFF);
            graphics.fill(x + w / 2 + 6, y + h / 2, x + w / 2 + 13, y + h / 2 + 1, 0x40FFFFFF);
            graphics.fill(x + w / 2, y + h / 2 - 12, x + w / 2 + 1, y + h / 2 - 5, 0x40FFFFFF);
            graphics.fill(x + w / 2, y + h / 2 + 6, x + w / 2 + 1, y + h / 2 + 13, 0x40FFFFFF);
        }
        if (entry.state() == ObservatoryPayload.DISCOVERED) {
            Component note = Component.translatable("observatory.orbitalis.image_waiting");
            int nw = font.width(note) + 8;
            Ui.round(graphics, x + (w - nw) / 2, y + h - 16, nw, 11, 0xD004060A);
            graphics.text(font, note, x + (w - nw) / 2 + 4, y + h - 14, Ui.ACCENT, false);
        }
    }

    // ------------------------------------------------------------------
    // Le catalogue
    // ------------------------------------------------------------------

    /**
     * Hauteur d'image, en pixels de la photographie, en deçà de laquelle on ne la recadre pas : l'astre en occupe les
     * 170 du milieu, et garde ainsi un peu de ciel autour de lui.
     */
    private static final float MIN_PHOTO_SPAN = 190.0F;

    /**
     * Quelques étoiles, toujours les mêmes pour un astre, dans une bande de ciel qui borde son image ; seules les
     * {@code shown} premières lignes, déjà révélées, en montrent.
     */
    private static void bandStars(GuiGraphicsExtractor graphics, CelestialBody body, int x, int y, int w, int h, int shown, int alpha) {
        if (w <= 0 || h <= 0) {
            return;
        }
        java.util.Random random = new java.util.Random(body.id().hashCode() * 31L + x);
        int stars = w * h / 90;
        for (int i = 0; i < stars; i++) {
            int sx = x + random.nextInt(w);
            int sy = y + random.nextInt(h);
            int light = 90 + random.nextInt(140);
            if (sy >= y + shown) {
                continue;
            }
            graphics.fill(sx, sy, sx + 1, sy + 1, (light * alpha / 255) << 24 | 0xFFFFFF);
        }
    }

    /** L'ouvrage des découvertes : la photographie à gauche, la fiche à droite, une double page par astre. */
    private void extractCatalogue(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        List<CelestialBody> pages = catalogue();
        if (pages.isEmpty()) {
            Ui.card(graphics, x, y, w, h, false);
            int lineY = y + h / 2 - 10;
            for (String line : Ui.lines(font, Component.translatable("observatory.orbitalis.catalogue_empty"), w - 60, 3)) {
                graphics.text(font, line, x + (w - font.width(line)) / 2, lineY, Ui.MUTED, false);
                lineY += 11;
            }
            return;
        }
        page = Mth.clamp(page, 0, pages.size() - 1);
        CelestialBody body = pages.get(page);
        int half = (w - 6) / 2;
        // La page de gauche : l'image, en grand, et sa légende.
        Ui.card(graphics, x, y, half, h, false);
        int photoW = half - 12;
        int photoH = photoW * SpacePhotoPainter.HEIGHT / SpacePhotoPainter.WIDTH;
        ObservatoryPayload.Entry entry = entry(body);
        photo(graphics, body, entry, x + 6, y + 6, photoW, photoH);
        int capY = y + 6 + photoH + 6;
        Ui.heading(graphics, font, Component.translatable(body.nameKey()), x + 8, capY);
        graphics.text(font, Ui.fit(font, Component.translatable(body.kind().nameKey()).getString(), half - 16), x + 8, capY + 12, Ui.MUTED, false);
        // La pagination, en pied de la page de gauche.
        int navY = y + h - BUTTON_HEIGHT - 5;
        button(graphics, Component.literal("‹"), x + 8, navY, 22, BUTTON_HEIGHT, page > 0, false, Ui.ACCENT, () -> page--);
        String counter = (page + 1) + " / " + pages.size();
        graphics.text(font, counter, x + half / 2 - font.width(counter) / 2, navY + 4, Ui.MUTED, false);
        button(graphics, Component.literal("›"), x + half - 30, navY, 22, BUTTON_HEIGHT, page < pages.size() - 1, false, Ui.ACCENT, () -> page++);

        // La page de droite : la fiche.
        int rx = x + half + 6;
        int rw = w - half - 6;
        Ui.card(graphics, rx, y, rw, h, false);
        // En tête, ce qu'est l'astre ; dessous, sa fiche. La description prend la place que la fiche lui laisse, et rien
        // ne déborde de la page.
        CelestialBody.Travel travel = body.travel();
        int sheet = travel == null ? 70 : 15 + 9 * 11 + 18 + travel.equipment().size() * 11;
        int lines = Math.clamp((h - 26 - sheet) / 10, 2, 6);
        graphics.enableScissor(rx + 1, y + 1, rx + rw - 1, y + h - 1);
        int descY = y + 8;
        for (String line : Ui.lines(font, Component.translatable(body.descriptionKey()), rw - 16, lines)) {
            graphics.text(font, line, rx + 8, descY, Ui.TEXT, false);
            descY += 10;
        }
        Ui.hline(graphics, rx + 6, descY + 3, rw - 12);
        extractSheet(graphics, body, rx + 8, descY + 10, rw - 16, y + h - 8 - (descY + 10));
        graphics.disableScissor();
    }

    /** La fiche d'un astre : pour une planète ou une lune, le voyage ; pour le reste, ce qu'en a tiré l'observatoire. */
    private void extractSheet(GuiGraphicsExtractor graphics, CelestialBody body, int x, int y, int w, int h) {
        CelestialBody.Travel travel = body.travel();
        if (travel == null) {
            Ui.heading(graphics, font, Component.translatable("observatory.orbitalis.sheet.observation"), x, y);
            int rowY = y + 14;
            Ui.row(graphics, font, Component.translatable("observatory.orbitalis.instrument"),
                    Component.translatable(body.deepSky() ? "observatory.orbitalis.instrument.telescope" : "observatory.orbitalis.instrument.any").getString(),
                    x, rowY, w, Ui.TEXT);
            rowY += 11;
            Ui.row(graphics, font, Component.translatable("observatory.orbitalis.reward"),
                    Component.translatable("observatory.orbitalis.points", body.reward()).getString(), x, rowY, w, Ui.TEXT);
            rowY += 18;
            for (String line : Ui.lines(font, Component.translatable("observatory.orbitalis.sheet.far"), w, 6)) {
                graphics.text(font, line, x, rowY, Ui.MUTED, false);
                rowY += 10;
            }
            return;
        }
        Ui.heading(graphics, font, Component.translatable("observatory.orbitalis.sheet.travel"), x, y);
        boolean reachable = travel.reachable();
        String status = Component.translatable(reachable ? "observatory.orbitalis.sheet.reachable" : "observatory.orbitalis.sheet.future").getString();
        Ui.pillRight(graphics, font, status, x + w, y - 2, reachable ? Ui.OK : Ui.WARN);
        int rowY = y + 15;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {text("observatory.orbitalis.sheet.distance"),
                travel.lightYears() ? Component.translatable("observatory.orbitalis.unit.ly", format(travel.distance())).getString()
                        : Component.translatable("observatory.orbitalis.unit.mkm", format(travel.distance())).getString()});
        if (travel.transferDays() > 0) {
            rows.add(new String[] {text("observatory.orbitalis.sheet.duration"), travel.transferDays() >= 400
                    ? Component.translatable("observatory.orbitalis.unit.years", format(travel.transferDays() / 365.0)).getString()
                    : Component.translatable("observatory.orbitalis.unit.days", travel.transferDays()).getString()});
            rows.add(new String[] {text("observatory.orbitalis.sheet.dv_ground"), grouped(travel.deltaVFromGround()) + " m/s"});
            rows.add(new String[] {text("observatory.orbitalis.sheet.dv_orbit"), grouped(travel.deltaVFromOrbit()) + " m/s"});
        } else {
            rows.add(new String[] {text("observatory.orbitalis.sheet.duration"), text("observatory.orbitalis.sheet.unknown_route")});
        }
        rows.add(new String[] {text("observatory.orbitalis.sheet.gravity"), format(travel.gravity()) + " g"});
        rows.add(new String[] {text("observatory.orbitalis.sheet.atmosphere"), text("observatory.orbitalis.air." + travel.atmosphere())});
        rows.add(new String[] {text("observatory.orbitalis.sheet.temperature"), travel.temperature() + " °C"});
        rows.add(new String[] {text("observatory.orbitalis.sheet.launcher"),
                reachable ? text(travel.reach().nameKey()) : text("observatory.orbitalis.sheet.no_launcher")});
        for (String[] row : rows) {
            Ui.row(graphics, font, Component.literal(row[0]), Ui.fit(font, row[1], w - Math.min(font.width(row[0]), 90) - 8), x, rowY, w, Ui.TEXT);
            rowY += 11;
        }
        // L'équipement : les technologies qu'il faut, acquises ou non.
        rowY += 5;
        Ui.heading(graphics, font, Component.translatable("observatory.orbitalis.sheet.equipment"), x, rowY);
        rowY += 13;
        for (String research : travel.equipment()) {
            if (rowY + 9 > y + h) {
                break;
            }
            boolean known = ClientResearch.get().isUnlocked(research);
            Ui.dot(graphics, x, rowY + 1, known ? Ui.OK : Ui.BAD);
            graphics.text(font, Ui.fit(font, Component.translatable("research.orbitalis.node." + research).getString(), w - 60), x + 9, rowY,
                    known ? Ui.TEXT : Ui.MUTED, false);
            Ui.right(graphics, font, Component.translatable(known ? "observatory.orbitalis.sheet.have" : "observatory.orbitalis.sheet.missing").getString(),
                    x + w, rowY, known ? Ui.OK : Ui.BAD);
            rowY += 11;
        }
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static String format(double value) {
        return value >= 100 ? grouped(value) : String.format(Locale.ROOT, value >= 10 ? "%.1f" : "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String grouped(double value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }
}
