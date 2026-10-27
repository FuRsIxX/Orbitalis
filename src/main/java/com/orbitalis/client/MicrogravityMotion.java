package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.survival.Microgravity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Nage en apesanteur, pour le joueur de ce client (voir {@link Microgravity}).
 * <p>
 * Le jeu donne déjà la poussée horizontale de la marche, sans pesanteur puisque le serveur l'a ôtée. On y ajoute,
 * avant le mouvement, la composante verticale du regard (on nage là où l'on regarde) et les touches de saut et
 * d'accroupissement ; après le mouvement, le freinage de l'eau sur les trois axes. Le vol créatif reste coupé.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class MicrogravityMotion {
    /** Vrai tant que ce client tient lui-même la posture du joueur. */
    private static boolean posing;

    private MicrogravityMotion() {
    }

    @SubscribeEvent
    static void beforeTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (!Microgravity.applies(player)) {
            if (posing) {
                posing = false;
                player.setForcedPose(null);
            }
            return;
        }
        // Pas de vol créatif en apesanteur : on nage.
        player.getAbilities().flying = false;
        double lift = 0.0;
        // Les propulseurs d'une combinaison avancée poussent bien plus fort que les bras.
        boolean thrusters = com.orbitalis.phase2.survival.SpaceSuits.has(player, com.orbitalis.phase2.survival.SuitModule.THRUSTERS);
        double boost = thrusters ? Microgravity.THRUSTER_BOOST : 1.0;
        float forward = player.input.getMoveVector().y;
        float strafe = player.input.getMoveVector().x;
        double push = (player.isSprinting() ? Microgravity.SWIM_SPRINT : Microgravity.SWIM) * boost;
        if (forward != 0.0F) {
            lift -= Mth.sin(player.getXRot() * Mth.DEG_TO_RAD) * push * forward;
        }
        if ((forward != 0.0F || strafe != 0.0F) && !player.onGround()) {
            // Le jeu pousse déjà un peu, à plat, dans le sens des touches ; on y ajoute le reste de la nage (et la part des
            // propulseurs), dans le même sens.
            double extra = push - (player.isSprinting() ? Microgravity.AIR_PUSH_SPRINT : Microgravity.AIR_PUSH);
            double length = Math.max(1.0, Math.sqrt(forward * forward + strafe * strafe));
            double sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
            double cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
            double x = (strafe * cos - forward * sin) / length * extra;
            double z = (forward * cos + strafe * sin) / length * extra;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x + x, motion.y, motion.z + z);
        }
        if (player.input.keyPresses.jump()) {
            lift += Microgravity.LIFT * boost;
        }
        if (player.input.keyPresses.shift()) {
            lift -= Microgravity.LIFT * boost;
        }
        if (lift != 0.0) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y + lift, motion.z);
        }
    }

    @SubscribeEvent
    static void afterTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !Microgravity.applies(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement().scale(Microgravity.DRAG);
        player.setDeltaMovement(motion.x, Mth.clamp(motion.y, -Microgravity.MAX_VERTICAL, Microgravity.MAX_VERTICAL), motion.z);
        player.setForcedPose(Microgravity.pose(player));
        posing = true;
    }
}
