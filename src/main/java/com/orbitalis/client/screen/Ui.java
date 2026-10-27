package com.orbitalis.client.screen;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Le style commun des écrans d'Orbitalis : celui d'une salle de contrôle d'aujourd'hui.
 * <p>
 * Un fond presque noir, des filets fins plutôt que des cadres épais, beaucoup de vide, un texte clair, et la couleur
 * réservée à ce qui a un sens : vert « prêt », ambre « en cours », rouge « non ». Les titres de rubrique sont en
 * petites capitales grises, les valeurs importantes en grand. Tout reste dessiné au pixel, avec la police et les
 * objets du jeu : c'est une salle de contrôle, mais dans Minecraft.
 */
public final class Ui {
    /** Fond des panneaux, et fond un peu plus clair des cartes posées dessus. */
    public static final int BG = 0xF407090C;
    public static final int SURFACE = 0xFF0F1318;
    public static final int SURFACE_HI = 0xFF171C23;
    /** Filets et contours. */
    public static final int LINE = 0xFF232A33;
    public static final int LINE_HI = 0xFF3A4450;
    /** Texte : principal, secondaire, éteint. */
    public static final int TEXT = 0xFFF2F4F7;
    public static final int MUTED = 0xFF8A94A1;
    public static final int DIM = 0xFF4D5661;
    /** L'accent, et les trois couleurs d'état. */
    public static final int ACCENT = 0xFF7CC4FF;
    public static final int OK = 0xFF4ADE80;
    public static final int WARN = 0xFFFFC24A;
    public static final int BAD = 0xFFFF5D5D;

    private Ui() {
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Un texte ramené à la largeur qu'on lui laisse : entier s'il tient, sinon coupé et terminé par des points de
     * suspension. Aucun texte d'un écran ne doit déborder sur son voisin ; on ne change pas pour autant la taille
     * des caractères, qui reste la même partout.
     */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(0, width - font.width("…")));
        return cut.stripTrailing() + "…";
    }

    /**
     * Un texte mis en lignes, sur un nombre de lignes donné au plus : s'il en faudrait davantage, la dernière se
     * termine par des points de suspension.
     */
    public static java.util.List<String> lines(Font font, Component text, int width, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<net.minecraft.network.chat.FormattedText> split = font.getSplitter().splitLines(text, width, net.minecraft.network.chat.Style.EMPTY);
        for (int i = 0; i < split.size() && i < maxLines; i++) {
            String line = split.get(i).getString();
            if (i == maxLines - 1 && split.size() > maxLines) {
                line = fit(font, line + " " + split.get(i + 1).getString(), width);
            }
            lines.add(line);
        }
        return lines;
    }

    /** La même couleur, plus ou moins transparente. */
    public static int alpha(int color, float alpha) {
        return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /** Rectangle aux coins adoucis d'un pixel. */
    public static void round(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Contour fin aux coins adoucis. */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Le panneau d'un écran : fond sombre, contour fin. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        round(graphics, x, y, w, h, BG);
        frame(graphics, x, y, w, h, LINE);
    }

    /** Une carte posée sur le panneau. */
    public static void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean raised) {
        round(graphics, x, y, w, h, raised ? SURFACE_HI : SURFACE);
        frame(graphics, x, y, w, h, raised ? LINE_HI : LINE);
    }

    public static void hline(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, LINE);
    }

    /** Titre de rubrique : petites capitales grises. */
    public static void heading(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.text(font, text.getString().toUpperCase(java.util.Locale.ROOT), x, y, MUTED, false);
    }

    /** Texte agrandi (les grandes valeurs : l'horloge, l'altitude). */
    public static void big(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, float scale, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    /** Texte aligné à droite. */
    public static void right(GuiGraphicsExtractor graphics, Font font, Component text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    public static void right(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    /** Une ligne « nom ……… valeur ». */
    public static void row(GuiGraphicsExtractor graphics, Font font, Component name, String value, int x, int y, int w, int valueColor) {
        graphics.text(font, fit(font, name.getString(), w - font.width(value) - 6), x, y, MUTED, false);
        right(graphics, font, value, x + w, y, valueColor);
    }

    /** Largeur d'une étiquette d'état. */
    public static int pillWidth(Font font, String text) {
        return font.width(text) + 15;
    }

    /** Étiquette d'état : une pastille de couleur et un mot, dans un contour de la même couleur. */
    public static int pill(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        int w = pillWidth(font, text);
        round(graphics, x, y, w, 11, alpha(color, 0.14F));
        frame(graphics, x, y, w, 11, alpha(color, 0.55F));
        graphics.fill(x + 4, y + 4, x + 7, y + 7, color);
        graphics.text(font, text, x + 10, y + 2, color, false);
        return w;
    }

    /** La même, calée à droite. */
    public static void pillRight(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        pill(graphics, font, text, right - pillWidth(font, text), y, color);
    }

    /** Jauge fine. */
    public static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float share, int color) {
        graphics.fill(x, y, x + w, y + h, LINE);
        int filled = Math.round(w * Mth.clamp(share, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + h, color);
        }
    }

    /**
     * Bouton. Plein et clair quand c'est l'action principale, en simple contour sinon ; éteint s'il ne fait rien.
     *
     * @param color couleur de l'action (l'accent, ou le rouge d'un arrêt)
     */
    public static void button(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y, int w, int h, boolean hovered,
            boolean enabled, boolean primary, int color) {
        if (!enabled) {
            round(graphics, x, y, w, h, SURFACE);
            frame(graphics, x, y, w, h, LINE);
        } else if (primary) {
            round(graphics, x, y, w, h, hovered ? TEXT : color);
        } else {
            round(graphics, x, y, w, h, hovered ? alpha(color, 0.22F) : SURFACE);
            frame(graphics, x, y, w, h, hovered ? color : alpha(color, 0.6F));
        }
        String text = fit(font, label.getString().toUpperCase(java.util.Locale.ROOT), w - 4);
        graphics.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, !enabled ? DIM : primary ? 0xFF05070A : hovered ? TEXT : color, false);
    }

    /** Petite pastille ronde (5 pixels). */
    public static void dot(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x + 1, y, x + 4, y + 5, color);
        graphics.fill(x, y + 1, x + 5, y + 4, color);
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
