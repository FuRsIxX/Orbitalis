package com.orbitalis.client.screen;

import java.util.List;
import java.util.Locale;

import com.orbitalis.phase1.power.AccumulatorBlock;
import com.orbitalis.phase1.power.AccumulatorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran d'un accumulateur, dans le style des machines, sans rien à régler : à gauche la jauge, en crans de la couleur
 * de sa génération ; à droite l'état en pastille, une phrase en clair (plein dans, vide dans), la charge, l'entrée et la
 * sortie ; en bas, le rappel des faces. Le détail vient au survol.
 */
public class AccumulatorScreen extends AbstractContainerScreen<AccumulatorMenu> {
    private static final int FILL = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SCREEN = 0xFF18221C;
    private static final int LABEL = 0xFF404040;
    private static final int INPUT = 0xFF3F76E4;
    private static final int OUTPUT = 0xFFE8872A;

    private static final int WIDTH = 200;
    private static final int HEIGHT = 112;
    /** La jauge : dix crans de six pixels. */
    private static final int GAUGE_X = 8;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 30;
    private static final int STEPS = 10;
    private static final int STEP = 7;
    private static final int TEXT_X = 46;

    private List<FormattedCharSequence> hover;

    public AccumulatorScreen(AccumulatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
    }

    private static void inset(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int inner) {
        rect(graphics, x, y, w, h, inner);
        rect(graphics, x, y, w - 1, 1, DARK);
        rect(graphics, x, y, 1, h - 1, DARK);
        rect(graphics, x + 1, y + h - 1, w - 1, 1, WHITE);
        rect(graphics, x + w - 1, y + 1, 1, h - 1, WHITE);
    }

    /** Une quantité d'énergie, en k ou en M au-delà de mille. */
    static String amount(long value) {
        if (value >= 1_000_000) {
            return trim(String.format(Locale.ROOT, "%.2f", value / 1_000_000.0)) + " M";
        }
        if (value >= 10_000) {
            return trim(String.format(Locale.ROOT, "%.1f", value / 1_000.0)) + " k";
        }
        return Long.toString(value);
    }

    private static String trim(String number) {
        return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
    }

    /** Une durée en secondes, en clair. */
    private static Component duration(long seconds) {
        if (seconds < 60) {
            return Component.translatable("accumulator.orbitalis.time.seconds", seconds);
        }
        if (seconds < 3600) {
            return Component.translatable("accumulator.orbitalis.time.minutes", (seconds + 30) / 60);
        }
        return Component.translatable("accumulator.orbitalis.time.hours", trim(String.format(Locale.ROOT, "%.1f", seconds / 3600.0)));
    }

    private float ratio() {
        return menu.getEnergyCapacity() <= 0 ? 0 : Math.clamp(menu.getEnergy() / (float) menu.getEnergyCapacity(), 0.0F, 1.0F);
    }

    /** L'état : en charge, en décharge, plein, vide ou au repos ; sa couleur ; et la phrase qui l'accompagne. */
    private Object[] state() {
        int net = menu.averageIn() - menu.averageOut();
        long energy = menu.getEnergy();
        long capacity = menu.getEnergyCapacity();
        if (energy >= capacity && capacity > 0) {
            return new Object[] {"full", 0xFF4FC3F7, Component.translatable("accumulator.orbitalis.eta.steady")};
        }
        if (energy <= 0 && net <= 0) {
            return new Object[] {"empty", 0xFFFF6B6B, Component.translatable("accumulator.orbitalis.eta.steady")};
        }
        if (net > 0) {
            return new Object[] {"charging", 0xFF7CE08A,
                    Component.translatable("accumulator.orbitalis.eta.full", duration((capacity - energy) / net / 20 + 1))};
        }
        if (net < 0) {
            return new Object[] {"discharging", 0xFFFFC857,
                    Component.translatable("accumulator.orbitalis.eta.empty", duration(energy / -net / 20 + 1))};
        }
        return new Object[] {"idle", 0xFF9DB5A4, Component.translatable("accumulator.orbitalis.eta.steady")};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        rect(graphics, x + 1, y, WIDTH - 2, HEIGHT, 0xFF000000);
        rect(graphics, x, y + 1, WIDTH, HEIGHT - 2, 0xFF000000);
        rect(graphics, x + 1, y + 1, WIDTH - 2, HEIGHT - 2, SHADOW);
        rect(graphics, x + 1, y + 1, WIDTH - 4, HEIGHT - 4, WHITE);
        rect(graphics, x + 3, y + 3, WIDTH - 6, HEIGHT - 6, FILL);
        // Les crans de la génération, en haut à droite.
        AccumulatorBlock.Tier tier = menu.tier();
        for (int k = 0; k < AccumulatorBlock.Tier.values().length; k++) {
            inset(graphics, x + WIDTH - 12 - (3 - k) * 7, y + 6, 6, 6, k <= tier.ordinal() ? tier.color : 0xFF8B8B8B);
        }
        // La jauge : dix crans qui s'allument du bas vers le haut.
        inset(graphics, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, STEPS * STEP + 3, 0xFF222426);
        int lit = (int) Math.ceil(ratio() * STEPS - 1.0E-4);
        for (int k = 0; k < STEPS; k++) {
            int sy = y + GAUGE_Y + 2 + (STEPS - 1 - k) * STEP;
            boolean on = k < lit;
            rect(graphics, x + GAUGE_X + 2, sy, GAUGE_W - 4, STEP - 1, on ? tier.color : 0xFF2E3436);
            if (on) {
                rect(graphics, x + GAUGE_X + 2, sy, GAUGE_W - 4, 1, Ui.alpha(WHITE, 0.45F));
            }
        }
        // Le rappel des faces : un petit cube, l'avant orange, les flancs bleus.
        int cx = x + GAUGE_X;
        int cy = y + 98;
        rect(graphics, cx, cy, 6, 8, OUTPUT);
        rect(graphics, cx + 6, cy, 3, 8, INPUT);
        rect(graphics, cx, cy - 2, 6, 2, 0xFF7FA6F0);
        rect(graphics, cx + 6, cy - 2, 3, 2, 0xFF7FA6F0);
        // L'écran des échanges.
        inset(graphics, x + TEXT_X - 2, y + 52, WIDTH - TEXT_X - 6, 30, SCREEN);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Ui.fit(font, title.getString(), WIDTH - 46), 8, 6, LABEL, false);
        Object[] state = state();
        String name = Component.translatable("accumulator.orbitalis.state." + state[0]).getString();
        int room = WIDTH - TEXT_X - 6;
        Ui.pill(graphics, font, Ui.fit(font, name, room - 8), TEXT_X - 2, 18, (int) state[1]);
        graphics.text(font, Ui.fit(font, ((Component) state[2]).getString(), room), TEXT_X - 2, 31, LABEL, false);
        String stored = Component.translatable("accumulator.orbitalis.stored", amount(menu.getEnergy()), amount(menu.getEnergyCapacity())).getString();
        graphics.text(font, Ui.fit(font, stored + " · " + Math.round(ratio() * 100) + " %", room), TEXT_X - 2, 41, LABEL, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.input", amount(menu.averageIn())).getString(), room - 6),
                TEXT_X + 1, 56, 0xFF8FB4FF, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.output", amount(menu.averageOut())).getString(), room - 6),
                TEXT_X + 1, 69, 0xFFFFB46A, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.faces").getString(), WIDTH - GAUGE_X - 20), GAUGE_X + 13, 98,
                LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hover = null;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        AccumulatorBlock.Tier tier = menu.tier();
        if (Ui.inside(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, STEPS * STEP + 3)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.gauge.detail", menu.getEnergy(), menu.getEnergyCapacity(),
                    Math.round(ratio() * 100)), 200);
        } else if (Ui.inside(mouseX, mouseY, leftPos + TEXT_X - 2, topPos + 52, WIDTH - TEXT_X - 6, 30)
                || Ui.inside(mouseX, mouseY, leftPos + GAUGE_X, topPos + 95, WIDTH - 2 * GAUGE_X, 12)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.flow.detail", tier.transfer), 200);
        }
        if (hover != null) {
            graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
        }
    }
}
