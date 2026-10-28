package com.orbitalis.client.screen;

import java.util.Locale;

import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.network.LaunchPadInfoPayload;
import com.orbitalis.phase1.rocket.RocketFuel;
import com.orbitalis.phase1.rocket.RocketItem;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.LaunchComplex;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran d'informations d'un pas de tir, ouvert par un clic droit sur la structure formée (table, dalle ou fusée).
 * <p>
 * À gauche, le pas de tir : sa taille, la masse qu'il supporte, la tour de service et les bras ombilicaux.
 * À droite, la fusée posée dessus : ce qu'elle peut atteindre, sa masse, sa hauteur, le delta-v de chaque étage
 * et le chargement de ses ergols. L'écran montre l'état relevé à son ouverture.
 */
public class LaunchPadScreen extends Screen implements BlockScreen {
    private static final int COLOR_TEXT = Ui.TEXT;
    private static final int COLOR_MUTED = Ui.MUTED;
    private static final int COLOR_OK = Ui.OK;
    private static final int COLOR_BAD = Ui.BAD;

    private static final int WIDTH = 360;
    private static final int HEIGHT = 236;
    private static final int COLUMN_GAP = 8;
    private static final int LINE_HEIGHT = 12;

    private final LaunchPadInfoPayload info;
    /** Zone du bouton « Copier le plan » : x, y, largeur, hauteur ; {@code null} sans fusée. */
    private int[] copyButton;
    private int left;
    private int top;

    public LaunchPadScreen(LaunchPadInfoPayload info) {
        super(Component.translatable("spaceport.orbitalis.pad.title"));
        this.info = info;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 10, COLOR_TEXT, false);
        // L'état du pas de tir, d'un coup d'œil, en haut à droite.
        boolean ready = info.commissioned() && info.radius() > 0;
        String state = Component.translatable(ready ? "spaceport.orbitalis.pad.operational" : "spaceport.orbitalis.pad.incomplete")
                .getString().toUpperCase(Locale.ROOT);
        Ui.pillRight(graphics, font, state, left + WIDTH - 12, top + 9, ready ? COLOR_OK : COLOR_BAD);
        // Une fusée dressée : son plan se copie (pour refaire ailleurs une fusée bâtie librement, par exemple).
        copyButton = null;
        if (!info.rocket().isEmpty()) {
            String label = Component.translatable("spaceport.orbitalis.pad.copy_plan").getString();
            // Le bouton écrit en capitales : sa largeur se mesure sur elles.
            int w = font.width(label.toUpperCase(Locale.ROOT)) + 14;
            int bx = left + WIDTH - 12 - Ui.pillWidth(font, state) - 6 - w;
            copyButton = new int[] {bx, top + 7, w, 14};
            boolean hovered = mouseX >= bx && mouseX < bx + w && mouseY >= top + 7 && mouseY < top + 21;
            Ui.button(graphics, font, Component.literal(label), bx, top + 7, w, 14, hovered, true, false, COLOR_MUTED);
        }
        Ui.hline(graphics, left + 1, top + 24, WIDTH - 2);

        int columnWidth = (WIDTH - 20 - COLUMN_GAP) / 2;
        int columnTop = top + 30;
        int columnHeight = HEIGHT - 40;
        int padX = left + 10;
        int rocketX = padX + columnWidth + COLUMN_GAP;
        Ui.card(graphics, padX, columnTop, columnWidth, columnHeight, false);
        Ui.card(graphics, rocketX, columnTop, columnWidth, columnHeight, false);

        extractPad(graphics, padX + 7, columnTop + 7, columnWidth - 14);
        extractRocket(graphics, rocketX + 7, columnTop + 7, columnWidth - 14);
    }

    // ------------------------------------------------------------------
    // Pas de tir
    // ------------------------------------------------------------------

    private void extractPad(GuiGraphicsExtractor graphics, int x, int y, int columnWidth) {
        LaunchComplex complex = new LaunchComplex(info.radius(), 0, info.skyClear(), info.towerHeight(), info.arms(),
                info.hasDeflector(), info.hasTrench(), info.mastHeight(), null, java.util.List.of(), 0, info.compact());
        heading(graphics, x, y, "spaceport.orbitalis.pad.heading_pad");
        y += LINE_HEIGHT + 4;

        boolean operational = info.commissioned() && complex.isComplete();
        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.state", Component.translatable(operational
                ? "spaceport.orbitalis.pad.operational" : "spaceport.orbitalis.pad.incomplete"), operational ? COLOR_OK : COLOR_BAD);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.size",
                Component.literal(complex.padSize() + " × " + complex.padSize()), COLOR_TEXT);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.max_mass", Component.literal(formatMass(complex.maxRocketMass())), COLOR_TEXT);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.sky", Component.translatable(info.skyClear()
                ? "spaceport.orbitalis.pad.sky_clear" : "spaceport.orbitalis.pad.sky_blocked"), info.skyClear() ? COLOR_OK : COLOR_BAD);
        y += LINE_HEIGHT;

        // Équipements exigés à partir de 5×5 : sans eux, le tablier ne supporte que les fusées d'un 3×3.
        if (info.radius() >= 1) {
            y = equipmentRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.deflector", info.hasDeflector());
            y = equipmentRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.trench", info.hasTrench());
            // Le pas de tir d'essai n'a pas de mâts.
            if (!info.compact()) {
                row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.masts",
                        Component.translatable("spaceport.orbitalis.pad.mast_height", info.mastHeight(), LaunchComplex.requiredMastHeight(info.radius())),
                        info.mastHeight() >= LaunchComplex.requiredMastHeight(info.radius()) ? COLOR_OK : COLOR_BAD);
                y += LINE_HEIGHT;
            }
        }
        y += 6;

        heading(graphics, x, y, "spaceport.orbitalis.pad.heading_tower");
        y += LINE_HEIGHT + 4;
        if (info.towerHeight() == 0) {
            graphics.text(font, Component.translatable("spaceport.orbitalis.pad.no_tower"), x, y, COLOR_MUTED, false);
            y += LINE_HEIGHT;
        } else {
            boolean tallEnough = info.towerAuthorized() && info.towerHeight() >= info.requiredTower();
            row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.tower_height",
                    Component.translatable("spaceport.orbitalis.pad.blocks", info.towerHeight()), tallEnough ? COLOR_OK : COLOR_BAD);
            y += LINE_HEIGHT;
            row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.arms", Component.literal(Integer.toString(info.arms())), COLOR_TEXT);
            y += LINE_HEIGHT;
            if (!info.towerAuthorized()) {
                graphics.text(font, Component.translatable("spaceport.orbitalis.console.tower_locked"), x, y, COLOR_BAD, false);
                y += LINE_HEIGHT;
            }
        }
        if (info.requiredTower() > 0) {
            row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.tower_required",
                    Component.translatable("spaceport.orbitalis.pad.blocks", info.requiredTower()), COLOR_TEXT);
        }
    }

    // ------------------------------------------------------------------
    // Fusée
    // ------------------------------------------------------------------

    private void extractRocket(GuiGraphicsExtractor graphics, int x, int y, int columnWidth) {
        heading(graphics, x, y, "spaceport.orbitalis.pad.heading_rocket");
        y += LINE_HEIGHT + 4;
        if (info.rocket().isEmpty()) {
            for (var line : font.split(Component.translatable("spaceport.orbitalis.pad.no_rocket"), columnWidth)) {
                graphics.text(font, line, x, y, COLOR_MUTED, false);
                y += LINE_HEIGHT;
            }
            return;
        }

        RocketStats stats = RocketStats.of(RocketItem.design(info.rocket()));
        graphics.item(info.rocket(), x, y - 2);
        graphics.text(font, Ui.fit(font, info.rocket().getHoverName().getString(), columnWidth - 20), x + 20, y + 2, COLOR_TEXT, false);
        y += LINE_HEIGHT + 6;

        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.reach", Component.translatable(stats.reach().nameKey()), COLOR_OK);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "rocket.orbitalis.mass", Component.literal(formatMass(stats.liftoffMass())), COLOR_TEXT);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.height",
                Component.translatable("spaceport.orbitalis.pad.blocks", info.rocketHeight()), COLOR_TEXT);
        y += LINE_HEIGHT;
        row(graphics, x, y, columnWidth, "rocket.orbitalis.delta_v", Component.literal(formatSpeed(stats.totalDeltaV())), COLOR_TEXT);
        y += LINE_HEIGHT;
        // Une ligne par étage : la phase des boosters et celle du premier étage seul se cumulent.
        int lastNumber = -1;
        for (RocketStats.StageStats stage : stats.stages()) {
            if (stage.number() == lastNumber) {
                continue;
            }
            lastNumber = stage.number();
            int number = stage.number();
            graphics.text(font, Component.translatable("rocket.orbitalis.stage_twr", number,
                    String.format(Locale.ROOT, "%.2f", stage.twr())), x, y, COLOR_MUTED, false);
            String deltaV = formatSpeed(stats.stages().stream().filter(candidate -> candidate.number() == number)
                    .mapToDouble(RocketStats.StageStats::deltaV).sum());
            graphics.text(font, deltaV, x + columnWidth - font.width(deltaV), y, COLOR_TEXT, false);
            y += LINE_HEIGHT;
        }
        y += 4;

        // Ergols : ce qui est chargé sur ce qu'il faut. Une fusée à poudre n'a rien à charger.
        RocketFuel required = RocketFuel.required(stats);
        if (required.isEmpty()) {
            graphics.text(font, Component.translatable("spaceport.orbitalis.console.no_fuel_needed"), x, y, COLOR_MUTED, false);
            return;
        }
        y = fuelRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.rp1", info.rp1(), required.rp1());
        y = fuelRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.oxygen", info.oxygen(), required.oxygen());
        y = fuelRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.hydrogen", info.hydrogen(), required.hydrogen());
        y = fuelRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.methane", info.methane(), required.methane());
        fuelRow(graphics, x, y, columnWidth, "spaceport.orbitalis.pad.helium3", info.helium3(), required.helium3());
    }

    /** Une ligne d'équipement du pas de tir : en place ou manquant. Renvoie la hauteur de la ligne suivante. */
    private int equipmentRow(GuiGraphicsExtractor graphics, int x, int y, int columnWidth, String labelKey, boolean present) {
        Component state = Component.translatable(present ? "spaceport.orbitalis.pad.present" : "spaceport.orbitalis.pad.missing");
        graphics.text(font, Ui.fit(font, Component.translatable(labelKey).getString(), columnWidth - font.width(state) - 16), x, y, COLOR_MUTED, false);
        Ui.dot(graphics, x + columnWidth - 5, y + 1, present ? COLOR_OK : COLOR_BAD);
        Ui.right(graphics, font, state, x + columnWidth - 9, y, present ? COLOR_OK : COLOR_BAD);
        return y + LINE_HEIGHT;
    }

    /** Une ligne d'ergol, affichée seulement si la fusée en a besoin. Renvoie la hauteur de la ligne suivante. */
    private int fuelRow(GuiGraphicsExtractor graphics, int x, int y, int columnWidth, String labelKey, int loaded, int required) {
        if (required <= 0) {
            return y;
        }
        row(graphics, x, y, columnWidth, labelKey, Component.literal(grouped(loaded) + " / " + grouped(required) + " mB"),
                loaded >= required ? COLOR_OK : COLOR_BAD);
        // Le plein, en jauge, sous la ligne.
        Ui.bar(graphics, x, y + 10, columnWidth, 1, loaded / (float) required, loaded >= required ? COLOR_OK : Ui.WARN);
        return y + LINE_HEIGHT + 3;
    }

    // ------------------------------------------------------------------
    // Mise en forme
    // ------------------------------------------------------------------

    private void heading(GuiGraphicsExtractor graphics, int x, int y, String key) {
        // Titre de rubrique : petites capitales grises, comme sur tous les écrans du mod.
        Ui.heading(graphics, font, Component.translatable(key), x, y);
    }

    /** Une ligne : le libellé à gauche, la valeur alignée à droite. */
    private void row(GuiGraphicsExtractor graphics, int x, int y, int columnWidth, String labelKey, Component value, int valueColor) {
        String shown = Ui.fit(font, value.getString(), columnWidth - 30);
        graphics.text(font, Ui.fit(font, Component.translatable(labelKey).getString(), columnWidth - font.width(shown) - 6), x, y, COLOR_MUTED, false);
        graphics.text(font, shown, x + columnWidth - font.width(shown), y, valueColor, false);
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

    private static String formatSpeed(double metersPerSecond) {
        return metric() ? grouped(metersPerSecond) + " m/s" : grouped(metersPerSecond * 3.28084) + " ft/s";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (copyButton != null && event.x() >= copyButton[0] && event.x() < copyButton[0] + copyButton[2]
                && event.y() >= copyButton[1] && event.y() < copyButton[1] + copyButton[3]) {
            Ui.click();
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new com.orbitalis.core.network.BlueprintCopyPayload());
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
