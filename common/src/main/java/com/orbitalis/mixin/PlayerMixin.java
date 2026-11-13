package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.platform.Compat;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** En apesanteur, la posture du joueur est imposée par le mod au lieu d'être calculée par le jeu. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void orbitalis$forcedPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        Pose pose = Compat.forcedPose(player);
        if (pose != null) {
            player.setPose(pose);
            ci.cancel();
        }
    }
}
