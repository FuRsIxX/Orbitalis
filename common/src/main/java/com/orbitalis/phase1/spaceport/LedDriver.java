package com.orbitalis.phase1.spaceport;

/**
 * Comportement d'une LED, tel que le pas de tir le simule. Rien ici ne vient du jeu : c'est un petit modèle
 * physique, le même pour le serveur (qui en tire la lumière réellement émise) et pour l'affichage (qui en tire
 * l'éclat que l'on voit).
 * <ul>
 * <li><b>Le courant.</b> Une LED ne s'allume ni ne s'éteint d'un coup : son alimentation charge puis décharge un
 * condensateur. Le courant monte vite (moins d'un vingtième de seconde) et retombe plus lentement, en traîne.</li>
 * <li><b>L'éclat perçu.</b> L'œil ne voit pas le courant mais sa racine, à peu près : une LED à demi alimentée
 * paraît encore bien allumée.</li>
 * <li><b>Le diagramme d'émission.</b> Une LED éclaire devant elle. Vue de face elle éblouit, vue de biais elle
 * faiblit, vue de dos on n'en perçoit qu'un reflet.</li>
 * <li><b>Le halo.</b> Une source ponctuelle très brillante bave dans l'œil : son halo occupe toujours à peu près
 * le même angle de vision. Il grandit donc avec la distance, et c'est pourquoi une balise se voit de très loin.
 * La nuit, pupille ouverte, il est plus large et plus fort.</li>
 * </ul>
 */
public final class LedDriver {
    /** Temps de montée et de descente du courant, en ticks. */
    private static final double RISE = 0.6;
    private static final double FALL = 2.6;
    /** Angle de vision qu'occupe le halo d'une LED à pleine puissance, en radians. */
    private static final double GLARE_ANGLE = 0.035;

    private LedDriver() {
    }

    /**
     * Courant d'une LED commandée par des impulsions, de 0 à 1.
     *
     * @param phase  instant dans la période, en ticks
     * @param period durée de la période
     * @param starts début de chaque impulsion de la période
     * @param length durée d'une impulsion
     */
    private static double pulsed(double phase, double period, double[] starts, double length) {
        // On rejoue deux périodes : la traîne de la précédente compte encore au début de celle-ci.
        double current = 0.0;
        double clock = -period;
        for (int cycle = 0; cycle < 2; cycle++) {
            for (double start : starts) {
                double begin = cycle == 0 ? start - period : start;
                if (phase < begin) {
                    break;
                }
                // Extinction depuis la fin de l'impulsion précédente, puis charge pendant celle-ci.
                current *= Math.exp(-(begin - clock) / FALL);
                double on = Math.min(length, phase - begin);
                current = 1.0 - (1.0 - current) * Math.exp(-on / RISE);
                clock = begin + on;
            }
        }
        return current * Math.exp(-(phase - clock) / FALL);
    }

    /**
     * Courant d'une balise de mât. Au repos, un éclat toutes les secondes et demie, décalé d'un mât à l'autre,
     * comme le balisage d'un pylône. Pendant un lancement, toutes ensemble : deux éclats rapprochés par seconde,
     * comme un feu à éclats.
     *
     * @param time   heure du monde, en ticks (avec sa fraction pour l'affichage)
     * @param index  numéro du mât
     * @param urgent vrai pendant un compte à rebours ou un vol
     */
    public static double beaconCurrent(double time, int index, boolean urgent) {
        if (urgent) {
            return pulsed(time % 20.0, 20.0, new double[] {0.0, 4.0}, 1.6);
        }
        return pulsed((time + index * 7.0) % 30.0, 30.0, new double[] {0.0}, 4.0);
    }

    /** Éclat perçu pour un courant donné, de 0 à 1. */
    public static double brightness(double current) {
        return Math.pow(Math.max(0.0, Math.min(1.0, current)), 0.55);
    }

    /** Force de la lumière réellement émise par une balise, pour un courant donné : quatre paliers, de 0 à 12. */
    public static int beaconLight(double current) {
        double seen = brightness(current);
        return seen < 0.2 ? 0 : seen < 0.5 ? 5 : seen < 0.8 ? 9 : 12;
    }

    /**
     * Part de la lumière d'une LED qui part dans une direction, de 0 à 1.
     *
     * @param cosine cosinus de l'angle entre l'axe de la LED et la direction du regard (1 : vue de face)
     * @param narrow vrai pour un projecteur, au faisceau serré ; faux pour une balise, qui rayonne large
     */
    public static double emission(double cosine, boolean narrow) {
        double floor = narrow ? 0.1 : 0.35;
        return floor + (1.0 - floor) * Math.pow(Math.max(0.0, cosine), narrow ? 2.5 : 0.8);
    }

    /**
     * Rayon du halo d'une LED, en blocs.
     *
     * @param size     rayon de la LED elle-même
     * @param seen     éclat perçu, de 0 à 1
     * @param distance distance de l'observateur, en blocs
     * @param dark     vrai de nuit
     */
    public static double haloRadius(double size, double seen, double distance, boolean dark) {
        double glare = GLARE_ANGLE * (dark ? 1.6 : 1.0) * distance * Math.sqrt(seen);
        return Math.min(4.0, size * (1.5 + 3.0 * seen) + glare);
    }

    /** Opacité du halo : de jour, le ciel clair le noie en grande partie. */
    public static double haloStrength(double seen, boolean dark) {
        return seen * (dark ? 1.0 : 0.45);
    }
}
