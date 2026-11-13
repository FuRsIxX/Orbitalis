package com.orbitalis.phase1.rocket;

/**
 * Couple d'ergols brûlé par un moteur.
 * <p>
 * La densité moyenne du mélange détermine la masse d'ergols que contient un réservoir d'un volume donné :
 * à volume égal, l'hydrogène, très léger, pèse presque trois fois moins que le kérosène.
 */
public enum Propellant {
    /** Propergol solide : coulé dans le moteur lui-même, il ne demande aucun réservoir. */
    SOLID("solid", 0.0, 0.0),
    /** Kérosène RP-1 et oxygène liquide. */
    KEROLOX("kerolox", 1030.0, 2.56),
    /** Hydrogène et oxygène liquides. */
    HYDROLOX("hydrolox", 360.0, 6.0),
    /** Hydrogène liquide seul, que chauffe le réacteur d'un moteur nucléaire thermique : aucun oxydant. */
    HYDROGEN("hydrogen", 71.0, 0.0),
    /** Xénon d'un propulseur ionique : chargé à l'usine dans le réservoir du propulseur, comme un propergol. */
    XENON("xenon", 0.0, 0.0),
    /** La lumière du Soleil, qui pousse une voile : aucun ergol du tout. */
    SUNLIGHT("sunlight", 0.0, 0.0),
    /** Méthane et oxygène liquides (moteurs type Raptor) : plus dense que l'hydrolox, plus propre que le kérosène. */
    METHALOX("methalox", 830.0, 3.6),
    /**
     * Fusion : un plasma de deutérium et d'hélium-3 chauffe de l'hydrogène, éjecté comme masse de réaction. Les réservoirs
     * ne gardent que l'hydrogène ; l'hélium-3, brûlé en quantités infimes, se charge à part (voir {@link RocketFuel}).
     */
    FUSION("fusion", 71.0, 0.0);

    private final String id;
    private final double density;
    private final double oxidizerRatio;

    /**
     * @param density       densité moyenne du mélange, en kg/m³
     * @param oxidizerRatio masse d'oxygène brûlée pour 1 kg de carburant
     */
    Propellant(String id, double density, double oxidizerRatio) {
        this.id = id;
        this.density = density;
        this.oxidizerRatio = oxidizerRatio;
    }

    public String id() {
        return id;
    }

    public boolean isSolid() {
        return this == SOLID;
    }

    /** Vrai si rien n'est à remplir au pas de tir : propergol coulé, xénon chargé à l'usine, ou pas d'ergol du tout. */
    public boolean isPreloaded() {
        return this == SOLID || this == XENON || this == SUNLIGHT;
    }

    /**
     * Vrai pour une propulsion à très faible poussée : le propulseur ionique et la voile. Elle ne sert qu'une fois en
     * orbite, où elle accélère des mois durant ; elle ne décolle pas, et ne convient pas à un équipage.
     */
    public boolean isLowThrust() {
        return this == XENON || this == SUNLIGHT;
    }

    /** Densité moyenne du mélange, en kg/m³. */
    public double density() {
        return density;
    }

    /** Part de carburant (RP-1, hydrogène ou méthane) dans la masse d'ergols. */
    public double fuelFraction() {
        return isPreloaded() ? 1.0 : 1.0 / (1.0 + oxidizerRatio);
    }

    /** Part d'oxygène liquide dans la masse d'ergols. */
    public double oxidizerFraction() {
        return 1.0 - fuelFraction();
    }

    public String nameKey() {
        return "rocket.orbitalis.propellant." + id;
    }
}
