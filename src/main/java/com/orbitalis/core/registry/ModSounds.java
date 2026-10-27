package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons propres au mod : le pas de tir, le vol et la cabine, la station. Leurs fichiers sont ceux de
 * {@code tools/sounds/orbitalis sound/}, convertis et ramenés à leur niveau par {@code tools/generate_sounds.py} ; pour
 * en remplacer un, voir {@code tools/sounds/LISEZ-MOI.md}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Orbitalis.MODID);

    /** Purge : un long chuintement de gaz, quand les lignes se vident. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_PURGE = register("pad_purge");
    /** Vanne : un claquement suivi d'un souffle bref. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_VALVE = register("pad_valve");
    /** Hydraulique : le gémissement des vérins qui rangent les bras. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_HYDRAULIC = register("pad_hydraulic");
    /** Déluge : le grondement de l'eau qui noie le pas de tir. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_DELUGE = register("pad_deluge");
    /** Le déluge, en boucle sans couture : il joue tant que l'eau coule. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_DELUGE_LOOP = register("pad_deluge_loop");
    /** Allumage : le grondement des moteurs dans le carneau, qui enfle puis s'éloigne. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_IGNITION = register("pad_ignition");
    /** Grincement : l'acier des bras et des passerelles qui travaille quand ils se rangent. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_CREAK = register("pad_creak");


    /**
     * Grondement des moteurs en vol : une boucle, que le jeu répète tant qu'ils brûlent, et qui s'éteint quand la
     * fusée entre dans l'espace. Jouée côté client par {@code EngineSound}.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_ENGINE = register("pad_engine");

    /**
     * Explosion d'une fusée, telle qu'on l'entend selon la distance : de près, de loin, de très loin. Une seule est
     * jouée par accident, par {@code BreakupFx} ; chacune a ses variantes, tirées au hasard. Leurs fichiers sont ceux
     * de {@code tools/sounds/explosion/}.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_NEAR = register("explosion_near");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_MID = register("explosion_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_FAR = register("explosion_far");
    /** Les tôles qui s'écrasent au sol : tronçons projetés par l'explosion, épave qui retombe, capsule qui s'écrase. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DEBRIS_FALL = register("debris_fall");

    /**
     * Surpression : la montée en pression des réservoirs, vannes oubliées ouvertes. La table de lancement le lance
     * pour qu'il s'achève à l'instant où ils cèdent, et le fait taire si les vannes sont refermées à temps.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAD_OVERPRESSURE = register("pad_overpressure");
    /** Alarme du pas de tir : la surpression passe 70 %, et elle sonne jusqu'à ce que les vannes se ferment, ou que tout cède. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_OVERPRESSURE = register("warning_overpressure");
    /** Pièce de fusée posée : un « clang » métallique, en cinq variantes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PART_PLACED = register("part_placed");

    // --- Le vol et la cabine ---
    /** Séparation d'un étage : le claquement des boulons pyrotechniques. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STAGE_SEPARATION = register("stage_separation");
    /** Largage de la coiffe. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FAIRING_JETTISON = register("fairing_jettison");
    /** Sifflement de la traversée de l'atmosphère, en boucle (voir {@code CapsuleSounds}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> REENTRY_LOOP = register("reentry_loop");
    /** Ouverture des parachutes ; la voile prend le relais, en boucle, jusqu'au sol. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARACHUTE_DEPLOY = register("parachute_deploy");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARACHUTE_DEPLOY_LOOP = register("parachute_deploy_loop");
    /** Le vaisseau touche le sol, sur la planète comme sur les autres mondes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_LANDING = register("capsule_landing");
    /** Moteur de descente, en boucle, sur la Lune comme sur les autres mondes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LANDER_ENGINE = register("lander_engine");
    /** Alertes de bord (voir {@code CockpitAlerts}) : elles peuvent sonner ensemble. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_ALERT_MANEUVER = register("capsule_alert_maneuver");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_ALERT_IMMINENT_CRASH = register("capsule_alert_imminent_crash");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_ALERT_STALL = register("capsule_alert_stall");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_ALERT_DEPLOY = register("capsule_alert_deploy");

    // --- La station ---
    /** Mise à niveau de l'air du sas. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_CYCLE = register("airlock_cycle");
    /** L'écoutille motorisée du sas qui monte, puis qui redescend. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_HATCH_OPEN = register("airlock_hatch_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_HATCH_CLOSE = register("airlock_hatch_close");
    /** Amarrage de la capsule à la station, et désamarrage. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCKING = register("docking");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNDOCKING = register("undocking");
    /** Ronronnement d'un générateur d'air en service, en boucle (voir {@code StationSounds}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFE_SUPPORT_HUM = register("life_support_hum");
    /** Ambiance des pièces pressurisées d'une station, en boucle. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_AMBIENT = register("station_ambient");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Orbitalis.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
