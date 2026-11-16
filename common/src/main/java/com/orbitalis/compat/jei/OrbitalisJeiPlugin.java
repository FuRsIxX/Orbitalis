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
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Plugin JEI d'Orbitalis. Cette classe n'est chargée que si JEI est installé :
 * NeoForge la trouve par l'annotation, Fabric par le point d'entrée « jei_mod_plugin ».
 * Elle déclare une catégorie de recettes par machine de transformation.
 */
@JeiPlugin
public class OrbitalisJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Orbitalis.id("jei_plugin");

    private RecipeType<RecipeHolder<MachineRecipe>> alloying;
    private RecipeType<RecipeHolder<MachineRecipe>> rolling;
    private RecipeType<RecipeHolder<MachineRecipe>> casimir;
    private RecipeType<RecipeHolder<FluidRecipe>> electrolysis;
    private RecipeType<RecipeHolder<FluidRecipe>> liquefaction;
    private RecipeType<RecipeHolder<FluidRecipe>> refining;
    private RecipeType<RecipeHolder<FluidRecipe>> sabatier;
    private RecipeType<RecipeHolder<FluidRecipe>> helium3;

    /** JEI une fois prêt, et le plugin lui-même : seulement pour l'outil de capture. */
    private static IJeiRuntime runtime;
    private static OrbitalisJeiPlugin instance;

    public OrbitalisJeiPlugin() {
        instance = this;
    }

    /** Outil de capture : ouvre les recettes de la fonderie à arc (« alloying ») ou de l'électrolyseur. */
    public static boolean showForPreview(String category) {
        if (runtime == null || instance == null) {
            return false;
        }
        RecipeType<?> type = "electrolysis".equals(category) ? instance.electrolysis : instance.alloying;
        runtime.getRecipesGui().showTypes(List.of(type));
        return true;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        alloying = RecipeType.createFromVanilla(ModRecipes.ALLOYING_TYPE.get());
        rolling = RecipeType.createFromVanilla(ModRecipes.ROLLING_TYPE.get());
        casimir = RecipeType.createFromVanilla(ModRecipes.CASIMIR_TYPE.get());
        electrolysis = RecipeType.createFromVanilla(ModRecipes.ELECTROLYSIS_TYPE.get());
        liquefaction = RecipeType.createFromVanilla(ModRecipes.LIQUEFACTION_TYPE.get());
        refining = RecipeType.createFromVanilla(ModRecipes.REFINING_TYPE.get());
        sabatier = RecipeType.createFromVanilla(ModRecipes.SABATIER_TYPE.get());
        helium3 = RecipeType.createFromVanilla(ModRecipes.HELIUM3_TYPE.get());

        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        // JEI compte les fluides dans l'unité du chargeur : un seau vaut 1000 sous NeoForge, 81000 sous Fabric.
        long unit = registration.getJeiHelpers().getPlatformFluidHelper().bucketVolume() / 1000;
        registration.addRecipeCategories(
                new MachineRecipeCategory(guiHelper, alloying, ModBlocks.ARC_FOUNDRY),
                new MachineRecipeCategory(guiHelper, rolling, ModBlocks.ROLLING_MILL),
                new MachineRecipeCategory(guiHelper, casimir, ModBlocks.CASIMIR_CHAMBER),
                new FluidRecipeCategory(guiHelper, electrolysis, ModBlocks.ELECTROLYZER, unit),
                new FluidRecipeCategory(guiHelper, liquefaction, ModBlocks.CRYOGENIC_LIQUEFIER, unit),
                new FluidRecipeCategory(guiHelper, refining, ModBlocks.REFINERY, unit),
                new FluidRecipeCategory(guiHelper, sabatier, ModBlocks.SABATIER_REACTOR, unit),
                new FluidRecipeCategory(guiHelper, helium3, ModBlocks.HELIUM3_EXTRACTOR, unit));
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
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        OrbitalisJeiPlugin.runtime = runtime;
        List<ItemStack> hidden = com.orbitalis.core.Features.hiddenItems().stream().map(ItemStack::new).toList();
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }

    /** Indique à JEI quelle machine réalise chaque catégorie de recettes. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(alloying, ModBlocks.ARC_FOUNDRY);
        // Les fours électriques cuisent les recettes du four du jeu.
        registration.addRecipeCatalysts(RecipeTypes.SMELTING, ModBlocks.INDUCTION_FURNACE, ModBlocks.MELTING_FURNACE);
        registration.addRecipeCatalysts(rolling, ModBlocks.ROLLING_MILL);
        registration.addRecipeCatalysts(casimir, ModBlocks.CASIMIR_CHAMBER);
        registration.addRecipeCatalysts(electrolysis, ModBlocks.ELECTROLYZER);
        registration.addRecipeCatalysts(liquefaction, ModBlocks.CRYOGENIC_LIQUEFIER);
        registration.addRecipeCatalysts(refining, ModBlocks.REFINERY);
        registration.addRecipeCatalysts(sabatier, ModBlocks.SABATIER_REACTOR);
        registration.addRecipeCatalysts(helium3, ModBlocks.HELIUM3_EXTRACTOR);
    }
}
