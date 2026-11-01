package com.orbitalis.phase1.machines;

import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une machine cassée emporte son contenu sur son objet : ses objets, ses fluides et son énergie. Il n'y a qu'un seul
 * exemplaire de ce contenu à la fois, sur le bloc ou sur l'objet, jamais les deux : la machine ne lâche plus rien au
 * sol en disparaissant.
 * <p>
 * En mode créatif, un bloc cassé ne tombe pas. Comme une boîte de Shulker, une machine qui contient quelque chose
 * tombe quand même, avec son contenu, pour qu'il ne se perde pas.
 */
public final class MachineDrops {
    private MachineDrops() {
    }

    /** À appeler depuis {@code playerWillDestroy} : en mode créatif, une machine pleine tombe avec son contenu. */
    public static void keepInCreative(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide() || !player.preventsBlockDrops()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        DataComponentMap components = blockEntity.collectComponents();
        if (!components.has(DataComponents.CONTAINER) && !components.has(ModDataComponents.MACHINE_FLUIDS.get())
                && !components.has(ModDataComponents.FLUID_CONTENT.get())) {
            return;
        }
        ItemStack stack = new ItemStack(state.getBlock());
        stack.applyComponents(components);
        Block.popResource(level, pos, stack);
    }
}
