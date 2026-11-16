package com.orbitalis.fabric.mixin.client;

import java.lang.reflect.Type;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.renderer.block.model.BlockElement;

/**
 * Sous Fabric : la lumière propre d'un élément de modèle (« light_emission », que 1.21.1 ne lit pas ; NeoForge lit la même
 * chose dans « neoforge_data »). Les faces des voyants, des écrans et des feux de mâts brillent ainsi dans le noir.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class BlockElementDeserializerMixin {
    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockElement;",
            at = @At("RETURN"))
    private void orbitalis$light(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<BlockElement> cir) {
        if (json instanceof JsonObject object && object.has("light_emission")) {
            int light = Math.max(0, Math.min(15, object.get("light_emission").getAsInt()));
            ((EmissiveElement) cir.getReturnValue()).orbitalis$setLight(light);
        }
    }
}
