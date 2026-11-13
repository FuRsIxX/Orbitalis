package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.recipe.FluidRecipe;
import com.orbitalis.phase1.machines.recipe.MachineRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

/**
 * Types de recettes propres au mod, avec leurs sérialiseurs.
 */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Orbitalis.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Orbitalis.MODID);

    /** Catégorie exigée par Minecraft pour toute recette ; nos recettes ne s'affichent pas dans le livre vanilla. */

    /** Recette en forme qui garde l'énergie des ingrédients : l'amélioration d'un accumulateur. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<net.minecraft.world.item.crafting.ShapedRecipe>> KEEP_ENERGY_SHAPED =
            SERIALIZERS.register("keep_energy_shaped", () -> com.orbitalis.core.energy.KeepEnergyRecipe.SERIALIZER);

    // ------------------------------------------------------------------
    // Fonderie à arc : alliages (deux ingrédients -> un lingot)
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> ALLOYING_TYPE =
            TYPES.register("alloying", RecipeType::simple);
    public static final MachineRecipe.Kind ALLOYING =
            new MachineRecipe.Kind(() -> ModRecipes.ALLOYING_TYPE.get(), () -> ModRecipes.ALLOYING_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> ALLOYING_SERIALIZER =
            SERIALIZERS.register("alloying", () -> MachineRecipe.createSerializer(ALLOYING));

    // ------------------------------------------------------------------
    // Laminoir : tôles (un lingot -> une tôle)
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> ROLLING_TYPE =
            TYPES.register("rolling", RecipeType::simple);
    public static final MachineRecipe.Kind ROLLING =
            new MachineRecipe.Kind(() -> ModRecipes.ROLLING_TYPE.get(), () -> ModRecipes.ROLLING_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> ROLLING_SERIALIZER =
            SERIALIZERS.register("rolling", () -> MachineRecipe.createSerializer(ROLLING));

    // ------------------------------------------------------------------
    // Électrolyseur : eau -> hydrogène + oxygène
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<FluidRecipe>> ELECTROLYSIS_TYPE =
            TYPES.register("electrolysis", RecipeType::simple);
    public static final FluidRecipe.Kind ELECTROLYSIS =
            new FluidRecipe.Kind(() -> ModRecipes.ELECTROLYSIS_TYPE.get(), () -> ModRecipes.ELECTROLYSIS_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FluidRecipe>> ELECTROLYSIS_SERIALIZER =
            SERIALIZERS.register("electrolysis", () -> FluidRecipe.createSerializer(ELECTROLYSIS));

    // ------------------------------------------------------------------
    // Liquéfacteur cryogénique : gaz -> liquide
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<FluidRecipe>> LIQUEFACTION_TYPE =
            TYPES.register("liquefaction", RecipeType::simple);
    public static final FluidRecipe.Kind LIQUEFACTION =
            new FluidRecipe.Kind(() -> ModRecipes.LIQUEFACTION_TYPE.get(), () -> ModRecipes.LIQUEFACTION_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FluidRecipe>> LIQUEFACTION_SERIALIZER =
            SERIALIZERS.register("liquefaction", () -> FluidRecipe.createSerializer(LIQUEFACTION));

    // ------------------------------------------------------------------
    // Raffinerie : charbon -> kérosène RP-1
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<FluidRecipe>> REFINING_TYPE =
            TYPES.register("refining", RecipeType::simple);
    public static final FluidRecipe.Kind REFINING =
            new FluidRecipe.Kind(() -> ModRecipes.REFINING_TYPE.get(), () -> ModRecipes.REFINING_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FluidRecipe>> REFINING_SERIALIZER =
            SERIALIZERS.register("refining", () -> FluidRecipe.createSerializer(REFINING));

    // ------------------------------------------------------------------
    // Réacteur de Sabatier : carbone + hydrogène -> méthane + eau
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<FluidRecipe>> SABATIER_TYPE =
            TYPES.register("sabatier", RecipeType::simple);
    public static final FluidRecipe.Kind SABATIER =
            new FluidRecipe.Kind(() -> ModRecipes.SABATIER_TYPE.get(), () -> ModRecipes.SABATIER_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FluidRecipe>> SABATIER_SERIALIZER =
            SERIALIZERS.register("sabatier", () -> FluidRecipe.createSerializer(SABATIER));

    // ------------------------------------------------------------------
    // Extracteur d'hélium-3 : régolithe lunaire -> hélium-3 + hydrogène
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<FluidRecipe>> HELIUM3_TYPE =
            TYPES.register("helium3_extraction", RecipeType::simple);
    public static final FluidRecipe.Kind HELIUM3 =
            new FluidRecipe.Kind(() -> ModRecipes.HELIUM3_TYPE.get(), () -> ModRecipes.HELIUM3_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FluidRecipe>> HELIUM3_SERIALIZER =
            SERIALIZERS.register("helium3_extraction", () -> FluidRecipe.createSerializer(HELIUM3));

    // ------------------------------------------------------------------
    // Chambre de Casimir : un objet -> la matière exotique
    // ------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> CASIMIR_TYPE =
            TYPES.register("casimir", RecipeType::simple);
    public static final MachineRecipe.Kind CASIMIR =
            new MachineRecipe.Kind(() -> ModRecipes.CASIMIR_TYPE.get(), () -> ModRecipes.CASIMIR_SERIALIZER.get());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> CASIMIR_SERIALIZER =
            SERIALIZERS.register("casimir", () -> MachineRecipe.createSerializer(CASIMIR));

    private ModRecipes() {
    }


    public static void register() {
        TYPES.register();
        SERIALIZERS.register();
    }
}
