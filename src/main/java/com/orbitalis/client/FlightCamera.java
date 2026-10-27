package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * La caméra de la montée. Au décollage, la vue passe à l'extérieur de la fusée, pour la voir monter ; tant que le
 * joueur ne touche pas à la souris, elle tourne lentement autour d'elle, un peu au-dessus, comme une caméra de
 * retransmission. Il reprend la main d'un geste ; la vue d'origine revient quand la cabine quitte sa fusée.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class FlightCamera {
    /** Ticks sans geste de la souris avant que la caméra ne tourne d'elle-même. */
    private static final int IDLE_TICKS = 40;
    /** Vitesse de rotation autour de la fusée, en degrés par tick, et inclinaison vers laquelle la vue glisse. */
    private static final float ORBIT_SPEED = 0.18F;
    private static final float ORBIT_PITCH = 8.0F;

    private static boolean ascending;
    private static CameraType before;
    private static float lastYaw;
    private static float lastPitch;
    private static int still;

    private FlightCamera() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        CapsuleEntity capsule = PilotControls.cabin();
        Player player = minecraft.player;
        boolean now = capsule != null && capsule.isFollowingRocket() && player != null;
        if (now && !ascending) {
            before = minecraft.options.getCameraType();
            if (before.isFirstPerson()) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            still = 0;
            lastYaw = player.getYRot();
            lastPitch = player.getXRot();
        } else if (!now && ascending) {
            if (before != null && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(before);
            }
            before = null;
        }
        ascending = now;
        if (!now || minecraft.isPaused() || minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        // Un geste de la souris rend la main au joueur ; immobile, la caméra reprend sa ronde.
        if (Math.abs(yaw - lastYaw) > 0.01F || Math.abs(pitch - lastPitch) > 0.01F) {
            still = 0;
        } else if (++still > IDLE_TICKS) {
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYRot(yaw + ORBIT_SPEED);
            player.setXRot(pitch + (ORBIT_PITCH - pitch) * 0.02F);
        }
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
    }
}
