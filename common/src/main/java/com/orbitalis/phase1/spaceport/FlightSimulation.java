package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.phase1.rocket.RocketStats;

/**
 * Vol d'une fusée, calculé pas à pas à partir de ses caractéristiques.
 * <p>
 * À chaque quart de seconde de vol, la fusée subit la poussée de l'étage allumé, son poids (qui diminue à mesure
 * que la vitesse horizontale approche de la vitesse orbitale), et la traînée de l'air, qui s'efface avec
 * l'altitude. Sa masse fond avec les ergols brûlés ; un étage vide est largué et le suivant s'allume.
 * <ul>
 * <li>Un lanceur capable d'atteindre l'orbite part à la verticale, puis s'incline peu à peu vers l'horizontale
 * à mesure qu'il dépense son delta-v. Ses moteurs à ergols liquides réduisent leur poussée pour ne pas dépasser
 * {@link #G_LIMIT} g. Dès que sa vitesse horizontale atteint la vitesse orbitale, les moteurs sont coupés : c'est
 * la mise en orbite, et les ergols qui restent sont sa marge.</li>
 * <li>Une fusée qui n'atteint pas l'orbite monte à la verticale ; ses moteurs éteints, elle poursuit sur sa lancée
 * jusqu'au sommet de sa trajectoire.</li>
 * </ul>
 * Le résultat est une table : l'altitude, la vitesse, la distance parcourue, l'accélération et l'étage allumé
 * à chaque instant, et la liste des événements du vol.
 */
public final class FlightSimulation {
    /** Accélération maximale tolérée, en g : au-delà, les moteurs à ergols liquides réduisent leur poussée. */
    public static final double G_LIMIT = 4.0;
    /** Rayon de la planète, en m, et son paramètre gravitationnel (g0 × R²), en m³/s². */
    private static final double PLANET_RADIUS = 6_371_000.0;
    private static final double MU = RocketStats.G0 * PLANET_RADIUS * PLANET_RADIUS;
    /** Hauteur d'échelle de l'atmosphère, en m : la densité de l'air est divisée par e tous les sept kilomètres. */
    private static final double SCALE_HEIGHT = 7_000.0;
    /** Pas de calcul, en secondes, et nombre de pas entre deux lignes de la table. */
    private static final double STEP = 0.25;
    private static final int STEPS_PER_SAMPLE = 4;
    /** Part du delta-v total dépensée quand le lanceur a fini de s'incliner, et forme de cette inclinaison. */
    private static final double TURN_FRACTION = 0.42;
    private static final double TURN_EXPONENT = 0.7;
    /** Altitude d'orbite visée, et altitude à partir de laquelle le lanceur la vise activement, en m. */
    private static final double ORBIT_ALTITUDE = 200_000.0;
    private static final double GUIDANCE_ALTITUDE = 60_000.0;
    /** Durée maximale d'un vol calculé, en secondes : garde-fou. */
    private static final double MAX_TIME = 3_000.0;

    /** Événements d'un vol. */
    public enum EventType {
        /** Passage de la ligne de Kármán, à 100 km : l'espace. */
        SPACE,
        /** Un étage vide est largué, le suivant s'allume. */
        STAGING,
        /** Les moteurs s'arrêtent, réservoirs vides. */
        BURNOUT,
        /** Vitesse orbitale atteinte : les moteurs sont coupés. */
        ORBIT,
        /** Sommet de la trajectoire d'un vol qui n'atteint pas l'orbite. */
        APOGEE
    }

    /**
     * @param time  instant de l'événement, en secondes depuis le décollage
     * @param stage étage concerné (celui qui s'allume, pour une séparation), à partir de 1
     */
    public record Event(EventType type, double time, int stage) {
    }

    /** État de la fusée à un instant du vol. */
    private record Sample(double altitude, double speed, double downrange, double acceleration, int stage, double vx, double vy) {
    }

    private final List<Sample> samples = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private double duration;
    private double maxAltitude;
    private double propellantLeft;

    private FlightSimulation() {
    }

    /** Calcule le vol d'une fusée. */
    public static FlightSimulation of(RocketStats stats) {
        FlightSimulation flight = new FlightSimulation();
        flight.run(stats);
        return flight;
    }

    private void run(RocketStats stats) {
        List<RocketStats.StageStats> stages = stats.stages();
        boolean orbital = stats.reach().ordinal() >= RocketStats.Reach.LOW_ORBIT.ordinal();
        double mass = stats.liftoffMass();
        // Surface offerte à l'air : elle grandit avec la fusée, moins vite que sa masse.
        double dragArea = 0.36 * Math.cbrt(Math.max(1.0, stats.liftoffMass()) / 20_000.0 * (stats.liftoffMass() / 20_000.0));

        double time = 0;
        double altitude = 0;
        double downrange = 0;
        double vy = 0;
        double vx = 0;
        double spent = 0;
        int steps = 0;
        boolean inSpace = false;
        boolean inOrbit = false;
        samples.add(new Sample(0, 0, 0, 0, 1, 0, 0));

        for (int index = 0; index < stages.size() && !inOrbit; index++) {
            RocketStats.StageStats stage = stages.get(index);
            if (stage.propellant().isLowThrust()) {
                // Propulseur ionique ou voile : il ne sert qu'une fois en orbite, des mois durant ; le lancement
                // s'arrête au dernier étage chimique.
                if (index > 0) {
                    events.add(new Event(EventType.BURNOUT, time, stages.get(index - 1).number()));
                }
                break;
            }
            // Une séparation à chaque nouvel étage ; le largage des boosters (même numéro d'étage) se fait de lui-même.
            if (index > 0 && stage.number() != stages.get(index - 1).number()) {
                events.add(new Event(EventType.STAGING, time, stage.number()));
            }
            double propellant = stage.propellantMass();
            // Débit et poussée de l'étage, tels que son delta-v soit exactement celui de ses caractéristiques.
            double flow = stage.burnTime() > 0 ? propellant / stage.burnTime() : 0;
            double exhaust = propellant > 0 && mass > propellant ? stage.deltaV() / Math.log(mass / (mass - propellant)) : 0;
            double thrust = flow * exhaust;
            boolean throttles = !stage.propellant().isSolid();

            while (propellant > 0 && flow > 0 && time < MAX_TIME) {
                double acceleration = thrust / mass;
                double throttle = 1.0;
                if (throttles && acceleration > G_LIMIT * RocketStats.G0) {
                    throttle = G_LIMIT * RocketStats.G0 / acceleration;
                    acceleration = G_LIMIT * RocketStats.G0;
                }
                // Inclinaison par rapport à la verticale : nulle au départ, elle croît avec le delta-v dépensé.
                double pitch = 0;
                if (orbital) {
                    double fraction = Math.clamp((spent - 150.0) / (stats.totalDeltaV() * TURN_FRACTION), 0.0, 1.0);
                    pitch = Math.PI / 2 * Math.pow(fraction, TURN_EXPONENT);
                }
                double speed = Math.hypot(vx, vy);
                double radius = PLANET_RADIUS + altitude;
                double gravity = MU / (radius * radius) - vx * vx / radius;
                // Une fois sorti de l'atmosphère, le lanceur vise son altitude d'orbite : il dose la part verticale de
                // sa poussée pour y arriver en palier, et garde tout le reste pour prendre de la vitesse horizontale.
                if (orbital && altitude > GUIDANCE_ALTITUDE) {
                    double wantedClimb = Math.clamp((ORBIT_ALTITUDE - altitude) / 50.0, -150.0, 900.0);
                    double vertical = ((wantedClimb - vy) / 12.0 + gravity) / acceleration;
                    pitch = Math.acos(Math.clamp(vertical, -0.25, 0.95));
                }
                double drag = speed > 0 ? 0.5 * 1.225 * Math.exp(-altitude / SCALE_HEIGHT) * speed * speed * dragArea / mass : 0;
                vx += (acceleration * Math.sin(pitch) - (speed > 0 ? drag * vx / speed : 0)) * STEP;
                vy += (acceleration * Math.cos(pitch) - gravity - (speed > 0 ? drag * vy / speed : 0)) * STEP;
                downrange += vx * STEP;
                altitude += vy * STEP;
                if (altitude < 0) {
                    // Poussée encore insuffisante pour quitter la table : la fusée ne s'enfonce pas dans le sol.
                    altitude = 0;
                    vy = Math.max(0, vy);
                }
                propellant -= flow * throttle * STEP;
                mass -= flow * throttle * STEP;
                spent += acceleration * STEP;
                time += STEP;
                if (!inSpace && altitude >= RocketStats.SPACE_ALTITUDE_KM * 1000.0) {
                    inSpace = true;
                    events.add(new Event(EventType.SPACE, time, stage.number()));
                }
                if (++steps % STEPS_PER_SAMPLE == 0) {
                    record(altitude, vx, vy, downrange, acceleration, stage.number());
                }
                if (orbital && altitude > 80_000.0 && vx >= Math.sqrt(MU / (PLANET_RADIUS + altitude))) {
                    inOrbit = true;
                    propellantLeft = Math.max(0, propellant);
                    events.add(new Event(EventType.ORBIT, time, stage.number()));
                    break;
                }
            }
            // L'étage vide est largué.
            mass -= Math.max(0, propellant) + stage.dryMass();
            if (index == stages.size() - 1 && !inOrbit) {
                events.add(new Event(EventType.BURNOUT, time, stage.number()));
            }
        }

        // Sans mise en orbite, la fusée poursuit sur sa lancée jusqu'au sommet de sa trajectoire.
        int last = stages.isEmpty() ? 1 : stages.get(stages.size() - 1).number();
        while (!inOrbit && vy > 0 && time < MAX_TIME) {
            double radius = PLANET_RADIUS + altitude;
            vy -= (MU / (radius * radius) - vx * vx / radius) * STEP;
            altitude += vy * STEP;
            downrange += vx * STEP;
            time += STEP;
            if (!inSpace && altitude >= RocketStats.SPACE_ALTITUDE_KM * 1000.0) {
                inSpace = true;
                events.add(new Event(EventType.SPACE, time, last));
            }
            if (++steps % STEPS_PER_SAMPLE == 0) {
                record(altitude, vx, vy, downrange, 0, last);
            }
        }
        if (!inOrbit) {
            events.add(new Event(EventType.APOGEE, time, last));
        }
        // Mise en orbite : la coupure des moteurs arrondit l'orbite à cette altitude (vitesse circulaire, plus de vitesse
        // verticale). C'est l'état que garde la dernière ligne de la table : l'orbite instantanée y devient l'orbite basse.
        if (inOrbit) {
            record(altitude, Math.sqrt(MU / (PLANET_RADIUS + altitude)), 0.0, downrange, 0, last);
        } else {
            record(altitude, vx, vy, downrange, 0, last);
        }
        duration = Math.max(STEP, time);
        events.sort(java.util.Comparator.comparingDouble(Event::time));
    }

    private void record(double altitude, double vx, double vy, double downrange, double acceleration, int stage) {
        samples.add(new Sample(altitude, Math.hypot(vx, vy), downrange, acceleration, stage, vx, vy));
        maxAltitude = Math.max(maxAltitude, altitude);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /** Durée du vol, du décollage à la mise en orbite ou au sommet de la trajectoire, en secondes. */
    public double duration() {
        return duration;
    }

    /** Altitude la plus haute atteinte, en m. */
    public double maxAltitude() {
        return maxAltitude;
    }

    /** Ergols restant dans le dernier étage à la mise en orbite, en kg ; 0 sans mise en orbite. */
    public double propellantLeft() {
        return propellantLeft;
    }

    /** Événements du vol, dans l'ordre. */
    public List<Event> events() {
        return events;
    }

    /** Ligne de la table à cet avancement du vol (de 0 à 1), et part du chemin vers la ligne suivante. */
    private double position(double progress) {
        return Math.clamp(progress, 0.0, 1.0) * (samples.size() - 1);
    }

    private Sample at(int index) {
        return samples.get(Math.clamp(index, 0, samples.size() - 1));
    }

    /** Altitude à cet avancement du vol, en m. */
    public double altitude(double progress) {
        double position = position(progress);
        int index = (int) position;
        return at(index).altitude() + (at(index + 1).altitude() - at(index).altitude()) * (position - index);
    }

    /** Vitesse à cet avancement du vol, en m/s. */
    public double speed(double progress) {
        double position = position(progress);
        int index = (int) position;
        return at(index).speed() + (at(index + 1).speed() - at(index).speed()) * (position - index);
    }

    /** Distance parcourue au sol à cet avancement du vol, en m. */
    public double downrange(double progress) {
        double position = position(progress);
        int index = (int) position;
        return at(index).downrange() + (at(index + 1).downrange() - at(index).downrange()) * (position - index);
    }

    /** Accélération ressentie à bord à cet avancement du vol, en g ; nulle moteurs éteints. */
    public double gForce(double progress) {
        return at((int) Math.round(position(progress))).acceleration() / RocketStats.G0;
    }

    /**
     * L'orbite qu'aurait la fusée si ses moteurs s'éteignaient à cet avancement du vol : celle que décrit sa vitesse à
     * cet instant (l'orbite osculatrice). Elle se déduit de la distance au centre de la planète et des deux composantes
     * de la vitesse, par l'énergie et le moment cinétique.
     *
     * @return l'apogée et le périgée en m au-dessus du sol (apogée infinie : la fusée s'échappe ; périgée négatif : la
     *         trajectoire recoupe la planète), l'excentricité, le paramètre de l'orbite en m, et l'anomalie vraie en radians
     */
    public double[] orbit(double progress) {
        double position = position(progress);
        int index = (int) position;
        double t = position - index;
        Sample a = at(index);
        Sample b = at(index + 1);
        double r = PLANET_RADIUS + a.altitude() + (b.altitude() - a.altitude()) * t;
        double vt = a.vx() + (b.vx() - a.vx()) * t;
        double vr = a.vy() + (b.vy() - a.vy()) * t;
        double energy = (vt * vt + vr * vr) / 2.0 - MU / r;
        double h = r * vt;
        double e = Math.sqrt(Math.max(0.0, 1.0 + 2.0 * energy * h * h / (MU * MU)));
        double p = h * h / MU;
        double nu = e < 1.0E-6 ? 0.0 : Math.acos(Math.clamp((p / r - 1.0) / e, -1.0, 1.0)) * (vr >= 0.0 ? 1.0 : -1.0);
        // Une orbite fermée (énergie négative) se mesure par son demi-grand axe : cela vaut aussi pour une fusée encore
        // posée, dont la « trajectoire » est une chute droite (excentricité 1).
        double periapsis;
        double apoapsis;
        if (energy < 0.0) {
            double semiMajor = -MU / (2.0 * energy);
            periapsis = semiMajor * (1.0 - Math.min(e, 1.0)) - PLANET_RADIUS;
            apoapsis = semiMajor * (1.0 + Math.min(e, 1.0)) - PLANET_RADIUS;
        } else {
            periapsis = p / (1.0 + e) - PLANET_RADIUS;
            apoapsis = Double.POSITIVE_INFINITY;
        }
        return new double[] {apoapsis, periapsis, e, p, nu};
    }

    /** Étage allumé à cet avancement du vol, à partir de 1. */
    public int stage(double progress) {
        return at((int) Math.round(position(progress))).stage();
    }
}
