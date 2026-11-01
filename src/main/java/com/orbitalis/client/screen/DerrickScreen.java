package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.orbitalis.core.network.DerrickCommandPayload;
import com.orbitalis.core.network.DerrickStatusPayload;
import com.orbitalis.phase1.quarry.DerrickBlockEntity;
import com.orbitalis.phase1.quarry.QuarryKinds;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran d'un derrick. À gauche, l'état de la tour et ses commandes : énergie, eau de refroidissement, chaleur de la
 * tête, avancement, lancer ou arrêter, carotter, la taille du carré et le remblai. Au milieu, la carte des filons que
 * dressent les satellites d'observation de l'agence. À droite, les sortes à extraire : un clic les coche ou les
 * décoche ; on y lit combien le relevé en a vu, et à quelles profondeurs le carottage les a trouvées.
 */
public class DerrickScreen extends Screen implements BlockScreen {
    private static final int WIDTH = 430;
    private static final int HEIGHT = 242;
    private static final int ROW = 11;

    private DerrickStatusPayload status;
    private int left;
    private int top;
    private int ticks;
    private final List<Zone> zones = new ArrayList<>();
    private List<FormattedCharSequence> tooltip;

    private record Zone(int x, int y, int w, int h, Runnable action) {
    }

    public DerrickScreen(DerrickStatusPayload status) {
        super(Component.translatable("block.orbitalis.drill_derrick"));
        this.status = status;
    }

    public void receive(DerrickStatusPayload payload) {
        if (payload.pos().equals(status.pos())) {
            status = payload;
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    /** L'état se rafraîchit chaque seconde tant que l'écran est ouvert. */
    @Override
    public void tick() {
        super.tick();
        if (++ticks % 20 == 0) {
            send(DerrickBlockEntity.ACTION_REFRESH, 0);
        }
    }

    private void send(int action, int argument) {
        ClientPacketDistributor.sendToServer(new DerrickCommandPayload(status.pos(), action, argument));
    }

    private DerrickBlockEntity.Status state() {
        DerrickBlockEntity.Status[] values = DerrickBlockEntity.Status.values();
        return values[Math.clamp(status.status(), 0, values.length - 1)];
    }

    private int stateColor() {
        return switch (state()) {
            case WORKING, CORING -> Ui.OK;
            case STOPPED, FINISHED -> Ui.MUTED;
            case FULL, OVERHEATED -> Ui.WARN;
            case UNFORMED, NO_POWER -> Ui.BAD;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        zones.clear();
        tooltip = null;
        String key = state().name().toLowerCase(Locale.ROOT);
        // En trois temps : l'état en un mot, la phrase qui dit quoi faire, le détail au survol.
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 9, Ui.TEXT, false);
        Ui.pillRight(graphics, font, Component.translatable("derrick.orbitalis.state." + key).getString().toUpperCase(Locale.ROOT),
                left + WIDTH - 12, top + 7, stateColor());
        Component hint = state() == DerrickBlockEntity.Status.UNFORMED
                ? Component.translatable("derrick.orbitalis.hint.unformed", status.missing())
                : Component.translatable("derrick.orbitalis.hint." + key);
        // La phrase tient sur deux lignes au plus, quelle que soit la langue.
        int hintY = top + 24;
        for (String line : Ui.lines(font, hint, WIDTH - 24, 2)) {
            graphics.text(font, line, left + 12, hintY, Ui.MUTED, false);
            hintY += 10;
        }
        Ui.hline(graphics, left + 1, top + 46, WIDTH - 2);

        extractStatus(graphics, mouseX, mouseY, left + 12, top + 54, 140);
        extractMap(graphics, mouseX, mouseY, left + 164, top + 54, 130);
        extractKinds(graphics, mouseX, mouseY, left + 306, top + 54, 112);

        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Colonne de gauche : état et commandes
    // ------------------------------------------------------------------

    private void extractStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, Component.translatable("derrick.orbitalis.section.tower").getString()
                .toUpperCase(Locale.ROOT), w)), x, y);
        y += 13;
        y = gauge(graphics, mouseX, mouseY, x, y, w, "derrick.orbitalis.energy", status.energy(), status.energyCapacity(), Ui.WARN,
                Component.translatable("derrick.orbitalis.energy.detail", status.energy(), status.energyCapacity(),
                        DerrickBlockEntity.RUN_DRAW, DerrickBlockEntity.BLOCK_ENERGY));
        y = gauge(graphics, mouseX, mouseY, x, y, w, "derrick.orbitalis.water", status.water(), status.waterCapacity(), Ui.ACCENT,
                Component.translatable("derrick.orbitalis.water.detail", status.water(), status.waterCapacity(), DerrickBlockEntity.COOLANT_PER_TICK));
        int heatColor = status.heat() < 500 ? Ui.OK : status.heat() < 800 ? Ui.WARN : Ui.BAD;
        y = gauge(graphics, mouseX, mouseY, x, y, w, "derrick.orbitalis.heat", status.heat(), DerrickBlockEntity.HEAT_MAX, heatColor,
                Component.translatable("derrick.orbitalis.heat.detail"));
        y += 2;
        int depth = Math.max(1, status.top() - status.bottom());
        float progress = status.layer() >= status.top() ? 0.0F : (status.top() - status.layer()) / (float) depth;
        Ui.row(graphics, font, Component.translatable("derrick.orbitalis.layer"), "y " + status.layer(), x, y, w, Ui.TEXT);
        y += ROW;
        Ui.bar(graphics, x, y, w, 2, progress, Ui.ACCENT);
        y += 6;
        Ui.row(graphics, font, Component.translatable("derrick.orbitalis.mined"), Integer.toString(status.mined()), x, y, w, Ui.TEXT);
        y += ROW;
        Ui.row(graphics, font, Component.translatable("derrick.orbitalis.stored"), Integer.toString(status.stored()), x, y, w, Ui.TEXT);
        y += ROW + 4;

        // Commandes : lancer ou arrêter, carotter, puis le carré et le remblai.
        boolean formed = state() != DerrickBlockEntity.Status.UNFORMED;
        int half = (w - 4) / 2;
        button(graphics, mouseX, mouseY, Component.translatable(status.running() ? "derrick.orbitalis.stop" : "derrick.orbitalis.start"),
                x, y, half, 16, formed, !status.running(), status.running() ? Ui.BAD : Ui.OK, () -> send(DerrickBlockEntity.ACTION_RUN, 0));
        boolean coring = status.core() >= 0;
        button(graphics, mouseX, mouseY, coring ? Component.literal(status.core() + " %")
                : Component.translatable("derrick.orbitalis.core"), x + half + 4, y, half, 16, formed && !coring, false, Ui.ACCENT,
                () -> send(DerrickBlockEntity.ACTION_CORE, 0));
        if (Ui.inside(mouseX, mouseY, x + half + 4, y, half, 16)) {
            tooltip = font.split(Component.translatable("derrick.orbitalis.core.detail", DerrickBlockEntity.CORE_ENERGY,
                    DerrickBlockEntity.CORE_POINTS), 220);
        }
        y += 20;
        int side = 2 * status.radius() + 1;
        button(graphics, mouseX, mouseY, Component.literal("-"), x, y, 16, 16, status.radius() > DerrickBlockEntity.MIN_RADIUS, false, Ui.ACCENT,
                () -> send(DerrickBlockEntity.ACTION_RADIUS, -1));
        String area = Ui.fit(font, Component.translatable("derrick.orbitalis.area", side, side).getString(), w - 40);
        graphics.text(font, area, x + (w - font.width(area)) / 2, y + 4, Ui.TEXT, false);
        button(graphics, mouseX, mouseY, Component.literal("+"), x + w - 16, y, 16, 16, status.radius() < DerrickBlockEntity.MAX_RADIUS, false,
                Ui.ACCENT, () -> send(DerrickBlockEntity.ACTION_RADIUS, 1));
        y += 20;
        button(graphics, mouseX, mouseY, Component.translatable(status.backfill() ? "derrick.orbitalis.backfill.on" : "derrick.orbitalis.backfill.off"),
                x, y, w, 16, true, false, status.backfill() ? Ui.OK : Ui.MUTED, () -> send(DerrickBlockEntity.ACTION_BACKFILL, 0));
        if (Ui.inside(mouseX, mouseY, x, y, w, 16)) {
            tooltip = font.split(Component.translatable("derrick.orbitalis.backfill.detail"), 220);
        }
    }

    private int gauge(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, String label, int value, int capacity, int color,
            Component detail) {
        String percent = (capacity <= 0 ? 0 : (int) (100L * value / capacity)) + " %";
        Ui.row(graphics, font, Component.translatable(label), percent, x, y, w, color);
        Ui.bar(graphics, x, y + 10, w, 3, capacity <= 0 ? 0 : value / (float) capacity, color);
        if (Ui.inside(mouseX, mouseY, x, y, w, 14)) {
            tooltip = font.split(detail, 220);
        }
        return y + 17;
    }

    private void button(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Component label, int x, int y, int w, int h, boolean enabled,
            boolean primary, int color, Runnable action) {
        boolean hovered = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        Ui.button(graphics, font, label, x, y, w, h, hovered, enabled, primary, color);
        if (enabled) {
            zones.add(new Zone(x, y, w, h, action));
        }
    }

    // ------------------------------------------------------------------
    // Colonne du milieu : la carte des filons
    // ------------------------------------------------------------------

    private void extractMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, Component.translatable("derrick.orbitalis.section.map").getString()
                .toUpperCase(Locale.ROOT), w)), x, y);
        y += 13;
        int side = status.surveySide();
        if (side <= 0 || status.surveyCells().length != side * side) {
            Ui.card(graphics, x, y, w, w, false);
            int textY = y + 10;
            for (String line : Ui.lines(font, Component.translatable("derrick.orbitalis.map.none"), w - 12, 8)) {
                graphics.text(font, line, x + 6, textY, Ui.MUTED, false);
                textY += 10;
            }
            return;
        }
        int cell = Math.max(1, w / side);
        int size = cell * side;
        int ox = x + (w - size) / 2;
        Ui.card(graphics, x, y, w, w, false);
        int oy = y + (w - size) / 2;
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int kind = status.surveyCells()[row * side + column] - 1;
                int color = kind < 0 ? Ui.SURFACE_HI : QuarryKinds.color(kind);
                if (kind >= 0 && (status.filter() & (1 << kind)) == 0) {
                    color = Ui.alpha(color, 0.3F);
                }
                graphics.fill(ox + column * cell, oy + row * cell, ox + (column + 1) * cell, oy + (row + 1) * cell, color);
            }
        }
        // Le derrick, au centre.
        int centre = side / 2;
        Ui.frame(graphics, ox + centre * cell - 1, oy + centre * cell - 1, cell + 2, cell + 2, Ui.TEXT);
        if (Ui.inside(mouseX, mouseY, x, y, w, w)) {
            tooltip = font.split(Component.translatable("derrick.orbitalis.map.detail", status.observers()), 220);
        }
    }

    // ------------------------------------------------------------------
    // Colonne de droite : les sortes à extraire
    // ------------------------------------------------------------------

    private void extractKinds(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, Component.translatable("derrick.orbitalis.section.kinds").getString()
                .toUpperCase(Locale.ROOT), w)), x, y);
        y += 13;
        int[] counts = status.surveyCounts();
        int[] bands = status.coreBands();
        for (int kind = 0; kind < QuarryKinds.COUNT; kind++) {
            boolean on = (status.filter() & (1 << kind)) != 0;
            boolean hovered = Ui.inside(mouseX, mouseY, x, y - 1, w, ROW);
            if (hovered) {
                graphics.fill(x - 2, y - 1, x + w + 2, y + ROW - 1, Ui.SURFACE_HI);
            }
            int color = QuarryKinds.color(kind);
            Ui.frame(graphics, x, y + 1, 7, 7, on ? color : Ui.LINE_HI);
            if (on) {
                graphics.fill(x + 2, y + 3, x + 5, y + 6, color);
            }
            // À droite : ce que le relevé en a vu ; sinon, si le carottage l'a trouvée, un repère.
            String value = counts.length > kind && counts[kind] > 0 ? Integer.toString(counts[kind]) : found(bands, kind) ? "●" : "";
            int valueWidth = font.width(value);
            graphics.text(font, value, x + w - valueWidth, y, on ? Ui.TEXT : Ui.DIM, false);
            graphics.text(font, Ui.fit(font, Component.translatable(QuarryKinds.nameKey(kind)).getString(), w - 14 - valueWidth), x + 11, y,
                    on ? Ui.TEXT : Ui.DIM, false);
            int kindIndex = kind;
            zones.add(new Zone(x - 2, y - 1, w + 4, ROW, () -> send(DerrickBlockEntity.ACTION_FILTER, kindIndex)));
            if (hovered) {
                tooltip = font.split(depths(bands, kind), 220);
            }
            y += ROW;
        }
    }

    private static boolean found(int[] bands, int kind) {
        for (int band : bands) {
            if ((band & (1 << kind)) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Ce que dit le carottage de cette sorte : entre quelles profondeurs on la trouve. */
    private Component depths(int[] bands, int kind) {
        int first = -1;
        int last = -1;
        for (int band = 0; band < bands.length; band++) {
            if ((bands[band] & (1 << kind)) != 0) {
                if (first < 0) {
                    first = band;
                }
                last = band;
            }
        }
        if (bands.length == 0) {
            return Component.translatable("derrick.orbitalis.kind.no_core");
        }
        if (first < 0) {
            return Component.translatable("derrick.orbitalis.kind.absent");
        }
        int high = status.top() - first * DerrickBlockEntity.BAND;
        int low = Math.max(status.bottom(), status.top() - (last + 1) * DerrickBlockEntity.BAND + 1);
        return Component.translatable("derrick.orbitalis.kind.depths", high, low);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Zone zone : List.copyOf(zones)) {
                if (Ui.inside(event.x(), event.y(), zone.x(), zone.y(), zone.w(), zone.h())) {
                    zone.action().run();
                    Ui.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
