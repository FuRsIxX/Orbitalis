package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.serialization.Lifecycle;

import net.minecraft.world.level.storage.PrimaryLevelData;

/**
 * Les mondes du mod ont leurs dimensions dans ses données : 1.21.1 les dit alors « expérimentaux », et demande de le
 * confirmer (avec une sauvegarde) à chaque ouverture. NeoForge cache cet avertissement ; on le cache aussi sous Fabric.
 */
@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin {
    @Inject(method = "worldGenSettingsLifecycle", at = @At("RETURN"), cancellable = true)
    private void orbitalis$stable(CallbackInfoReturnable<Lifecycle> cir) {
        if (cir.getReturnValue() == Lifecycle.experimental()) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
