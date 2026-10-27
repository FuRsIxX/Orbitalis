package com.orbitalis.phase1.spaceport;

import org.joml.Vector3d;

/**
 * Mouvement d'une fusée en vol : la mécanique d'un engin poussé par ses moteurs et tiré par la pesanteur.
 * <p>
 * À chaque instant, deux forces seulement : la poussée, dans l'axe de la fusée, vers son nez ; et la pesanteur, vers
 * le bas. La fusée garde la vitesse qu'elle a (rien ne la freine là-haut), et ces deux forces la changent peu à peu.
 * Tout le reste en découle :
 * <ul>
 * <li>debout, la poussée l'emporte sur la pesanteur : elle monte droit, de plus en plus vite ;</li>
 * <li>couchée, la poussée ne la porte plus autant : elle cesse de gagner de la vitesse vers le haut, et en prend de
 * côté. Sa trajectoire se courbe dans le sens où pointe son nez ;</li>
 * <li>trop couchée, la pesanteur l'emporte : elle retombe ;</li>
 * <li>redresser le nez n'arrête pas la dérive déjà prise : il faut pousser dans l'autre sens pour la reprendre.</li>
 * </ul>
 * La poussée vaut deux fois le poids de la fusée : debout, elle monte donc avec l'accélération de la montée normale
 * ({@code LaunchTableBlockEntity.ASCENT_RATE}), et à 60° de la verticale elle ne monte plus du tout.
 * <p>
 * Le résultat est la position de la fusée par rapport à sa table de lancement, en blocs. Le serveur la calcule à
 * chaque tick ; pour une fusée sans équipage, qui suit exactement sa consigne, les clients refont le même calcul.
 */
public final class RocketMotion {
    /** Ce que la pesanteur retire à la vitesse verticale, en blocs par tick et par tick. */
    private static final double GRAVITY = 2.0 * LaunchTableBlockEntity.ASCENT_RATE / 400.0;
    /** Accélération que donne la poussée, dans l'axe de la fusée : deux fois la pesanteur. */
    private static final double THRUST = 2.0 * GRAVITY;
    /**
     * Distance au pas de tir que la fusée ne dépasse pas, en blocs. La table, restée au sol, calcule le vol : son
     * morceau de monde est retenu en mémoire tant qu'il dure, mais on borne tout de même l'éloignement.
     */
    public static final double LIMIT = 700.0;
    /**
     * Pour les tests automatiques : une limite plus courte. Un joueur simulé ne garde pas le monde en mémoire autour
     * de lui ; sa cabine ne doit donc pas sortir de la zone que le test retient. Zéro en temps normal.
     */
    public static double testLimit;

    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double vz;

    public void reset() {
        x = y = z = vx = vy = vz = 0.0;
    }

    /**
     * Un tick de vol.
     *
     * @param nose direction du nez de la fusée : celle de sa poussée
     */
    public void step(Vector3d nose) {
        step(nose, 1.0);
    }

    /**
     * Un tick de vol.
     *
     * @param nose     direction du nez de la fusée : celle de sa poussée
     * @param throttle part de la poussée : 0 moteurs coupés (la fusée continue sur son élan, et la pesanteur la
     *                 ralentit), 1 à pleine poussée
     */
    public void step(Vector3d nose, double throttle) {
        vx += THRUST * throttle * nose.x;
        vy += THRUST * throttle * nose.y - GRAVITY;
        vz += THRUST * throttle * nose.z;
        x += vx;
        y += vy;
        z += vz;
        double away = Math.sqrt(x * x + z * z);
        double limit = testLimit > 0.0 ? testLimit : LIMIT;
        if (away > limit) {
            // Au bord de la zone, la fusée ne va pas plus loin : elle garde sa hauteur et sa vitesse le long du bord.
            x *= limit / away;
            z *= limit / away;
            double outward = (vx * x + vz * z) / limit;
            if (outward > 0.0) {
                vx -= outward * x / limit;
                vz -= outward * z / limit;
            }
        }
    }

    /** Position par rapport à la table : vers l'est, vers le haut (la hauteur gagnée), vers le sud. */
    public Vector3d offset() {
        return new Vector3d(x, y, z);
    }

    /** Vitesse, en blocs par tick. */
    public Vector3d velocity() {
        return new Vector3d(vx, vy, vz);
    }

    public void copy(RocketMotion other) {
        x = other.x;
        y = other.y;
        z = other.z;
        vx = other.vx;
        vy = other.vy;
        vz = other.vz;
    }

    public double[] save() {
        return new double[] {x, y, z, vx, vy, vz};
    }

    public void load(double[] saved) {
        if (saved.length >= 6) {
            x = saved[0];
            y = saved[1];
            z = saved[2];
            vx = saved[3];
            vy = saved[4];
            vz = saved[5];
        }
    }
}
