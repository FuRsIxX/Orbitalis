package com.orbitalis.phase1.satellite;

import java.util.List;

import com.orbitalis.phase1.rocket.RocketStats;

/**
 * Les astres que les satellites de l'agence peuvent observer, du plus proche au plus lointain.
 * <p>
 * Chacun demande un instrument : les satellites d'observation, tournés vers la Terre, ne voient bien que le système
 * solaire proche ; le télescope spatial voit tout, et lui seul le ciel profond. Chacun demande aussi un temps
 * d'observation, et la découverte d'un astre en révèle d'autres (la Lune mène à Mars et à Vénus, Jupiter à ses lunes,
 * les Pléiades au ciel profond...).
 * <p>
 * Les planètes et les lunes ont une fiche de voyage : comment s'y rendre, ce qu'on y trouve, et ce qu'il faut pour y
 * aller. La Lune, Mars, Titan et Europe sont déjà des destinations ; les autres le deviendront.
 */
public enum CelestialBody {
    MOON(Kind.MOON, false, 1_200, 20, null,
            new Travel(0.384, false, 5_900, RocketStats.DELTA_V_LUNAR, 3, 0.165, "none", -20, RocketStats.Reach.LUNAR,
                    List.of("spacesuits", "lunar_lander", "life_support"))),
    COMET(Kind.COMET, false, 1_800, 30, "moon", null),
    MARS(Kind.PLANET, false, 2_400, 40, "moon",
            new Travel(225.0, false, 4_300, RocketStats.DELTA_V_MARS, 210, 0.38, "thin_co2", -60, RocketStats.Reach.MARS,
                    List.of("spacesuits", "mars_lander", "heat_shields", "life_support"))),
    VENUS(Kind.PLANET, false, 2_400, 40, "moon",
            new Travel(41.0, false, 3_500, 12_900, 150, 0.90, "dense_co2", 465, null, List.of("heat_shields", "landers", "closed_loop_life_support"))),
    MERCURY(Kind.PLANET, false, 3_600, 50, "venus",
            new Travel(77.0, false, 13_000, 22_000, 180, 0.38, "none", 167, null, List.of("heat_shields", "solar_arrays", "landers"))),
    JUPITER(Kind.GAS_GIANT, false, 4_800, 60, "mars",
            new Travel(628.0, false, 6_300, 16_000, 1_000, 2.53, "hydrogen", -110, null, List.of("rtg", "nuclear_thermal", "probes"))),
    EUROPA(Kind.MOON, true, 6_000, 80, "jupiter",
            new Travel(628.0, false, 8_800, 18_500, 1_050, 0.134, "none", -160, RocketStats.Reach.EUROPA,
                    List.of("spacesuits", "europa_lander", "nuclear_thermal", "cryobot"))),
    SATURN(Kind.GAS_GIANT, true, 6_000, 70, "jupiter",
            new Travel(1_275.0, false, 7_300, 17_200, 2_200, 1.07, "hydrogen", -140, null, List.of("rtg", "nuclear_thermal", "probes"))),
    TITAN(Kind.MOON, true, 7_200, 90, "saturn",
            new Travel(1_275.0, false, 7_600, 17_500, 2_300, 0.14, "nitrogen", -179, RocketStats.Reach.TITAN,
                    List.of("spacesuits", "titan_lander", "nuclear_thermal", "heat_shields"))),
    PLEIADES(Kind.CLUSTER, true, 3_600, 50, null, null),
    ORION_NEBULA(Kind.NEBULA, true, 4_800, 60, "pleiades", null),
    EAGLE_NEBULA(Kind.NEBULA, true, 6_000, 80, "orion_nebula", null),
    CRAB_PULSAR(Kind.PULSAR, true, 6_000, 90, "orion_nebula", null),
    ANDROMEDA(Kind.GALAXY, true, 7_200, 100, "pleiades", null),
    PROXIMA_B(Kind.EXOPLANET, true, 9_600, 150, "orion_nebula",
            new Travel(4.24, true, 0, 0, 0, 1.1, "unknown", -39, null, List.of("fusion", "warp_drive", "closed_loop_life_support"))),
    TRAPPIST_1E(Kind.EXOPLANET, true, 12_000, 180, "proxima_b",
            new Travel(39.5, true, 0, 0, 0, 0.93, "unknown", -22, null, List.of("warp_drive", "closed_loop_life_support", "isru"))),
    SAGITTARIUS_A(Kind.BLACK_HOLE, true, 14_400, 250, "andromeda", null);

    /** Nature d'un astre : elle dit comment il est montré et décrit. */
    public enum Kind {
        PLANET,
        MOON,
        GAS_GIANT,
        COMET,
        CLUSTER,
        NEBULA,
        PULSAR,
        GALAXY,
        EXOPLANET,
        BLACK_HOLE;

        public String nameKey() {
            return "observatory.orbitalis.kind." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * La fiche de voyage d'une planète ou d'une lune.
     *
     * @param distance        distance moyenne à la Terre, en millions de km, ou en années-lumière
     * @param lightYears      vrai si la distance est en années-lumière
     * @param deltaVFromOrbit delta-v depuis l'orbite basse jusqu'à la surface (ou l'orbite), en m/s ; 0 hors de portée
     * @param deltaVFromGround delta-v total depuis le sol, en m/s ; 0 hors de portée
     * @param transferDays    durée du voyage, en jours ; 0 hors de portée
     * @param gravity         pesanteur à la surface, en g
     * @param atmosphere      nature de l'atmosphère (clé de traduction)
     * @param temperature     température moyenne à la surface, en °C
     * @param reach           ce que le lanceur doit atteindre pour y aller, si c'est déjà une destination ; sinon {@code null}
     * @param equipment       technologies qu'il faut pour y aller et y survivre
     */
    public record Travel(double distance, boolean lightYears, int deltaVFromOrbit, double deltaVFromGround, int transferDays, double gravity,
            String atmosphere, int temperature, RocketStats.Reach reach, List<String> equipment) {
        /** Vrai si l'on peut déjà s'y rendre. */
        public boolean reachable() {
            return reach != null;
        }
    }

    private final Kind kind;
    private final boolean deepSky;
    private final int effort;
    private final int reward;
    private final String prerequisite;
    private final Travel travel;

    CelestialBody(Kind kind, boolean deepSky, int effort, int reward, String prerequisite, Travel travel) {
        this.kind = kind;
        this.deepSky = deepSky;
        this.effort = effort;
        this.reward = reward;
        this.prerequisite = prerequisite;
        this.travel = travel;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Kind kind() {
        return kind;
    }

    /** Vrai si seul le télescope spatial peut l'observer ; sinon, les satellites d'observation le peuvent aussi. */
    public boolean deepSky() {
        return deepSky;
    }

    /** Temps d'observation qu'il demande, en ticks, avec un seul instrument (un télescope compte triple). */
    public int effort() {
        return effort;
    }

    /** Points de recherche que rapporte son image, reçue au sol. */
    public int reward() {
        return reward;
    }

    /** L'astre dont la découverte révèle celui-ci, ou {@code null} s'il est connu d'emblée. */
    public CelestialBody prerequisite() {
        return prerequisite == null ? null : byId(prerequisite);
    }

    /** Sa fiche de voyage, ou {@code null} : on ne visite ni une nébuleuse ni un trou noir. */
    public Travel travel() {
        return travel;
    }

    public String nameKey() {
        return "observatory.orbitalis.body." + id();
    }

    public String descriptionKey() {
        return "observatory.orbitalis.body." + id() + ".desc";
    }

    public static CelestialBody byId(String id) {
        for (CelestialBody body : values()) {
            if (body.id().equals(id)) {
                return body;
            }
        }
        return null;
    }
}
