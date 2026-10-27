package com.orbitalis.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Les bruits du vaisseau en descente, côté client, pour son équipage comme pour ceux qui le regardent : le sifflement
 * de la traversée de l'atmosphère, les parachutes qui s'ouvrent puis la voile qui claque jusqu'au sol, et le moteur
 * de descente, sur la Lune comme sur tous les autres mondes.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class CapsuleSounds {
    /** Distance au-delà de laquelle on ne suit plus un vaisseau, en blocs. */
    private static final double HEARD = 192.0;
    /**
     * Durée du son d'ouverture des parachutes, en ticks (1,64 s) : la voile prend le relais à sa fin. Elle part un
     * souffle avant qu'il s'achève, pour que les deux se fondent. À reprendre si son fichier change de longueur.
     */
    private static final int DEPLOY_TICKS = 32;
    /** Ce que l'on a vu de chaque vaisseau au tick précédent. */
    private static final Map<CapsuleEntity, Seen> SEEN = new WeakHashMap<>();

    private CapsuleSounds() {
    }

    private static final class Seen {
        private CapsuleEntity.Phase phase;
        private long chuteAt;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            SEEN.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof CapsuleEntity capsule && capsule.distanceToSqr(minecraft.player) <= HEARD * HEARD) {
                follow(minecraft, capsule);
            }
        }
    }

    private static void follow(Minecraft minecraft, CapsuleEntity capsule) {
        Seen seen = SEEN.computeIfAbsent(capsule, c -> new Seen());
        CapsuleEntity.Phase phase = capsule.getPhase();
        long now = capsule.level().getGameTime();
        if (phase == CapsuleEntity.Phase.CHUTE && seen.phase != CapsuleEntity.Phase.CHUTE) {
            if (seen.phase == null) {
                // Arrivé en pleine descente sous voile : la voile, tout de suite.
                seen.chuteAt = now - DEPLOY_TICKS;
            } else {
                // Les parachutes sortent de leur sac.
                minecraft.getSoundManager().play(new EntityBoundSoundInstance(ModSounds.PARACHUTE_DEPLOY.get(), SoundSource.BLOCKS, 4.0F, 1.0F,
                        capsule, capsule.getRandom().nextLong()));
                seen.chuteAt = now;
            }
        }
        seen.phase = phase;
        String id = Integer.toString(capsule.getId());
        if (phase == CapsuleEntity.Phase.CHUTE && now - seen.chuteAt >= DEPLOY_TICKS) {
            LoopSound.of("chute:" + id, ModSounds.PARACHUTE_DEPLOY_LOOP.get()).at(capsule::position, 6.0, 64.0).fade(0.5F, 0.05F)
                    .play(() -> capsule.isAlive() && capsule.getPhase() == CapsuleEntity.Phase.CHUTE);
        }
        if (capsule.isAtmosphericEntry()) {
            LoopSound.of("reentry:" + id, ModSounds.REENTRY_LOOP.get()).at(capsule::position, 8.0, 160.0).fade(0.05F, 0.04F)
                    .play(() -> capsule.isAlive() && capsule.isAtmosphericEntry());
        }
        if (engineOn(capsule)) {
            LoopSound.of("lander:" + id, ModSounds.LANDER_ENGINE.get()).at(capsule::position, 8.0, 128.0).fade(0.25F, 0.15F)
                    .play(() -> capsule.isAlive() && engineOn(capsule));
        }
    }

    /** Vrai tant que le moteur de descente pousse : à la main, quand le pilote l'allume ; sinon, de bout en bout. */
    private static boolean engineOn(CapsuleEntity capsule) {
        CapsuleEntity.Phase phase = capsule.getPhase();
        return phase == CapsuleEntity.Phase.POWERED_DESCENT ? capsule.isBurning()
                : phase == CapsuleEntity.Phase.LUNAR_DESCENT || phase == CapsuleEntity.Phase.LUNAR_ASCENT;
    }
}
