package com.orbitalis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;

/**
 * Sous Fabric : la lumière propre d'un élément est inscrite dans les sommets de ses faces (là où le jeu range la lumière),
 * comme le fait NeoForge. Le rendu des blocs de Fabric en tient compte ; celui du jeu, par {@link VertexConsumerMixin}.
 */
@Mixin(BlockModel.class)
public abstract class BlockModelMixin {
    /** Le format des sommets des blocs : position (3), couleur, texture (2), lumière, normale. */
    @org.spongepowered.asm.mixin.Unique
    private static final int STRIDE = 8;
    @org.spongepowered.asm.mixin.Unique
    private static final int LIGHT = 6;

    @Inject(method = "bakeFace", at = @At("RETURN"))
    private static void orbitalis$bakeLight(BlockElement element, BlockElementFace face, TextureAtlasSprite sprite, Direction direction,
            ModelState transform, CallbackInfoReturnable<BakedQuad> cir) {
        int light = ((EmissiveElement) element).orbitalis$light();
        if (light <= 0) {
            return;
        }
        int[] vertices = cir.getReturnValue().getVertices();
        int packed = LightTexture.pack(light, light);
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * STRIDE + LIGHT] = packed;
        }
    }
}
