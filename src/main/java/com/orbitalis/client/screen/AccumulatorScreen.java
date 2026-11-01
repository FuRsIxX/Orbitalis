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
 * Écran d'un accumulateur, épuré, dans le style salle de contrôle des écrans d'Orbitalis ({@link Ui}) : le nom et les
 * crans de la génération ; la charge en grand et ce qu'elle représente ; une barre de charge sur toute la largeur ; une
 * ligne d'état (pastille, mot, phrase en clair) ; l'entrée et la sortie côte à côte. Rien à régler, et le détail (les
 * faces, le débit) vient au survol.
 */
public class AccumulatorScreen extends AbstractContainerScreen<AccumulatorMenu> {
    private static final int INPUT = 0xFF5B8CFF;
    private static final int OUTPUT = 0xFFFF9A3D;

    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int PAD = 10;
    private static final int INNER = WIDTH - 2 * PAD;
    private static final int BIG_Y = 30;
    private static final int BAR_Y = 52;
    private static final int BAR_H = 8;
    private static final int STATE_Y = 66;
    private static final int FLOW_Y = 84;

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
        // Les crans de la génération, en haut à droite, puis un filet sous l'en-tête.
        for (int k = 0; k < AccumulatorBlock.Tier.values().length; k++) {
            Ui.round(graphics, x + WIDTH - PAD - 4 * 8 + 1 + k * 8, y + 9, 6, 4, k <= tier.ordinal() ? tier.color : Ui.LINE_HI);
        }
        Ui.hline(graphics, x + PAD, y + 21, INNER);
        // La barre de charge, sur toute la largeur, graduée tous les dixièmes.
        Ui.round(graphics, x + PAD, y + BAR_Y, INNER, BAR_H, Ui.LINE);
        int filled = Math.round(INNER * ratio());
        if (filled > 1) {
            Ui.round(graphics, x + PAD, y + BAR_Y, filled, BAR_H, tier.color);
            graphics.fill(x + PAD + 1, y + BAR_Y + 1, x + PAD + filled - 1, y + BAR_Y + 2, Ui.alpha(0xFFFFFFFF, 0.35F));
        }
        for (int k = 1; k < 10; k++) {
            int tx = x + PAD + INNER * k / 10;
            graphics.fill(tx, y + BAR_Y, tx + 1, y + BAR_Y + BAR_H, Ui.alpha(0xFF000000, 0.35F));
        }
        // L'entrée et la sortie : une pastille de leur couleur.
        Ui.dot(graphics, x + PAD, y + FLOW_Y + 1, INPUT);
        Ui.dot(graphics, x + PAD + INNER / 2 + 4, y + FLOW_Y + 1, OUTPUT);
        Ui.hline(graphics, x + PAD, y + FLOW_Y - 6, INNER);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        AccumulatorBlock.Tier tier = menu.tier();
        graphics.text(font, Ui.fit(font, title.getString(), INNER - 40), PAD, 8, Ui.TEXT, false);
        // La charge, en grand, et ce qu'elle représente, à droite.
        String percent = Math.round(ratio() * 100) + " %";
        Ui.big(graphics, font, percent, PAD, BIG_Y, 2.0F, tier.color);
        String stored = Component.translatable("accumulator.orbitalis.stored", amount(menu.getEnergy()), amount(menu.getEnergyCapacity())).getString();
        Ui.right(graphics, font, Ui.fit(font, stored, INNER - font.width(percent) * 2 - 8), PAD + INNER, BIG_Y + 8, Ui.MUTED);
        // L'état : une pastille, le mot, puis la phrase en clair.
        State state = state();
        Ui.dot(graphics, PAD, STATE_Y + 1, state.color());
        String name = Component.translatable("accumulator.orbitalis.state." + state.key()).getString();
        graphics.text(font, name, PAD + 9, STATE_Y, state.color(), false);
        int after = PAD + 9 + font.width(name) + 6;
        graphics.text(font, Ui.fit(font, state.sentence().getString(), PAD + INNER - after), after, STATE_Y, Ui.MUTED, false);
        // L'entrée et la sortie, côte à côte.
        int half = INNER / 2 - 12;
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.input", amount(menu.averageIn())).getString(), half),
                PAD + 9, FLOW_Y, Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, Component.translatable("accumulator.orbitalis.output", amount(menu.averageOut())).getString(), half),
                PAD + INNER / 2 + 13, FLOW_Y, Ui.TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hover = null;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        AccumulatorBlock.Tier tier = menu.tier();
        if (Ui.inside(mouseX, mouseY, leftPos + PAD, topPos + BIG_Y, INNER, BAR_Y + BAR_H - BIG_Y)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.gauge.detail", menu.getEnergy(), menu.getEnergyCapacity(),
                    Math.round(ratio() * 100)), 200);
        } else if (Ui.inside(mouseX, mouseY, leftPos + PAD, topPos + FLOW_Y - 4, INNER, 14)) {
            hover = font.split(Component.translatable("accumulator.orbitalis.faces").copy().append(". ")
                    .append(Component.translatable("accumulator.orbitalis.flow.detail", tier.transfer)), 200);
        }
        if (hover != null) {
            graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
        }
    }
}
