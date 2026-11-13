package com.orbitalis.platform;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Le résultat d'une recette : un objet, son nombre et ses composants ({"id": …, "count": …}), qu'on recopie à chaque usage. */
public record ItemStackTemplate(ItemStack stack) {
    public static final Codec<ItemStackTemplate> CODEC = ItemStack.STRICT_CODEC.xmap(ItemStackTemplate::new, ItemStackTemplate::stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStackTemplate> STREAM_CODEC = ItemStack.STREAM_CODEC
            .map(ItemStackTemplate::new, ItemStackTemplate::stack);

    public ItemStack create() {
        return stack.copy();
    }

    public Item item() {
        return stack.getItem();
    }

    public int count() {
        return stack.getCount();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
