package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.orbitalis.platform.event.Events;

import net.minecraft.world.entity.Entity;

/** Monter sur une entité, en descendre : le mod peut s'y opposer (l'équipage reste assis pendant le vol). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private Entity vehicle;

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void orbitalis$mount(Entity mounted, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (!Events.allowMount((Entity) (Object) this, mounted, true)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"), cancellable = true)
    private void orbitalis$dismount(CallbackInfo ci) {
        Entity mounted = vehicle;
        if (mounted != null && !mounted.isRemoved() && !Events.allowMount((Entity) (Object) this, mounted, false)) {
            ci.cancel();
        }
    }
}
