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
 * Écran d'un accumulateur, dans le style salle de contrôle des écrans d'Orbitalis ({@link Ui}), sans rien à régler. En
 * tête, la génération et ses crans. À gauche, la jauge, en crans de la couleur de la génération. À droite, l'état en
 * pastille et une phrase en clair (plein dans, vide dans), la charge en grand, puis deux cartes, l'entrée et la sortie,
 * chacune avec sa jauge fine rapportée au débit de la génération. En pied, le rappel des faces. Le détail vient au
 * survol.
 */
public class AccumulatorScreen extends AbstractContainerScreen<AccumulatorMenu> {
    private static final int INPUT = 0xFF5B8CFF;
    private static final int OUTPUT = 0xFFFF9A3D;

    private static final int WIDTH = 224;
    private static final int HEIGHT = 140;
    private static final int PAD = 10;
    /** La jauge : dix crans de six pixels, deux d'écart. */
    private static final int GAUGE_X = PAD;
    private static final int GAUGE_Y = 28;
    private static final int GAUGE_W = 36;
    private static final int STEPS = 10;
    private static final int STEP = 8;
    private static final int GAUGE_H = STEPS * STEP + 6;
    /** La colonne de droite. */
    private static final int COL_X = GAUGE_X + GAUGE_W + 10;
    private static final int COL_W = WIDTH - COL_X - PAD;
    private static final int FLOW_Y = 78;
    private static final int FLOW_H = 16;

    private List<FormattedCharSequence> hover;

    public AccumulatorScreen(AccumulatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    /** Une quantité d'énergie, en k ou en M au-delà de dix mille. */
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

    private record State(String key, int color, Component sentence) {
    }

    /** L'état : en charge, en décharge, plein, vide ou au repos ; sa couleur ; et la phrase qui l'accompagne. */
    private State state() {
        int net = menu.averageIn() - menu.averageOut();
        long energy = menu.getEnergy();
        long capacity = menu.getEnergyCapacity();
        if (energy >= capacity && capacity > 0) {
            return new State("full", Ui.ACCENT, Component.translatable("accumulator.orbitalis.eta.steady"));
        }
        if (energy <= 0 && net <= 0) {
            return new State("empty", Ui.BAD, Component.translatable("accumulator.orbitalis.eta.steady"));
        }
        if (net > 0) {
            return new State("charging", Ui.OK, Component.translatable("accumulator.orbitalis.eta.full", duration((capacity - energy) / net / 20 + 1)));
        }
        if (net < 0) {
            return new State("discharging", Ui.WARN, Component.translatable("accumulator.orbitalis.eta.empty", duration(energy / -net / 20 + 1)));
        }
        return new State("idle", Ui.MUTED, Component.translatable("accumulator.orbitalis.eta.steady"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        AccumulatorBlock.Tier tier = menu.tier();
        Ui.panel(graphics, x, y, WIDTH, HEIGHT);
        // Un liseré de la couleur de la génération, en tête.
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 2, Ui.alpha(tier.color, 0.8F));
        Ui.hline(graphics, x + PAD, y + 21, WIDTH - 2 * PAD);
        // Les crans de la génération, en haut à droite.
        for (int k = 0; k < AccumulatorBlock.Tier.values().length; k++) {
            Ui.round(graphics, x + WIDTH - PAD - 4 * 9 + 2 + k * 9, y + 8, 7, 5, k <= tier.ordinal() ? tier.color : Ui.LINE_HI);
        }
        // La jauge : une carte, dix crans qui s'allument du bas vers le haut, le dernier allumé un peu plus clair.
        Ui.card(graphics, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, false);
        float ratio = ratio();
        int lit = (int) Math.ceil(ratio * STEPS - 1.0E-4);
        for (int k = 0; k < STEPS; k++) {
            int sy = y + GAUGE_Y + 3 + (STEPS - 1 - k) * STEP;
            boolean on = k < lit;
            int color = on ? tier.color : Ui.LINE;
            Ui.round(graphics, x + GAUGE_X + 4, sy, GAUGE_W - 8, STEP - 2, color);
            if (on && k == lit - 1) {
                graphics.fill(x + GAUGE_X + 5, sy, x + GAUGE_X + GAUGE_W - 5, sy + 1, Ui.alpha(0xFFFFFFFF, 0.5F));
            }
        }
        // Les deux cartes des échanges, chacune avec sa jauge fine.
        float transfer = Math.max(1, tier.transfer);
        flowCard(graphics, x + COL_X, y + FLOW_Y, INPUT, menu.averageIn() / transfer);
        flowCard(graphics, x + COL_X, y + FLOW_Y + FLOW_H + 4, OUTPUT, menu.averageOut() / transfer);
        // Le rappel des faces : un petit cube, la jauge à l'avant, la sortie orange au dos, les entrées bleues.
        int cx = x + PAD;
        int cy = y + HEIGHT - 14;
        Ui.round(graphics, cx, cy, 8, 8, Ui.alpha(tier.color, 0.85F));
        graphics.fill(cx + 8, cy + 1, cx + 11, cy + 8, INPUT);
        graphics.fill(cx + 1, cy - 3, cx + 11, cy, OUTPUT);
    }

    private static void flowCard(GuiGraphicsExtractor graphics, int x, int y, int color, float share) {
        Ui.card(graphics, x, y, COL_W, FLOW_H, false);
        graphics.fill(x + 1, y + 1, x + 3, y + FLOW_H - 1, color);
        Ui.bar(graphics, x + 7, y + FLOW_H - 4, COL_W - 12, 1, share, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        AccumulatorBlock.Tier tier = menu.tier();
        graphics.text(font, Ui.fit(font, title.getString(), WIDTH - 2 * PAD - 44), PAD, 8, Ui.TEXT, false);
        State state = state();
        String name = Ui.fit(font, Component.translatable("accumulator.orbitalis.state." + state.key()).getString(), COL_W - 16);
        Ui.pill(graphics, font, name, COL_X, GAUGE_Y, state.color());
        graphics.text(font, Ui.fit(font, state.sentence().getString(), COL_W), COL_X, GAUGE_Y + 15, Ui.MUTED, false);
        // La charge, en grand, et ce qu'elle représente.
        String percent = Math.round(ratio() * 100) + " %";
        Ui.big(graphics, font, percent, COL_X, GAUGE_Y + 29, 2.0F, tier.color);
        String stored = Component.translatable("accumulator.orbitalis.stored", amount(menu.getEnergy()), amount(menu.getEnergyCapacity())).getString();
        int room = COL_W - font.width(percent) * 2 - 6;
        Ui.right(graphics, font, Ui.fit(font, stored, room), COL_X + COL_W, GAUGE_Y + 37, Ui.MUTED);
        // L'entrée et la sortie.
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.input", amount(menu.averageIn())).getString(), COL_W - 12),
                COL_X + 7, FLOW_Y + 3, Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.output", amount(menu.averageOut())).getString(), COL_W - 12),
                COL_X + 7, FLOW_Y + FLOW_H + 7, Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.faces").getString(), WIDTH - 2 * PAD - 16), PAD + 16,
                HEIGHT - 13, Ui.MUTED, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hover = null;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        AccumulatorBlock.Tier tier = menu.tier();
        if (Ui.inside(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.gauge.detail", menu.getEnergy(), menu.getEnergyCapacity(),
                    Math.round(ratio() * 100)), 200);
        } else if (Ui.inside(mouseX, mouseY, leftPos + COL_X, topPos + FLOW_Y, COL_W, 2 * FLOW_H + 4)
                || Ui.inside(mouseX, mouseY, leftPos + PAD, topPos + HEIGHT - 18, WIDTH - 2 * PAD, 14)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.flow.detail", tier.transfer), 200);
        }
        if (hover != null) {
            graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
        }
    }
}
