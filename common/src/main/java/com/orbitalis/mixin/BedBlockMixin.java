package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hors de la Terre, un lit ne sert à rien mais n'explose pas (comme en 26.2, où la règle des lits le disait) : on dort dans
 * une couchette d'équipage ou un caisson d'hibernation.
 */
@Mixin(BedBlock.class)
public abstract class BedBlockMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void orbitalis$noSleepInSpace(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (level.dimension().location().getNamespace().equals(Orbitalis.MODID) && !level.dimensionType().bedWorks()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("bed.orbitalis.no_sleep"), true);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide()));
        }
    }
}
