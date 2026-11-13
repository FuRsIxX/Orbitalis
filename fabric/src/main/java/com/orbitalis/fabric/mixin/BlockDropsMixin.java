package com.orbitalis.fabric.mixin;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.orbitalis.platform.event.BlockDropsEvent;
import com.orbitalis.platform.event.Events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sous Fabric : ce que laisse tomber un bloc cassé passe par le mod (les centres spatiaux abandonnés rendent de la ferraille). */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @ModifyExpressionValue(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<ItemStack> orbitalis$drops(List<ItemStack> drops, BlockState state, Level level, BlockPos pos,
            @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        List<ItemStack> list = new ArrayList<>(drops);
        Events.drops(new BlockDropsEvent((ServerLevel) level, pos, state, blockEntity, breaker, list));
        return list;
    }
}
