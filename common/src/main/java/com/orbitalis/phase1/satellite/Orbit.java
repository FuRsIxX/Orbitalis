package com.orbitalis.phase1.satellite;

import java.util.Locale;

import com.orbitalis.phase1.rocket.RocketStats;

/**
 * Orbites de travail des satellites, avec leurs valeurs réelles.
 * <p>
 * Le lanceur n'a pas à circulariser lui-même les orbites hautes : comme dans la réalité, il dépose le satellite
 * sur une orbite de transfert, et c'est le moteur d'apogée du satellite qui termine le trajet. Il suffit donc
 * d'atteindre l'orbite de transfert géostationnaire pour viser l'orbite moyenne ou l'orbite géostationnaire.
 */
public enum Orbit {
    /** Orbite basse, peu inclinée : celle des premiers satellites et des télescopes. */
    LOW(400, 92.6, RocketStats.Reach.LOW_ORBIT, RocketStats.DELTA_V_LOW_ORBIT),
    /** Orbite polaire héliosynchrone : le satellite survole toute la planète, toujours à la même heure locale. */
    POLAR(800, 100.9, RocketStats.Reach.LOW_ORBIT, RocketStats.DELTA_V_LOW_ORBIT),
    /** Orbite moyenne : celle des constellations de navigation. */
    MEDIUM(20_200, 718.0, RocketStats.Reach.GEO_TRANSFER, RocketStats.DELTA_V_GEO_TRANSFER),
    /** Orbite géostationnaire : le satellite reste au-dessus du même point de l'équateur. */
    GEOSTATIONARY(35_786, 1436.1, RocketStats.Reach.GEO_TRANSFER, RocketStats.DELTA_V_GEO_TRANSFER),
    /** Orbite lunaire : un grand halo autour de la Lune, comme celui du Lunar Gateway, à la distance de la Lune. */
    LUNAR(384_400, 9360.0, RocketStats.Reach.LUNAR, RocketStats.DELTA_V_LUNAR);

    private final int altitudeKm;
    private final double periodMinutes;
    private final RocketStats.Reach requiredReach;
    private final double requiredDeltaV;

    Orbit(int altitudeKm, double periodMinutes, RocketStats.Reach requiredReach, double requiredDeltaV) {
        this.altitudeKm = altitudeKm;
        this.periodMinutes = periodMinutes;
        this.requiredReach = requiredReach;
        this.requiredDeltaV = requiredDeltaV;
    }

    /** Altitude, en km. */
    public int altitudeKm() {
        return altitudeKm;
    }

    /** Durée d'un tour de la planète, en minutes. */
    public double periodMinutes() {
        return periodMinutes;
    }

    /** Ce que le lanceur doit pouvoir atteindre pour y déposer un satellite. */
    public RocketStats.Reach requiredReach() {
        return requiredReach;
    }

    /** Delta-v que le lanceur doit fournir depuis le sol, en m/s. */
    public double requiredDeltaV() {
        return requiredDeltaV;
    }

    /** Vrai si un lanceur capable d'atteindre {@code reach} peut desservir cette orbite. */
    public boolean isReachedBy(RocketStats.Reach reach) {
        return reach.ordinal() >= requiredReach.ordinal();
    }

    public String nameKey() {
        return "satellite.orbitalis.orbit." + name().toLowerCase(Locale.ROOT);
    }
}
