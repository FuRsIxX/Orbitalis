package com.orbitalis.compat.jei;

import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModRecipes;
import com.orbitalis.phase1.fuel.recipe.FluidRecipe;
import com.orbitalis.phase1.machines.recipe.MachineRecipe;
import com.orbitalis.phase1.machines.recipe.MachineRecipeLookup;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Plugin JEI d'Orbitalis. Cette classe n'est chargée que si JEI est installé.
 * Elle déclare une catégorie de recettes par machine de transformation.
 */
@JeiPlugin
public class OrbitalisJeiPlugin implements IModPlugin {
    private static final Identifier UID = Orbitalis.id("jei_plugin");

    private IRecipeType<RecipeHolder<MachineRecipe>> alloying;
    private IRecipeType<RecipeHolder<MachineRecipe>> rolling;
    private IRecipeType<RecipeHolder<MachineRecipe>> casimir;
    private IRecipeType<RecipeHolder<FluidRecipe>> electrolysis;
    private IRecipeType<RecipeHolder<FluidRecipe>> liquefaction;
    private IRecipeType<RecipeHolder<FluidRecipe>> refining;
    private IRecipeType<RecipeHolder<FluidRecipe>> sabatier;
    private IRecipeType<RecipeHolder<FluidRecipe>> helium3;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        alloying = IRecipeType.create(ModRecipes.ALLOYING_TYPE.get());
        rolling = IRecipeType.create(ModRecipes.ROLLING_TYPE.get());
        casimir = IRecipeType.create(ModRecipes.CASIMIR_TYPE.get());
        electrolysis = IRecipeType.create(ModRecipes.ELECTROLYSIS_TYPE.get());
        liquefaction = IRecipeType.create(ModRecipes.LIQUEFACTION_TYPE.get());
        refining = IRecipeType.create(ModRecipes.REFINING_TYPE.get());
        sabatier = IRecipeType.create(ModRecipes.SABATIER_TYPE.get());
        helium3 = IRecipeType.create(ModRecipes.HELIUM3_TYPE.get());

        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MachineRecipeCategory(guiHelper, alloying, ModBlocks.ARC_FOUNDRY),
                new MachineRecipeCategory(guiHelper, rolling, ModBlocks.ROLLING_MILL),
                new MachineRecipeCategory(guiHelper, casimir, ModBlocks.CASIMIR_CHAMBER),
                new FluidRecipeCategory(guiHelper, electrolysis, ModBlocks.ELECTROLYZER),
                new FluidRecipeCategory(guiHelper, liquefaction, ModBlocks.CRYOGENIC_LIQUEFIER),
                new FluidRecipeCategory(guiHelper, refining, ModBlocks.REFINERY),
                new FluidRecipeCategory(guiHelper, sabatier, ModBlocks.SABATIER_REACTOR),
                new FluidRecipeCategory(guiHelper, helium3, ModBlocks.HELIUM3_EXTRACTOR));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        registration.addRecipes(alloying, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.ALLOYING_TYPE.get())));
        registration.addRecipes(rolling, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.ROLLING_TYPE.get())));
        registration.addRecipes(casimir, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.CASIMIR_TYPE.get())));
        registration.addRecipes(electrolysis, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.ELECTROLYSIS_TYPE.get())));
        registration.addRecipes(liquefaction, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.LIQUEFACTION_TYPE.get())));
        registration.addRecipes(refining, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.REFINING_TYPE.get())));
        registration.addRecipes(sabatier, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.SABATIER_TYPE.get())));
        registration.addRecipes(helium3, List.copyOf(MachineRecipeLookup.recipes(level, ModRecipes.HELIUM3_TYPE.get())));
    }

    /** Le contenu en réserve pour une prochaine version n'apparaît pas dans JEI. */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        java.util.List<net.minecraft.world.item.ItemStack> hidden = com.orbitalis.core.Features.hiddenItems().stream()
                .map(net.minecraft.world.item.ItemStack::new).toList();
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hidden);
        }
    }

    /** Indique à JEI quelle machine réalise chaque catégorie de recettes. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(alloying, ModBlocks.ARC_FOUNDRY);
        registration.addCraftingStation(rolling, ModBlocks.ROLLING_MILL);
        registration.addCraftingStation(casimir, ModBlocks.CASIMIR_CHAMBER);
        registration.addCraftingStation(electrolysis, ModBlocks.ELECTROLYZER);
        registration.addCraftingStation(liquefaction, ModBlocks.CRYOGENIC_LIQUEFIER);
        registration.addCraftingStation(refining, ModBlocks.REFINERY);
        registration.addCraftingStation(sabatier, ModBlocks.SABATIER_REACTOR);
        registration.addCraftingStation(helium3, ModBlocks.HELIUM3_EXTRACTOR);
    }
}
