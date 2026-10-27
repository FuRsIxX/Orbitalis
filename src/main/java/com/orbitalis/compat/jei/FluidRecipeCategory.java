package com.orbitalis.compat.jei;

import java.util.List;

import com.orbitalis.phase1.fuel.recipe.FluidRecipe;

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
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * Affichage JEI des recettes d'une machine à fluides :
 * entrée (objet et/ou fluide) à gauche, flèche animée, fluides produits à droite.
 */
public class FluidRecipeCategory extends AbstractRecipeCategory<RecipeHolder<FluidRecipe>> {
    private static final int WIDTH = 116;
    private static final int HEIGHT = 36;

    public FluidRecipeCategory(IGuiHelper guiHelper, IRecipeType<RecipeHolder<FluidRecipe>> recipeType, ItemLike machine) {
        super(recipeType, new ItemStack(machine).getHoverName(), guiHelper.createDrawableItemLike(machine), WIDTH, HEIGHT);
    }

    @Override
    @SuppressWarnings("deprecation") // Ingredient.items() : seul moyen d'afficher la quantité requise
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FluidRecipe> holder, IFocusGroup focuses) {
        FluidRecipe recipe = holder.value();

        int x = recipe.itemInput().isPresent() && recipe.fluidInput().isPresent() ? 1 : 19;
        if (recipe.itemInput().isPresent()) {
            var ingredient = recipe.itemInput().get();
            List<ItemStack> stacks = ingredient.ingredient().items()
                    .map(item -> new ItemStack(item, ingredient.count()))
                    .toList();
            builder.addInputSlot(x, 1).setStandardSlotBackground().addItemStacks(stacks);
            x += 18;
        }
        if (recipe.fluidInput().isPresent()) {
            FluidStackTemplate fluid = recipe.fluidInput().get();
            builder.addInputSlot(x, 1)
                    .setStandardSlotBackground()
                    .setFluidRenderer(fluid.amount(), false, 16, 16)
                    .add(fluid.fluid().value(), fluid.amount());
        }

        List<FluidStackTemplate> outputs = recipe.fluidOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluidStackTemplate fluid = outputs.get(i);
            builder.addOutputSlot(79 + i * 18, 1)
                    .setStandardSlotBackground()
                    .setFluidRenderer(fluid.amount(), false, 16, 16)
                    .add(fluid.fluid().value(), fluid.amount());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<FluidRecipe> holder, IFocusGroup focuses) {
        FluidRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(46, 1);

        // Exemple : « 800 FE · 1 s »
        Component details = Component.translatable("gui.orbitalis.jei.recipe_cost", recipe.energy(), recipe.time() / 20);
        builder.addText(details, WIDTH, 10).setPosition(0, 24);
    }
}
