package com.orbitalis.phase1.spaceport;

import com.orbitalis.phase1.rocket.RocketStats;

/**
 * Profil d'un vol : altitude, vitesse, distance parcourue, accélération et étage allumé en fonction de
 * l'avancement du vol (de 0 à 1). Il vient d'un vrai calcul du vol, pas à pas (voir {@link FlightSimulation}).
 * <p>
 * Le serveur s'en sert pour la télémétrie du pupitre et les annonces ; les clients, pour tracer la trajectoire en
 * temps réel sur les écrans. Les deux font le même calcul à partir de la même fusée : ils voient le même vol.
 * <p>
 * Le vol calculé dure plusieurs minutes ; en jeu, il tient en une demi-minute à une minute, selon la mission (voir
 * {@link #flightTicks}). Il n'est pas accéléré d'un seul
 * tenant : tant que l'on voit la fusée monter au-dessus de son pas de tir, il avance exactement au rythme qui lui
 * donne l'altitude où on la voit (un bloc valant {@code 1000 / BLOCKS_PER_KM} mètres) ; une fois qu'elle a disparu
 * dans le ciel, il accélère pour finir à temps ({@link #simProgress}). Ce que disent les écrans colle ainsi à ce
 * que l'on voit : une fusée qui vient de décoller n'est pas déjà dans l'espace.
 * <p>
 * Partout ici, « avancement » désigne l'avancement du vol en jeu (de 0 à 1, au fil des ticks). L'horloge de mission
 * compte les secondes du jeu, celles que le joueur vit.
 */
public final class FlightProfile {
    /** Altitude d'une orbite basse, en km. */
    public static final double ORBIT_ALTITUDE_KM = 200.0;
    /** Vitesse sur une orbite basse, en m/s. */
    public static final double ORBIT_SPEED = 7_800.0;

    // Vols calculés : la même fusée est interrogée des dizaines de fois par seconde, par le serveur et par chaque écran.
    // En partie solo, le serveur et le client en ont chacun leur copie, identique mais distincte : le cache les reconnaît
    // à leur contenu, et en garde plusieurs. Sans cela, ils se le disputaient, et le vol était recalculé sans cesse,
    // assez pour ralentir le serveur pendant tout le vol.
    private static final int CACHED = 16;
    private static final java.util.Map<RocketStats, FlightSimulation> FLIGHTS = new java.util.LinkedHashMap<>(CACHED, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<RocketStats, FlightSimulation> eldest) {
            return size() > CACHED;
        }
    };
    private static final java.util.Map<FlightSimulation, double[]> WARPS = new java.util.WeakHashMap<>();
    private static RocketStats lastStats;
    private static FlightSimulation lastFlight;

    private FlightProfile() {
    }

    /** Vol de cette fusée ; il n'est calculé qu'une fois tant qu'on interroge la même fusée. */
    public static synchronized FlightSimulation flight(RocketStats stats) {
        if (stats != lastStats || lastFlight == null) {
            lastFlight = FLIGHTS.computeIfAbsent(stats, FlightSimulation::of);
            lastStats = stats;
        }
        return lastFlight;
    }

    public static boolean isOrbital(RocketStats stats) {
        return stats.reach().ordinal() >= RocketStats.Reach.LOW_ORBIT.ordinal();
    }

    /** Altitude la plus haute du vol : celle de la mise en orbite, ou le sommet de la trajectoire, en km. */
    public static double targetAltitudeKm(RocketStats stats) {
        return Math.max(1.0, flight(stats).maxAltitude() / 1000.0);
    }

    /** Durée d'un vol en jeu, au plus court et au plus long, en ticks. */
    public static final int MIN_FLIGHT_TICKS = 20 * 20;
    public static final int MAX_FLIGHT_TICKS = 50 * 20;

    /** Échelle du monde : nombre de blocs que vaut un kilomètre d'altitude, pour la fusée que l'on voit monter. */
    public static final double BLOCKS_PER_KM = 16.0;

    // Dernière table calculée : où en est le vol calculé à chaque tick du vol en jeu.

    /**
     * Durée du vol en jeu de cette fusée, en ticks. La montée que l'on voit (une vingtaine de secondes) prend toujours
     * le temps qu'il lui faut ; la suite, hors de vue, dure selon la mission : une dizaine de secondes pour une
     * fusée-sonde, un quart de minute pour l'orbite basse, une vingtaine de secondes pour les planètes lointaines, et
     * deux secondes de plus par étage, le temps de chaque séparation. Un vol dure ainsi de 20 à 50 secondes.
     */
    public static synchronized int flightTicks(RocketStats stats) {
        return warp(stats).length - 1;
    }

    /** Durée de la suite du vol, une fois la fusée hors de vue, en secondes de jeu. */
    private static double afterSightSeconds(RocketStats stats) {
        double base = switch (stats.reach()) {
            case NONE, ATMOSPHERIC -> 6.0;
            case SUBORBITAL -> 10.0;
            case LOW_ORBIT -> 14.0;
            case GEO_TRANSFER, LUNAR -> 17.0;
            case VENUS, MARS, GEOSTATIONARY -> 19.0;
            default -> 21.0;
        };
        return base + 2.0 * Math.max(0, stats.stages().size() - 1);
    }

    /**
     * Table du vol : pour chaque tick du vol en jeu, où en est le vol calculé (de 0 à 1).
     * <ol>
     * <li>Tant que la fusée est en vue, elle monte dans le monde comme {@code ASCENT_RATE × t²} blocs : le vol
     * calculé est pris à l'instant où il a cette altitude. Une fusée nerveuse y est ralentie, un lanceur lourd
     * un peu accéléré : dans les deux cas, l'écran dit la hauteur que l'on voit.</li>
     * <li>Quand elle sort de vue ({@code ASCENT_LIMIT}), ou qu'elle approche du sommet de sa trajectoire, le reste du
     * vol calculé est réparti sur les ticks restants, en accélérant sans à-coup.</li>
     * </ol>
     */
    private static synchronized double[] warp(RocketStats stats) {
        FlightSimulation flight = flight(stats);
        double[] cached = WARPS.get(flight);
        if (cached != null) {
            return cached;
        }
        // La montée visible d'abord, sur la plus longue durée possible : on ne sait qu'ensuite combien le vol durera.
        double[] table = new double[MAX_FLIGHT_TICKS + 1];
        // L'altitude du vol calculé, échantillonnée : on y cherche l'instant où chaque altitude est atteinte.
        int samples = 2400;
        double[] heights = new double[samples + 1];
        for (int k = 0; k <= samples; k++) {
            heights[k] = flight.altitude(k / (double) samples) / 1000.0;
        }
        double cap = Math.min(LaunchTableBlockEntity.ASCENT_LIMIT / BLOCKS_PER_KM, 0.9 * flight.maxAltitude() / 1000.0);
        int seen = 0;
        int at = 0;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            double seconds = tick / 20.0;
            double target = LaunchTableBlockEntity.ASCENT_RATE * seconds * seconds / BLOCKS_PER_KM;
            if (target > cap) {
                break;
            }
            while (at < samples && heights[at] < target) {
                at++;
            }
            double below = at > 0 ? heights[at - 1] : 0.0;
            double part = at > 0 && heights[at] > below ? (target - below) / (heights[at] - below) : 0.0;
            table[tick] = Math.max(table[tick - 1], (Math.max(0, at - 1) + Math.clamp(part, 0.0, 1.0)) / samples);
            seen = tick;
        }
        // La durée du vol : la montée visible, puis la suite, à la mesure de la mission.
        // Une durée en secondes entières : l'horloge de mission finit sur une seconde ronde.
        int seconds = (int) Math.round((seen + afterSightSeconds(stats) * 20.0) / 20.0);
        int ticks = Math.clamp(seconds * 20, Math.max(MIN_FLIGHT_TICKS, (seen / 20 + 2) * 20), MAX_FLIGHT_TICKS);
        table = java.util.Arrays.copyOf(table, ticks + 1);
        // Le reste du vol, sur les ticks restants : au rythme de la sortie de vue d'abord, puis de plus en plus vite.
        double start = table[seen];
        double rate = seen > 0 ? table[seen] - table[seen - 1] : 0.0;
        int left = ticks - seen;
        for (int tick = seen + 1; tick <= ticks; tick++) {
            double along = (tick - seen) / (double) left;
            table[tick] = start + rate * left >= 1.0 ? start + (1.0 - start) * along
                    : start + rate * (tick - seen) + (1.0 - start - rate * left) * along * along;
        }
        table[ticks] = 1.0;
        WARPS.put(flight, table);
        return table;
    }

    /**
     * Où en est le vol calculé (de 0 à 1) pour un avancement du vol en jeu (de 0 à 1) : voir {@link #warp}.
     */
    public static double simProgress(RocketStats stats, double progress) {
        double[] table = warp(stats);
        double tick = Math.clamp(progress, 0.0, 1.0) * (table.length - 1);
        int floor = (int) Math.min(table.length - 2, Math.floor(tick));
        return table[floor] + (table[floor + 1] - table[floor]) * (tick - floor);
    }

    /** Temps écoulé dans le vol calculé, en secondes, pour un avancement du vol en jeu. */
    public static double simSeconds(RocketStats stats, double progress) {
        return simProgress(stats, progress) * flight(stats).duration();
    }

    /** Avancement du vol en jeu (de 0 à 1) auquel le vol calculé atteint cet instant, en secondes. */
    public static double gameProgress(RocketStats stats, double simSeconds) {
        double duration = flight(stats).duration();
        double target = Math.clamp(simSeconds / duration, 0.0, 1.0);
        double low = 0.0;
        double high = 1.0;
        for (int k = 0; k < 40; k++) {
            double middle = (low + high) / 2.0;
            if (simProgress(stats, middle) < target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2.0;
    }

    /** Altitude de la fusée, en km. */
    public static double altitudeKm(RocketStats stats, double progress) {
        return flight(stats).altitude(simProgress(stats, progress)) / 1000.0;
    }

    /** Vitesse de la fusée, en m/s. */
    public static double speed(RocketStats stats, double progress) {
        return flight(stats).speed(simProgress(stats, progress));
    }

    /** Distance parcourue au sol depuis le pas de tir, en km. */
    public static double downrangeKm(RocketStats stats, double progress) {
        return flight(stats).downrange(simProgress(stats, progress)) / 1000.0;
    }

    /** Accélération ressentie à bord, en g. */
    public static double gForce(RocketStats stats, double progress) {
        return flight(stats).gForce(simProgress(stats, progress));
    }

    /**
     * L'orbite qu'aurait la fusée si ses moteurs s'éteignaient maintenant (voir {@link FlightSimulation#orbit}) : apogée et
     * périgée en km au-dessus du sol, excentricité, paramètre en km, anomalie vraie en radians.
     */
    public static double[] orbitKm(RocketStats stats, double progress) {
        double[] orbit = flight(stats).orbit(simProgress(stats, progress));
        return new double[] {orbit[0] / 1000.0, orbit[1] / 1000.0, orbit[2], orbit[3] / 1000.0, orbit[4]};
    }

    /** Étage allumé, à partir de 1. */
    public static int stage(RocketStats stats, double progress) {
        return flight(stats).stage(simProgress(stats, progress));
    }

    /** Temps écoulé depuis le décollage, en secondes : celles du jeu, que le joueur vit. */
    public static double missionSeconds(RocketStats stats, double progress) {
        return flightTicks(stats) / 20.0 * Math.clamp(progress, 0.0, 1.0);
    }
}
