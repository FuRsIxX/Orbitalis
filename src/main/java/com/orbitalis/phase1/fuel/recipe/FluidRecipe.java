package com.orbitalis.phase1.fuel.recipe;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModRecipes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * Recette d'une machine à fluides : une entrée (objet et/ou fluide), une ou deux sorties fluides,
 * une énergie totale (FE) et une durée (ticks).
 * <p>
 * La même classe sert à l'électrolyseur, au liquéfacteur et à la raffinerie ; le {@link Kind}
 * indique à quelle machine appartient la recette.
 */
public class FluidRecipe implements Recipe<FluidRecipeInput> {
    /** Famille de recettes : associe le type de recette et son sérialiseur. */
    public record Kind(Supplier<RecipeType<FluidRecipe>> type, Supplier<RecipeSerializer<FluidRecipe>> serializer) {
    }

    private final Kind kind;
    private final Optional<SizedIngredient> itemInput;
    private final Optional<FluidStackTemplate> fluidInput;
    private final List<FluidStackTemplate> fluidOutputs;
    private final int energy;
    private final int time;

    public FluidRecipe(Kind kind, Optional<SizedIngredient> itemInput, Optional<FluidStackTemplate> fluidInput,
            List<FluidStackTemplate> fluidOutputs, int energy, int time) {
        this.kind = kind;
        this.itemInput = itemInput;
        this.fluidInput = fluidInput;
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.energy = energy;
        this.time = time;
    }

    public Optional<SizedIngredient> itemInput() {
        return itemInput;
    }

    public Optional<FluidStackTemplate> fluidInput() {
        return fluidInput;
    }

    public List<FluidStackTemplate> fluidOutputs() {
        return fluidOutputs;
    }

    /** Énergie totale consommée par la recette, en FE. */
    public int energy() {
        return energy;
    }

    /** Durée de la recette, en ticks (20 ticks = 1 seconde). */
    public int time() {
        return time;
    }

    /** Énergie consommée à chaque tick de fonctionnement. */
    public int energyPerTick() {
        return Math.max(1, energy / Math.max(1, time));
    }

    /** Vrai si la recette consomme ce fluide. */
    public boolean usesFluid(Fluid fluid) {
        return fluidInput.isPresent() && fluidInput.get().fluid().value() == fluid;
    }

    /** Vrai si la recette consomme cet objet. */
    public boolean usesItem(ItemStack stack) {
        return itemInput.isPresent() && itemInput.get().ingredient().test(stack);
    }

    @Override
    public boolean matches(FluidRecipeInput input, Level level) {
        boolean itemMatches = itemInput.map(ingredient -> ingredient.test(input.item())).orElse(input.item().isEmpty());
        boolean fluidMatches = fluidInput
                .map(fluid -> input.fluid().getFluid() == fluid.fluid().value() && input.fluid().getAmount() >= fluid.amount())
                .orElse(true);
        return itemMatches && fluidMatches;
    }

    /** Ces recettes ne produisent que des fluides. */
    @Override
    public ItemStack assemble(FluidRecipeInput input) {
        return ItemStack.EMPTY;
    }

    /** Recette « spéciale » : elle n'apparaît pas dans le livre de recettes vanilla (JEI la présente). */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<FluidRecipe> getSerializer() {
        return kind.serializer().get();
    }

    @Override
    public RecipeType<FluidRecipe> getType() {
        return kind.type().get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.MACHINES_CATEGORY.get();
    }

    /** Crée le sérialiseur (fichiers JSON et réseau) d'une famille de recettes. */
    public static RecipeSerializer<FluidRecipe> createSerializer(Kind kind) {
        MapCodec<FluidRecipe> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SizedIngredient.NESTED_CODEC.optionalFieldOf("item_input").forGetter(FluidRecipe::itemInput),
                FluidStackTemplate.CODEC.optionalFieldOf("fluid_input").forGetter(FluidRecipe::fluidInput),
                FluidStackTemplate.CODEC.listOf(1, 2).fieldOf("fluid_outputs").forGetter(FluidRecipe::fluidOutputs),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy").forGetter(FluidRecipe::energy),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("time").forGetter(FluidRecipe::time))
                .apply(instance, (item, fluid, outputs, energy, time) -> new FluidRecipe(kind, item, fluid, outputs, energy, time)));

        StreamCodec<RegistryFriendlyByteBuf, FluidRecipe> streamCodec = StreamCodec.composite(
                ByteBufCodecs.optional(SizedIngredient.STREAM_CODEC), FluidRecipe::itemInput,
                ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), FluidRecipe::fluidInput,
                FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), FluidRecipe::fluidOutputs,
                ByteBufCodecs.VAR_INT, FluidRecipe::energy,
                ByteBufCodecs.VAR_INT, FluidRecipe::time,
                (item, fluid, outputs, energy, time) -> new FluidRecipe(kind, item, fluid, outputs, energy, time));

        return new RecipeSerializer<>(codec, streamCodec);
    }
}
