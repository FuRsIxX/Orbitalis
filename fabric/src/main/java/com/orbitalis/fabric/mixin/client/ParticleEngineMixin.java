package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.client.render.TankParticles;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Sous Fabric : les éclats d'un réservoir qu'on frappe ou qu'on casse ont la couleur de son contenu (comme sous NeoForge). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    protected ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void orbitalis$destroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (state.is(ModBlocks.FLUID_TANK.get()) && TankParticles.INSTANCE.addDestroyEffects(state, level, pos, (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void orbitalis$crack(BlockPos pos, Direction side, CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.FLUID_TANK.get()) && TankParticles.INSTANCE.addHitEffects(state, level,
                new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false), (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }
}
