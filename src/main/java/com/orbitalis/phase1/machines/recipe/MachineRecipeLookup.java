package com.orbitalis.phase1.machines.recipe;

import java.util.Collection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Accès aux recettes de machines, côté serveur comme côté client.
 * <p>
 * Le serveur lit ses recettes directement. Le client, lui, ne connaît que la copie envoyée par le
 * serveur (voir {@link MachineRecipeSync}), rangée ici par {@code ClientRecipes}.
 */
public final class MachineRecipeLookup {
    private static RecipeMap clientRecipes = RecipeMap.EMPTY;

    private MachineRecipeLookup() {
    }

    /** Appelé côté client à la réception des recettes envoyées par le serveur. */
    public static void setClientRecipes(RecipeMap recipes) {
        clientRecipes = recipes;
    }

    /** Toutes les recettes d'un type de machine. */
    public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> recipes(Level level, RecipeType<T> type) {
        RecipeMap recipes = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess().recipeMap() : clientRecipes;
        return recipes.byType(type);
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
