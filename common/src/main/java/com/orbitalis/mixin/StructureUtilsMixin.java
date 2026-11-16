package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Les tests en jeu ne sont pas enfermés dans des murs de barrière, comme en 26.2 : un module de station s'accroche au-delà
 * de la structure du test, une fusée en vol la quitte. Les tests restent écartés (voir {@link StructureGridSpawnerMixin}).
 */
@Mixin(StructureUtils.class)
public abstract class StructureUtilsMixin {
    @Inject(method = "encaseStructure", at = @At("HEAD"), cancellable = true)
    private static void orbitalis$noWalls(AABB bounds, ServerLevel level, boolean ceiling, CallbackInfo ci) {
        ci.cancel();
    }
}
