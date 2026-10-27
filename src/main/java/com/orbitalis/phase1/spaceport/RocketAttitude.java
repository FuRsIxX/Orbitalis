package com.orbitalis.phase1.spaceport;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import net.minecraft.core.Direction;

/**
 * Attitude d'une fusée en vol : son orientation dans l'espace, sur ses trois axes, et la vitesse à laquelle elle tourne.
 * <p>
 * La fusée part debout, le nez au ciel. Le pilote, sanglé sur le dos, regarde vers le nez ; « le haut » de son
 * tableau de bord est à l'opposé du sens du tir. Ses commandes agissent sur les axes de la fusée, pas sur ceux du
 * monde, comme sur un vrai lanceur :
 * <ul>
 * <li><b>tangage</b> : le nez bascule vers le sens du tir (piquer) ou s'en relève (cabrer) ;</li>
 * <li><b>lacet</b> : le nez part à gauche ou à droite ;</li>
 * <li><b>roulis</b> : la fusée tourne sur elle-même, ce qui change le sens dans lequel agissent les deux autres.</li>
 * </ul>
 * Une commande n'impose pas un angle : elle braque les tuyères, ce qui donne une accélération de rotation. La fusée
 * prend de la vitesse de rotation, la garde par inertie, et il faut contrer pour l'arrêter. Le stabilisateur freine
 * de lui-même la rotation sur les axes que le pilote ne touche pas ; en vol assisté, il ramène aussi le nez vers la
 * consigne.
 * <p>
 * Tout est calculé par le serveur, à chaque tick. Les clients n'en reçoivent que l'orientation, qu'ils dessinent.
 */
public final class RocketAttitude {
    /** Vitesse de rotation maximale sur un axe, en degrés par tick. */
    public static final double MAX_RATE = 1.3;
    /** Accélération de rotation que donne une commande tenue, en degrés par tick et par tick, pour une fusée courte. */
    private static final double TORQUE = 0.07;
    /** Part de la vitesse de rotation que le stabilisateur laisse à chaque tick sur un axe libre : manuel, puis assisté. */
    private static final double DAMPING_MANUAL = 0.88;
    private static final double DAMPING_ASSISTED = 0.8;
    /** Vitesse à laquelle le stabilisateur assisté ramène le nez vers la consigne, en degrés par tick. */
    private static final double HOLD_RATE = 0.45;
    /**
     * Pilotage à la main : une commande tenue donne directement une vitesse de rotation, atteinte en quelques ticks, et
     * la fusée s'arrête presque aussitôt qu'on la lâche (les tuyères contrent d'elles-mêmes). C'est ce qui la rend
     * maniable : on la vise, sans avoir à contrer son élan. Vitesse visée en degrés par tick, puis part de l'écart à
     * cette vitesse rattrapée à chaque tick, en vol et à l'arrêt.
     */
    private static final double MANUAL_RATE = 0.9;
    private static final double MANUAL_RESPONSE = 0.3;
    private static final double MANUAL_STOP = 0.45;

    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);

    /** Orientation : la rotation qui amène la fusée debout sur sa table à sa position actuelle. */
    private final Quaterniond orientation = new Quaterniond();
    /** Vitesses de rotation sur les axes de la fusée, en degrés par tick : piquer, lacet à droite, roulis à droite. */
    private double pitchRate;
    private double yawRate;
    private double rollRate;
    /** Sens du tir, à l'horizontale : celui du carneau. */
    private final Vector3d downrange = new Vector3d(1.0, 0.0, 0.0);
    /** Axe de tangage de la fusée debout : piquer, c'est tourner autour de lui. Il pointe à droite du pilote. */
    private final Vector3d pitchAxis = new Vector3d(0.0, 0.0, 1.0);

    /** Remet la fusée debout, immobile, prête à partir dans ce sens. */
    public void reset(Direction direction) {
        orientation.identity();
        pitchRate = yawRate = rollRate = 0.0;
        if (direction != null && direction.getAxis().isHorizontal()) {
            downrange.set(direction.getStepX(), 0.0, direction.getStepZ());
        }
        UP.cross(downrange, pitchAxis);
    }

    /** Direction, dans le monde, de la trajectoire à tenir pour une assiette donnée (en degrés au-dessus de l'horizon). */
    public Vector3d course(double pitchDegrees) {
        double radians = Math.toRadians(pitchDegrees);
        return new Vector3d(downrange).mul(Math.cos(radians)).add(0.0, Math.sin(radians), 0.0);
    }

    /**
     * La même trajectoire, mais dans le cap que tient la fusée : seule l'inclinaison est imposée, pas la direction.
     * C'est la consigne d'un pilote humain. Il choisit vers où il part ; ce qu'on lui demande, c'est de coucher la
     * fusée au bon rythme. Sans cela, le moindre écart de cap, invisible quand on surveille son inclinaison, compterait
     * comme une erreur de plus en plus grande à mesure que la fusée se couche. Tant que le nez est presque à la
     * verticale, il n'a pas de cap : on garde celui du tir.
     */
    public Vector3d courseAlongNose(double pitchDegrees) {
        Vector3d nose = nose();
        double flat = Math.sqrt(nose.x * nose.x + nose.z * nose.z);
        if (flat < 0.08) {
            return course(pitchDegrees);
        }
        double radians = Math.toRadians(pitchDegrees);
        return new Vector3d(nose.x / flat, 0.0, nose.z / flat).mul(Math.cos(radians)).add(0.0, Math.sin(radians), 0.0);
    }

    /** Orientation d'une fusée qui suit exactement la trajectoire, sans roulis : celle que tient l'ordinateur de bord. */
    public static Quaternionf following(Direction direction, double pitchDegrees) {
        Vector3d along = new Vector3d(direction.getStepX(), 0.0, direction.getStepZ());
        Vector3d axis = new Vector3d(UP).cross(along);
        return new Quaternionf(new Quaterniond().rotationAxis(Math.toRadians(90.0 - pitchDegrees), axis.x, axis.y, axis.z));
    }

    /**
     * Un tick de vol aux commandes.
     *
     * @param pitch    commande de tangage : 1 pour piquer vers le sens du tir, -1 pour cabrer
     * @param yaw      commande de lacet : 1 pour la droite
     * @param roll     commande de roulis : 1 pour la droite
     * @param agility  maniabilité de la fusée, autour de 1 : une fusée haute et lourde tourne moins vite
     * @param assisted vrai en vol assisté
     * @param course   direction de la trajectoire à tenir : en vol assisté, le stabilisateur y ramène le nez
     */
    public void steer(int pitch, int yaw, int roll, double agility, boolean assisted, Vector3d course) {
        double damping = assisted ? DAMPING_ASSISTED : DAMPING_MANUAL;
        double torque = TORQUE * agility;
        // Où est la consigne, vue de la fusée : de combien il faudrait piquer, et partir à droite, pour l'atteindre.
        Vector3d seen = orientation.transformInverse(new Vector3d(course));
        double pitchGap = Math.toDegrees(Math.atan2(seen.dot(downrange), seen.y));
        double yawGap = Math.toDegrees(Math.atan2(seen.dot(pitchAxis), seen.y));
        if (assisted) {
            pitchRate = axis(pitchRate, pitch, torque, damping, pitchGap);
            yawRate = axis(yawRate, yaw, torque, damping, yawGap);
            rollRate = axis(rollRate, roll, torque, damping, Double.NaN);
        } else {
            pitchRate = manual(pitchRate, pitch, agility);
            yawRate = manual(yawRate, yaw, agility);
            rollRate = manual(rollRate, roll, agility);
        }
        // Les rotations se font autour des axes de la fusée : elles s'ajoutent à droite de son orientation.
        orientation.rotateAxis(Math.toRadians(pitchRate), pitchAxis.x, pitchAxis.y, pitchAxis.z);
        orientation.rotateAxis(Math.toRadians(-yawRate), downrange.x, downrange.y, downrange.z);
        orientation.rotateAxis(Math.toRadians(-rollRate), 0.0, 1.0, 0.0);
        orientation.normalize();
    }

    /** Un axe, à la main : la vitesse de rotation rejoint vite celle que demande la commande, et zéro sans commande. */
    private static double manual(double rate, int input, double agility) {
        double response = Math.clamp(agility, 0.5, 1.2);
        double wanted = input * MANUAL_RATE * response;
        double share = input != 0 ? MANUAL_RESPONSE * response : MANUAL_STOP;
        return Math.clamp(rate + (wanted - rate) * share, -MAX_RATE, MAX_RATE);
    }

    /** Un axe : la commande accélère la rotation ; sans commande, le stabilisateur la freine, ou vise la consigne. */
    private static double axis(double rate, int input, double torque, double damping, double gap) {
        if (input != 0) {
            rate += input * torque;
        } else if (!Double.isNaN(gap)) {
            double wanted = Math.clamp(gap * 0.15, -HOLD_RATE, HOLD_RATE);
            rate += Math.clamp(wanted - rate, -torque, torque);
        } else {
            rate *= damping;
        }
        return Math.clamp(rate, -MAX_RATE, MAX_RATE);
    }

    /** L'ordinateur de bord tient les commandes : le nez est sur la trajectoire, et la fusée ne tourne plus. */
    public void follow(Vector3d course) {
        Vector3d nose = nose();
        orientation.premul(new Quaterniond().rotationTo(nose, course)).normalize();
        pitchRate = yawRate = rollRate = 0.0;
    }

    /** Sens du tir, à l'horizontale. */
    public Vector3d downrange() {
        return new Vector3d(downrange);
    }

    /** Direction du nez de la fusée, dans le monde. */
    public Vector3d nose() {
        return orientation.transform(new Vector3d(UP));
    }

    /** Assiette : angle du nez au-dessus de l'horizon, en degrés. */
    public double pitch() {
        return Math.toDegrees(Math.asin(Math.clamp(nose().y, -1.0, 1.0)));
    }

    /** Écart entre le nez et une direction, en degrés. */
    public double errorTo(Vector3d course) {
        return Math.toDegrees(Math.acos(Math.clamp(nose().dot(course), -1.0, 1.0)));
    }

    /** Vitesse de rotation, dans le monde : un vecteur porté par l'axe de rotation, long de l'angle parcouru par tick (en radians). */
    public Vector3d angularVelocity() {
        Vector3d body = new Vector3d(pitchAxis).mul(pitchRate).sub(new Vector3d(downrange).mul(yawRate)).sub(0.0, rollRate, 0.0);
        return orientation.transform(body).mul(Math.PI / 180.0);
    }

    public Quaternionf orientation() {
        return new Quaternionf(orientation);
    }

    public double[] save() {
        return new double[] {orientation.x, orientation.y, orientation.z, orientation.w, pitchRate, yawRate, rollRate};
    }

    public void load(double[] saved) {
        if (saved.length >= 7) {
            orientation.set(saved[0], saved[1], saved[2], saved[3]).normalize();
            pitchRate = saved[4];
            yawRate = saved[5];
            rollRate = saved[6];
        }
    }
}
