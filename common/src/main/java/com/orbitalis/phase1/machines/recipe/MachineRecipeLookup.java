package com.orbitalis.phase1.machines.recipe;

import java.util.Collection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import com.orbitalis.platform.SizedIngredient;

/**
 * Accès aux recettes de machines, côté serveur comme côté client.
 * <p>
 * Le serveur et le client lisent les mêmes recettes : le jeu les envoie au client à la connexion.
 */
public final class MachineRecipeLookup {
    private MachineRecipeLookup() {
    }

    /** Toutes les recettes d'un type de machine. */
    public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> recipes(Level level, RecipeType<T> type) {
        // En 1.21.1, le client reçoit toutes les recettes du serveur : le même gestionnaire sert des deux côtés.
        return level.getRecipeManager().getAllRecipesFor(type);
    }

    /** Vrai si l'objet sert d'ingrédient à au moins une recette de ce type de machine. */
    public static boolean isIngredient(Level level, RecipeType<MachineRecipe> type, ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        Collection<RecipeHolder<MachineRecipe>> recipes = recipes(level, type);
        // Si le client n'a pas encore reçu les recettes, il laisse le serveur décider.
        if (recipes.isEmpty() && level.isClientSide()) {
            return true;
        }
        for (RecipeHolder<MachineRecipe> holder : recipes) {
            for (SizedIngredient ingredient : holder.value().ingredients()) {
                if (ingredient.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
