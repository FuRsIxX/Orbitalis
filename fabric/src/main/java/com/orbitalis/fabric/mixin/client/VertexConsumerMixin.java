package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Sous Fabric : quand le jeu dessine une face (objets, blocs dessinés par un rendu), la lumière inscrite dans ses sommets
 * (voir {@link BlockModelMixin}) l'emporte sur celle du monde si elle est plus forte, comme sous NeoForge.
 */
@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
    @Inject(method = "putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V",
            at = @At("HEAD"))
    private void orbitalis$bakedLight(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha,
            int[] lightmap, int overlay, boolean readAlpha, CallbackInfo ci) {
        int[] vertices = quad.getVertices();
        int count = Math.min(lightmap.length, vertices.length / 8);
        for (int vertex = 0; vertex < count; vertex++) {
            int baked = vertices[vertex * 8 + 6];
            if (baked != 0) {
                int world = lightmap[vertex];
                lightmap[vertex] = LightTexture.pack(Math.max(LightTexture.block(world), LightTexture.block(baked)),
                        Math.max(LightTexture.sky(world), LightTexture.sky(baked)));
            }
        }
    }
}
