package com.orbitalis.phase1.fuel.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Entrées d'une machine à fluides présentées à une recette : un objet et un fluide (l'un ou l'autre peut être vide).
 */
public record FluidRecipeInput(ItemStack item, FluidStack fluid) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return item;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return item.isEmpty() && fluid.isEmpty();
    }
}
