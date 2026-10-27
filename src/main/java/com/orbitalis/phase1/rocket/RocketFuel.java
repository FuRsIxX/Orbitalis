package com.orbitalis.phase1.rocket;

import com.orbitalis.core.config.ServerConfig;

/**
 * Ergols liquides à charger dans une fusée avant le lancement, en mB.
 * <p>
 * À l'échelle du jeu, 1 mB d'ergol représente {@link #KG_PER_MB} kg ; le multiplicateur de consommation
 * de la configuration du serveur s'applique ensuite.
 *
 * @param rp1      kérosène RP-1
 * @param oxygen   oxygène liquide
 * @param hydrogen hydrogène liquide
 * @param methane  méthane liquide
 * @param helium3  hélium-3, le combustible des moteurs à fusion
 */
public record RocketFuel(int rp1, int oxygen, int hydrogen, int methane, int helium3) {
    /** Hélium-3 brûlé par un moteur à fusion, par mB d'hydrogène éjecté. */
    public static final double HELIUM3_PER_HYDROGEN = 0.05;

    /** Sans méthane ni hélium-3. */
    public RocketFuel(int rp1, int oxygen, int hydrogen) {
        this(rp1, oxygen, hydrogen, 0, 0);
    }

    /** Sans hélium-3. */
    public RocketFuel(int rp1, int oxygen, int hydrogen, int methane) {
        this(rp1, oxygen, hydrogen, methane, 0);
    }

    /** Masse d'ergol représentée par 1 mB, en kg. */
    public static final double KG_PER_MB = 10.0;

    public static final RocketFuel NONE = new RocketFuel(0, 0, 0);

    /** Ergols nécessaires pour remplir tous les réservoirs de la fusée. */
    public static RocketFuel required(RocketStats stats) {
        double scale = ServerConfig.FUEL_CONSUMPTION_MULTIPLIER.get() / KG_PER_MB;
        double rp1 = 0;
        double oxygen = 0;
        double hydrogen = 0;
        double methane = 0;
        double helium3 = 0;
        for (RocketStats.StageStats stage : stats.stages()) {
            Propellant propellant = stage.propellant();
            if (propellant.isPreloaded()) {
                continue;
            }
            // Ce qu'on charge pour l'étage : avec des boosters, il en brûle une part pendant qu'ils poussent.
            double fuel = stage.loaded() * propellant.fuelFraction() * scale;
            oxygen += stage.loaded() * propellant.oxidizerFraction() * scale;
            if (propellant == Propellant.KEROLOX) {
                rp1 += fuel;
            } else if (propellant == Propellant.METHALOX) {
                methane += fuel;
            } else if (propellant == Propellant.FUSION) {
                hydrogen += fuel;
                helium3 += fuel * HELIUM3_PER_HYDROGEN;
            } else {
                hydrogen += fuel;
            }
        }
        return new RocketFuel((int) Math.ceil(rp1), (int) Math.ceil(oxygen), (int) Math.ceil(hydrogen), (int) Math.ceil(methane),
                (int) Math.ceil(helium3));
    }

    public int total() {
        return rp1 + oxygen + hydrogen + methane + helium3;
    }

    /** Vrai si la fusée ne demande aucun ergol liquide (fusée à poudre). */
    public boolean isEmpty() {
        return total() == 0;
    }
}
