package com.orbitalis.phase1.conduit;

import java.util.Locale;

import com.orbitalis.phase1.spaceport.FuelPortBlock;

import net.minecraft.network.chat.Component;

/**
 * Couleur d'un tuyau. Deux tuyaux de couleurs différentes ne se raccordent pas, même collés : chaque ergol garde sa
 * conduite jusqu'au pas de tir. Un tuyau sans couleur se raccorde à tous.
 * <p>
 * En jeu, la couleur teint les nœuds, les colliers et les arêtes du tube (leurs textures sont grises) ; sans couleur,
 * la teinte rend l'aspect d'origine : nœuds bleus pour les liquides, de laiton pour les gaz, arêtes sombres.
 */
public enum PipeColor {
    NONE(0),
    ORANGE(0xF08A24),
    LIGHT_BLUE(0x5FB0F5),
    WHITE(0xF2F2F2),
    GREEN(0x5BC236),
    PURPLE(0xA05BD6),
    BLUE(0x2F5FE0),
    YELLOW(0xF2D23A),
    RED(0xD8443A);

    /** Gris des textures teintées : nœuds et colliers, puis arêtes (voir tools/generate_conduit_models.py). */
    private static final int CORNER_GREY = 0xD0;
    private static final int RAIL_GREY = 0xC8;
    /** Aspect d'origine, sans couleur : nœuds des tuyaux à liquides, à gaz, et arêtes. */
    private static final int LIQUID_CORNER = 0x3F76E4;
    private static final int GAS_CORNER = 0xC9A227;
    private static final int RAIL = 0x4A515A;

    private final int rgb;

    PipeColor(int rgb) {
        this.rgb = rgb;
    }

    /** La couleur suivante, au clic droit ; après la dernière, le tuyau redevient sans couleur. */
    public PipeColor next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Vrai si deux tuyaux de ces couleurs se raccordent : même couleur, ou l'un des deux sans couleur. */
    public boolean joins(PipeColor other) {
        return this == NONE || other == NONE || this == other;
    }

    public Component displayName() {
        return Component.translatable("conduit.orbitalis.color." + name().toLowerCase(Locale.ROOT));
    }

    /** La couleur de la conduite d'un port de pas de tir, selon son ergol ; aucune pour l'électricité et les données. */
    public static PipeColor forPort(FuelPortBlock.Kind kind) {
        return switch (kind) {
            case RP1 -> ORANGE;
            case LOX -> LIGHT_BLUE;
            case LH2 -> WHITE;
            case CH4 -> GREEN;
            case HE3 -> PURPLE;
            case WATER -> BLUE;
            default -> NONE;
        };
    }

    /**
     * Teinte d'une face teintée, en ARGB.
     *
     * @param tint 0 pour les nœuds et les colliers, 1 pour les arêtes
     * @param gas  vrai pour un tuyau à gaz (nœuds de laiton sans couleur)
     */
    public int tint(int tint, boolean gas) {
        int grey = tint == 0 ? CORNER_GREY : RAIL_GREY;
        int target = this != NONE ? rgb : tint == 1 ? RAIL : gas ? GAS_CORNER : LIQUID_CORNER;
        int r = Math.min(255, ((target >> 16) & 0xFF) * 255 / grey);
        int g = Math.min(255, ((target >> 8) & 0xFF) * 255 / grey);
        int b = Math.min(255, (target & 0xFF) * 255 / grey);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
