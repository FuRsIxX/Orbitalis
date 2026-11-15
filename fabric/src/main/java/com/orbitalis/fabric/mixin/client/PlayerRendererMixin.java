package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.client.port.event.ClientEvents;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** Sous Fabric : le mod peut empêcher le dessin d'un joueur (couché dans une fusée qui pique du nez). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void orbitalis$render(AbstractClientPlayer player, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        if (!ClientEvents.renderPlayer(player)) {
            ci.cancel();
        }
    }
}
