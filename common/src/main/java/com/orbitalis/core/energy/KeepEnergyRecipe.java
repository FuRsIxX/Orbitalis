package com.orbitalis.core.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Une recette en forme qui garde l'énergie : ce que les ingrédients avaient en réserve (un accumulateur qu'on améliore)
 * passe dans l'objet fabriqué, qui ne perd donc rien à l'amélioration ; seule sa capacité change. Pour le reste, c'est
 * une recette en forme ordinaire, que le livre de recettes et JEI montrent comme les autres.
 */
public class KeepEnergyRecipe extends ShapedRecipe {
    private final String group;
    private final CraftingBookCategory category;
    private final ShapedRecipePattern shape;
    private final ItemStack output;
    private final boolean notify;

    public KeepEnergyRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean notify) {
        super(group, category, pattern, result, notify);
        this.group = group;
        this.category = category;
        this.shape = pattern;
        this.output = result;
        this.notify = notify;
    }

    public static final MapCodec<ShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(o -> ((KeepEnergyRecipe) o).group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(o -> ((KeepEnergyRecipe) o).category),
            ShapedRecipePattern.MAP_CODEC.forGetter(o -> ((KeepEnergyRecipe) o).shape),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(o -> ((KeepEnergyRecipe) o).output),
            Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(o -> ((KeepEnergyRecipe) o).notify))
            .apply(i, KeepEnergyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, o -> ((KeepEnergyRecipe) o).group,
            CraftingBookCategory.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).category,
            ShapedRecipePattern.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).shape,
            ItemStack.STREAM_CODEC, o -> ((KeepEnergyRecipe) o).output,
            ByteBufCodecs.BOOL, o -> ((KeepEnergyRecipe) o).notify,
            KeepEnergyRecipe::new);
    public static final RecipeSerializer<ShapedRecipe> SERIALIZER = com.orbitalis.platform.Recipes.serializer(MAP_CODEC, STREAM_CODEC);

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    /** L'objet fabriqué, avec toute l'énergie que portaient les ingrédients. */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
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
