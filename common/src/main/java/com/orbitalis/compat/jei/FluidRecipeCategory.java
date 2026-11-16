package com.orbitalis.compat.jei;

import java.util.Arrays;
import java.util.List;

import com.orbitalis.phase1.fuel.recipe.FluidRecipe;
import com.orbitalis.platform.fluid.FluidStackTemplate;

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
 * Affichage JEI des recettes d'une machine à fluides :
 * entrée (objet et/ou fluide) à gauche, flèche animée, fluides produits à droite.
 */
public class FluidRecipeCategory extends AbstractRecipeCategory<RecipeHolder<FluidRecipe>> {
    private static final int WIDTH = 116;
    private static final int HEIGHT = 36;

    /** Unités de fluide de JEI par millibucket : 1 sous NeoForge, 81 sous Fabric (gouttes). */
    private final long unit;

    public FluidRecipeCategory(IGuiHelper guiHelper, RecipeType<RecipeHolder<FluidRecipe>> recipeType, ItemLike machine, long unit) {
        super(recipeType, new ItemStack(machine).getHoverName(), guiHelper.createDrawableItemLike(machine), WIDTH, HEIGHT);
        this.unit = unit;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FluidRecipe> holder, IFocusGroup focuses) {
        FluidRecipe recipe = holder.value();

        int x = recipe.itemInput().isPresent() && recipe.fluidInput().isPresent() ? 1 : 19;
        if (recipe.itemInput().isPresent()) {
            builder.addInputSlot(x, 1).setStandardSlotBackground().addItemStacks(Arrays.asList(recipe.itemInput().get().getItems()));
            x += 18;
        }
        if (recipe.fluidInput().isPresent()) {
            FluidStackTemplate fluid = recipe.fluidInput().get();
            builder.addInputSlot(x, 1)
                    .setStandardSlotBackground()
                    .setFluidRenderer(fluid.amount() * unit, false, 16, 16)
                    .addFluidStack(fluid.fluid(), fluid.amount() * unit);
        }

        List<FluidStackTemplate> outputs = recipe.fluidOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluidStackTemplate fluid = outputs.get(i);
            builder.addOutputSlot(79 + i * 18, 1)
                    .setStandardSlotBackground()
                    .setFluidRenderer(fluid.amount() * unit, false, 16, 16)
                    .addFluidStack(fluid.fluid(), fluid.amount() * unit);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<FluidRecipe> holder, IFocusGroup focuses) {
        FluidRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrow(recipe.time()).setPosition(46, 1);

        // Exemple : « 800 FE · 1 s »
        Component details = Component.translatable("gui.orbitalis.jei.recipe_cost", recipe.energy(), recipe.time() / 20);
        builder.addText(details, WIDTH, 10).setPosition(0, 24);
    }
}
