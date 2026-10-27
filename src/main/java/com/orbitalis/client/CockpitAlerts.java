package com.orbitalis.client;

import com.orbitalis.core.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Les alertes de bord, que l'affichage du pilote déclenche selon ce qu'il voit. Chacune a son son, et elles peuvent
 * sonner ensemble :
 * <ul>
 * <li>manœuvre : une étape à faire dont la fenêtre se referme, une poussée à lancer, des attaches à lâcher ; elle se
 * répète tant que rien n'est fait ;</li>
 * <li>crash imminent : la structure qui cède, une descente trop rapide, le sol qui arrive sans parachutes ;</li>
 * <li>décrochage : le vaisseau trop penché, qu'il faut redresser vite ;</li>
 * <li>parachutes : la fenêtre de leur ouverture est là, et ils ne sont pas armés.</li>
 * </ul>
 * Les trois dernières sont des boucles, qui s'éteignent dès que le danger passe.
 */
final class CockpitAlerts {
    static final int MANEUVER = 1;
    static final int CRASH = 2;
    static final int STALL = 4;
    static final int DEPLOY = 8;
    /** L'alerte de manœuvre se répète tous les 2,5 s : son fichier dure 2,18 s. */
    private static final int MANEUVER_REPEAT = 50;
    /**
     * Ticks pendant lesquels une alerte tient encore après que l'affichage a cessé de la demander : à moins de vingt
     * images par seconde, il peut sauter un tick sans que le danger soit passé.
     */
    private static final int HOLD = 4;
    private static final int[] LAST = new int[4];
    private static int clock;
    private static int maneuverWait;

    private CockpitAlerts() {
    }

    /** À chaque tick : les alertes que l'affichage vient de demander. */
    static void tick(int alerts) {
        clock++;
        for (int k = 0; k < LAST.length; k++) {
            if ((alerts & 1 << k) != 0) {
                LAST[k] = clock;
            }
        }
        loop(CRASH, ModSounds.CAPSULE_ALERT_IMMINENT_CRASH.get(), "alert:crash");
        loop(STALL, ModSounds.CAPSULE_ALERT_STALL.get(), "alert:stall");
        loop(DEPLOY, ModSounds.CAPSULE_ALERT_DEPLOY.get(), "alert:deploy");
        if (on(MANEUVER)) {
            if (maneuverWait-- <= 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CAPSULE_ALERT_MANEUVER.get(), 1.0F, 1.0F));
                maneuverWait = MANEUVER_REPEAT;
            }
        } else {
            maneuverWait = 0;
        }
    }

    private static boolean on(int bit) {
        int k = Integer.numberOfTrailingZeros(bit);
        return LAST[k] > 0 && clock - LAST[k] <= HOLD;
    }

    private static void loop(int bit, SoundEvent event, String key) {
        if (on(bit)) {
            LoopSound.of(key, event).in(SoundSource.UI).volume(1.0F).fade(0.5F, 0.15F).play(() -> on(bit));
        }
    }
}
