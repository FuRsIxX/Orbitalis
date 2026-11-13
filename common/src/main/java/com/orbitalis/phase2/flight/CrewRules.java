package com.orbitalis.phase2.flight;

import com.orbitalis.Orbitalis;

import com.orbitalis.platform.event.EntityMountEvent;
import com.orbitalis.platform.event.LivingIncomingDamageEvent;

/**
 * Règles de l'équipage : une fois la fusée partie, on ne quitte plus la capsule avant qu'elle soit posée.
 */
public final class CrewRules {
    private CrewRules() {
    }

        public static void onDismount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof CapsuleEntity capsule
                && !capsule.isRemoved() && capsule.locksCrew() && event.getEntityMounting().isAlive()) {
            event.setCanceled(true);
        }
    }

    /** En vol, la capsule protège son équipage : ni l'explosion du lanceur ni la rentrée ne le blessent. */
        public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof CapsuleEntity capsule && capsule.locksCrew()) {
            event.setCanceled(true);
        }
    }
}
