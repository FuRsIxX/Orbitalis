package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.spaceport.ElevatorCabinEntity;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

/**
 * Registre des entités du mod.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Orbitalis.MODID);

    /** Capsule habitée : créée par la table de lancement, jamais par une commande. */
    public static final DeferredHolder<EntityType<?>, EntityType<CapsuleEntity>> CAPSULE = ENTITIES.register("capsule",
            () -> EntityType.Builder.<CapsuleEntity>of(CapsuleEntity::new, MobCategory.MISC)
                    .sized(1.75F, 2.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSummon()
                    .build(Orbitalis.id("capsule").toString()));

    /** Cabine de l'ascenseur de la tour de service : créée par son socle. */
    public static final DeferredHolder<EntityType<?>, EntityType<ElevatorCabinEntity>> ELEVATOR_CABIN = ENTITIES.register("elevator_cabin",
            () -> EntityType.Builder.<ElevatorCabinEntity>of(ElevatorCabinEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.0625F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .noSummon()
                    .build(Orbitalis.id("elevator_cabin").toString()));

    /** Rover : un véhicule conduisible, posé par son objet (voir RoverItem). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.orbitalis.phase3.exploration.RoverEntity>> ROVER = ENTITIES.register("rover",
            () -> EntityType.Builder.<com.orbitalis.phase3.exploration.RoverEntity>of(com.orbitalis.phase3.exploration.RoverEntity::new, MobCategory.MISC)
                    .sized(1.75F, 0.9F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(Orbitalis.id("rover").toString()));

    /** Drone volant : un giravion autonome, posé par son objet (voir RotorDroneItem). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.orbitalis.phase3.exploration.RotorDroneEntity>> ROTOR_DRONE = ENTITIES.register("rotor_drone",
            () -> EntityType.Builder.<com.orbitalis.phase3.exploration.RotorDroneEntity>of(com.orbitalis.phase3.exploration.RotorDroneEntity::new, MobCategory.MISC)
                    .sized(1.4F, 0.9F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(Orbitalis.id("rotor_drone").toString()));

    private ModEntities() {
    }

    public static void register() {
        ENTITIES.register();
    }
}
