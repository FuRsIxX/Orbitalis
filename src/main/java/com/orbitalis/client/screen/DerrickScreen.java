package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.orbitalis.core.network.DerrickCommandPayload;
import com.orbitalis.core.network.DerrickStatusPayload;
import com.orbitalis.phase1.quarry.DerrickBlockEntity;
import com.orbitalis.phase1.quarry.DerrickMenu;
import com.orbitalis.phase1.quarry.DerrickPlan;
import com.orbitalis.phase1.quarry.QuarryKinds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran de la foreuse, dans le style des machines : un panneau gris en relief, des cases en creux, des jauges dans
 * leurs cadres sombres et l'écran d'état. À gauche, comme une machine : l'eau, la chaleur et l'énergie, les dix-huit
 * cases du butin, la case du bloc de remplacement, l'état, et l'inventaire du joueur. Au milieu, les réglages : lancer,
 * le mode, la taille de la zone ou le tronçon entier, le remplacement, le carottage, puis ce que permet la tour. À
 * droite, la carte des filons que dressent les satellites, et les sortes à extraire, qu'un clic coche ou décoche.
 */
public class DerrickScreen extends AbstractContainerScreen<DerrickMenu> implements BlockScreen {
    // Couleurs des écrans de machines (voir tools/generate_textures.py, gui_panel et gui_slot).
    private static final int FILL = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SCREEN = 0xFF18221C;
    private static final int GAUGE = 0xFF222426;
    private static final int LABEL = 0xFF404040;
    private static final int OK = 0xFF7CE08A;
    private static final int IDLE = 0xFF9DB5A4;
    private static final int WARN = 0xFFFFC857;
    private static final int BAD = 0xFFFF6B6B;

    private static final int WIDTH = 400;
    private static final int HEIGHT = 194;
    /** Volet des réglages, puis volet de la carte et des sortes. */
    private static final int SETTINGS_X = 182;
    private static final int SETTINGS_W = 100;
    private static final int KINDS_X = 290;
    private static final int KINDS_W = 102;
    private static final int MAP = 66;
    private static final int ROW = 9;
    /** La carte d'instrument de la tour, sous les réglages. */
    private static final int CARD_Y = 126;
    private static final int CARD_H = 61;

    private DerrickStatusPayload status;
    private int ticks;
    private final List<Zone> zones = new ArrayList<>();
    private List<FormattedCharSequence> hover;

    private record Zone(int x, int y, int w, int h, Runnable action) {
    }

    public DerrickScreen(DerrickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelY = DerrickMenu.PLAYER_Y - 10;
    }

    /** L'état complet de la foreuse, envoyé à l'ouverture puis à chaque commande. */
    public void receive(DerrickStatusPayload payload) {
        if (payload.pos().equals(menu.pos())) {
            status = payload;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++ticks % 20 == 0) {
            send(DerrickBlockEntity.ACTION_REFRESH, 0);
        }
    }

    private void send(int action, int argument) {
        if (menu.pos() != null) {
            ClientPacketDistributor.sendToServer(new DerrickCommandPayload(menu.pos(), action, argument));
        }
    }

    private DerrickBlockEntity.Status state() {
        DerrickBlockEntity.Status[] values = DerrickBlockEntity.Status.values();
        return values[Math.clamp(menu.getStatus(), 0, values.length - 1)];
    }

    private int stateColor() {
        return switch (state()) {
            case WORKING, CORING -> OK;
            case STOPPED, FINISHED -> IDLE;
            case FULL, OVERHEATED -> WARN;
            case UNFORMED, NO_POWER -> BAD;
        };
    }

    // ------------------------------------------------------------------
    // Dessin du panneau
    // ------------------------------------------------------------------

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    /** Un cadre en creux : bord sombre en haut à gauche, bord clair en bas à droite. */
    private static void inset(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int inner) {
        rect(graphics, x, y, w, h, inner);
        rect(graphics, x, y, w - 1, 1, DARK);
        rect(graphics, x, y, 1, h - 1, DARK);
        rect(graphics, x + 1, y + h - 1, w - 1, 1, WHITE);
        rect(graphics, x + w - 1, y + 1, 1, h - 1, WHITE);
    }

    /** Un relief : bord clair en haut à gauche, ombre en bas à droite (les boutons). */
    private static void raised(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int inner) {
        rect(graphics, x, y, w, h, inner);
        rect(graphics, x, y, w - 1, 1, WHITE);
        rect(graphics, x, y, 1, h - 1, WHITE);
        rect(graphics, x + 1, y + h - 1, w - 1, 1, SHADOW);
        rect(graphics, x + w - 1, y + 1, 1, h - 1, SHADOW);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        // Le panneau des machines : bord noir, reflet, ombre.
        rect(graphics, x + 1, y, WIDTH - 2, HEIGHT, 0xFF000000);
        rect(graphics, x, y + 1, WIDTH, HEIGHT - 2, 0xFF000000);
        rect(graphics, x + 1, y + 1, WIDTH - 2, HEIGHT - 2, SHADOW);
        rect(graphics, x + 1, y + 1, WIDTH - 4, HEIGHT - 4, WHITE);
        rect(graphics, x + 3, y + 3, WIDTH - 6, HEIGHT - 6, FILL);
        // Séparation entre la partie machine et les volets.
        rect(graphics, x + 176, y + 6, 1, HEIGHT - 12, SHADOW);
        rect(graphics, x + 177, y + 6, 1, HEIGHT - 12, WHITE);

        // Jauges : l'eau, la chaleur, l'énergie, chacune coiffée d'un liseré de sa couleur.
        int gaugeTop = y + DerrickMenu.OUTPUT_Y - 1;
        rect(graphics, x + 7, gaugeTop - 2, 14, 1, 0xFF3F76E4);
        rect(graphics, x + 24, gaugeTop - 2, 10, 1, 0xFFFFB347);
        rect(graphics, x + 37, gaugeTop - 2, 10, 1, 0xFF7CE08A);
        inset(graphics, x + 7, gaugeTop, 14, 56, GAUGE);
        new FluidGauge(8, DerrickMenu.OUTPUT_Y, 12, 54).extract(graphics, x, y, Fluids.WATER, menu.getWater(), DerrickBlockEntity.WATER_CAPACITY);
        inset(graphics, x + 24, gaugeTop, 10, 56, GAUGE);
        int heat = Math.clamp(menu.getHeat() * 54 / DerrickBlockEntity.HEAT_MAX, 0, 54);
        int heatColor = menu.getHeat() < 500 ? 0xFF6FD08C : menu.getHeat() < 800 ? 0xFFFFB347 : 0xFFE5533D;
        rect(graphics, x + 25, y + DerrickMenu.OUTPUT_Y + 54 - heat, 8, heat, heatColor);
        inset(graphics, x + 37, gaugeTop, 10, 56, GAUGE);
        int capacity = Math.max(1, menu.getEnergyCapacity());
        int power = Math.clamp((int) ((long) menu.getEnergy() * 54 / capacity), 0, 54);
        for (int k = 0; k < power; k++) {
            if ((54 - 1 - k) % 4 != 3) {
                rect(graphics, x + 38, y + DerrickMenu.OUTPUT_Y + 53 - k, 8, 1, k % 4 < 2 ? 0xFF7CE08A : 0xFF4FA866);
            }
        }

        // Les cases : le butin, en orange comme les sorties des machines ; le bloc de remplacement, en bleu.
        for (int slot = 0; slot < DerrickBlockEntity.OUTPUT_SLOTS; slot++) {
            int sx = x + DerrickMenu.OUTPUT_X - 1 + (slot % 6) * 18;
            int sy = y + DerrickMenu.OUTPUT_Y - 1 + (slot / 6) * 18;
            inset(graphics, sx, sy, 18, 18, SLOT);
        }
        rect(graphics, x + DerrickMenu.OUTPUT_X - 2, y + DerrickMenu.OUTPUT_Y - 2, 6 * 18 + 2, 1, 0xFFE8872A);
        rect(graphics, x + DerrickMenu.REPLACE_X - 2, y + DerrickMenu.REPLACE_Y - 2, 20, 20, 0xFF4F86D6);
        inset(graphics, x + DerrickMenu.REPLACE_X - 1, y + DerrickMenu.REPLACE_Y - 1, 18, 18, SLOT);
        if (!menu.slots.get(DerrickBlockEntity.OUTPUT_SLOTS).hasItem()) {
            ghostBlock(graphics, x + DerrickMenu.REPLACE_X + 3, y + DerrickMenu.REPLACE_Y + 3);
        }
        // L'écran d'état.
        inset(graphics, x + 47, y + 77, 123, 21, SCREEN);
        // L'inventaire du joueur.
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int sy = row < 3 ? DerrickMenu.PLAYER_Y + row * 18 : DerrickMenu.PLAYER_Y + 58;
                inset(graphics, x + 7 + column * 18, y + sy - 1, 18, 18, SLOT);
            }
        }
        // Les titres des volets, soulignés d'un filet gravé et d'une amorce de couleur.
        for (int[] section : new int[][] {{SETTINGS_X, SETTINGS_W}, {KINDS_X, KINDS_W}}) {
            rect(graphics, x + section[0], y + 15, section[1], 1, SHADOW);
            rect(graphics, x + section[0], y + 16, section[1], 1, WHITE);
            rect(graphics, x + section[0], y + 15, 12, 1, 0xFFE2B132);
        }
        // La carte.
        inset(graphics, x + KINDS_X - 1, y + 17, MAP + 2, MAP + 2, GAUGE);
        // La carte d'instrument de la tour, sous les réglages.
        inset(graphics, x + SETTINGS_X - 1, y + CARD_Y, SETTINGS_W + 2, CARD_H, SCREEN);
    }

    /** Un bloc en fantôme, estompé : ce que remet la foreuse quand la case est vide (la roche du monde). */
    private static void ghostBlock(GuiGraphicsExtractor graphics, int x, int y) {
        int top = Ui.alpha(0xFFD0D0D0, 0.6F);
        int front = Ui.alpha(0xFF9A9A9A, 0.6F);
        int side = Ui.alpha(0xFF707070, 0.6F);
        rect(graphics, x + 2, y, 8, 1, top);
        rect(graphics, x + 1, y + 1, 8, 1, top);
        rect(graphics, x, y + 2, 8, 1, top);
        rect(graphics, x, y + 3, 8, 7, front);
        rect(graphics, x + 9, y, 1, 8, side);
        rect(graphics, x + 8, y + 1, 1, 9, side);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Ui.fit(font, title.getString(), 168), 8, 6, LABEL, false);
        graphics.text(font, playerInventoryTitle, 8, inventoryLabelY, LABEL, false);
        // L'écran d'état : l'état en couleur, puis la couche et ce qui a été extrait.
        String stateName = Component.translatable("derrick.orbitalis.state." + state().name().toLowerCase(Locale.ROOT)).getString();
        graphics.text(font, Ui.fit(font, stateName, 117), 50, 80, stateColor(), false);
        if (status != null) {
            String detail = state() == DerrickBlockEntity.Status.UNFORMED
                    ? Component.translatable("derrick.orbitalis.band.missing", status.missing()).getString()
                    : Component.translatable("derrick.orbitalis.band.layer", status.layer(), status.mined()).getString();
            graphics.text(font, Ui.fit(font, detail, 117), 50, 89, IDLE, false);
        }
        graphics.text(font, Component.translatable("derrick.orbitalis.section.settings"), SETTINGS_X, 6, LABEL, false);
        graphics.text(font, Ui.fit(font, Component.translatable("derrick.orbitalis.section.map").getString(), KINDS_W), KINDS_X, 6, LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        zones.clear();
        hover = null;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractSettings(graphics, mouseX, mouseY, leftPos + SETTINGS_X, topPos + 17);
        extractMap(graphics, mouseX, mouseY, leftPos + KINDS_X, topPos + 18);
        extractKinds(graphics, mouseX, mouseY, leftPos + KINDS_X, topPos + 18 + MAP + 6);
        gaugeTooltips(mouseX, mouseY);
        if (hover != null) {
            graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
        }
    }

    private void gaugeTooltips(int mouseX, int mouseY) {
        int y = topPos + DerrickMenu.OUTPUT_Y;
        if (Ui.inside(mouseX, mouseY, leftPos + 7, y - 1, 14, 56)) {
            hover = font.split(Component.translatable("derrick.orbitalis.water.detail", menu.getWater(), DerrickBlockEntity.WATER_CAPACITY,
                    DerrickBlockEntity.COOLANT_PER_TICK), 220);
        } else if (Ui.inside(mouseX, mouseY, leftPos + 24, y - 1, 10, 56)) {
            hover = font.split(Component.translatable("derrick.orbitalis.heat.detail"), 220);
        } else if (Ui.inside(mouseX, mouseY, leftPos + 37, y - 1, 10, 56)) {
            hover = font.split(Component.translatable("derrick.orbitalis.energy.detail", menu.getEnergy(), menu.getEnergyCapacity(),
                    DerrickBlockEntity.RUN_DRAW, DerrickBlockEntity.BLOCK_ENERGY), 220);
        } else if (Ui.inside(mouseX, mouseY, leftPos + DerrickMenu.REPLACE_X - 2, topPos + DerrickMenu.REPLACE_Y - 2, 20, 20)
                && menu.getCarried().isEmpty() && !menu.slots.get(DerrickBlockEntity.OUTPUT_SLOTS).hasItem()) {
            hover = font.split(Component.translatable("derrick.orbitalis.replace.slot", DerrickBlockEntity.REPLACE_ENERGY), 220);
        } else if (Ui.inside(mouseX, mouseY, leftPos + 47, topPos + 77, 123, 21)) {
            String key = state().name().toLowerCase(Locale.ROOT);
            hover = font.split(state() == DerrickBlockEntity.Status.UNFORMED && status != null
                    ? Component.translatable("derrick.orbitalis.hint.unformed", status.missing())
                    : Component.translatable("derrick.orbitalis.hint." + key), 220);
        }
    }

    // ------------------------------------------------------------------
    // Les réglages
    // ------------------------------------------------------------------

    private void button(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, Component label, int accent, boolean enabled,
            Component detail, Runnable action) {
        button(graphics, mouseX, mouseY, x, y, w, label, accent, enabled, null, detail, action);
    }

    /** Un bouton ; {@code led} non nul, c'est une bascule, avec son voyant à droite, allumé ou éteint. */
    private void button(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, Component label, int accent, boolean enabled,
            Boolean led, Component detail, Runnable action) {
        boolean hovered = enabled && Ui.inside(mouseX, mouseY, x, y, w, 15);
        raised(graphics, x, y, w, 15, !enabled ? 0xFFA9A9A9 : hovered ? 0xFFD6DCE6 : 0xFFB5B5B5);
        rect(graphics, x + 2, y + 2, 2, 11, enabled ? accent : 0xFF8B8B8B);
        int room = w - 10;
        if (led != null) {
            room -= 8;
            inset(graphics, x + w - 10, y + 4, 7, 7, 0xFF2A2E2A);
            if (led) {
                rect(graphics, x + w - 9, y + 5, 5, 5, enabled ? 0xFF5BE37D : 0xFF6E8F76);
                rect(graphics, x + w - 9, y + 5, 2, 2, 0xFFD8FFE0);
            }
        }
        // Un petit bouton (moins, plus) garde son signe entier, centré à droite de sa bande de couleur.
        String text = w < 20 ? label.getString() : Ui.fit(font, label.getString(), room);
        int textX = w < 20 ? x + (w - font.width(text) + 4) / 2 : x + 7 + (room - font.width(text)) / 2;
        graphics.text(font, text, textX, y + 4, enabled ? 0xFF202020 : 0xFF6B6B6B, false);
        if (enabled) {
            zones.add(new Zone(x, y, w, 15, action));
        }
        if (detail != null && Ui.inside(mouseX, mouseY, x, y, w, 15)) {
            hover = font.split(detail, 220);
        }
    }

    private void extractSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y) {
        if (status == null) {
            return;
        }
        int w = SETTINGS_W;
        boolean formed = state() != DerrickBlockEntity.Status.UNFORMED;
        button(graphics, mouseX, mouseY, x, y, w, Component.translatable(status.running() ? "derrick.orbitalis.stop" : "derrick.orbitalis.start"),
                status.running() ? BAD : OK, formed, null, () -> send(DerrickBlockEntity.ACTION_RUN, 0));
        y += 18;
        boolean all = status.mode() == DerrickBlockEntity.MODE_ALL;
        button(graphics, mouseX, mouseY, x, y, w, Component.translatable(all ? "derrick.orbitalis.mode.all" : "derrick.orbitalis.mode.ores"),
                all ? WARN : 0xFF6FA8DC, true, Component.translatable(all ? "derrick.orbitalis.mode.all.detail" : "derrick.orbitalis.mode.ores.detail"),
                () -> send(DerrickBlockEntity.ACTION_MODE, 0));
        y += 18;
        // La taille : moins, le côté, plus.
        button(graphics, mouseX, mouseY, x, y, 15, Component.literal("-"), 0xFF6FA8DC, !status.chunk() && status.side() > 3, null,
                () -> send(DerrickBlockEntity.ACTION_SIDE, -1));
        String area = status.chunk() ? Component.translatable("derrick.orbitalis.area.chunk").getString()
                : Component.translatable("derrick.orbitalis.area", status.side(), status.side()).getString();
        area = Ui.fit(font, area, w - 34);
        graphics.text(font, area, x + (w - font.width(area)) / 2, y + 4, LABEL, false);
        button(graphics, mouseX, mouseY, x + w - 15, y, 15, Component.literal("+"), 0xFF6FA8DC, !status.chunk() && status.side() < status.maxSide(),
                null, () -> send(DerrickBlockEntity.ACTION_SIDE, 1));
        y += 18;
        boolean chunkPossible = status.maxSide() > DerrickBlockEntity.CHUNK;
        button(graphics, mouseX, mouseY, x, y, w, Component.translatable(status.chunk() ? "derrick.orbitalis.chunk.on" : "derrick.orbitalis.chunk.off"),
                status.chunk() ? OK : 0xFF8B8B8B, chunkPossible, status.chunk(), Component.translatable(chunkPossible ? "derrick.orbitalis.chunk.detail"
                        : "derrick.orbitalis.chunk.too_small"), () -> send(DerrickBlockEntity.ACTION_CHUNK, 0));
        y += 18;
        button(graphics, mouseX, mouseY, x, y, w, Component.translatable(status.replace() ? "derrick.orbitalis.replace.on" : "derrick.orbitalis.replace.off"),
                status.replace() ? OK : 0xFF8B8B8B, true, status.replace(), Component.translatable("derrick.orbitalis.replace.detail", DerrickBlockEntity.REPLACE_ENERGY),
                () -> send(DerrickBlockEntity.ACTION_REPLACE, 0));
        y += 18;
        boolean coring = status.core() >= 0;
        button(graphics, mouseX, mouseY, x, y, w, coring ? Component.literal(status.core() + " %") : Component.translatable("derrick.orbitalis.core"),
                0xFF4FC3F7, formed && !coring, Component.translatable("derrick.orbitalis.core.detail", DerrickBlockEntity.CORE_ENERGY,
                        DerrickBlockEntity.CORE_POINTS), () -> send(DerrickBlockEntity.ACTION_CORE, 0));
        y = topPos + CARD_Y + 4;
        // Ce que permet la tour, sur sa carte d'instrument : sa hauteur et l'échelle de ses étages, la zone, la profondeur
        // et la réserve, puis l'avancement du forage.
        String depth = status.depth() == 0 ? Component.translatable("derrick.orbitalis.depth.bottom").getString()
                : Component.translatable("derrick.orbitalis.depth", status.depth()).getString();
        String[] lines = {
                Component.translatable("derrick.orbitalis.tower", status.levels() + 1).getString(),
                Component.translatable("derrick.orbitalis.tower.max", status.maxSide(), status.maxSide()).getString(),
                depth,
                Component.translatable("derrick.orbitalis.stored.short", status.stored()).getString()};
        for (int k = 0; k < lines.length; k++) {
            graphics.text(font, Ui.fit(font, lines[k], w - 6), x + 3, y, k == 0 ? 0xFFE2C35A : 0xFFB8D8C0, false);
            y += k == 0 ? 9 : 10;
            if (k == 0) {
                // L'échelle des étages : un cran par étage de mâts possible, allumé s'il est bâti.
                int cell = (w - 6 - (DerrickPlan.MAX_LEVELS - 1)) / DerrickPlan.MAX_LEVELS;
                for (int level = 1; level <= DerrickPlan.MAX_LEVELS; level++) {
                    int cx = x + 3 + (level - 1) * (cell + 1);
                    boolean built = level <= status.levels();
                    rect(graphics, cx, y, cell, 3, built ? 0xFFE2B132 : 0xFF2E3A33);
                    if (built) {
                        rect(graphics, cx, y, cell, 1, 0xFFF4D77A);
                    }
                }
                y += 6;
            }
        }
        // L'avancement : de la surface au fond que permet la tour.
        int span = Math.max(1, status.top() - status.bottom());
        float done = status.running() || status.mined() > 0 ? Math.clamp((status.top() - status.layer()) / (float) span, 0.0F, 1.0F) : 0.0F;
        int barY = topPos + CARD_Y + CARD_H - 7;
        rect(graphics, x + 3, barY, w - 6, 3, 0xFF2E3A33);
        rect(graphics, x + 3, barY, Math.round((w - 6) * done), 3, 0xFF4FC3F7);
    }

    // ------------------------------------------------------------------
    // La carte et les sortes
    // ------------------------------------------------------------------

    private void extractMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y) {
        if (status == null) {
            return;
        }
        int width = status.surveyWidth();
        int[] cells = status.surveyCells();
        if (width <= 0 || cells.length % width != 0) {
            int textY = y + 4;
            for (String line : Ui.lines(font, Component.translatable("derrick.orbitalis.map.none.short"), MAP - 4, 6)) {
                graphics.text(font, line, x + 2, textY, IDLE, false);
                textY += 9;
            }
            if (Ui.inside(mouseX, mouseY, x, y, MAP, MAP)) {
                hover = font.split(Component.translatable("derrick.orbitalis.map.none"), 220);
            }
            return;
        }
        int depth = cells.length / width;
        int cell = Math.max(1, MAP / Math.max(width, depth));
        int ox = x + (MAP - cell * width) / 2;
        int oy = y + (MAP - cell * depth) / 2;
        for (int row = 0; row < depth; row++) {
            for (int column = 0; column < width; column++) {
                int kind = cells[row * width + column] - 1;
                int color = kind < 0 ? 0xFF2E3236 : QuarryKinds.color(kind);
                if (kind >= 0 && status.mode() == DerrickBlockEntity.MODE_ORES && (status.filter() & (1 << kind)) == 0) {
                    color = Ui.alpha(color, 0.3F);
                }
                graphics.fill(ox + column * cell, oy + row * cell, ox + (column + 1) * cell, oy + (row + 1) * cell, color);
            }
        }
        if (Ui.inside(mouseX, mouseY, x, y, MAP, MAP)) {
            hover = font.split(Component.translatable("derrick.orbitalis.map.detail", status.observers()), 220);
        }
    }

    private void extractKinds(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y) {
        if (status == null) {
            return;
        }
        boolean all = status.mode() == DerrickBlockEntity.MODE_ALL;
        int[] counts = status.surveyCounts();
        int[] bands = status.coreBands();
        for (int kind = 0; kind < QuarryKinds.COUNT; kind++) {
            boolean on = all || (status.filter() & (1 << kind)) != 0;
            boolean hovered = Ui.inside(mouseX, mouseY, x - 1, y - 1, KINDS_W + 2, ROW);
            if (hovered && !all) {
                rect(graphics, x - 1, y - 1, KINDS_W + 2, ROW, 0xFFB5B5B5);
            }
            int color = QuarryKinds.color(kind);
            inset(graphics, x, y, 7, 7, on ? color : SLOT);
            String value = counts.length > kind && counts[kind] > 0 ? Integer.toString(counts[kind]) : found(bands, kind) ? "*" : "";
            int valueWidth = font.width(value);
            graphics.text(font, value, x + KINDS_W - valueWidth, y, on ? LABEL : 0xFF8B8B8B, false);
            graphics.text(font, Ui.fit(font, Component.translatable(QuarryKinds.nameKey(kind)).getString(), KINDS_W - 13 - valueWidth), x + 10, y,
                    on ? LABEL : 0xFF8B8B8B, false);
            int kindIndex = kind;
            if (!all) {
                zones.add(new Zone(x - 1, y - 1, KINDS_W + 2, ROW, () -> send(DerrickBlockEntity.ACTION_FILTER, kindIndex)));
            }
            if (hovered) {
                hover = font.split(all ? Component.translatable("derrick.orbitalis.mode.all.detail") : depths(bands, kind), 220);
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
}
