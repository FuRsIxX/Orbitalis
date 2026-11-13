package com.orbitalis.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Un ingrédient de recette en nombre : {"ingredient": …, "count": …}. */
public record SizedIngredient(Ingredient ingredient, int count) {
    public static final Codec<SizedIngredient> NESTED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(SizedIngredient::ingredient),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(SizedIngredient::count))
            .apply(instance, SizedIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SizedIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SizedIngredient::ingredient,
            ByteBufCodecs.VAR_INT, SizedIngredient::count,
            SizedIngredient::new);

    /** Vrai si la pile est de l'ingrédient, en assez grand nombre. */
    public boolean test(ItemStack stack) {
        return ingredient.test(stack) && stack.getCount() >= count;
    }

    /** Les piles qui conviennent, au bon nombre (pour l'affichage). */
    public ItemStack[] getItems() {
        ItemStack[] items = ingredient.getItems();
        ItemStack[] sized = new ItemStack[items.length];
        for (int k = 0; k < items.length; k++) {
            sized[k] = items[k].copyWithCount(count);
        }
        return sized;
    }
}
