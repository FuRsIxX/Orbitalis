package com.orbitalis.phase2.survival;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Apesanteur, en orbite : on s'y déplace comme dans l'eau, sans pesanteur. On dérive, on freine peu à peu, on nage
 * dans la direction où l'on regarde ; saut pour monter, accroupi pour descendre ; en sprint, on s'allonge et l'on
 * nage plus vite, à l'horizontale comme un nageur.
 * <p>
 * Le mouvement du joueur est calculé par son client (voir {@code client.MicrogravityMotion}) ; le serveur, lui, ôte
 * la pesanteur et tient la posture, pour que tous les joueurs voient la même.
 */
public final class Microgravity {
    /**
     * Poussée de nage dans la direction du regard, en blocs par tick², au pas et en sprint : avec le freinage, on file à
     * 4,4 blocs par seconde au pas (un peu plus vite qu'à pied) et 6,6 en sprint.
     */
    public static final double SWIM = 0.03;
    public static final double SWIM_SPRINT = 0.045;
    /** Ce que le jeu donne déjà de lui-même en l'air, à l'horizontale, au pas et en sprint. */
    public static final double AIR_PUSH = 0.02;
    public static final double AIR_PUSH_SPRINT = 0.026;
    /** Poussée verticale du saut et de l'accroupissement, en blocs par tick². */
    public static final double LIFT = 0.025;
    /** Freinage de l'eau, appliqué en plus de celui de l'air : on s'arrête en une seconde environ. */
    public static final double DRAG = 0.95;
    /** Ce que les propulseurs d'une combinaison avancée ajoutent à la nage : elle va presque deux fois plus vite. */
    public static final double THRUSTER_BOOST = 1.8;
    /** Vitesse verticale maximale, en blocs par tick : un bond depuis le plancher reste doux. */
    public static final double MAX_VERTICAL = 0.35;

    private Microgravity() {
    }

    /** Vrai si ce joueur flotte en apesanteur : en orbite, ni créatif, ni spectateur, ni assis dans un vaisseau. */
    public static boolean applies(Player player) {
        return SpaceRules.isOrbit(player.level()) && !player.isCreative() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping();
    }

    /**
     * Posture en apesanteur : allongé en nageant (en sprint), debout sinon. Là où l'on ne tient pas debout, on se
     * recroqueville, ou l'on reste allongé.
     */
    public static Pose pose(Player player) {
        if (player.isSprinting() && fits(player, Pose.SWIMMING)) {
            return Pose.SWIMMING;
        }
        if (fits(player, Pose.STANDING)) {
            return Pose.STANDING;
        }
        return fits(player, Pose.CROUCHING) ? Pose.CROUCHING : Pose.SWIMMING;
    }

    private static boolean fits(Player player, Pose pose) {
        return player.level().noCollision(player, player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7));
    }
}
