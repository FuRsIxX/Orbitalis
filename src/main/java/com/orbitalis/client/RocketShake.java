package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.flight.CapsuleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Le tremblement du décollage. À bord, la cabine vibre dès l'allumage, très fort tant que la fusée est près du sol,
 * puis plus doucement pendant toute la poussée ; elle se calme entre deux étages. Près du pas de tir, le sol tremble
 * aussi sous les spectateurs, d'autant plus qu'ils sont près et que la fusée est basse. En première comme en
 * troisième personne. L'option du jeu « effets de distorsion » règle son ampleur (à zéro, plus rien ne tremble).
 * <p>
 * Au retour, la cabine tremble aussi pendant toute la traversée de l'atmosphère, d'autant plus qu'elle va vite, jusqu'à
 * l'ouverture des parachutes ; leur voile, en se gonflant, donne une dernière secousse qui s'éteint avec elle.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class RocketShake {
    /** Ampleur du tremblement à bord, en degrés : à la poussée de croisière, puis en plus près du sol. */
    private static final float CREW_CRUISE = 0.12F;
    private static final float CREW_LIFTOFF = 0.4F;
    /** Hauteur au-dessus de la table à laquelle le tremblement du décollage s'est apaisé, en blocs. */
    private static final float CALM_HEIGHT = 100.0F;
    /** Ampleur ressentie au pied du pas de tir, en degrés. */
    private static final float GROUND = 0.3F;
    /** Ampleur de la rentrée à pleine vitesse, en degrés ; celle du choc d'ouverture des parachutes. */
    private static final float ENTRY = 0.45F;
    private static final float CHUTE_JOLT = 0.3F;
    /** Vitesse de chute (en blocs par tick) sous laquelle l'air ne secoue plus la cabine, puis celle où il la secoue à plein. */
    private static final float ENTRY_CALM = 0.4F;
    private static final float ENTRY_FULL = 3.0F;

    private RocketShake() {
    }

    // Après la vue de l'équipage (voir PilotControls), qui tourne la caméra avec la cabine.
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        float amplitude = 0.0F;
        // À bord, la cabine ne tremble que sur le pas de tir et pendant la montée : en orbite, amarrée, en rentrée
        // ou posée, les moteurs du lanceur se sont tus.
        if (minecraft.player.getVehicle() instanceof CapsuleEntity capsule && capsule.getTable() != null
                && (capsule.getPhase() == CapsuleEntity.Phase.PAD || capsule.getPhase() == CapsuleEntity.Phase.ASCENT)) {
            float thrust = com.orbitalis.client.EngineSound.thrust(capsule.getTable());
            float height = (float) (capsule.getY() - capsule.getTable().getY());
            float low = Mth.clamp(1.0F - height / CALM_HEIGHT, 0.0F, 1.0F);
            amplitude = thrust * (CREW_CRUISE + CREW_LIFTOFF * low * low);
        }
        if (minecraft.player.getVehicle() instanceof CapsuleEntity capsule) {
            if (capsule.isAtmosphericEntry()) {
                // Dans l'air, bouclier en avant : d'autant plus fort que la capsule tombe vite.
                float sink = (float) (capsule.yo - capsule.getY());
                float share = Mth.clamp((sink - ENTRY_CALM) / (ENTRY_FULL - ENTRY_CALM), 0.0F, 1.0F);
                amplitude = Math.max(amplitude, ENTRY * (0.25F + 0.75F * share));
            } else if (capsule.hasOpenParachutes()) {
                // Le choc de l'ouverture : il s'éteint à mesure que la voile se gonfle.
                float left = 1.0F - capsule.chuteInflation((float) event.getPartialTick());
                amplitude = Math.max(amplitude, CHUTE_JOLT * left * left);
            }
        }
        // Le sol qui tremble : seulement pour qui n'est pas en orbite.
        if (!com.orbitalis.phase2.survival.SpaceRules.isOrbit(minecraft.level)) {
            amplitude = Math.max(amplitude, GROUND * com.orbitalis.client.EngineSound.rumble(minecraft.player.position()));
        }
        amplitude *= minecraft.options.screenEffectScale().get().floatValue();
        if (amplitude <= 0.001F) {
            return;
        }
        // Plusieurs fréquences mêlées, qui ne se répètent pas : un grondement sourd, pas une trépidation nerveuse.
        float t = (float) (minecraft.level.getGameTime() % 100_000L + event.getPartialTick());
        float yaw = 0.7F * Mth.sin(t * 1.3F) + 0.3F * Mth.sin(t * 2.9F + 1.7F);
        float pitch = 0.7F * Mth.sin(t * 1.7F + 0.4F) + 0.3F * Mth.sin(t * 3.3F);
        float roll = 0.4F * Mth.sin(t * 1.1F + 2.2F);
        event.setYaw(event.getYaw() + yaw * amplitude);
        event.setPitch(event.getPitch() + pitch * amplitude);
        event.setRoll(event.getRoll() + roll * amplitude);
    }
}
