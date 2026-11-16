package com.orbitalis.compat.jei;

import java.util.Arrays;
import java.util.List;

import com.orbitalis.phase1.machines.recipe.MachineRecipe;
import com.orbitalis.platform.SizedIngredient;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;

/**
 * Affichage JEI des recettes d'une machine de transformation :
 * ingrédients à gauche, flèche animée, résultat à droite, énergie et durée en dessous.
 */
public class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
    private static final int WIDTH = 116;
    private static final int HEIGHT = 36;

    public MachineRecipeCategory(IGuiHelper guiHelper, RecipeType<RecipeHolder<MachineRecipe>> recipeType, ItemLike machine) {
        super(recipeType, new ItemStack(machine).getHoverName(), guiHelper.createDrawableItemLike(machine), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();

        // Ingrédients : chaque emplacement montre tous les objets acceptés, avec la quantité requise.
        List<SizedIngredient> ingredients = recipe.ingredients();
        int startX = ingredients.size() == 1 ? 19 : 1;
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addInputSlot(startX + i * 18, 1)
                    .setStandardSlotBackground()
                    .addItemStacks(Arrays.asList(ingredients.get(i).getItems()));
        }

        builder.addOutputSlot(91, 1)
                .setOutputSlotBackground()
                .addItemStack(recipe.result().create());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrow(recipe.time()).setPosition(50, 1);

        // Exemple : « 4000 FE · 10 s »
        Component details = Component.translatable("gui.orbitalis.jei.recipe_cost", recipe.energy(), recipe.time() / 20);
        builder.addText(details, WIDTH, 10).setPosition(0, 24);
    }
}
