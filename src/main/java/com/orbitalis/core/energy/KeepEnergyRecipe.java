package com.orbitalis.core.energy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Une recette en forme qui garde l'énergie : ce que les ingrédients avaient en réserve (un accumulateur qu'on améliore)
 * passe dans l'objet fabriqué, qui ne perd donc rien à l'amélioration ; seule sa capacité change. Pour le reste, c'est
 * une recette en forme ordinaire, que le livre de recettes et JEI montrent comme les autres.
 */
public class KeepEnergyRecipe extends ShapedRecipe {
    private final ItemStackTemplate output;

    public KeepEnergyRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
            ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.output = result;
    }

    public static final MapCodec<ShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> ((KeepEnergyRecipe) o).commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> ((KeepEnergyRecipe) o).bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> ((KeepEnergyRecipe) o).output))
            .apply(i, KeepEnergyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).bookInfo,
            ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
            ItemStackTemplate.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).output,
            KeepEnergyRecipe::new);
    public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return SERIALIZER;
    }

    /** L'objet fabriqué, avec toute l'énergie que portaient les ingrédients. */
    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = super.assemble(input);
        long energy = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            Integer stored = input.getItem(slot).get(ModDataComponents.STORED_ENERGY.get());
            if (stored != null) {
                energy += stored;
            }
        }
        if (energy > 0) {
            result.set(ModDataComponents.STORED_ENERGY.get(), (int) Math.min(Integer.MAX_VALUE, energy));
        }
        return result;
    }
}
