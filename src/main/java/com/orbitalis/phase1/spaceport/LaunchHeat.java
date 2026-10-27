package com.orbitalis.phase1.spaceport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Chaleur d'un lancement : les gaz des moteurs brûlent ce qui s'en approche.
 * <p>
 * De l'allumage jusqu'à ce que la fusée soit haute, trois zones sont dangereuses, de la plus chaude à la moins
 * chaude : le pied de la fusée, où frappe le dard des moteurs ; le jet du carneau, qui porte loin au-delà de sa
 * bouche et s'élargit en s'éloignant ; et le nuage brûlant qui roule sur le tablier et ses abords. Les créatures
 * qui s'y trouvent sont blessées deux fois par seconde, et prennent feu dans les deux premières. Un joueur en mode
 * créatif ne craint rien, pas plus que l'équipage, à l'abri dans sa capsule.
 */
final class LaunchHeat {
    /** Intervalle entre deux brûlures, en ticks. */
    private static final int INTERVAL = 10;
    /** Durée pendant laquelle les gaz frappent encore le pas de tir après le décollage, en secondes. */
    private static final float GROUND_SECONDS = 7.0F;

    private LaunchHeat() {
    }

    /** Puissance des moteurs au sol, de 0 (rien ne brûle) à 1. */
    private static float strength(LaunchTableBlockEntity table) {
        long ticks = table.phaseTicks();
        return switch (table.getPhase()) {
            case COUNTDOWN -> {
                float remaining = (LaunchTableBlockEntity.COUNTDOWN_TICKS - ticks) / 20.0F;
                float ignition = LaunchTableBlockEntity.IGNITION_TICKS / 20.0F;
                yield table.hasRocket() && remaining < ignition ? Math.min(1.0F, (ignition - remaining) / 1.5F) : 0.0F;
            }
            case FLIGHT -> Math.max(0.0F, 1.0F - ticks / 20.0F / GROUND_SECONDS);
            default -> 0.0F;
        };
    }

    static void tick(ServerLevel level, LaunchTableBlockEntity table) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        float strength = strength(table);
        if (strength <= 0.0F) {
            return;
        }
        BlockPos pos = table.getBlockPos();
        Direction trench = table.getTrenchDirection();
        int radius = Math.max(1, table.getComplex().radius());
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        // Le dard frappe au pied de la fusée ; le jet sort du carneau et porte d'autant plus loin que le pas de tir est grand.
        double core = table.rocketHalfWidth() + 1.5;
        double plume = radius + (4.0 + 2.0 * radius) * strength;
        double cloud = radius + 2.5;
        double reach = Math.max(plume, cloud) + 2.0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(reach, 8.0, reach),
                candidate -> candidate.isAlive() && !candidate.isSpectator() && !candidate.isPassenger() && !candidate.fireImmune())) {
            double dx = entity.getX() - x;
            double dz = entity.getZ() - z;
            double dy = entity.getY() - y;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double along = dx * trench.getStepX() + dz * trench.getStepZ();
            double across = Math.abs(dz * trench.getStepX() - dx * trench.getStepZ());
            float damage = 0.0F;
            boolean ignite = false;
            if (distance < core && dy > -3.0 && dy < 4.0) {
                damage = 14.0F;
                ignite = true;
            } else if (along > 0.0 && along < plume && across < 1.5 + along * 0.15 && dy > -3.0 && dy < 2.0 + along * 0.3) {
                // Le jet se refroidit en s'éloignant de la table.
                damage = (float) (9.0 - 6.0 * along / plume);
                ignite = true;
            } else if (distance < cloud && dy > -3.0 && dy < 4.0) {
                damage = 3.0F;
            }
            if (damage > 0.0F) {
                entity.hurtServer(level, level.damageSources().inFire(), damage * strength);
                if (ignite) {
                    entity.igniteForSeconds(4.0F);
                }
            }
        }
    }
}
