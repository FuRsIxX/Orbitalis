package com.orbitalis.compat.jei;

import java.util.List;

import com.orbitalis.phase1.machines.recipe.MachineRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Affichage JEI des recettes d'une machine de transformation :
 * ingrédients à gauche, flèche animée, résultat à droite, énergie et durée en dessous.
 */
public class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
    private static final int WIDTH = 116;
    private static final int HEIGHT = 36;

    public MachineRecipeCategory(IGuiHelper guiHelper, IRecipeType<RecipeHolder<MachineRecipe>> recipeType, ItemLike machine) {
        super(recipeType, new ItemStack(machine).getHoverName(), guiHelper.createDrawableItemLike(machine), WIDTH, HEIGHT);
    }

    @Override
    @SuppressWarnings("deprecation") // Ingredient.items() : seul moyen d'afficher la quantité requise
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();

        // Ingrédients : chaque emplacement montre tous les objets acceptés, avec la quantité requise.
        List<SizedIngredient> ingredients = recipe.ingredients();
        int startX = ingredients.size() == 1 ? 19 : 1;
        for (int i = 0; i < ingredients.size(); i++) {
            SizedIngredient ingredient = ingredients.get(i);
            List<ItemStack> stacks = ingredient.ingredient().items()
                    .map(item -> new ItemStack(item, ingredient.count()))
                    .toList();
            builder.addInputSlot(startX + i * 18, 1)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
        }

        builder.addOutputSlot(91, 1)
                .setOutputSlotBackground()
                .add(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(50, 1);

        // Exemple : « 4000 FE · 10 s »
        Component details = Component.translatable("gui.orbitalis.jei.recipe_cost", recipe.energy(), recipe.time() / 20);
        builder.addText(details, WIDTH, 10).setPosition(0, 24);
    }
}
