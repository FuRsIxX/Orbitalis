package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * En 1.21.1, un bloc à entité de bloc ne dessine pas son modèle, sauf à le demander ; en 26, si. Les blocs du mod (machines,
 * réservoirs, pupitres) gardent donc leur modèle, comme en 26 ; ceux qui veulent autre chose le disent eux-mêmes.
 */
@Mixin(BaseEntityBlock.class)
public abstract class BaseEntityBlockMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void orbitalis$model(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (getClass().getName().startsWith("com.orbitalis.")) {
            cir.setReturnValue(RenderShape.MODEL);
        }
    }
}
