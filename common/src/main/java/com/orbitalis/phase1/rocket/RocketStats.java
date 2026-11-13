package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Statistiques d'une fusée, calculées à partir de son plan.
 * <p>
 * Le delta-v de chaque étage vient de l'équation de Tsiolkovski : {@code Δv = Isp × g0 × ln(m0 / m1)}, où m0 est
 * la masse au moment où l'étage s'allume (lui-même, plein, plus tout ce qu'il porte) et m1 la masse quand ses
 * réservoirs sont vides. Toutes les grandeurs sont en unités SI : kg, N, m/s, s.
 *
 * @param stages         étages utilisés, du premier au dernier
 * @param liftoffMass    masse au décollage, en kg
 * @param liftoffThrust  poussée au décollage, en N
 * @param totalDeltaV    somme des delta-v des étages, en m/s
 * @param apogeeKm       altitude maximale estimée d'un vol vertical, en km (sans objet si l'orbite est atteinte)
 * @param reach          ce que la fusée peut atteindre
 * @param issues         défauts du plan ; s'il y en a, la fusée ne peut pas voler
 */
public record RocketStats(List<StageStats> stages, double liftoffMass, double liftoffThrust, double totalDeltaV,
        double apogeeKm, Reach reach, List<Issue> issues) {
    /** Accélération de la pesanteur au niveau de la mer, en m/s². */
    public static final double G0 = 9.80665;

    // Delta-v nécessaire depuis le sol, pertes par gravité et par traînée comprises, en m/s.
    public static final double DELTA_V_LOW_ORBIT = 9_400;
    public static final double DELTA_V_GEO_TRANSFER = 11_850;
    public static final double DELTA_V_LUNAR = 12_550;
    public static final double DELTA_V_MARS = 13_000;
    public static final double DELTA_V_GEOSTATIONARY = 13_300;
    // Les planètes, pour une sonde : de quoi s'injecter vers elles et s'y mettre en orbite (voir CelestialBody).
    public static final double DELTA_V_VENUS = 12_900;
    public static final double DELTA_V_JUPITER = 16_000;
    public static final double DELTA_V_SATURN = 17_200;
    public static final double DELTA_V_TITAN = 17_500;
    public static final double DELTA_V_EUROPA = 18_500;
    public static final double DELTA_V_MERCURY = 22_000;
    /** Altitude à partir de laquelle on considère que l'espace est atteint (ligne de Kármán), en km. */
    public static final double SPACE_ALTITUDE_KM = 100;
    /**
     * Impulsion totale que donne une voile solaire au cours de sa mission, en N·s : une poussée de l'ordre du dixième
     * de newton, tenue un an. Son delta-v est cette impulsion divisée par la masse qu'elle pousse.
     */
    public static final double SAIL_IMPULSE = 4_000_000;
    /** Part du delta-v restante après les pertes d'un vol vertical. */
    private static final double VERTICAL_EFFICIENCY = 0.85;

    /**
     * Ce qu'une fusée peut atteindre, du plus modeste au plus ambitieux : dans l'ordre du delta-v qu'il demande. Les
     * paliers sous l'orbite n'en ont pas (0) : ils se jugent sur l'altitude.
     */
    public enum Reach {
        NONE(0),
        ATMOSPHERIC(0),
        SUBORBITAL(0),
        LOW_ORBIT(DELTA_V_LOW_ORBIT),
        GEO_TRANSFER(DELTA_V_GEO_TRANSFER),
        LUNAR(DELTA_V_LUNAR),
        VENUS(DELTA_V_VENUS),
        /** Transfert vers Mars : l'injection demande un peu plus que la Lune, et le freinage se fait dans son air. */
        MARS(DELTA_V_MARS),
        GEOSTATIONARY(DELTA_V_GEOSTATIONARY),
        JUPITER(DELTA_V_JUPITER),
        SATURN(DELTA_V_SATURN),
        TITAN(DELTA_V_TITAN),
        EUROPA(DELTA_V_EUROPA),
        MERCURY(DELTA_V_MERCURY);

        private final double deltaV;

        Reach(double deltaV) {
            this.deltaV = deltaV;
        }

        /** Delta-v qu'il demande depuis le sol, en m/s ; 0 sous l'orbite. */
        public double deltaV() {
            return deltaV;
        }

        /** Le plus lointain palier orbital que ce delta-v atteint, ou {@code null} s'il n'atteint pas l'orbite. */
        public static Reach orbital(double deltaV) {
            Reach best = null;
            for (Reach reach : values()) {
                if (reach.deltaV > 0 && deltaV >= reach.deltaV) {
                    best = reach;
                }
            }
            return best;
        }

        public String nameKey() {
            return "rocket.orbitalis.reach." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Défauts possibles d'un plan. */
    public enum IssueType {
        /** Aucune pièce installée. */
        EMPTY,
        /** Un étage a des réservoirs mais pas de moteur. */
        NO_ENGINE,
        /** Un étage a des moteurs à ergols liquides mais pas de réservoir. */
        NO_TANK,
        /** Un étage à poudre a des réservoirs, qui ne servent à rien. */
        USELESS_TANK,
        /** Un étage supérieur n'a pas d'interétage pour le relier à celui du dessous. */
        MISSING_INTERSTAGE,
        /** Un étage est utilisé alors que celui du dessous est vide. */
        STAGE_GAP,
        /** La poussée au décollage ne dépasse pas le poids. */
        LOW_THRUST,
        /** Une propulsion à faible poussée (ionique, voile) emporte un équipage : le voyage durerait des mois. */
        CREWED_LOW_THRUST,
        /** Un moteur nucléaire au premier étage : il ne s'allume qu'en altitude, jamais au-dessus du pas de tir. */
        NUCLEAR_LIFTOFF,
        /** Des boosters latéraux qui ne vont pas : une paire de boosters à poudre, sur un premier étage chimique. */
        BAD_BOOSTERS,
        /** Des réservoirs d'hydrogène sous un autre moteur que le moteur nucléaire : ils ne gardent que l'hydrogène seul. */
        HYDROGEN_TANK,
        /** Pas de coiffe. */
        NO_NOSE_CONE;

        public String messageKey() {
            return "rocket.orbitalis.issue." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * @param stage numéro de l'étage concerné (1 = premier étage), ou 0 si le défaut concerne toute la fusée
     */
    public record Issue(IssueType type, int stage) {
    }

    /**
     * Statistiques d'un étage.
     *
     * @param number         numéro de l'étage (1 = premier étage)
     * @param dryMass        masse à vide, interétage compris, en kg
     * @param propellantMass masse d'ergols, réservoirs pleins, en kg
     * @param thrust         poussée, en N : au niveau de la mer pour le premier étage, dans le vide pour les autres
     * @param twr            rapport poussée/poids à l'allumage
     * @param deltaV         delta-v fourni par l'étage, en m/s
     * @param burnTime       durée de combustion, en secondes
     * @param loaded         ergols à charger au pas de tir pour cet étage, en kg (ceux qu'il brûle avec les boosters
     *                       compris ; rien pour la phase des boosters elle-même)
     */
    public record StageStats(int number, Propellant propellant, double dryMass, double propellantMass,
            double thrust, double twr, double deltaV, double burnTime, double loaded) {
    }

    public boolean isFlightReady() {
        return issues.isEmpty();
    }

    /** Calcule les statistiques d'un plan. */
    public static RocketStats of(RocketDesign design) {
        List<Issue> issues = new ArrayList<>();

        // Nombre d'étages utilisés : jusqu'au dernier qui contient quelque chose.
        int stageCount = 0;
        for (int i = 0; i < design.stages().size(); i++) {
            if (!design.stages().get(i).isEmpty()) {
                stageCount = i + 1;
            }
        }
        if (stageCount == 0) {
            issues.add(new Issue(IssueType.EMPTY, 0));
            return new RocketStats(List.of(), 0, 0, 0, 0, Reach.NONE, issues);
        }

        RocketParts.Part payload = RocketParts.get(design.payload());
        boolean crewed = RocketParts.isCrewCabin(design.payload());
        RocketParts.Part nose = RocketParts.get(design.nose());
        if (nose == null) {
            issues.add(new Issue(IssueType.NO_NOSE_CONE, 0));
        }
        // Masse portée par l'étage en cours de calcul : au départ, ce qui se trouve au-dessus du dernier étage.
        double carried = (payload == null ? 0 : payload.mass()) + (nose == null ? 0 : nose.mass());

        // On calcule du dernier étage vers le premier : chaque étage porte tous ceux du dessus.
        StageStats[] stats = new StageStats[stageCount];
        for (int i = stageCount - 1; i >= 0; i--) {
            RocketDesign.Stage stage = design.stages().get(i);
            int number = i + 1;
            RocketParts.Part enginePart = RocketParts.get(stage.engines());
            RocketParts.Part tankPart = RocketParts.get(stage.tanks());
            RocketParts.Part interstage = RocketParts.get(stage.interstage());

            if (stage.isEmpty() || (enginePart == null && tankPart == null)) {
                issues.add(new Issue(IssueType.STAGE_GAP, number));
            } else if (enginePart == null) {
                issues.add(new Issue(IssueType.NO_ENGINE, number));
            }
            if (i > 0 && interstage == null && !stage.isEmpty()) {
                issues.add(new Issue(IssueType.MISSING_INTERSTAGE, number));
            }

            int engineCount = stage.engines().getCount();
            int tankCount = stage.tanks().getCount();
            double dryMass = (interstage == null ? 0 : interstage.mass())
                    + (enginePart == null ? 0 : enginePart.mass() * engineCount)
                    + (tankPart == null ? 0 : tankPart.mass() * tankCount);

            Propellant propellant = enginePart == null ? Propellant.SOLID : enginePart.engine().propellant();
            double propellantMass = 0;
            double thrust = 0;
            double isp = 0;
            double burnTime = 0;
            if (enginePart != null) {
                RocketParts.Engine engine = enginePart.engine();
                if (propellant.isPreloaded()) {
                    propellantMass = engine.solidLoad() * engineCount;
                    if (tankPart != null) {
                        issues.add(new Issue(IssueType.USELESS_TANK, number));
                    }
                } else if (tankPart == null) {
                    issues.add(new Issue(IssueType.NO_TANK, number));
                } else {
                    if (propellant != Propellant.HYDROGEN && propellant != Propellant.FUSION
                            && stage.tanks().is(com.orbitalis.core.registry.ModItems.HYDROGEN_TANK.get())) {
                        issues.add(new Issue(IssueType.HYDROGEN_TANK, number));
                    }
                    propellantMass = tankPart.tank().volume() * propellant.density() * tankCount;
                }
                // Le premier étage traverse l'atmosphère : poussée au sol, et impulsion spécifique moyenne entre
                // le sol et le vide. Les étages supérieurs fonctionnent dans le vide.
                boolean first = i == 0;
                thrust = (first ? engine.thrustSeaLevel() : engine.thrustVacuum()) * 1000.0 * engineCount;
                isp = first ? (engine.ispSeaLevel() + engine.ispVacuum()) / 2.0 : engine.ispVacuum();
                // Débit massique d'un moteur : poussée / (Isp × g0).
                double flow = engine.thrustVacuum() * 1000.0 * engineCount / (engine.ispVacuum() * G0);
                burnTime = flow > 0 ? propellantMass / flow : 0;
            }

            double emptyMass = dryMass + carried;
            double fullMass = emptyMass + propellantMass;
            double deltaV = propellantMass > 0 && emptyMass > 0 ? isp * G0 * Math.log(fullMass / emptyMass) : 0;
            if (propellant == Propellant.SUNLIGHT && enginePart != null && emptyMass > 0) {
                // Une voile n'a pas d'ergol : sa lumière pousse d'autant mieux que la charge est légère.
                deltaV = SAIL_IMPULSE * engineCount / emptyMass;
            }
            if (propellant.isLowThrust() && enginePart != null && crewed) {
                issues.add(new Issue(IssueType.CREWED_LOW_THRUST, number));
            }
            double twr = fullMass > 0 ? thrust / (fullMass * G0) : 0;
            stats[i] = new StageStats(number, propellant, dryMass, propellantMass, thrust, twr, deltaV, burnTime, propellantMass);
            carried = fullMass;
        }

        // Boosters latéraux : ils brûlent avec le premier étage, puis se larguent ; le premier étage poursuit seul.
        List<StageStats> list = new ArrayList<>(List.of(stats));
        double liftoffMass = carried;
        if (!design.boosters().isEmpty()) {
            RocketParts.Part booster = RocketParts.get(design.boosters());
            RocketParts.Part coreEngine = RocketParts.get(design.stages().get(0).engines());
            boolean fits = booster != null && booster.engine() != null && design.boosters().is(com.orbitalis.core.registry.ModItems.SOLID_BOOSTER.get())
                    && design.boosters().getCount() == RocketDesign.BOOSTER_PAIR && coreEngine != null && coreEngine.engine() != null
                    && !coreEngine.engine().propellant().isLowThrust();
            if (!fits) {
                issues.add(new Issue(IssueType.BAD_BOOSTERS, 1));
            } else {
                liftoffMass = withBoosters(list, design, booster.engine(), coreEngine.engine(), carried);
            }
        }

        // Un moteur nucléaire n'allume pas son réacteur au-dessus du pas de tir.
        if (RocketParts.get(design.stages().get(0).engines()) != null
                && (stats[0].propellant() == Propellant.HYDROGEN || stats[0].propellant() == Propellant.FUSION)) {
            issues.add(new Issue(IssueType.NUCLEAR_LIFTOFF, 1));
        }

        // Un premier étage à faible poussée ne quitte pas la table, quelle que soit sa charge.
        boolean lowFirst = RocketParts.get(design.stages().get(0).engines()) != null && stats[0].propellant().isLowThrust();
        if (lowFirst || (list.get(0).thrust() > 0 && list.get(0).twr() <= 1.0)) {
            issues.add(new Issue(IssueType.LOW_THRUST, 1));
        }

        // Les étages à faible poussée n'agissent qu'en orbite : il faut que les autres l'atteignent sans eux.
        double totalDeltaV = 0;
        double chemicalDeltaV = 0;
        for (StageStats stage : list) {
            totalDeltaV += stage.deltaV();
            if (!stage.propellant().isLowThrust()) {
                chemicalDeltaV += stage.deltaV();
            }
        }
        if (chemicalDeltaV < DELTA_V_LOW_ORBIT) {
            totalDeltaV = chemicalDeltaV;
        }
        // Vol vertical : toute la vitesse restante après les pertes se transforme en altitude (h = v² / 2g).
        double speed = chemicalDeltaV * VERTICAL_EFFICIENCY;
        double apogeeKm = speed * speed / (2.0 * G0) / 1000.0;

        // Les défauts sont listés du premier étage au dernier.
        issues.sort(java.util.Comparator.comparingInt(Issue::stage));
        return new RocketStats(List.copyOf(list), liftoffMass, list.get(0).thrust(), totalDeltaV, apogeeKm,
                reach(totalDeltaV, apogeeKm, issues.isEmpty()), List.copyOf(issues));
    }

    /**
     * Remplace le premier étage de la liste par ses deux phases : boosters et premier étage ensemble, jusqu'à
     * l'extinction des boosters ; puis le premier étage seul, avec ce qui lui reste d'ergols. Les deux phases portent
     * le numéro du premier étage : le largage des boosters se fait de lui-même, sans étape de séquence.
     *
     * @return la masse au décollage, boosters compris
     */
    private static double withBoosters(List<StageStats> list, RocketDesign design, RocketParts.Engine booster, RocketParts.Engine core,
            double coreLiftoffMass) {
        StageStats first = list.get(0);
        int boosters = design.boosters().getCount();
        int cores = design.stages().get(0).engines().getCount();
        double boosterFlow = booster.thrustVacuum() * 1000.0 * boosters / (booster.ispVacuum() * G0);
        double coreFlow = core.thrustVacuum() * 1000.0 * cores / (core.ispVacuum() * G0);
        double boosterIsp = (booster.ispSeaLevel() + booster.ispVacuum()) / 2.0;
        double coreIsp = (core.ispSeaLevel() + core.ispVacuum()) / 2.0;
        double boosterDry = booster.mass() * boosters;
        double boosterLoad = booster.solidLoad() * boosters;
        double burn = boosterFlow > 0 ? boosterLoad / boosterFlow : 0;
        double coreUsed = Math.min(first.propellantMass(), coreFlow * burn);

        double start = coreLiftoffMass + boosterDry + boosterLoad;
        double end = start - boosterLoad - coreUsed;
        double isp = (boosterFlow * boosterIsp + coreFlow * coreIsp) / (boosterFlow + coreFlow);
        double thrust = (booster.thrustSeaLevel() * boosters + core.thrustSeaLevel() * cores) * 1000.0;
        StageStats together = new StageStats(first.number(), Propellant.SOLID, boosterDry, boosterLoad + coreUsed, thrust,
                thrust / (start * G0), isp * G0 * Math.log(start / end), burn, 0);

        double remaining = first.propellantMass() - coreUsed;
        double coreStart = end - boosterDry;
        double coreEnd = coreStart - remaining;
        StageStats alone = new StageStats(first.number(), first.propellant(), first.dryMass(), remaining, first.thrust(),
                first.thrust() / (coreStart * G0), remaining > 0 && coreEnd > 0 ? coreIsp * G0 * Math.log(coreStart / coreEnd) : 0,
                coreFlow > 0 ? remaining / coreFlow : 0, first.loaded());
        list.set(0, alone);
        list.add(0, together);
        return start;
    }

    private static Reach reach(double deltaV, double apogeeKm, boolean flightReady) {
        if (!flightReady || deltaV <= 0) {
            return Reach.NONE;
        }
        Reach orbital = Reach.orbital(deltaV);
        if (orbital != null) {
            return orbital;
        }
        return apogeeKm >= SPACE_ALTITUDE_KM ? Reach.SUBORBITAL : Reach.ATMOSPHERIC;
    }
}
