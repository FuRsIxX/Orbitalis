package com.orbitalis.phase1.machines.recipe;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModRecipes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Recette d'une machine de transformation : un ou plusieurs ingrédients (avec quantité),
 * un résultat, une énergie totale (FE) et une durée (ticks).
 * <p>
 * La même classe sert à toutes les machines ; c'est le {@link Kind} qui indique à quelle machine
 * appartient la recette (fonderie à arc, laminoir...).
 */
public class MachineRecipe implements Recipe<MachineRecipeInput> {
    /** Famille de recettes : associe le type de recette et son sérialiseur. */
    public record Kind(Supplier<RecipeType<MachineRecipe>> type, Supplier<RecipeSerializer<MachineRecipe>> serializer) {
    }

    private final Kind kind;
    private final List<SizedIngredient> ingredients;
    private final ItemStackTemplate result;
    private final int energy;
    private final int time;

    public MachineRecipe(Kind kind, List<SizedIngredient> ingredients, ItemStackTemplate result, int energy, int time) {
        this.kind = kind;
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.energy = energy;
        this.time = time;
    }

    public List<SizedIngredient> ingredients() {
        return ingredients;
    }

    public ItemStackTemplate result() {
        return result;
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

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        return assignSlots(input) != null;
    }

    /**
     * Associe chaque ingrédient à un emplacement d'entrée distinct qui le contient en quantité suffisante.
     * L'ordre des objets dans la machine n'a donc pas d'importance.
     *
     * @return pour chaque ingrédient, l'indice de son emplacement ; {@code null} si la recette ne correspond pas
     */
    public int[] assignSlots(MachineRecipeInput input) {
        int[] slots = new int[ingredients.size()];
        boolean[] used = new boolean[input.size()];
        if (!assign(0, input, slots, used)) {
            return null;
        }
        // Un emplacement occupé par un objet étranger à la recette l'invalide.
        for (int slot = 0; slot < input.size(); slot++) {
            if (!used[slot] && !input.getItem(slot).isEmpty()) {
                return null;
            }
        }
        return slots;
    }

    private boolean assign(int ingredientIndex, MachineRecipeInput input, int[] slots, boolean[] used) {
        if (ingredientIndex == ingredients.size()) {
            return true;
        }
        SizedIngredient ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            if (!used[slot] && ingredient.test(input.getItem(slot))) {
                used[slot] = true;
                slots[ingredientIndex] = slot;
                if (assign(ingredientIndex + 1, input, slots, used)) {
                    return true;
                }
                used[slot] = false;
            }
        }
        return false;
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input) {
        return result.create();
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
    public RecipeSerializer<MachineRecipe> getSerializer() {
        return kind.serializer().get();
    }

    @Override
    public RecipeType<MachineRecipe> getType() {
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
    public static RecipeSerializer<MachineRecipe> createSerializer(Kind kind) {
        MapCodec<MachineRecipe> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SizedIngredient.NESTED_CODEC.listOf(1, 2).fieldOf("ingredients").forGetter(MachineRecipe::ingredients),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(MachineRecipe::result),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy").forGetter(MachineRecipe::energy),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("time").forGetter(MachineRecipe::time))
                .apply(instance, (ingredients, result, energy, time) -> new MachineRecipe(kind, ingredients, result, energy, time)));

        StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec = StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MachineRecipe::ingredients,
                ItemStackTemplate.STREAM_CODEC, MachineRecipe::result,
                ByteBufCodecs.VAR_INT, MachineRecipe::energy,
                ByteBufCodecs.VAR_INT, MachineRecipe::time,
                (ingredients, result, energy, time) -> new MachineRecipe(kind, ingredients, result, energy, time));

        return new RecipeSerializer<>(codec, streamCodec);
    }
}
