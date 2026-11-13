package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.platform.Compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Juste avant qu'un bloc soit remplacé par un autre, son entité de bloc peut encore agir (rendre son contenu, prévenir son
 * réseau) : c'est ce que faisait {@code preRemoveSideEffects} en 26.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void orbitalis$preRemove(Level level, BlockPos pos, BlockState newState, boolean movedByPiston, CallbackInfo ci) {
        BlockState self = (BlockState) (Object) this;
        if (!level.isClientSide() && !self.is(newState.getBlock()) && self.hasBlockEntity()
                && level.getBlockEntity(pos) instanceof Compat.RemovalAware aware) {
            aware.preRemoveSideEffects(pos, self);
        }
    }
}
